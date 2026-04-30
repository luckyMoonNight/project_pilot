package com.luckymoon.moon_readcode_server.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工程分析报告。
 *
 * 以入口（Controller 端点）为核心视角：
 *   1) 项目级分析结论（基于语义摘要）；
 *   2) 入口端点列表 + 每个端点的调用链路 + 语义分析；
 *   3) Markdown 渲染版，前端直接展示。
 */
@Data
@Builder
public class ProjectReport {

    private String projectId;

    /** 基本统计 */
    private Overview overview;

    /** 项目级语义分析结论 */
    private String projectSummary;

    /** 入口端点分析（每个 Controller 方法一个） */
    private List<EntryPoint> entryPoints;

    /** Markdown 渲染版本 */
    private String markdown;

    @Data
    @Builder
    public static class Overview {
        private int fileCount;
        private int classCount;
        private int methodCount;
        private int relationCount;
    }

    @Data
    @Builder
    public static class EntryPoint {
        /** 入口方法签名，如 PilotController.runAll(ScanRequest) */
        private String signature;
        /** 所属 Controller 类名 */
        private String controller;
        /** 入口方法的语义摘要 */
        private String summary;
        /** 调用链路：从入口出发依次调用的方法签名列表 */
        private List<String> callChain;
        /** 链路中涉及的关键类及其语义摘要 */
        private List<InvolvedClass> involvedClasses;
    }

    @Data
    @Builder
    public static class InvolvedClass {
        /** 类全限定名 */
        private String qualifiedName;
        /** 类的 Spring 角色（CONTROLLER/SERVICE/MAPPER 等） */
        private String stereotype;
        /** 类的语义摘要 */
        private String summary;
    }
}