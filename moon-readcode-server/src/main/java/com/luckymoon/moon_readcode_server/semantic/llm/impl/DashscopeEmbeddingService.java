package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云通义千问 Embedding 实现，使用 OpenAI 兼容模式。
 *
 * 通过 pilot.llm.provider=dashscope 激活。
 * 推荐模型：text-embedding-v3（1024 维）
 *
 * 文档：https://help.aliyun.com/zh/model-studio/developer-reference/general-text-embedding
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "dashscope")
public class DashscopeEmbeddingService implements EmbeddingService {

    private static final String DEFAULT_API_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** DashScope 单次最多 25 条文本，超过会报错；这里留点冗余 */
    private static final int BATCH_LIMIT = 20;

    private final PilotProperties properties;
    private final RestClient restClient;

    public DashscopeEmbeddingService(PilotProperties properties) {
        this.properties = properties;
        String apiBase = properties.getLlm().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = DEFAULT_API_BASE;
        }
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("使用 dashscope 时必须配置 pilot.llm.api-key");
        }
        this.restClient = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("DashScope Embedding 已就绪 model={} dim={}",
                properties.getLlm().getEmbeddingModel(), dimension());
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
        // 分批请求，避免超限
        for (int i = 0; i < texts.size(); i += BATCH_LIMIT) {
            List<String> sub = texts.subList(i, Math.min(i + BATCH_LIMIT, texts.size()));
            result.addAll(callApi(sub));
        }
        return result;
    }

    private List<float[]> callApi(List<String> texts) {
        Map<String, Object> request = Map.of(
                "model", modelName(),
                "input", texts,
                "dimensions", dimension(),
                "encoding_format", "float"
        );

        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data == null) {
            throw new IllegalStateException("DashScope embeddings 返回为空");
        }
        // DashScope 返回顺序与请求顺序一致，但仍按 index 排序更稳
        response.data.sort((a, b) -> Integer.compare(a.index, b.index));
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
