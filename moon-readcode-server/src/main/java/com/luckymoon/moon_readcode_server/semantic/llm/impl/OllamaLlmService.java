package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
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
 * 本地 Ollama LLM 实现，使用 OpenAI 兼容模式 API（/v1/chat/completions）。
 *
 * 通过 pilot.llm.provider=ollama 激活。
 * 默认指向 docker compose 内的 ollama 服务（http://ollama:11434/v1）。
 * 宿主机直跑可改为 http://host.docker.internal:11434/v1 或 http://localhost:11434/v1。
 *
 * 注意：调用前必须先 ollama pull <model>，否则会 404。
 * 文档：https://github.com/ollama/ollama/blob/main/docs/openai.md
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "ollama")
public class OllamaLlmService implements LlmService {

    private static final String DEFAULT_API_BASE = "http://localhost:11434/v1";

    private final PilotProperties properties;
    private final RestClient restClient;

    public OllamaLlmService(PilotProperties properties) {
        this.properties = properties;
        String apiBase = properties.getLlm().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = DEFAULT_API_BASE;
        }
        // Ollama 不校验 key，传任何字符串都行；若用户配了就带上，便于一些代理网关复用
        String apiKey = properties.getLlm().getApiKey();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
        log.info("Ollama LLM 已就绪 model={} apiBase={}", properties.getLlm().getChatModel(), apiBase);
    }

    @Override
    public String complete(String prompt) {
        return chat(List.of(Message.user(prompt)));
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }
        messages.add(Message.user(userPrompt));
        return chat(messages);
    }

    @Override
    public String chat(List<Message> messages) {
        List<Map<String, String>> body = new ArrayList<>(messages.size());
        for (Message m : messages) {
            body.add(Map.of("role", m.role(), "content", m.content()));
        }
        // Ollama 默认 stream=true，必须显式关闭，否则返回 SSE，反序列化失败
        Map<String, Object> request = Map.of(
                "model", modelName(),
                "messages", body,
                "temperature", 0.3,
                "stream", false
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "调用 Ollama 失败：请确认已执行 `ollama pull " + modelName() + "`，"
                            + "并确认服务地址可达。原始错误：" + e.getMessage(), e);
        }

        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new IllegalStateException("Ollama 返回为空");
        }
        return response.choices.get(0).message.content;
    }

    @Override
    public String modelName() {
        return properties.getLlm().getChatModel();
    }

    /** OpenAI 兼容格式响应映射（Ollama 也是这套结构） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Choice {
        public ChoiceMessage message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChoiceMessage {
        public String content;
    }
}
