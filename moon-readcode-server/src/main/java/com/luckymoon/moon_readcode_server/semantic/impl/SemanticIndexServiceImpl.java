package com.luckymoon.moon_readcode_server.semantic.impl;

import com.luckymoon.moon_readcode_server.entity.CodeClass;
import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.entity.CodeMethod;
import com.luckymoon.moon_readcode_server.entity.CodeSummary;
import com.luckymoon.moon_readcode_server.mapper.CodeClassMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeFileMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeMethodMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeSummaryMapper;
import com.luckymoon.moon_readcode_server.semantic.SemanticIndexService;
import com.luckymoon.moon_readcode_server.semantic.dto.SummarizeResult;
import com.luckymoon.moon_readcode_server.semantic.llm.EmbeddingService;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
import com.luckymoon.moon_readcode_server.semantic.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 自底向上的语义索引 Pipeline：method → class → file → project。
 *
 * 关键设计：
 *   1. 每一层摘要都依赖下一层已生成的摘要，避免把整段源码一次性塞给 LLM；
 *   2. 摘要文本写入 code_summary 表，同时按相同 vector_id 写入向量库；
 *   3. metadata 包含 projectId/targetType/targetId/targetRef，便于 RAG 阶段做过滤检索；
 *   4. 重复执行幂等：先按 projectId 清空旧摘要 + 向量再重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticIndexServiceImpl implements SemanticIndexService {

    private final CodeFileMapper codeFileMapper;
    private final CodeClassMapper codeClassMapper;
    private final CodeMethodMapper codeMethodMapper;
    private final CodeSummaryMapper codeSummaryMapper;

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Override
    @Transactional
    public SummarizeResult buildIndex(String projectId) {
        long start = System.currentTimeMillis();

        // 幂等：先清空当前 project 的摘要 + 对应向量
        codeSummaryMapper.deleteByProjectId(projectId);
        vectorStore.deleteByFilter(Map.of("projectId", projectId));

        Counter c = new Counter();

        // ---------- Layer 1: 方法级摘要 ----------
        Map<Long, String> methodIdToSummary = new HashMap<>();
        Map<Long, List<CodeMethod>> classMethods = new HashMap<>();
        for (CodeMethod method : codeMethodMapper.selectByProjectId(projectId)) {
            classMethods.computeIfAbsent(method.getClassId(), k -> new ArrayList<>()).add(method);
            try {
                String summary = summarizeMethod(method);
                methodIdToSummary.put(method.getId(), summary);
                persistSummary(projectId, "METHOD", method.getId(), method.getSignature(), summary);
                c.methodSummaries++;
                c.vectorizedCount++;
            } catch (Exception e) {
                log.warn("方法摘要失败 signature={} : {}", method.getSignature(), e.getMessage());
                c.failedCount++;
            }
        }

        // ---------- Layer 2: 类级摘要 ----------
        Map<Long, String> classIdToSummary = new HashMap<>();
        Map<Long, List<CodeClass>> fileClasses = new HashMap<>();
        for (CodeClass clazz : codeClassMapper.selectByProjectId(projectId)) {
            fileClasses.computeIfAbsent(clazz.getFileId(), k -> new ArrayList<>()).add(clazz);
            try {
                List<CodeMethod> methods = classMethods.getOrDefault(clazz.getId(), List.of());
                String summary = summarizeClass(clazz, methods, methodIdToSummary);
                classIdToSummary.put(clazz.getId(), summary);
                persistSummary(projectId, "CLASS", clazz.getId(), clazz.getQualifiedName(), summary);
                c.classSummaries++;
                c.vectorizedCount++;
            } catch (Exception e) {
                log.warn("类摘要失败 qualifiedName={} : {}", clazz.getQualifiedName(), e.getMessage());
                c.failedCount++;
            }
        }

        // ---------- Layer 3: 文件级摘要 ----------
        List<CodeFile> files = codeFileMapper.selectByProjectId(projectId);
        for (CodeFile file : files) {
            if (!"java".equalsIgnoreCase(file.getFileType())) {
                continue;
            }
            try {
                List<CodeClass> classes = fileClasses.getOrDefault(file.getId(), List.of());
                String summary = summarizeFile(file, classes, classIdToSummary);
                persistSummary(projectId, "FILE", file.getId(), file.getFilePath(), summary);
                c.fileSummaries++;
                c.vectorizedCount++;
            } catch (Exception e) {
                log.warn("文件摘要失败 path={} : {}", file.getFilePath(), e.getMessage());
                c.failedCount++;
            }
        }

        // ---------- Layer 4: 项目级摘要 ----------
        try {
            String projectSummary = summarizeProject(projectId, classIdToSummary.values());
            persistSummary(projectId, "PROJECT", null, projectId, projectSummary);
            c.projectSummaries++;
            c.vectorizedCount++;
        } catch (Exception e) {
            log.warn("项目摘要失败 projectId={} : {}", projectId, e.getMessage());
            c.failedCount++;
        }

        long cost = System.currentTimeMillis() - start;
        log.info("语义索引完成 projectId={} 方法={} 类={} 文件={} 项目={} 失败={} 耗时={}ms",
                projectId, c.methodSummaries, c.classSummaries, c.fileSummaries,
                c.projectSummaries, c.failedCount, cost);

        return SummarizeResult.builder()
                .projectId(projectId)
                .methodSummaries(c.methodSummaries)
                .classSummaries(c.classSummaries)
                .fileSummaries(c.fileSummaries)
                .projectSummaries(c.projectSummaries)
                .vectorizedCount(c.vectorizedCount)
                .failedCount(c.failedCount)
                .costMillis(cost)
                .build();
    }

    /* ---------------------------------------------------------------- */
    /*                     四个层级各自的 prompt 构建                   */
    /* ---------------------------------------------------------------- */

    private String summarizeMethod(CodeMethod method) {
        String prompt = """
                请用一段中文（不超过 120 字）说明以下 Java 方法的职责、关键逻辑与可能的副作用。
                方法签名：%s
                返回类型：%s
                注解：%s
                方法源码：
                ```java
                %s
                ```
                """.formatted(
                method.getSignature(),
                nullToEmpty(method.getReturnType()),
                nullToEmpty(method.getAnnotations()),
                truncate(method.getBodySnippet(), 4000));
        return llmService.complete("你是一名资深 Java 工程师，擅长用简洁中文解释代码职责。", prompt);
    }

    private String summarizeClass(CodeClass clazz, List<CodeMethod> methods,
                                  Map<Long, String> methodIdToSummary) {
        String methodList = methods.stream()
                .map(m -> "- " + m.getMethodName()
                        + " : " + nullToEmpty(methodIdToSummary.get(m.getId())))
                .collect(Collectors.joining("\n"));
        String prompt = """
                请用一段中文（不超过 200 字）说明以下 Java 类的设计目的、所属业务原型和对外能力。
                类名：%s
                业务原型（推断）：%s
                注解：%s
                继承：%s
                实现：%s
                方法摘要列表：
                %s
                """.formatted(
                clazz.getQualifiedName(),
                nullToEmpty(clazz.getStereotype()),
                nullToEmpty(clazz.getAnnotations()),
                nullToEmpty(clazz.getSuperClass()),
                nullToEmpty(clazz.getInterfaces()),
                truncate(methodList, 4000));
        return llmService.complete("你是一名资深 Java 工程师，擅长用简洁中文总结类的设计意图。", prompt);
    }

    private String summarizeFile(CodeFile file, List<CodeClass> classes,
                                 Map<Long, String> classIdToSummary) {
        String classList = classes.stream()
                .map(c -> "- " + c.getClassName()
                        + " (" + nullToEmpty(c.getStereotype()) + ") : "
                        + nullToEmpty(classIdToSummary.get(c.getId())))
                .collect(Collectors.joining("\n"));
        String prompt = """
                请用一段中文（不超过 200 字）说明以下源文件的整体职责。
                文件路径：%s
                包名：%s
                包含的类：
                %s
                """.formatted(
                file.getFilePath(),
                nullToEmpty(file.getPackageName()),
                truncate(classList, 4000));
        return llmService.complete("你是一名资深 Java 工程师，擅长用简洁中文总结文件的职责。", prompt);
    }

    private String summarizeProject(String projectId, java.util.Collection<String> classSummaries) {
        // 按字符串顺序合并所有类摘要，必要时截断，避免超 LLM 上下文
        String aggregated = String.join("\n", classSummaries);
        String prompt = """
                以下是工程 %s 中所有类的摘要列表。请你基于它们，用 200~400 字的中文总结：
                1) 该工程的整体技术架构与分层；
                2) 主要业务模块与功能；
                3) 典型的执行链路与协作关系。
                类摘要列表：
                %s
                """.formatted(projectId, truncate(aggregated, 8000));
        return llmService.complete("你是一名资深架构师，擅长以工程视角综述代码库。", prompt);
    }

    /* ---------------------------------------------------------------- */
    /*                       持久化 + 向量写入                            */
    /* ---------------------------------------------------------------- */

    private void persistSummary(String projectId, String targetType, Long targetId,
                                String targetRef, String summary) {
        String vectorId = UUID.randomUUID().toString();

        CodeSummary entity = new CodeSummary();
        entity.setProjectId(projectId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setTargetRef(targetRef);
        entity.setSummary(summary);
        entity.setVectorId(vectorId);
        entity.setModelName(llmService.modelName());
        codeSummaryMapper.insert(entity);

        // 向量化文档：摘要 + 关键标识，便于关键词召回
        String document = "[" + targetType + "] " + nullToEmpty(targetRef) + "\n" + summary;
        float[] vector = embeddingService.embed(document);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projectId", projectId);
        metadata.put("targetType", targetType);
        if (targetId != null) {
            metadata.put("targetId", targetId);
        }
        metadata.put("targetRef", nullToEmpty(targetRef));

        vectorStore.upsert(VectorStore.VectorRecord.builder()
                .id(vectorId)
                .document(document)
                .vector(vector)
                .metadata(metadata)
                .build());
    }

    /* ---------------------------------------------------------------- */
    /*                              工具                                 */
    /* ---------------------------------------------------------------- */

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...<truncated>";
    }

    private static class Counter {
        int methodSummaries;
        int classSummaries;
        int fileSummaries;
        int projectSummaries;
        int vectorizedCount;
        int failedCount;
    }
}
