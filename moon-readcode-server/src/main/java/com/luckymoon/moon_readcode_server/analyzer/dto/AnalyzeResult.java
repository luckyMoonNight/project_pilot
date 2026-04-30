package com.luckymoon.moon_readcode_server.analyzer.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 一次 AST 分析的统计结果。
 */
@Data
@Builder
public class AnalyzeResult {

    private String projectId;

    /** 处理的文件总数 */
    private int totalFiles;

    /** 解析成功的文件数 */
    private int parsedFiles;

    /** 解析失败的文件数（语法错误等） */
    private int failedFiles;

    /** 提取出的类总数 */
    private int classes;

    /** 提取出的方法总数 */
    private int methods;

    /** 提取出的关系总数 */
    private int relations;

    private long costMillis;
}
