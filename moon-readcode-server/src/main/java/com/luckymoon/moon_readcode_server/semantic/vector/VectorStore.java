package com.luckymoon.moon_readcode_server.semantic.vector;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 向量库统一抽象。屏蔽 Chroma/Milvus/Qdrant/InMemory 的差异。
 */
public interface VectorStore {

    /**
     * 写入或更新一条向量。
     */
    void upsert(VectorRecord record);

    /**
     * 批量写入。
     */
    void upsertAll(List<VectorRecord> records);

    /**
     * 按 id 删除。
     */
    void delete(String id);

    /**
     * 按 metadata 过滤删除（如按 projectId 整体清空）。
     */
    void deleteByFilter(Map<String, Object> filter);

    /**
     * 相似度检索。
     *
     * @param queryVector 查询向量
     * @param topK        召回数量
     * @param filter      可选 metadata 过滤
     */
    List<VectorMatch> search(float[] queryVector, int topK, Map<String, Object> filter);

    /** 一条向量记录 */
    @Data
    @Builder
    class VectorRecord {
        /** 唯一 id，与 code_summary.vector_id 对齐 */
        private String id;
        /** 原始文本（chunk 内容） */
        private String document;
        /** 向量 */
        private float[] vector;
        /** 元数据：projectId / targetType / targetId / targetRef / packageName 等 */
        private Map<String, Object> metadata;
    }

    /** 一条命中结果 */
    @Data
    @Builder
    class VectorMatch {
        private String id;
        private String document;
        private Map<String, Object> metadata;
        /** 相似度分数（越大越相似，已归一化） */
        private double score;
    }
}
