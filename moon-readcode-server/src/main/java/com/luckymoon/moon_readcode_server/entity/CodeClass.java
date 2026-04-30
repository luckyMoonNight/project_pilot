package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 类级元数据：每一个 Java 类/接口/枚举对应一行。
 * 由 Phase 2 AST 分析器从 code_file 中解析得到。
 */
@Data
public class CodeClass {

    private Long id;

    private String projectId;

    /** 所属源文件 id（code_file.id） */
    private Long fileId;

    /** 简单类名 */
    private String className;

    /** 全限定类名：包名 + 类名 */
    private String qualifiedName;

    private String packageName;

    /** CLASS / INTERFACE / ENUM / RECORD / ANNOTATION */
    private String classType;

    /**
     * 业务原型，由注解推断出的常见 Spring 角色：
     * CONTROLLER / SERVICE / MAPPER / ENTITY / CONFIG / OTHER
     */
    private String stereotype;

    private String superClass;

    /** 实现的接口，逗号分隔 */
    private String interfaces;

    /** 类上注解，逗号分隔（如 @Service,@RequiredArgsConstructor） */
    private String annotations;

    private Integer startLine;

    private Integer endLine;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
