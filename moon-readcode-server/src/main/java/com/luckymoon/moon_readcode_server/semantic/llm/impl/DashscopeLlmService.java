package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云通义千问（DashScope）LLM 实现，使用 OpenAI 兼容模式 API。
 *
 * 通过 pilot.llm.provider=dashscope 激活。
 * 必须配置 pilot.llm.api-key（即 DashScope 的 API Key）。
 *
 * 文档：https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "dashscope")
public class DashscopeLlmService implements LlmService {

    private static final String DEFAULT_API_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private final PilotProperties properties;
    private final RestClient restClient;

    public DashscopeLlmService(PilotProperties properties) {
        this.properties = properties;
        String apiBase = properties.getLlm().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = DEFAULT_API_BASE;
        }
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("使用 dashscope 时必须配置 pilot.llm.api-key（即 DASHSCOPE_API_KEY）");
        }
        this.restClient = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("DashScope LLM 已就绪 model={} apiBase={}", properties.getLlm().getChatModel(), apiBase);
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
        Map<String, Object> request = Map.of(
                "model", modelName(),
                "messages", body,
                "temperature", 0.3
        );

        ChatResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new IllegalStateException("DashScope 返回为空");
        }
        return response.choices.get(0).message.content;
    }

    @Override
    public String modelName() {
        return properties.getLlm().getChatModel();
    }

    /** OpenAI 兼容格式响应映射 */
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
