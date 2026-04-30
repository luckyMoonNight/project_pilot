package com.luckymoon.moon_readcode_server.controller;

import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.service.CodeFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/code-file")
@RequiredArgsConstructor
public class CodeFileController {

    private final CodeFileService codeFileService;

    @GetMapping("/list")
    public ResponseEntity<List<CodeFile>> listAll() {
        return ResponseEntity.ok(codeFileService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CodeFile> getById(@PathVariable Long id) {
        CodeFile codeFile = codeFileService.getById(id);
        if (codeFile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(codeFile);
    }

    @GetMapping("/search")
    public ResponseEntity<List<CodeFile>> getByFilePath(@RequestParam String filePath) {
        return ResponseEntity.ok(codeFileService.getByFilePath(filePath));
    }

    @PostMapping
    public ResponseEntity<String> save(@RequestBody CodeFile codeFile) {
        boolean result = codeFileService.save(codeFile);
        return result ? ResponseEntity.ok("保存成功") : ResponseEntity.internalServerError().body("保存失败");
    }

    @PutMapping
    public ResponseEntity<String> update(@RequestBody CodeFile codeFile) {
        boolean result = codeFileService.updateById(codeFile);
        return result ? ResponseEntity.ok("更新成功") : ResponseEntity.internalServerError().body("更新失败");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        boolean result = codeFileService.deleteById(id);
        return result ? ResponseEntity.ok("删除成功") : ResponseEntity.internalServerError().body("删除失败");
    }
}
