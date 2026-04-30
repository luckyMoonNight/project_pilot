package com.luckymoon.moon_readcode_server.hotspot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HotspotAnalyzeResult {

    /** 本次分析使用的问答记录数 */
    private int totalQuestions;

    /** 生成的热点主题数 */
    private int topicCount;

    /** 生成的热点主题列表 */
    private List<HotspotTopicDto> topics;

    /** 提示信息（如问答记录不足时的提醒） */
    private String message;
}
