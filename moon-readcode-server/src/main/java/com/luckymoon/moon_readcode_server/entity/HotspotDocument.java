package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotspotDocument {

    private Long id;

    private String projectId;

    /** 关联的热点主题 id */
    private Long topicId;

    private String title;

    /** 文档正文（Markdown） */
    private String content;

    /** GENERATED / PUBLISHED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
