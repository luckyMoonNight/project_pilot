package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.CodeFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeFileMapper {

    List<CodeFile> selectAll();

    CodeFile selectById(@Param("id") Long id);

    List<CodeFile> selectByFilePath(@Param("filePath") String filePath);

    int insert(CodeFile codeFile);

    int updateById(CodeFile codeFile);

    int deleteById(@Param("id") Long id);
}
