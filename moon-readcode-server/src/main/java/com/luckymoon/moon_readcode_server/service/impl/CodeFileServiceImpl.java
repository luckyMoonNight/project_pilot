package com.luckymoon.moon_readcode_server.service.impl;

import com.luckymoon.moon_readcode_server.entity.CodeFile;
import com.luckymoon.moon_readcode_server.mapper.CodeFileMapper;
import com.luckymoon.moon_readcode_server.service.CodeFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeFileServiceImpl implements CodeFileService {

    private final CodeFileMapper codeFileMapper;

    @Override
    public List<CodeFile> listAll() {
        return codeFileMapper.selectAll();
    }

    @Override
    public CodeFile getById(Long id) {
        return codeFileMapper.selectById(id);
    }

    @Override
    public List<CodeFile> getByFilePath(String filePath) {
        return codeFileMapper.selectByFilePath(filePath);
    }

    @Override
    public boolean save(CodeFile codeFile) {
        return codeFileMapper.insert(codeFile) > 0;
    }

    @Override
    public boolean updateById(CodeFile codeFile) {
        return codeFileMapper.updateById(codeFile) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return codeFileMapper.deleteById(id) > 0;
    }
}
