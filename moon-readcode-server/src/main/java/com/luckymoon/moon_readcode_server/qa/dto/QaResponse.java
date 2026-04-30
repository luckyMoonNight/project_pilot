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

    /** 思考过程，展示 RAG 检索 → LLM 推理的完整链路 */
    private ThinkingProcess thinkingProcess;

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

    @Data
    @Builder
    public static class ThinkingProcess {
        /** 向量检索耗时（毫秒） */
        private long retrievalMillis;
        /** LLM 生成耗时（毫秒） */
        private long llmMillis;
        /** 检索条件描述 */
        private String retrievalFilter;
        /** 检索命中数量 */
        private int retrievalHits;
        /** 发送给 LLM 的 system prompt */
        private String systemPrompt;
        /** 发送给 LLM 的上下文（拼接后的代码摘要） */
        private String context;
        /** 上下文 token 数（近似：按字符数 / 2 估算） */
        private int contextLengthChars;
    }
}
