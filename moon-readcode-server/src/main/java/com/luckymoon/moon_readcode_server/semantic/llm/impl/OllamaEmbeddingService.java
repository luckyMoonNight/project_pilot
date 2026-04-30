package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地 Ollama Embedding 实现，使用 OpenAI 兼容模式 API（/v1/embeddings）。
 *
 * 通过 pilot.llm.provider=ollama 激活。
 * 推荐模型：
 *   - bge-m3            （1024 维，多语言强，~1.2GB）
 *   - nomic-embed-text  （768  维，英文好，~274MB）
 *
 * ⚠️ Ollama 的 embedding 接口不支持自定义 dimensions，维度由模型决定，
 *    需要保证 pilot.vector.dimension 与所选模型实际维度一致。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "ollama")
public class OllamaEmbeddingService implements EmbeddingService {

    private static final String DEFAULT_API_BASE = "http://localhost:11434/v1";

    /** Ollama 单次批量没有硬上限，但本地推理 embedding 是串行的，分批避免一次卡太久 */
    private static final int BATCH_LIMIT = 16;

    private final PilotProperties properties;
    private final RestClient restClient;

    public OllamaEmbeddingService(PilotProperties properties) {
        this.properties = properties;
        String apiBase = properties.getLlm().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = DEFAULT_API_BASE;
        }
        String apiKey = properties.getLlm().getApiKey();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
        log.info("Ollama Embedding 已就绪 model={} dim={} apiBase={}",
                properties.getLlm().getEmbeddingModel(), dimension(), apiBase);
    }

    @Override
    public float[] embed(String text) {
        List<float[]> all = embedAll(List.of(text == null ? "" : text));
        return all.isEmpty() ? new float[dimension()] : all.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_LIMIT) {
            List<String> sub = texts.subList(i, Math.min(i + BATCH_LIMIT, texts.size()));
            result.addAll(callApi(sub));
        }
        return result;
    }

    private List<float[]> callApi(List<String> texts) {
        // Ollama 的 OpenAI 兼容接口：input 接收数组；不支持 dimensions 参数（传了会被忽略）
        Map<String, Object> request = Map.of(
                "model", modelName(),
                "input", texts,
                "encoding_format", "float"
        );

        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "调用 Ollama embedding 失败：请确认已执行 `ollama pull " + modelName() + "`。"
                            + "原始错误：" + e.getMessage(), e);
        }

        if (response == null || response.data == null || response.data.isEmpty()) {
            throw new IllegalStateException("Ollama embeddings 返回为空");
        }
        // 按 index 排序，避免 provider 乱序
        response.data.sort((a, b) -> Integer.compare(a.index, b.index));

        // 维度自检：若配置维度与实际不一致，提前告警，避免后面写入向量库时出错
        int actualDim = response.data.get(0).embedding.size();
        if (actualDim != dimension()) {
            log.warn("⚠️ 配置的 pilot.vector.dimension={} 与模型 {} 实际维度 {} 不一致，"
                            + "请把 PILOT_VECTOR_DIMENSION 改成 {} 后重启",
                    dimension(), modelName(), actualDim, actualDim);
        }

        List<float[]> result = new ArrayList<>(response.data.size());
        for (EmbeddingItem item : response.data) {
            float[] vec = new float[item.embedding.size()];
            for (int i = 0; i < vec.length; i++) {
                vec[i] = item.embedding.get(i);
            }
            result.add(vec);
        }
        return result;
    }

    @Override
    public int dimension() {
        return properties.getVector().getDimension();
    }

    @Override
    public String modelName() {
        return properties.getLlm().getEmbeddingModel();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingResponse {
        public List<EmbeddingItem> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingItem {
        public int index;
        public List<Float> embedding;
    }
}
