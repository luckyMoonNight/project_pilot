package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotspotTopic {

    private Long id;

    private String projectId;

    private String topicName;

    private Integer questionCount;

    /** 代表性问题（JSON 数组） */
    private String representativeQuestions;

    /** 涉及的代码模块（JSON 数组） */
    private String involvedModules;

    /** 系统分析结论 */
    private String analysis;

    /** PENDING / APPROVED / REJECTED / REVISED */
    private String status;

    /** 被驳回的原因 */
    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
