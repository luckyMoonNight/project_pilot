package com.luckymoon.moon_readcode_server.qa.impl;

import com.luckymoon.moon_readcode_server.entity.QaHistory;
import com.luckymoon.moon_readcode_server.mapper.QaHistoryMapper;
import com.luckymoon.moon_readcode_server.qa.QaService;
import com.luckymoon.moon_readcode_server.qa.dto.QaRequest;
import com.luckymoon.moon_readcode_server.qa.dto.QaResponse;
import com.luckymoon.moon_readcode_server.semantic.llm.EmbeddingService;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
import com.luckymoon.moon_readcode_server.semantic.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 问答默认实现。
 *
 * 流程：
 *   1. 把 question 转向量；
 *   2. 在向量库中按 projectId（+ 可选 targetType）过滤召回 topK；
 *   3. 把召回的摘要拼成 context，喂给 LLM 生成最终回答；
 *   4. 把命中条目作为 references 一并返回，便于前端"展开依据"。
 *
 * 注意：当前 InMemoryVectorStore 的 filter 仅支持等值匹配，因此 targetTypeFilter 多值
 * 在内存实现下退化为"任一匹配"——通过把多值变成多次 search 后合并实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    private static final int DEFAULT_TOP_K = 6;

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final QaHistoryMapper qaHistoryMapper;

    @Override
    public QaResponse ask(QaRequest request) {
        long start = System.currentTimeMillis();

        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        int topK = request.getTopK() == null || request.getTopK() <= 0
                ? DEFAULT_TOP_K : request.getTopK();
        float[] queryVector = embeddingService.embed(request.getQuestion());

        // ========== 1. 向量检索 ==========
        long retrievalStart = System.currentTimeMillis();
        List<String> targetTypes = parseTargetTypes(request.getTargetTypeFilter());
        String retrievalFilter = targetTypes.isEmpty()
                ? "projectId=" + request.getProjectId()
                : "projectId=" + request.getProjectId() + ", targetType=" + targetTypes;

        List<VectorStore.VectorMatch> merged = new ArrayList<>();
        if (targetTypes.isEmpty()) {
            merged.addAll(vectorStore.search(queryVector, topK,
                    Map.of("projectId", request.getProjectId())));
        } else {
            for (String type : targetTypes) {
                Map<String, Object> filter = new HashMap<>();
                filter.put("projectId", request.getProjectId());
                filter.put("targetType", type);
                merged.addAll(vectorStore.search(queryVector, topK, filter));
            }
            merged.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            if (merged.size() > topK) {
                merged = new ArrayList<>(merged.subList(0, topK));
            }
        }
        long retrievalMillis = System.currentTimeMillis() - retrievalStart;

        // ========== 2. 构造 LLM 上下文 ==========
        String context = merged.stream()
                .map(m -> "## " + safeStr(m.getMetadata().get("targetType"))
                        + " " + safeStr(m.getMetadata().get("targetRef")) + "\n"
                        + m.getDocument())
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = """
                你是一名资深 Java 架构师助手。请严格基于下方"代码摘要片段"回答用户问题。
                如果摘要中找不到相关信息，要明确说明"无法从已有上下文中得到答案"，禁止编造。
                回答要点：1) 直接给结论；2) 解释设计思路或执行链路；3) 如有 Spring 角色（Controller/Service 等）请指出。
                """;
        String userPrompt = """
                # 用户问题
                %s

                # 代码摘要片段
                %s
                """.formatted(request.getQuestion(), context);

        // ========== 3. 调用 LLM ==========
        log.info("RAG 问答 | question={} | 检索到 {} 条 | 上下文 {} 字符 | 检索耗时 {}ms",
                request.getQuestion(), merged.size(), context.length(), retrievalMillis);

        long llmStart = System.currentTimeMillis();
        String answer = llmService.complete(systemPrompt, userPrompt);
        long llmMillis = System.currentTimeMillis() - llmStart;

        log.info("RAG 问答完成 | LLM 耗时 {}ms | 总耗时 {}ms", llmMillis,
                System.currentTimeMillis() - start);

        // ========== 4. 组装响应 ==========
        List<QaResponse.Reference> refs = merged.stream()
                .map(m -> QaResponse.Reference.builder()
                        .id(m.getId())
                        .targetType(safeStr(m.getMetadata().get("targetType")))
                        .targetRef(safeStr(m.getMetadata().get("targetRef")))
                        .score(m.getScore())
                        .snippet(m.getDocument())
                        .build())
                .toList();

        QaResponse.ThinkingProcess thinking = QaResponse.ThinkingProcess.builder()
                .retrievalMillis(retrievalMillis)
                .llmMillis(llmMillis)
                .retrievalFilter(retrievalFilter)
                .retrievalHits(merged.size())
                .systemPrompt(systemPrompt.trim())
                .context(context)
                .contextLengthChars(context.length())
                .build();

        long cost = System.currentTimeMillis() - start;

        // ========== 5. 保存问答历史（用于热点分析） ==========
        try {
            String vectorJson = Arrays.toString(queryVector);
            String hitRefsJson = refs.stream()
                    .map(r -> r.getTargetType() + ":" + r.getTargetRef())
                    .collect(Collectors.joining(", ", "[", "]"));

            QaHistory history = new QaHistory();
            history.setProjectId(request.getProjectId());
            history.setQuestion(request.getQuestion());
            history.setQuestionVector(vectorJson);
            history.setAnswer(answer);
            history.setHitRefs(hitRefsJson);
            qaHistoryMapper.insert(history);
            log.debug("问答历史已保存 id={}", history.getId());
        } catch (Exception e) {
            log.warn("保存问答历史失败（不影响问答结果）: {}", e.getMessage());
        }

        return QaResponse.builder()
                .answer(answer)
                .references(refs)
                .costMillis(cost)
                .thinkingProcess(thinking)
                .build();
    }

    private List<String> parseTargetTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
