package com.luckymoon.moon_readcode_server.semantic.llm;

import java.util.List;

/**
 * 文本向量化服务统一抽象。
 * 用于把摘要、查询等文本转成向量，喂给向量库做 ANN 检索。
 */
public interface EmbeddingService {

    /**
     * 把单条文本转为向量。
     */
    float[] embed(String text);

    /**
     * 批量向量化。可被实现类覆盖以利用 provider 的 batch API 提高吞吐。
     */
    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /** 向量维度，必须与配置中的 pilot.vector.dimension 一致 */
    int dimension();

    /** 当前 embedding 模型名 */
    String modelName();
}
