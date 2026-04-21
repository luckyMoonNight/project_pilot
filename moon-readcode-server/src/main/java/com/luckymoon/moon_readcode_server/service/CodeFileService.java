package com.luckymoon.moon_readcode_server.service;

import com.luckymoon.moon_readcode_server.entity.CodeFile;

import java.util.List;

public interface CodeFileService {

    List<CodeFile> listAll();

    CodeFile getById(Long id);

    List<CodeFile> getByFilePath(String filePath);

    boolean save(CodeFile codeFile);

    boolean updateById(CodeFile codeFile);

    boolean deleteById(Long id);
}
