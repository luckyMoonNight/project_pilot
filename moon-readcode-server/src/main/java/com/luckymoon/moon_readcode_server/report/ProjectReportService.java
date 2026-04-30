package com.luckymoon.moon_readcode_server.report;

import com.luckymoon.moon_readcode_server.report.dto.ProjectReport;

/**
 * 工程报告生成服务（Phase 5）。
 */
public interface ProjectReportService {

    /**
     * 基于已扫描 + 已分析 + 已建索引的工程，输出综合分析报告。
     */
    ProjectReport generate(String projectId);
}
