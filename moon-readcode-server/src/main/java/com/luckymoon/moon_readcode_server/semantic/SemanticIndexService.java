package com.luckymoon.moon_readcode_server.semantic;

import com.luckymoon.moon_readcode_server.semantic.dto.SummarizeResult;

/**
 * 语义索引服务（Phase 3 入口）。
 *
 * 职责：自底向上为工程生成"方法 → 类 → 文件 → 项目"四层语义摘要，
 * 同时把摘要写入向量库，建立可被 RAG 检索的知识点。
 */
public interface SemanticIndexService {

    /**
     * 为指定工程构建/重建语义索引。要求 Phase 1 + Phase 2 已完成。
     */
    SummarizeResult buildIndex(String projectId);
}
