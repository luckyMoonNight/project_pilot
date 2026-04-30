package com.luckymoon.moon_readcode_server.controller;

import com.luckymoon.moon_readcode_server.analyzer.JavaAstAnalyzer;
import com.luckymoon.moon_readcode_server.analyzer.dto.AnalyzeResult;
import com.luckymoon.moon_readcode_server.qa.QaService;
import com.luckymoon.moon_readcode_server.qa.dto.QaRequest;
import com.luckymoon.moon_readcode_server.qa.dto.QaResponse;
import com.luckymoon.moon_readcode_server.report.ProjectReportService;
import com.luckymoon.moon_readcode_server.report.dto.ProjectReport;
import com.luckymoon.moon_readcode_server.scanner.ProjectScanner;
import com.luckymoon.moon_readcode_server.scanner.dto.ScanResult;
import com.luckymoon.moon_readcode_server.semantic.SemanticIndexService;
import com.luckymoon.moon_readcode_server.semantic.dto.SummarizeResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * project-pilot 统一编排接口。
 *
 * 推荐调用顺序：
 *   1) POST /api/pilot/scan        →  扫描工程文件入库（Phase 1）
 *   2) POST /api/pilot/analyze     →  AST 结构化（Phase 2）
 *   3) POST /api/pilot/index       →  语义摘要 + 向量化（Phase 3）
 *   4) POST /api/pilot/qa          →  基于索引的问答（Phase 4）
 *   5) POST /api/pilot/report      →  生成综合分析报告（Phase 5）
 *   * POST /api/pilot/run-all      →  按顺序串联前 1-3 步，便于一键体验
 */
@RestController
@RequestMapping("/api/pilot")
@RequiredArgsConstructor
public class PilotController {

    private final ProjectScanner projectScanner;
    private final JavaAstAnalyzer javaAstAnalyzer;
    private final SemanticIndexService semanticIndexService;
    private final QaService qaService;
    private final ProjectReportService projectReportService;

    @PostMapping("/scan")
    public ResponseEntity<ScanResult> scan(@RequestBody(required = false) ScanRequest req) {
        String basePath = req == null ? null : req.getBasePath();
        String projectId = req == null ? null : req.getProjectId();
        return ResponseEntity.ok(projectScanner.scan(basePath, projectId));
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResult> analyze(@RequestParam String projectId) {
        return ResponseEntity.ok(javaAstAnalyzer.analyze(projectId));
    }

    @PostMapping("/index")
    public ResponseEntity<SummarizeResult> index(@RequestParam String projectId) {
        return ResponseEntity.ok(semanticIndexService.buildIndex(projectId));
    }

    @PostMapping("/qa")
    public ResponseEntity<QaResponse> qa(@RequestBody QaRequest request) {
        return ResponseEntity.ok(qaService.ask(request));
    }

    @PostMapping("/report")
    public ResponseEntity<ProjectReport> report(@RequestParam String projectId) {
        return ResponseEntity.ok(projectReportService.generate(projectId));
    }

    /**
     * 一键串行：扫描 → AST → 语义索引。完成后即可直接 /qa 与 /report。
     */
    @PostMapping("/run-all")
    public ResponseEntity<RunAllResult> runAll(@RequestBody(required = false) ScanRequest req) {
        ScanResult scan = projectScanner.scan(
                req == null ? null : req.getBasePath(),
                req == null ? null : req.getProjectId());
        AnalyzeResult analyze = javaAstAnalyzer.analyze(scan.getProjectId());
        SummarizeResult index = semanticIndexService.buildIndex(scan.getProjectId());
        return ResponseEntity.ok(RunAllResult.builder()
                .scan(scan)
                .analyze(analyze)
                .index(index)
                .build());
    }

    @Data
    public static class ScanRequest {
        private String basePath;
        private String projectId;
    }

    @Data
    @lombok.Builder
    public static class RunAllResult {
        private ScanResult scan;
        private AnalyzeResult analyze;
        private SummarizeResult index;
    }
}
