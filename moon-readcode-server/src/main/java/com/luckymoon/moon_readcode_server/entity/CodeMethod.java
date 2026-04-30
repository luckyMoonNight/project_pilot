package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 方法级元数据。
 * signature 形如 com.foo.UserService#getUser(java.lang.Long)，作为方法的唯一定位 key。
 */
@Data
public class CodeMethod {

    private Long id;

    private String projectId;

    /** 所属类 id（code_class.id） */
    private Long classId;

    private String methodName;

    /** 方法签名，全限定类名 + #方法名 + (参数类型列表) */
    private String signature;

    private String returnType;

    /** 参数列表，存 JSON：[{"name":"id","type":"java.lang.Long"}] */
    private String parameters;

    /** 方法注解，逗号分隔 */
    private String annotations;

    /** 修饰符，逗号分隔，如 public,static */
    private String modifiers;

    /** 方法体源码片段（截取自源文件），用于后续语义摘要 */
    private String bodySnippet;

    private Integer startLine;

    private Integer endLine;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
