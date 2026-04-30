package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.CodeMethod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeMethodMapper {

    int insert(CodeMethod codeMethod);

    int updateById(CodeMethod codeMethod);

    int deleteByClassId(@Param("classId") Long classId);

    int deleteByProjectId(@Param("projectId") String projectId);

    CodeMethod selectById(@Param("id") Long id);

    CodeMethod selectBySignature(@Param("projectId") String projectId,
                                 @Param("signature") String signature);

    List<CodeMethod> selectByClassId(@Param("classId") Long classId);

    List<CodeMethod> selectByProjectId(@Param("projectId") String projectId);
}
