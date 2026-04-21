package com.luckymoon.moon_readcode_server.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeFile {

    private Long id;

    private String fileName;

    private String filePath;

    private String fileType;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
