package com.luckymoon.moon_readcode_server.semantic.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Phase 3 摘要 + 向量化的执行结果统计。
 */
@Data
@Builder
public class SummarizeResult {

    private String projectId;

    private int methodSummaries;
    private int classSummaries;
    private int fileSummaries;
    private int projectSummaries;

    /** 向量化的总条数 */
    private int vectorizedCount;

    /** 失败条数（LLM 调用异常或数据缺失） */
    private int failedCount;

    private long costMillis;
}
