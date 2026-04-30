package com.luckymoon.moon_readcode_server.report.impl;

import com.luckymoon.moon_readcode_server.entity.CodeClass;
import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.entity.CodeMethod;
import com.luckymoon.moon_readcode_server.entity.CodeRelation;
import com.luckymoon.moon_readcode_server.entity.CodeSummary;
import com.luckymoon.moon_readcode_server.mapper.CodeClassMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeFileMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeMethodMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeRelationMapper;
import com.luckymoon.moon_readcode_server.mapper.CodeSummaryMapper;
import com.luckymoon.moon_readcode_server.report.ProjectReportService;
import com.luckymoon.moon_readcode_server.report.dto.ProjectReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报告生成实现：以 Controller 入口为核心视角，输出分析结论和代码调用链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReportServiceImpl implements ProjectReportService {

    private static final int MAX_FLOW_DEPTH = 8;

    private final CodeFileMapper codeFileMapper;
    private final CodeClassMapper codeClassMapper;
    private final CodeMethodMapper codeMethodMapper;
    private final CodeRelationMapper codeRelationMapper;
    private final CodeSummaryMapper codeSummaryMapper;

    @Override
    public ProjectReport generate(String projectId) {
        List<CodeFile> files = codeFileMapper.selectByProjectId(projectId);
        List<CodeClass> classes = codeClassMapper.selectByProjectId(projectId);
        List<CodeMethod> methods = codeMethodMapper.selectByProjectId(projectId);
        List<CodeRelation> relations = codeRelationMapper.selectByProjectId(projectId);

        ProjectReport.Overview overview = ProjectReport.Overview.builder()
                .fileCount(files.size())
                .classCount(classes.size())
                .methodCount(methods.size())
                .relationCount(relations.size())
                .build();

        // 项目级摘要
        CodeSummary projectSummary = codeSummaryMapper.selectByTarget(projectId, "PROJECT", null);
        String summaryText = projectSummary == null ? null : projectSummary.getSummary();

        // 构建索引
        Map<Long, CodeClass> classById = classes.stream()
                .collect(Collectors.toMap(CodeClass::getId, c -> c));
        Map<Long, CodeMethod> methodById = methods.stream()
                .collect(Collectors.toMap(CodeMethod::getId, m -> m));
        Map<Long, List<CodeRelation>> outEdges = new HashMap<>();
        for (CodeRelation relation : relations) {
            if ("METHOD_CALL".equals(relation.getRelationType()) && "METHOD".equals(relation.getFromType())) {
                outEdges.computeIfAbsent(relation.getFromId(), k -> new ArrayList<>()).add(relation);
            }
        }
        Map<String, List<CodeMethod>> methodByName = methods.stream()
                .collect(Collectors.groupingBy(CodeMethod::getMethodName));

        // 查找所有方法摘要，按 targetRef 索引
        List<CodeSummary> methodSummaries = codeSummaryMapper.selectByProjectAndType(projectId, "METHOD");
        Map<String, String> methodSummaryMap = new HashMap<>();
        for (CodeSummary summary : methodSummaries) {
            if (summary.getTargetRef() != null) {
                methodSummaryMap.put(summary.getTargetRef(), summary.getSummary());
            }
        }

        // 查找所有类摘要
        List<CodeSummary> classSummaries = codeSummaryMapper.selectByProjectAndType(projectId, "CLASS");
        Map<String, String> classSummaryMap = new HashMap<>();
        for (CodeSummary summary : classSummaries) {
            if (summary.getTargetRef() != null) {
                classSummaryMap.put(summary.getTargetRef(), summary.getSummary());
            }
        }

        // 构建入口分析
        List<ProjectReport.EntryPoint> entryPoints = new ArrayList<>();
        for (CodeMethod method : methods) {
            CodeClass owner = classById.get(method.getClassId());
            if (owner == null || !"CONTROLLER".equals(owner.getStereotype())) {
                continue;
            }

            // 遍历调用链
            List<String> callChain = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            Set<Long> involvedClassIds = new LinkedHashSet<>();
            walkFlow(method, callChain, visited, involvedClassIds, outEdges, methodByName, methodById, 0);

            // 收集链路中涉及的关键类
            List<ProjectReport.InvolvedClass> involvedClasses = new ArrayList<>();
            for (Long classId : involvedClassIds) {
                CodeClass clazz = classById.get(classId);
                if (clazz == null) continue;
                String classSummary = classSummaryMap.get(clazz.getQualifiedName());
                involvedClasses.add(ProjectReport.InvolvedClass.builder()
                        .qualifiedName(clazz.getQualifiedName())
                        .stereotype(clazz.getStereotype())
                        .summary(classSummary)
                        .build());
            }

            // 入口方法摘要
            String entryMethodSummary = methodSummaryMap.get(method.getSignature());
            if (entryMethodSummary == null) {
                entryMethodSummary = methodSummaryMap.get(
                        owner.getQualifiedName() + "#" + method.getMethodName());
            }

            entryPoints.add(ProjectReport.EntryPoint.builder()
                    .signature(method.getSignature())
                    .controller(owner.getQualifiedName())
                    .summary(entryMethodSummary)
                    .callChain(callChain)
                    .involvedClasses(involvedClasses)
                    .build());
        }

        String markdown = renderMarkdown(projectId, overview, summaryText, entryPoints);

        return ProjectReport.builder()
                .projectId(projectId)
                .overview(overview)
                .projectSummary(summaryText)
                .entryPoints(entryPoints)
                .markdown(markdown)
                .build();
    }

    private void walkFlow(CodeMethod current, List<String> callChain, Set<Long> visited,
                          Set<Long> involvedClassIds,
                          Map<Long, List<CodeRelation>> outEdges,
                          Map<String, List<CodeMethod>> methodByName,
                          Map<Long, CodeMethod> methodById, int depth) {
        if (current == null || depth >= MAX_FLOW_DEPTH || !visited.add(current.getId())) {
            return;
        }
        callChain.add(current.getSignature());
        if (current.getClassId() != null) {
            involvedClassIds.add(current.getClassId());
        }

        List<CodeRelation> edges = outEdges.getOrDefault(current.getId(), List.of());
        for (CodeRelation edge : edges) {
            CodeMethod next = resolveTarget(edge, methodByName, methodById);
            if (next != null && !visited.contains(next.getId())) {
                walkFlow(next, callChain, visited, involvedClassIds, outEdges, methodByName, methodById, depth + 1);
            }
        }
    }

    private CodeMethod resolveTarget(CodeRelation edge,
                                     Map<String, List<CodeMethod>> methodByName,
                                     Map<Long, CodeMethod> methodById) {
        if (edge.getToId() != null) {
            return methodById.get(edge.getToId());
        }
        String ref = edge.getToRef();
        if (ref == null || ref.isBlank()) return null;
        String name = ref.contains(".") ? ref.substring(ref.lastIndexOf('.') + 1) : ref;
        int paren = name.indexOf('(');
        if (paren > 0) name = name.substring(0, paren);
        List<CodeMethod> candidates = methodByName.get(name);
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private String renderMarkdown(String projectId, ProjectReport.Overview overview,
                                  String projectSummary,
                                  List<ProjectReport.EntryPoint> entryPoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 工程分析报告 · ").append(projectId).append("\n\n");

        // 概览（简洁一行）
        sb.append("**规模**: ")
                .append(overview.getFileCount()).append(" 文件 / ")
                .append(overview.getClassCount()).append(" 类 / ")
                .append(overview.getMethodCount()).append(" 方法 / ")
                .append(overview.getRelationCount()).append(" 调用关系\n\n");

        // 项目级语义分析
        if (projectSummary != null && !projectSummary.isBlank()) {
            sb.append("### 📋 项目分析结论\n\n");
            sb.append(projectSummary).append("\n\n");
        }

        // 入口端点分析
        sb.append("### 🚪 入口端点分析\n\n");
        if (entryPoints.isEmpty()) {
            sb.append("_未识别到 Controller 入口端点_\n\n");
        } else {
            for (int i = 0; i < entryPoints.size(); i++) {
                ProjectReport.EntryPoint entry = entryPoints.get(i);
                sb.append("#### ").append(i + 1).append(". `")
                        .append(simplifySignature(entry.getSignature())).append("`\n\n");

                // 入口方法分析结论
                if (entry.getSummary() != null && !entry.getSummary().isBlank()) {
                    sb.append(entry.getSummary()).append("\n\n");
                }

                // 调用链路
                if (entry.getCallChain() != null && entry.getCallChain().size() > 1) {
                    sb.append("**调用链路**:\n```\n");
                    for (int j = 0; j < entry.getCallChain().size(); j++) {
                        sb.append(j == 0 ? "" : "  → ");
                        sb.append(simplifySignature(entry.getCallChain().get(j))).append("\n");
                    }
                    sb.append("```\n\n");
                }

                // 涉及的关键组件
                if (entry.getInvolvedClasses() != null && !entry.getInvolvedClasses().isEmpty()) {
                    sb.append("**涉及组件**:\n");
                    for (ProjectReport.InvolvedClass clazz : entry.getInvolvedClasses()) {
                        String shortName = clazz.getQualifiedName().contains(".")
                                ? clazz.getQualifiedName().substring(clazz.getQualifiedName().lastIndexOf('.') + 1)
                                : clazz.getQualifiedName();
                        sb.append("- **").append(shortName).append("**");
                        if (clazz.getStereotype() != null) {
                            sb.append(" `").append(clazz.getStereotype()).append("`");
                        }
                        if (clazz.getSummary() != null && !clazz.getSummary().isBlank()) {
                            // 取摘要第一句话
                            String firstLine = clazz.getSummary().split("\n")[0].trim();
                            if (firstLine.length() > 100) {
                                firstLine = firstLine.substring(0, 100) + "...";
                            }
                            sb.append(" — ").append(firstLine);
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }

                if (i < entryPoints.size() - 1) {
                    sb.append("---\n\n");
                }
            }
        }
        return sb.toString();
    }

    /** 简化方法签名：去掉包名前缀，只保留 ClassName.methodName(ParamTypes) */
    private String simplifySignature(String signature) {
        if (signature == null) return "";
        int hashIdx = signature.lastIndexOf('#');
        if (hashIdx > 0) {
            String classPart = signature.substring(0, hashIdx);
            String methodPart = signature.substring(hashIdx + 1);
            String shortClass = classPart.contains(".")
                    ? classPart.substring(classPart.lastIndexOf('.') + 1) : classPart;
            return shortClass + "." + methodPart;
        }
        // 如果没有 # 分隔符，尝试简化全限定名
        if (signature.contains("(")) {
            int parenIdx = signature.indexOf('(');
            String beforeParen = signature.substring(0, parenIdx);
            String afterParen = signature.substring(parenIdx);
            if (beforeParen.contains(".")) {
                String[] parts = beforeParen.split("\\.");
                if (parts.length >= 2) {
                    return parts[parts.length - 2] + "." + parts[parts.length - 1] + afterParen;
                }
            }
        }
        return signature;
    }
}
