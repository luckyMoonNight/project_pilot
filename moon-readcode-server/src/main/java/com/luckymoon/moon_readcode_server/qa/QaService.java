package com.luckymoon.moon_readcode_server.qa;

import com.luckymoon.moon_readcode_server.qa.dto.QaRequest;
import com.luckymoon.moon_readcode_server.qa.dto.QaResponse;

/**
 * RAG 问答服务（Phase 4）。
 */
public interface QaService {

    /**
     * 基于已建好的语义索引回答问题。
     */
    QaResponse ask(QaRequest request);
}
