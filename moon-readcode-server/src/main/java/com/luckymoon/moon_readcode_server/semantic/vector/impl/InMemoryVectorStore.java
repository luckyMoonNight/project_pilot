package com.luckymoon.moon_readcode_server.semantic.vector.impl;

import com.luckymoon.moon_readcode_server.semantic.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量库实现，作为默认实现，无需外部依赖即可让整个流程跑通。
 *
 * 通过 pilot.vector.type=inmemory（默认）激活；
 * 切换到 chroma 时本类不被注入。
 *
 * 注意：仅适用于小规模 POC，进程重启数据丢失。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.vector", name = "type", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, VectorRecord> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorRecord record) {
        store.put(record.getId(), record);
    }

    @Override
    public void upsertAll(List<VectorRecord> records) {
        for (VectorRecord r : records) {
            store.put(r.getId(), r);
        }
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public void deleteByFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            store.clear();
            return;
        }
        store.entrySet().removeIf(e -> matches(e.getValue(), filter));
    }

    @Override
    public List<VectorMatch> search(float[] queryVector, int topK, Map<String, Object> filter) {
        List<VectorMatch> all = new ArrayList<>(store.size());
        for (VectorRecord r : store.values()) {
            if (filter != null && !matches(r, filter)) {
                continue;
            }
            double score = cosine(queryVector, r.getVector());
            all.add(VectorMatch.builder()
                    .id(r.getId())
                    .document(r.getDocument())
                    .metadata(r.getMetadata())
                    .score(score)
                    .build());
        }
        all.sort(Comparator.comparingDouble(VectorMatch::getScore).reversed());
        return all.size() <= topK ? all : new ArrayList<>(all.subList(0, topK));
    }

    private boolean matches(VectorRecord record, Map<String, Object> filter) {
        if (record.getMetadata() == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object actual = record.getMetadata().get(entry.getKey());
            if (actual == null || !actual.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return -1.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
