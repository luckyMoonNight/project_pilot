package com.luckymoon.moon_readcode_server.hotspot.scheduler;

import com.luckymoon.moon_readcode_server.hotspot.HotspotService;
import com.luckymoon.moon_readcode_server.hotspot.dto.HotspotAnalyzeResult;
import com.luckymoon.moon_readcode_server.mapper.QaHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 热点分析定时任务。
 * 默认每天凌晨 2 点触发，可通过配置关闭或调整 cron 表达式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotspotScheduler {

    private static final int MIN_NEW_QUESTIONS = 5;

    private final HotspotService hotspotService;
    private final QaHistoryMapper qaHistoryMapper;

    @Value("${pilot.hotspot.auto-analyze-enabled:false}")
    private boolean autoAnalyzeEnabled;

    @Value("${pilot.hotspot.auto-analyze-project-id:}")
    private String autoAnalyzeProjectId;

    /**
     * 定时触发热点分析。
     * cron 表达式通过配置 pilot.hotspot.auto-analyze-cron 控制，默认每天凌晨 2 点。
     */
    @Scheduled(cron = "${pilot.hotspot.auto-analyze-cron:0 0 2 * * ?}")
    public void scheduledAnalyze() {
        if (!autoAnalyzeEnabled) {
            log.debug("热点自动分析已关闭（pilot.hotspot.auto-analyze-enabled=false）");
            return;
        }

        if (autoAnalyzeProjectId == null || autoAnalyzeProjectId.isBlank()) {
            log.warn("热点自动分析：未配置 pilot.hotspot.auto-analyze-project-id，跳过");
            return;
        }

        // 检查最近 24 小时内是否有足够的新问答记录
        String yesterday = LocalDateTime.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int recentCount = qaHistoryMapper.countAfter(autoAnalyzeProjectId, yesterday);

        if (recentCount < MIN_NEW_QUESTIONS) {
            log.info("热点自动分析：最近 24 小时新增问答 {} 条，不足 {} 条，跳过",
                    recentCount, MIN_NEW_QUESTIONS);
            return;
        }

        log.info("热点自动分析：开始分析 projectId={}, 最近新增 {} 条问答",
                autoAnalyzeProjectId, recentCount);

        try {
            HotspotAnalyzeResult result = hotspotService.analyze(autoAnalyzeProjectId);
            log.info("热点自动分析完成：{}", result.getMessage());
        } catch (Exception e) {
            log.error("热点自动分析失败：{}", e.getMessage(), e);
        }
    }
}
