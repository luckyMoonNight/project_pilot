package com.luckymoon.moon_readcode_server.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工程分析报告。
 *
 * 内容覆盖：
 *   1) 整体概览（项目级摘要 + 各类型计数）；
 *   2) 模块视图（按 package 聚合的类列表）；
 *   3) 业务原型分布（CONTROLLER/SERVICE/MAPPER/...）；
 *   4) 典型执行链路样例（基于 code_relation 抽取）；
 *   5) Markdown 渲染版，便于直接展示。
 */
@Data
@Builder
public class ProjectReport {

    private String projectId;

    private Overview overview;

    /** packageName -> 该包下的类全限定名列表 */
    private Map<String, List<String>> moduleView;

    /** stereotype -> 类全限定名列表 */
    private Map<String, List<String>> stereotypeView;

    /** 典型执行链路样例：每条是从 Controller 出发的方法调用链 */
    private List<List<String>> executionFlows;

    /** 项目级摘要文本（可能为 null，如果 Phase 3 未运行） */
    private String projectSummary;

    /** Markdown 渲染版本，前端 / IDE 可直接展示 */
    private String markdown;

    @Data
    @Builder
    public static class Overview {
        private int fileCount;
        private int classCount;
        private int methodCount;
        private int relationCount;
    }
}
