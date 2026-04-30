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

    /** 根据工程标识 + 相对路径定位文件，扫描增量识别使用 */
    CodeFile selectByProjectAndPath(@Param("projectId") String projectId,
                                    @Param("filePath") String filePath);

    /** 列出某工程下所有文件 */
    List<CodeFile> selectByProjectId(@Param("projectId") String projectId);

    int insert(CodeFile codeFile);

    int updateById(CodeFile codeFile);

    int deleteById(@Param("id") Long id);
}
