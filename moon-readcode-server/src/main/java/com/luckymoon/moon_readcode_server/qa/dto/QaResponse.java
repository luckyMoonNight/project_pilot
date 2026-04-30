package com.luckymoon.moon_readcode_server.qa.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应体。
 */
@Data
@Builder
public class QaResponse {

    /** LLM 最终回答 */
    private String answer;

    /** 召回的引用条目，前端可用于"展开依据" */
    private List<Reference> references;

    /** 本次问答耗时毫秒 */
    private long costMillis;

    @Data
    @Builder
    public static class Reference {
        /** code_summary.vector_id */
        private String id;
        /** 目标类型 */
        private String targetType;
        /** 目标可读标识，如全限定名/方法签名 */
        private String targetRef;
        /** 命中分数 */
        private double score;
        /** 摘要文本 */
        private String snippet;
    }
}
