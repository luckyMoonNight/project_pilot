package com.luckymoon.moon_readcode_server.hotspot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HotspotDocumentDto {

    private Long id;

    private String projectId;

    private Long topicId;

    private String topicName;

    private String title;

    /** 文档正文（Markdown） */
    private String content;

    /** GENERATED / PUBLISHED */
    private String status;

    private LocalDateTime createTime;
}
