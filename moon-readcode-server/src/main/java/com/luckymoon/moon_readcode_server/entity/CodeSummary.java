package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语义摘要表实体。
 * 一条记录对应：某个目标（FILE/CLASS/METHOD/MODULE/PROJECT）的自然语言摘要 + 向量库 id。
 */
@Data
public class CodeSummary {

    private Long id;

    private String projectId;

    /** FILE / CLASS / METHOD / MODULE / PROJECT */
    private String targetType;

    /** 对应表的主键 id（PROJECT 维度时为 null） */
    private Long targetId;

    /** 冗余的可读标识，如全限定名、方法签名、模块包名 */
    private String targetRef;

    /** LLM 生成的自然语言摘要 */
    private String summary;

    /** 在向量库中的 id（与 Chroma 对齐） */
    private String vectorId;

    /** 生成时所用模型 */
    private String modelName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
