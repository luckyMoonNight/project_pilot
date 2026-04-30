package com.luckymoon.moon_readcode_server.semantic.llm;

import java.util.List;

/**
 * LLM 对话服务统一抽象。
 * 任何 provider（DashScope/OpenAI/Ollama/Mock）都需要实现此接口，让上层 Pipeline 不感知具体实现。
 */
public interface LlmService {

    /**
     * 单轮文本补全：给一段 prompt，返回 LLM 的文本回复。
     * 用于：生成文件/类/方法的自然语言摘要、回答用户问题。
     */
    String complete(String prompt);

    /**
     * 带 system 指令的多轮调用。
     * @param systemPrompt 角色/规则约束
     * @param userPrompt   用户输入
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * 多轮对话调用。messages 中每一项形如 ["role", "content"]，role 取值 system/user/assistant。
     * 此方法主要为后续 Phase 4 的多轮问答预留。
     */
    String chat(List<Message> messages);

    /** 当前实现使用的模型名 */
    String modelName();

    /** 简单的对话消息载体 */
    record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }
}
