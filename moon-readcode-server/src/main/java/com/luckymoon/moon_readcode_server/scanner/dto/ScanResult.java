package com.luckymoon.moon_readcode_server.scanner.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 一次扫描的统计结果。
 */
@Data
@Builder
public class ScanResult {

    /** 工程标识 */
    private String projectId;

    /** 扫描的根路径 */
    private String basePath;

    /** 扫描到的总文件数 */
    private int totalFiles;

    /** 新增入库的文件数 */
    private int insertedFiles;

    /** 更新的文件数（hash 变化） */
    private int updatedFiles;

    /** 内容未变化跳过的文件数 */
    private int unchangedFiles;

    /** 因超大或读取失败而跳过的文件数 */
    private int skippedFiles;

    /** 耗时毫秒 */
    private long costMillis;
}
