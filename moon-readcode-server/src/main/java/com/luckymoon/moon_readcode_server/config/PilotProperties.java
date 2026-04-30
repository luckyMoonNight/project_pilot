package com.luckymoon.moon_readcode_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * project-pilot 配置项。
 * 集中管理：扫描路径、扫描过滤规则、向量库、LLM 模型等所有可调参数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pilot")
public class PilotProperties {

    /** 扫描相关配置 */
    private Scanner scanner = new Scanner();

    /** 向量库（Chroma）配置 */
    private Vector vector = new Vector();

    /** LLM 配置 */
    private Llm llm = new Llm();

    @Data
    public static class Scanner {
        /** 默认要扫描的工程根路径（兼容历史配置 project.code.base-path） */
        private String basePath;

        /** 工程标识，未传入时按 basePath 推导 */
        private String defaultProjectId;

        /** 包含的文件后缀 */
        private List<String> includeExtensions = List.of("java");

        /** 需要跳过的目录名 */
        private List<String> excludeDirs = List.of(
                "target", "build", "out", "node_modules",
                ".git", ".idea", ".gradle", ".mvn"
        );

        /** 单个文件最大字节数，超出则跳过（避免把超大生成代码灌进库） */
        private long maxFileSizeBytes = 1024L * 1024L; // 1MB
    }

    @Data
    public static class Vector {
        /** 向量库类型：chroma / inmemory */
        private String type = "inmemory";
        /** Chroma HTTP 地址 */
        private String chromaUrl = "http://localhost:8000";
        /** Chroma collection 名称 */
        private String chromaCollection = "moon_readcode";
        /** 向量维度（与所选 embedding 模型对齐） */
        private int dimension = 1024;
    }

    @Data
    public static class Llm {
        /** 提供商：mock / dashscope / openai / ollama */
        private String provider = "mock";
        /** 对话模型名 */
        private String chatModel = "mock-chat";
        /** Embedding 模型名 */
        private String embeddingModel = "mock-embedding";
        /** API Key（按 provider 含义不同） */
        private String apiKey;
        /** 自定义 API Base，用于私有部署或代理 */
        private String apiBase;
    }
}
