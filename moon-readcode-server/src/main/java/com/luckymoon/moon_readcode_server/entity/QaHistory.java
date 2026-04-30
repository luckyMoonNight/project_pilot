package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaHistory {

    private Long id;

    private String projectId;

    private String question;

    /** 问题的 embedding 向量（JSON 数组，用于聚类） */
    private String questionVector;

    private String answer;

    /** 命中的代码引用（JSON 数组） */
    private String hitRefs;

    /** 用户反馈：null/GOOD/BAD */
    private String feedback;

    private LocalDateTime createTime;
}
