package com.luckymoon.moon_readcode_server.semantic.llm.impl;

import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.semantic.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock 实现：不调用真实 LLM，返回基于 prompt 的模板化文本。
 * 作用：让整个 Pipeline 在未配置 LLM API Key 时也能跑通端到端，方便联调。
 *
 * 通过 pilot.llm.provider=mock（默认）激活。
 * 切换到真实 provider 后，将由对应实现类被加载，本类不会注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pilot.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmService implements LlmService {

    private final PilotProperties properties;

    @Override
    public String complete(String prompt) {
        return complete(null, prompt);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        log.debug("[MockLlm] system={} userLen={}",
                systemPrompt == null ? 0 : systemPrompt.length(),
                userPrompt == null ? 0 : userPrompt.length());
        // 输出可读的 mock 摘要：把 prompt 的前若干字符回显，便于人工检查 Pipeline 是否串通
        String preview = userPrompt == null ? "" : userPrompt.substring(0, Math.min(120, userPrompt.length()));
        return "[mock-summary] " + preview.replaceAll("\\s+", " ");
    }

    @Override
    public String chat(List<Message> messages) {
        StringBuilder sb = new StringBuilder("[mock-chat]");
        for (Message m : messages) {
            sb.append(" <").append(m.role()).append(">");
        }
        return sb.toString();
    }

    @Override
    public String modelName() {
        return properties.getLlm().getChatModel();
    }
}
