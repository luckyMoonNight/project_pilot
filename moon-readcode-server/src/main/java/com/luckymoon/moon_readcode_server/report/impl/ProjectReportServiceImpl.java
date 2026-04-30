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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 报告生成默认实现：纯查询 + Markdown 渲染，不再触发 LLM 调用。
 *
 * 设计目标：让"还没建语义索引"的工程也能拿到一份基础架构报告；
 * 若已建索引，则把项目级摘要拼到报告头部，让结论更具可读性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReportServiceImpl implements ProjectReportService {

    /** 单条调用链最大跳数，避免循环依赖时无限展开 */
    private static final int MAX_FLOW_DEPTH = 6;
    /** 输出的样例链路条数上限 */
    private static final int MAX_FLOW_SAMPLES = 5;

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

        Map<String, List<String>> moduleView = buildModuleView(classes);
        Map<String, List<String>> stereotypeView = buildStereotypeView(classes);
        List<List<String>> executionFlows = buildExecutionFlows(classes, methods, relations);

        CodeSummary projectSummary = codeSummaryMapper.selectByTarget(projectId, "PROJECT", null);
        String summaryText = projectSummary == null ? null : projectSummary.getSummary();

        String markdown = renderMarkdown(projectId, overview, moduleView, stereotypeView,
                executionFlows, summaryText);

        return ProjectReport.builder()
                .projectId(projectId)
                .overview(overview)
                .moduleView(moduleView)
                .stereotypeView(stereotypeView)
                .executionFlows(executionFlows)
                .projectSummary(summaryText)
                .markdown(markdown)
                .build();
    }

    /* ---------------------------------------------------------------- */
    /*                            子视图构建                             */
    /* ---------------------------------------------------------------- */

    private Map<String, List<String>> buildModuleView(List<CodeClass> classes) {
        Map<String, List<String>> view = new TreeMap<>();
        for (CodeClass c : classes) {
            String pkg = c.getPackageName() == null ? "(default)" : c.getPackageName();
            view.computeIfAbsent(pkg, k -> new ArrayList<>()).add(c.getQualifiedName());
        }
        view.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return view;
    }

    private Map<String, List<String>> buildStereotypeView(List<CodeClass> classes) {
        Map<String, List<String>> view = new LinkedHashMap<>();
        // 固定输出顺序，方便阅读
        for (String stereotype : List.of("CONTROLLER", "SERVICE", "MAPPER", "ENTITY",
                "CONFIG", "COMPONENT", "OTHER")) {
            view.put(stereotype, new ArrayList<>());
        }
        for (CodeClass c : classes) {
            view.computeIfAbsent(c.getStereotype() == null ? "OTHER" : c.getStereotype(),
                    k -> new ArrayList<>()).add(c.getQualifiedName());
        }
        // 移除空 stereotype 桶，避免噪音
        view.entrySet().removeIf(e -> e.getValue().isEmpty());
        view.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return view;
    }

    /**
     * 从 Controller 类的方法出发，沿 METHOD_CALL 关系做有限深度遍历，得到典型执行链路样例。
     * 弱解析阶段 to_id 大概率为 null，这里用 to_ref 的方法名后缀去匹配本工程内的方法名做近似跳转。
     */
    private List<List<String>> buildExecutionFlows(List<CodeClass> classes,
                                                   List<CodeMethod> methods,
                                                   List<CodeRelation> relations) {
        // 索引：classId -> CodeClass
        Map<Long, CodeClass> classById = classes.stream()
                .collect(Collectors.toMap(CodeClass::getId, c -> c));
        // 索引：methodId -> 出边关系
        Map<Long, List<CodeRelation>> outEdges = new HashMap<>();
        for (CodeRelation r : relations) {
            if ("METHOD_CALL".equals(r.getRelationType()) && "METHOD".equals(r.getFromType())) {
                outEdges.computeIfAbsent(r.getFromId(), k -> new ArrayList<>()).add(r);
            }
        }
        // 索引：方法名 -> 候选 CodeMethod 列表（同名重载/不同类同名都进来，用于近似跳转）
        Map<String, List<CodeMethod>> methodByName = methods.stream()
                .collect(Collectors.groupingBy(CodeMethod::getMethodName));
        // 索引：methodId -> CodeMethod
        Map<Long, CodeMethod> methodById = methods.stream()
                .collect(Collectors.toMap(CodeMethod::getId, m -> m));

        List<List<String>> flows = new ArrayList<>();
        for (CodeMethod m : methods) {
            if (flows.size() >= MAX_FLOW_SAMPLES) break;
            CodeClass owner = classById.get(m.getClassId());
            if (owner == null || !"CONTROLLER".equals(owner.getStereotype())) {
                continue;
            }
            List<String> flow = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            walkFlow(m, flow, visited, outEdges, methodByName, methodById, 0);
            if (flow.size() > 1) {
                flows.add(flow);
            }
        }
        return flows;
    }

    private void walkFlow(CodeMethod current, List<String> flow, Set<Long> visited,
                          Map<Long, List<CodeRelation>> outEdges,
                          Map<String, List<CodeMethod>> methodByName,
                          Map<Long, CodeMethod> methodById, int depth) {
        if (current == null || depth >= MAX_FLOW_DEPTH || !visited.add(current.getId())) {
            return;
        }
        flow.add(current.getSignature());
        List<CodeRelation> edges = outEdges.getOrDefault(current.getId(), List.of());
        for (CodeRelation edge : edges) {
            CodeMethod next = resolveTarget(edge, methodByName, methodById);
            if (next != null) {
                walkFlow(next, flow, visited, outEdges, methodByName, methodById, depth + 1);
                // 只走一条主分支，避免链路爆炸；若需要全图建议另起 graph dump 接口
                return;
            }
        }
    }

    /** 关系目标解析：优先 to_id，否则按 to_ref 末尾的方法名去 methodByName 找候选 */
    private CodeMethod resolveTarget(CodeRelation edge,
                                     Map<String, List<CodeMethod>> methodByName,
                                     Map<Long, CodeMethod> methodById) {
        if (edge.getToId() != null) {
            return methodById.get(edge.getToId());
        }
        String ref = edge.getToRef();
        if (ref == null || ref.isBlank()) return null;
        String name = ref.contains(".") ? ref.substring(ref.lastIndexOf('.') + 1) : ref;
        // 去掉可能存在的括号
        int paren = name.indexOf('(');
        if (paren > 0) name = name.substring(0, paren);
        List<CodeMethod> candidates = methodByName.get(name);
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    /* ---------------------------------------------------------------- */
    /*                          Markdown 渲染                            */
    /* ---------------------------------------------------------------- */

    private String renderMarkdown(String projectId, ProjectReport.Overview overview,
                                  Map<String, List<String>> moduleView,
                                  Map<String, List<String>> stereotypeView,
                                  List<List<String>> executionFlows,
                                  String projectSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 工程分析报告 - ").append(projectId).append("\n\n");

        sb.append("## 一、整体概览\n\n");
        sb.append("- 文件数：").append(overview.getFileCount()).append("\n");
        sb.append("- 类数：").append(overview.getClassCount()).append("\n");
        sb.append("- 方法数：").append(overview.getMethodCount()).append("\n");
        sb.append("- 关系数：").append(overview.getRelationCount()).append("\n\n");

        if (projectSummary != null && !projectSummary.isBlank()) {
            sb.append("## 二、项目语义摘要\n\n");
            sb.append(projectSummary).append("\n\n");
        }

        sb.append("## 三、业务原型分布\n\n");
        for (Map.Entry<String, List<String>> entry : stereotypeView.entrySet()) {
            sb.append("### ").append(entry.getKey())
                    .append(" (").append(entry.getValue().size()).append(")\n");
            for (String name : entry.getValue()) {
                sb.append("- `").append(name).append("`\n");
            }
            sb.append("\n");
        }

        sb.append("## 四、模块视图（按 package）\n\n");
        for (Map.Entry<String, List<String>> entry : moduleView.entrySet()) {
            sb.append("### `").append(entry.getKey()).append("`\n");
            for (String name : entry.getValue()) {
                sb.append("- ").append(name).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 五、典型执行链路样例\n\n");
        if (executionFlows.isEmpty()) {
            sb.append("_未识别到从 Controller 出发的典型链路（可能是非 Web 工程，或未运行 Phase 2 分析）_\n");
        } else {
            int idx = 1;
            for (List<String> flow : executionFlows) {
                sb.append("**链路 ").append(idx++).append("：**\n");
                sb.append(String.join("\n  → ", flow)).append("\n\n");
            }
        }
        return sb.toString();
    }
}
