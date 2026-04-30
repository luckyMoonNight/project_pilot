package com.luckymoon.moon_readcode_server.analyzer;

import com.luckymoon.moon_readcode_server.analyzer.dto.AnalyzeResult;

/**
 * Java AST 分析器：基于 JavaParser 把 code_file 中的源码解析为类/方法/关系结构化数据。
 *
 * 调用前提：先由 ProjectScanner 把工程文件扫描入库。
 */
public interface JavaAstAnalyzer {

    /**
     * 分析指定工程的所有 java 文件，写入 code_class / code_method / code_relation。
     *
     * @param projectId 工程标识
     * @return 分析结果统计
     */
    AnalyzeResult analyze(String projectId);
}
