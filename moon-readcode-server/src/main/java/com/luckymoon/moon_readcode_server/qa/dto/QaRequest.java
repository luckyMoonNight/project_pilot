package com.luckymoon.moon_readcode_server.qa.dto;

import lombok.Data;

/**
 * RAG 问答请求体。
 */
@Data
public class QaRequest {

    /** 工程标识，限定检索范围 */
    private String projectId;

    /** 用户问题 */
    private String question;

    /** 召回的 chunk 数量，默认 6 */
    private Integer topK;

    /**
     * 限定检索的目标类型，逗号分隔（如 CLASS,METHOD），为空则不限制。
     * 用于支持"只问执行链路"或"只问业务场景"等定向场景。
     */
    private String targetTypeFilter;
}
