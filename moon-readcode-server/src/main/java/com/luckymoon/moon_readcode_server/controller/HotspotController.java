package com.luckymoon.moon_readcode_server.controller;

import com.luckymoon.moon_readcode_server.hotspot.HotspotService;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotAnalyzeResult;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotDocumentDto;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotTopicDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 热点问题分析与文档沉淀 API。
 */
@RestController
@RequestMapping("/api/pilot/hotspot")
@RequiredArgsConstructor
public class HotspotController {

    private final HotspotService hotspotService;

    /** 触发热点分析（聚类 + 生成主题） */
    @PostMapping("/analyze")
    public ResponseEntity<HotspotAnalyzeResult> analyze(@RequestParam String projectId) {
        return ResponseEntity.ok(hotspotService.analyze(projectId));
    }

    /** 获取热点主题列表 */
    @GetMapping("/topics")
    public ResponseEntity<List<HotspotTopicDto>> listTopics(@RequestParam String projectId) {
        return ResponseEntity.ok(hotspotService.listTopics(projectId));
    }

    /** 审核通过 → 触发文档生成 */
    @PostMapping("/topics/{id}/approve")
    public ResponseEntity<HotspotDocumentDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(hotspotService.approve(id));
    }

    /** 驳回，附带原因 */
    @PostMapping("/topics/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        hotspotService.reject(id, request.getReason());
        return ResponseEntity.ok().build();
    }

    /** 修正分析结论后通过 */
    @PostMapping("/topics/{id}/revise")
    public ResponseEntity<HotspotDocumentDto> revise(@PathVariable Long id,
                                                      @RequestBody ReviseRequest request) {
        return ResponseEntity.ok(hotspotService.revise(id, request.getRevisedAnalysis()));
    }

    /** 获取文档列表 */
    @GetMapping("/documents")
    public ResponseEntity<List<HotspotDocumentDto>> listDocuments(@RequestParam String projectId) {
        return ResponseEntity.ok(hotspotService.listDocuments(projectId));
    }

    /** 获取文档详情 */
    @GetMapping("/documents/{id}")
    public ResponseEntity<HotspotDocumentDto> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(hotspotService.getDocument(id));
    }

    @Data
    public static class RejectRequest {
        private String reason;
    }

    @Data
    public static class ReviseRequest {
        private String revisedAnalysis;
    }
}
