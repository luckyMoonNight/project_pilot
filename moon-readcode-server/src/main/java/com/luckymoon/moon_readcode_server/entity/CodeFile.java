package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工程文件元数据。
 * 一个工程被扫描后，每一个 .java（以及未来可能扩展的）源文件对应一行。
 */
@Data
public class CodeFile {

    private Long id;

    /** 工程标识（一次扫描归属一个 project） */
    private String projectId;

    private String fileName;

    /** 相对工程根目录的相对路径，便于跨机器复用 */
    private String filePath;

    /** 文件后缀类型，如 java / xml / properties */
    private String fileType;

    /** Java 文件的包名，方便按模块聚合 */
    private String packageName;

    /** 文件内容 SHA-256，用于增量识别（内容未变则跳过 AST/语义重算） */
    private String contentHash;

    /** 文件原始内容 */
    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
