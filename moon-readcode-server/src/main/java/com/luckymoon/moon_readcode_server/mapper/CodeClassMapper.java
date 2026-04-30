package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.CodeClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeClassMapper {

    int insert(CodeClass codeClass);

    int updateById(CodeClass codeClass);

    int deleteByFileId(@Param("fileId") Long fileId);

    int deleteByProjectId(@Param("projectId") String projectId);

    CodeClass selectById(@Param("id") Long id);

    CodeClass selectByQualifiedName(@Param("projectId") String projectId,
                                    @Param("qualifiedName") String qualifiedName);

    List<CodeClass> selectByFileId(@Param("fileId") Long fileId);

    List<CodeClass> selectByProjectId(@Param("projectId") String projectId);

    List<CodeClass> selectByStereotype(@Param("projectId") String projectId,
                                       @Param("stereotype") String stereotype);
}
