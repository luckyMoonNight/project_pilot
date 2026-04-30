package com.luckymoon.moon_readcode_server.scanner;

import com.luckymoon.moon_readcode_server.scanner.dto.ScanResult;

/**
 * 工程扫描器。
 * 负责递归读取指定路径下的源文件，按规则过滤后落库到 code_file 表。
 *
 * 设计上故意不依赖 AST/语义层，保持单一职责：只做"找到文件 + 入库"。
 */
public interface ProjectScanner {

    /**
     * 扫描指定路径。
     *
     * @param basePath  工程根目录绝对路径，传 null 时回退到 PilotProperties.scanner.basePath
     * @param projectId 工程标识，传 null 时按 basePath 推导
     * @return 扫描统计结果
     */
    ScanResult scan(String basePath, String projectId);
}
