package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Mock 实现：基于文本 SHA-256 的伪向量。
 * 同一文本始终生成同一向量，且向量已 L2 归一化，可用 cosine 相似度检索。
 * 仅供 Pipeline 联调，效果远差于真实 embedding 模型。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingService implements EmbeddingService {

    private final PilotProperties properties;

    @Override
    public float[] embed(String text) {
        int dim = dimension();
        float[] vec = new float[dim];
        if (text == null || text.isEmpty()) {
            return vec;
        }
        // 用 SHA-256 反复 hash 填充向量，保证确定性
        byte[] seed = sha256(text.getBytes(StandardCharsets.UTF_8));
        int filled = 0;
        byte[] block = seed;
        while (filled < dim) {
            for (int i = 0; i < block.length && filled < dim; i++) {
                // 把 byte 映射到 [-1, 1]
                vec[filled++] = (block[i] & 0xff) / 127.5f - 1.0f;
            }
            block = sha256(block);
        }
        // L2 归一化，便于 cosine 相似度检索
        double norm = 0.0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }
        return vec;
    }

    @Override
    public int dimension() {
        return properties.getVector().getDimension();
    }

    @Override
    public String modelName() {
        return properties.getLlm().getEmbeddingModel();
    }

    private byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 未提供 SHA-256 实现", e);
        }
    }
}
