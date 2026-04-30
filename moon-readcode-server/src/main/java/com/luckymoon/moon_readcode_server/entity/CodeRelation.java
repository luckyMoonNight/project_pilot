package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码间关系表，用于支撑"执行链路"分析。
 * 关系包括：方法调用、类依赖、字段注入、继承、实现等。
 */
@Data
public class CodeRelation {

    private Long id;

    private String projectId;

    /** METHOD_CALL / CLASS_DEPEND / FIELD_INJECT / IMPLEMENT / EXTEND */
    private String relationType;

    /** 源对象 id（method id 或 class id） */
    private Long fromId;

    /** CLASS / METHOD */
    private String fromType;

    /** 目标的全限定名或方法签名（可能是工程外的库，无法解析到 id） */
    private String toRef;

    /** 若已能解析到本工程内的具体目标，则记录其 id */
    private Long toId;

    private LocalDateTime createTime;
}
