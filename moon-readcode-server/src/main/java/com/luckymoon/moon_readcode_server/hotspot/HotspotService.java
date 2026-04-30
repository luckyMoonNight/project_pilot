package com.luckymoon.moon_readcode_server.hotspot;

import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotAnalyzeResult;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotDocumentDto;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotTopicDto;

import java.util.List;

public interface HotspotService {

    /** 分析热点问题（聚类），返回生成的主题列表 */
    HotspotAnalyzeResult analyze(String projectId);

    /** 获取热点主题列表 */
    List<HotspotTopicDto> listTopics(String projectId);

    /** 审核通过 → 触发文档生成 */
    HotspotDocumentDto approve(Long topicId);

    /** 驳回，附带原因 */
    void reject(Long topicId, String reason);

    /** 修正分析结论后通过 */
    HotspotDocumentDto revise(Long topicId, String revisedAnalysis);

    /** 获取文档列表 */
    List<HotspotDocumentDto> listDocuments(String projectId);

    /** 获取文档详情 */
    HotspotDocumentDto getDocument(Long documentId);
}
