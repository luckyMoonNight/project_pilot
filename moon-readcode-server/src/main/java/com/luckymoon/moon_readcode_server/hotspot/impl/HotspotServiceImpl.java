package com.luckymoon.moon_readcode_server.hotspot.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckymoon.moon_readcode_server.entity.HotspotDocument;
import com.luckymoon.moon_readcode_server.entity.HotspotTopic;
import com.luckymoon.moon_readcode_server.entity.QaHistory;
import com.luckymoon.moon_readcode_server.hotspot.HotspotService;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotAnalyzeResult;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotDocumentDto;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotTopicDto;
import com.luckymoon.moon_readcode_server.mapper.HotspotDocumentMapper;
import com.luckymoon.moon_readcode_server.mapper.HotspotTopicMapper;
import com.luckymoon.moon_readcode_server.mapper.QaHistoryMapper;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 热点问题分析与文档生成实现。
 *
 * 聚类算法：基于问题 embedding 向量做简单的贪心聚类（避免引入额外 ML 依赖）。
 * 相似度阈值内的问题归为同一组，每组提取主题名称和分析结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotspotServiceImpl implements HotspotService {

    private static final int MIN_QUESTIONS_FOR_ANALYZE = 5;
    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final int MAX_TOPICS = 10;

    private final QaHistoryMapper qaHistoryMapper;
    private final HotspotTopicMapper hotspotTopicMapper;
    private final HotspotDocumentMapper hotspotDocumentMapper;
    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public HotspotAnalyzeResult analyze(String projectId) {
        List<QaHistory> histories = qaHistoryMapper.selectByProjectId(projectId);

        if (histories.size() < MIN_QUESTIONS_FOR_ANALYZE) {
            return HotspotAnalyzeResult.builder()
                    .totalQuestions(histories.size())
                    .topicCount(0)
                    .topics(List.of())
                    .message("问答记录不足 " + MIN_QUESTIONS_FOR_ANALYZE + " 条，暂不分析（当前 " + histories.size() + " 条）")
                    .build();
        }

        // 清除该项目旧的 PENDING 主题（避免重复分析堆积）
        hotspotTopicMapper.deleteByProjectId(projectId);

        // 聚类：将相似的问题归为一组
        List<List<QaHistory>> clusters = clusterQuestions(histories);

        // 为每个聚类生成热点主题
        List<HotspotTopicDto> topicDtos = new ArrayList<>();
        for (List<QaHistory> cluster : clusters) {
            if (topicDtos.size() >= MAX_TOPICS) break;
            if (cluster.size() < 2) continue; // 只有一次被问到的不算热点

            HotspotTopic topic = buildTopic(projectId, cluster);
            hotspotTopicMapper.insert(topic);

            topicDtos.add(toTopicDto(topic, null));
        }

        // 如果没有聚类出热点，但有足够问题，也尝试生成 top 问题作为主题
        if (topicDtos.isEmpty() && histories.size() >= MIN_QUESTIONS_FOR_ANALYZE) {
            HotspotTopic singleTopic = buildSingleTopicFromAll(projectId, histories);
            hotspotTopicMapper.insert(singleTopic);
            topicDtos.add(toTopicDto(singleTopic, null));
        }

        log.info("热点分析完成 | projectId={} | 问答记录 {} 条 | 生成 {} 个主题",
                projectId, histories.size(), topicDtos.size());

        return HotspotAnalyzeResult.builder()
                .totalQuestions(histories.size())
                .topicCount(topicDtos.size())
                .topics(topicDtos)
                .message("分析完成，生成 " + topicDtos.size() + " 个热点主题，请审核")
                .build();
    }

    @Override
    public List<HotspotTopicDto> listTopics(String projectId) {
        List<HotspotTopic> topics = hotspotTopicMapper.selectByProjectId(projectId);
        return topics.stream().map(t -> {
            HotspotDocument doc = hotspotDocumentMapper.selectByTopicId(t.getId());
            return toTopicDto(t, doc == null ? null : doc.getId());
        }).toList();
    }

    @Override
    @Transactional
    public HotspotDocumentDto approve(Long topicId) {
        HotspotTopic topic = hotspotTopicMapper.selectById(topicId);
        if (topic == null) throw new IllegalArgumentException("主题不存在: " + topicId);

        topic.setStatus("APPROVED");
        hotspotTopicMapper.updateById(topic);

        return generateDocument(topic);
    }

    @Override
    @Transactional
    public void reject(Long topicId, String reason) {
        HotspotTopic topic = hotspotTopicMapper.selectById(topicId);
        if (topic == null) throw new IllegalArgumentException("主题不存在: " + topicId);

        topic.setStatus("REJECTED");
        topic.setRejectReason(reason);
        hotspotTopicMapper.updateById(topic);
        log.info("热点主题被驳回 | topicId={} | reason={}", topicId, reason);
    }

    @Override
    @Transactional
    public HotspotDocumentDto revise(Long topicId, String revisedAnalysis) {
        HotspotTopic topic = hotspotTopicMapper.selectById(topicId);
        if (topic == null) throw new IllegalArgumentException("主题不存在: " + topicId);

        topic.setStatus("REVISED");
        topic.setAnalysis(revisedAnalysis);
        hotspotTopicMapper.updateById(topic);

        return generateDocument(topic);
    }

    @Override
    public List<HotspotDocumentDto> listDocuments(String projectId) {
        List<HotspotDocument> docs = hotspotDocumentMapper.selectByProjectId(projectId);
        return docs.stream().map(d -> {
            HotspotTopic topic = hotspotTopicMapper.selectById(d.getTopicId());
            return toDocumentDto(d, topic == null ? "" : topic.getTopicName());
        }).toList();
    }

    @Override
    public HotspotDocumentDto getDocument(Long documentId) {
        HotspotDocument doc = hotspotDocumentMapper.selectById(documentId);
        if (doc == null) throw new IllegalArgumentException("文档不存在: " + documentId);
        HotspotTopic topic = hotspotTopicMapper.selectById(doc.getTopicId());
        return toDocumentDto(doc, topic == null ? "" : topic.getTopicName());
    }

    /* ================================================================ */
    /*                          聚类逻辑                                */
    /* ================================================================ */

    /**
     * 简单贪心聚类：遍历所有问答，如果当前问题和某个已有聚类的中心问题相似度 >= 阈值，
     * 就归入该聚类；否则新建一个聚类。
     */
    private List<List<QaHistory>> clusterQuestions(List<QaHistory> histories) {
        List<List<QaHistory>> clusters = new ArrayList<>();
        List<float[]> clusterCenters = new ArrayList<>();

        for (QaHistory history : histories) {
            float[] vector = parseVector(history.getQuestionVector());
            if (vector == null || vector.length == 0) continue;

            boolean assigned = false;
            for (int i = 0; i < clusterCenters.size(); i++) {
                double similarity = cosineSimilarity(vector, clusterCenters.get(i));
                if (similarity >= SIMILARITY_THRESHOLD) {
                    clusters.get(i).add(history);
                    assigned = true;
                    break;
                }
            }

            if (!assigned) {
                List<QaHistory> newCluster = new ArrayList<>();
                newCluster.add(history);
                clusters.add(newCluster);
                clusterCenters.add(vector);
            }
        }

        // 按聚类大小降序排列
        clusters.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return clusters;
    }

    private float[] parseVector(String vectorJson) {
        if (vectorJson == null || vectorJson.isBlank()) return null;
        try {
            // 格式可能是 "[0.1, 0.2, ...]" 或 java Arrays.toString 格式
            String cleaned = vectorJson.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            String[] parts = cleaned.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            log.debug("解析向量失败: {}", e.getMessage());
            return null;
        }
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) return 0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    /* ================================================================ */
    /*                        主题 & 文档生成                            */
    /* ================================================================ */

    private HotspotTopic buildTopic(String projectId, List<QaHistory> cluster) {
        List<String> questions = cluster.stream()
                .map(QaHistory::getQuestion)
                .toList();

        // 收集涉及的代码模块
        Set<String> modules = new HashSet<>();
        for (QaHistory h : cluster) {
            if (h.getHitRefs() != null) {
                Arrays.stream(h.getHitRefs().replaceAll("[\\[\\]]", "").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(modules::add);
            }
        }

        // 用 LLM 为这组问题生成主题名称和分析结论
        String questionsText = questions.stream()
                .limit(10)
                .collect(Collectors.joining("\n- ", "- ", ""));

        String systemPrompt = "你是一名代码分析专家。请根据以下一组相似的用户问题，提取出一个精准的主题名称（10字以内），" +
                "并给出分析结论（说明用户关注的焦点、涉及的代码模块和可能的改进建议）。" +
                "格式要求：第一行是主题名称，从第三行开始是分析结论。";

        String userPrompt = "以下是用户关于同一主题反复提问的问题列表（共 " + cluster.size() + " 次）：\n" + questionsText;

        String llmResult = llmService.complete(systemPrompt, userPrompt);
        String[] lines = llmResult.split("\n", 3);
        String topicName = lines[0].trim();
        String analysis = lines.length > 2 ? lines[2].trim() : llmResult;

        HotspotTopic topic = new HotspotTopic();
        topic.setProjectId(projectId);
        topic.setTopicName(topicName);
        topic.setQuestionCount(cluster.size());
        topic.setRepresentativeQuestions(toJson(questions.stream().limit(5).toList()));
        topic.setInvolvedModules(toJson(modules.stream().limit(10).toList()));
        topic.setAnalysis(analysis);
        topic.setStatus("PENDING");
        return topic;
    }

    /** 当聚类没有产出多条聚类时，把所有问题作为一个整体让 LLM 分析 */
    private HotspotTopic buildSingleTopicFromAll(String projectId, List<QaHistory> histories) {
        List<String> questions = histories.stream()
                .map(QaHistory::getQuestion)
                .limit(20)
                .toList();

        Set<String> modules = new HashSet<>();
        for (QaHistory h : histories) {
            if (h.getHitRefs() != null) {
                Arrays.stream(h.getHitRefs().replaceAll("[\\[\\]]", "").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(modules::add);
            }
        }

        String systemPrompt = "你是一名代码分析专家。以下是用户对某个项目提出的所有问题。" +
                "请总结出用户最关注的核心主题（10字以内），并给出分析结论。" +
                "格式要求：第一行是主题名称，从第三行开始是分析结论。";
        String userPrompt = "用户问题列表：\n" + questions.stream()
                .collect(Collectors.joining("\n- ", "- ", ""));

        String llmResult = llmService.complete(systemPrompt, userPrompt);
        String[] lines = llmResult.split("\n", 3);

        HotspotTopic topic = new HotspotTopic();
        topic.setProjectId(projectId);
        topic.setTopicName(lines[0].trim());
        topic.setQuestionCount(histories.size());
        topic.setRepresentativeQuestions(toJson(questions.stream().limit(5).toList()));
        topic.setInvolvedModules(toJson(modules.stream().limit(10).toList()));
        topic.setAnalysis(lines.length > 2 ? lines[2].trim() : llmResult);
        topic.setStatus("PENDING");
        return topic;
    }

    /** 审核通过后，用 LLM 基于热点主题生成正式文档 */
    private HotspotDocumentDto generateDocument(HotspotTopic topic) {
        // 检查是否已有文档
        HotspotDocument existing = hotspotDocumentMapper.selectByTopicId(topic.getId());
        if (existing != null) {
            existing.setStatus("GENERATED");
            hotspotDocumentMapper.updateById(existing);
            return toDocumentDto(existing, topic.getTopicName());
        }

        String systemPrompt = """
                你是一名技术文档写手。请根据以下热点分析结论，生成一篇结构清晰的 Markdown 技术文档。
                文档需要包含：
                1. 概述：这个热点问题的背景
                2. 核心结论：直接给出分析结论
                3. 涉及的代码模块及其职责
                4. 建议与改进方向
                不要虚构内容，严格基于提供的分析结论来撰写。
                """;

        String userPrompt = """
                # 热点主题：%s
                
                ## 被问次数：%d 次
                
                ## 代表性问题
                %s
                
                ## 涉及模块
                %s
                
                ## 分析结论
                %s
                """.formatted(
                topic.getTopicName(),
                topic.getQuestionCount(),
                topic.getRepresentativeQuestions(),
                topic.getInvolvedModules(),
                topic.getAnalysis()
        );

        String content = llmService.complete(systemPrompt, userPrompt);

        HotspotDocument document = new HotspotDocument();
        document.setProjectId(topic.getProjectId());
        document.setTopicId(topic.getId());
        document.setTitle("热点分析 · " + topic.getTopicName());
        document.setContent(content);
        document.setStatus("GENERATED");
        hotspotDocumentMapper.insert(document);

        log.info("文档已生成 | topicId={} | docId={} | title={}", topic.getId(), document.getId(), document.getTitle());
        return toDocumentDto(document, topic.getTopicName());
    }

    /* ================================================================ */
    /*                          DTO 转换                                */
    /* ================================================================ */

    private HotspotTopicDto toTopicDto(HotspotTopic topic, Long documentId) {
        return HotspotTopicDto.builder()
                .id(topic.getId())
                .projectId(topic.getProjectId())
                .topicName(topic.getTopicName())
                .questionCount(topic.getQuestionCount())
                .representativeQuestions(parseJsonList(topic.getRepresentativeQuestions()))
                .involvedModules(parseJsonList(topic.getInvolvedModules()))
                .analysis(topic.getAnalysis())
                .status(topic.getStatus())
                .rejectReason(topic.getRejectReason())
                .createTime(topic.getCreateTime())
                .documentId(documentId)
                .build();
    }

    private HotspotDocumentDto toDocumentDto(HotspotDocument doc, String topicName) {
        return HotspotDocumentDto.builder()
                .id(doc.getId())
                .projectId(doc.getProjectId())
                .topicId(doc.getTopicId())
                .topicName(topicName)
                .title(doc.getTitle())
                .content(doc.getContent())
                .status(doc.getStatus())
                .createTime(doc.getCreateTime())
                .build();
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
