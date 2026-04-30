package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.CodeSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeSummaryMapper {

    int insert(CodeSummary summary);

    int updateById(CodeSummary summary);

    int deleteByProjectId(@Param("projectId") String projectId);

    int deleteByTarget(@Param("projectId") String projectId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId);

    CodeSummary selectByTarget(@Param("projectId") String projectId,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId);

    CodeSummary selectByVectorId(@Param("vectorId") String vectorId);

    List<CodeSummary> selectByProjectAndType(@Param("projectId") String projectId,
                                             @Param("targetType") String targetType);

    List<CodeSummary> selectByProjectId(@Param("projectId") String projectId);
}
