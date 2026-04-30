package com.luckymoon.moon_readcode_server.semantic.vector.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chroma 向量库实现（使用 JDK HttpClient，避免 Spring RestClient 序列化问题）。
 *
 * 通过 pilot.vector.type=chroma 激活。
 * 当前实现按 Chroma v1 REST 协议编写（0.4.x 兼容）。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.vector", name = "type", havingValue = "chroma")
public class ChromaVectorStore implements VectorStore {

    private final PilotProperties properties;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 缓存 collection 的内部 uuid */
    private volatile String collectionId;

    public ChromaVectorStore(PilotProperties properties) {
        this.properties = properties;
        this.baseUrl = properties.getVector().getChromaUrl();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("ChromaVectorStore 初始化 baseUrl={}", baseUrl);
    }

    @Override
    public void upsert(VectorRecord record) {
        upsertAll(List.of(record));
    }

    @Override
    public void upsertAll(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        ensureCollection();

        Map<String, Object> body = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(records.size());
        List<float[]> embeddings = new ArrayList<>(records.size());
        List<String> documents = new ArrayList<>(records.size());
        List<Map<String, Object>> metadatas = new ArrayList<>(records.size());
        for (VectorRecord r : records) {
            ids.add(r.getId());
            embeddings.add(r.getVector());
            documents.add(r.getDocument());
            metadatas.add(r.getMetadata() == null ? Map.of() : r.getMetadata());
        }
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        body.put("metadatas", metadatas);

        post("/api/v1/collections/" + collectionId + "/upsert", body);
    }

    @Override
    public void delete(String id) {
        ensureCollection();
        post("/api/v1/collections/" + collectionId + "/delete", Map.of("ids", List.of(id)));
    }

    @Override
    public void deleteByFilter(Map<String, Object> filter) {
        ensureCollection();
        if (filter != null && !filter.isEmpty()) {
            post("/api/v1/collections/" + collectionId + "/delete", Map.of("where", filter));
        } else {
            // Chroma 不允许空 body 删全部；先 GET 全量 ids 再按 ids 删
            Map<String, Object> getResp = postForMap(
                    "/api/v1/collections/" + collectionId + "/get",
                    Map.of("include", List.of()));
            if (getResp == null || getResp.get("ids") == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> allIds = (List<String>) getResp.get("ids");
            if (allIds.isEmpty()) {
                return;
            }
            post("/api/v1/collections/" + collectionId + "/delete", Map.of("ids", allIds));
        }
    }

    @Override
    public List<VectorMatch> search(float[] queryVector, int topK, Map<String, Object> filter) {
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(queryVector));
        body.put("n_results", topK);
        body.put("include", List.of("documents", "metadatas", "distances"));
        if (filter != null && !filter.isEmpty()) {
            body.put("where", filter);
        }

        ChromaQueryResponse resp = postForObject(
                "/api/v1/collections/" + collectionId + "/query",
                body, ChromaQueryResponse.class);

        if (resp == null || resp.ids == null || resp.ids.isEmpty()) {
            return List.of();
        }
        List<String> ids = resp.ids.get(0);
        List<String> docs = resp.documents == null || resp.documents.isEmpty()
                ? List.of() : resp.documents.get(0);
        List<Map<String, Object>> metas = resp.metadatas == null || resp.metadatas.isEmpty()
                ? List.of() : resp.metadatas.get(0);
        List<Double> dists = resp.distances == null || resp.distances.isEmpty()
                ? List.of() : resp.distances.get(0);

        List<VectorMatch> matches = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            double dist = i < dists.size() ? dists.get(i) : 0.0;
            matches.add(VectorMatch.builder()
                    .id(ids.get(i))
                    .document(i < docs.size() ? docs.get(i) : null)
                    .metadata(i < metas.size() ? metas.get(i) : Map.of())
                    .score(1.0 / (1.0 + dist))
                    .build());
        }
        return matches;
    }

    /* ====== Chroma collection 初始化 ====== */

    private synchronized void ensureCollection() {
        if (collectionId != null) {
            return;
        }
        String name = properties.getVector().getChromaCollection();
        // 尝试 GET 已有 collection
        try {
            String url = baseUrl + "/api/v1/collections/" + name;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> existing = objectMapper.readValue(
                        response.body(), new TypeReference<>() {});
                if (existing.get("id") != null) {
                    this.collectionId = existing.get("id").toString();
                    log.info("Chroma collection 就绪 (已存在) name={} id={}", name, collectionId);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("Chroma collection {} GET 失败，将尝试创建：{}", name, e.getMessage());
        }

        // 创建
        Map<String, Object> created = postForMap(
                "/api/v1/collections",
                Map.of("name", name, "get_or_create", true));
        if (created == null || created.get("id") == null) {
            throw new IllegalStateException("无法创建 Chroma collection: " + name);
        }
        this.collectionId = created.get("id").toString();
        log.info("Chroma collection 就绪 (新创建) name={} id={}", name, collectionId);
    }

    /* ====== 底层 HTTP 工具 ====== */

    /** 发 POST，忽略响应体 */
    private void post(String path, Object body) {
        doPost(path, body);
    }

    /** 发 POST，返回 Map */
    private Map<String, Object> postForMap(String path, Object body) {
        String responseBody = doPost(path, body);
        try {
            return objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败: " + responseBody, e);
        }
    }

    /** 发 POST，返回指定类型对象 */
    private <T> T postForObject(String path, Object body, Class<T> responseType) {
        String responseBody = doPost(path, body);
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败: " + responseBody, e);
        }
    }

    /** 统一 POST 请求 */
    private String doPost(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String url = baseUrl + path;
            log.debug("Chroma POST url={} body={}", url, json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .version(HttpClient.Version.HTTP_1_1)  // 强制 HTTP/1.1，避免 HTTP/2 协商问题
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("Chroma response status={} body={}", response.statusCode(),
                    response.body().length() > 200 ? response.body().substring(0, 200) + "..." : response.body());

            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Chroma POST " + path + " 返回 " + response.statusCode()
                        + ": " + response.body());
            }
            return response.body();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Chroma 请求失败 path=" + path, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChromaQueryResponse {
        public List<List<String>> ids;
        public List<List<String>> documents;
        public List<List<Map<String, Object>>> metadatas;
        public List<List<Double>> distances;
    }
}
