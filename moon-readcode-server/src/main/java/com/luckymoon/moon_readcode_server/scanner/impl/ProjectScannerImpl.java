package com.luckymoon.moon_readcode_server.scanner.impl;

import com.luckymoon.moon_readcode_server.config.PilotProperties;
import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.mapper.CodeFileMapper;
import com.luckymoon.moon_readcode_server.scanner.ProjectScanner;
import com.luckymoon.moon_readcode_server.scanner.dto.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认扫描器实现：基于 NIO Files.walkFileTree 递归遍历。
 * 增量策略：基于 SHA-256 hash 比对，未变化跳过、变化更新、新文件插入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectScannerImpl implements ProjectScanner {

    /** 简单的 Java 包名正则；只要拿到 package 一行即可，不需要完整解析 */
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private final PilotProperties properties;
    private final CodeFileMapper codeFileMapper;

    @Override
    public ScanResult scan(String basePath, String projectId) {
        long start = System.currentTimeMillis();

        String resolvedBase = StringUtils.firstNonBlank(basePath, properties.getScanner().getBasePath());
        if (StringUtils.isBlank(resolvedBase)) {
            throw new IllegalArgumentException("扫描路径未指定：请传入 basePath 或配置 pilot.scanner.base-path");
        }
        Path root = Paths.get(resolvedBase).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("扫描路径不存在或不是目录：" + root);
        }

        String resolvedProjectId = StringUtils.firstNonBlank(
                projectId,
                properties.getScanner().getDefaultProjectId(),
                root.getFileName().toString()
        );

        Set<String> includeExt = new HashSet<>(properties.getScanner().getIncludeExtensions());
        Set<String> excludeDirs = new HashSet<>(properties.getScanner().getExcludeDirs());
        long maxSize = properties.getScanner().getMaxFileSizeBytes();

        Counter counter = new Counter();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (excludeDirs.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    counter.total++;
                    String ext = extractExtension(file.getFileName().toString());
                    if (!includeExt.contains(ext)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (attrs.size() > maxSize) {
                        log.warn("跳过超大文件：{} ({} bytes)", file, attrs.size());
                        counter.skipped++;
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        handleFile(root, file, ext, resolvedProjectId, counter);
                    } catch (Exception e) {
                        log.warn("处理文件失败：{}", file, e);
                        counter.skipped++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("扫描目录出错：" + root, e);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("扫描完成 projectId={} base={} 总计={} 新增={} 更新={} 未变={} 跳过={} 耗时={}ms",
                resolvedProjectId, root, counter.total, counter.inserted,
                counter.updated, counter.unchanged, counter.skipped, cost);

        return ScanResult.builder()
                .projectId(resolvedProjectId)
                .basePath(root.toString())
                .totalFiles(counter.total)
                .insertedFiles(counter.inserted)
                .updatedFiles(counter.updated)
                .unchangedFiles(counter.unchanged)
                .skippedFiles(counter.skipped)
                .costMillis(cost)
                .build();
    }

    private void handleFile(Path root, Path file, String ext, String projectId, Counter counter) throws IOException {
        String relativePath = root.relativize(file).toString().replace('\\', '/');
        String content = Files.readString(file);
        String hash = DigestUtils.sha256Hex(content);

        CodeFile existing = codeFileMapper.selectByProjectAndPath(projectId, relativePath);
        if (existing != null && hash.equals(existing.getContentHash())) {
            counter.unchanged++;
            return;
        }

        CodeFile entity = new CodeFile();
        entity.setProjectId(projectId);
        entity.setFileName(file.getFileName().toString());
        entity.setFilePath(relativePath);
        entity.setFileType(ext);
        entity.setPackageName(extractPackageName(content, ext));
        entity.setContentHash(hash);
        entity.setContent(content);

        if (existing == null) {
            codeFileMapper.insert(entity);
            counter.inserted++;
        } else {
            entity.setId(existing.getId());
            codeFileMapper.updateById(entity);
            counter.updated++;
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private String extractPackageName(String content, String ext) {
        if (!"java".equals(ext)) {
            return null;
        }
        Matcher m = PACKAGE_PATTERN.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    /** 内部计数器，避免散乱的局部变量 */
    private static class Counter {
        int total;
        int inserted;
        int updated;
        int unchanged;
        int skipped;
    }
}
