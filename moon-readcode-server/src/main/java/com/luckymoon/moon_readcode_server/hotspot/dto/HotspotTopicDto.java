package com.luckymoon.moon_readcode_server.hotspot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HotspotTopicDto {

    private Long id;

    private String projectId;

    private String topicName;

    private Integer questionCount;

    private List<String> representativeQuestions;

    private List<String> involvedModules;

    private String analysis;

    /** PENDING / APPROVED / REJECTED / REVISED */
    private String status;

    private String rejectReason;

    private LocalDateTime createTime;

    /** 关联的文档 id（如果已生成） */
    private Long documentId;
}
