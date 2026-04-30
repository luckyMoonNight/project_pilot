package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.CodeRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeRelationMapper {

    int insert(CodeRelation relation);

    int batchInsert(@Param("list") List<CodeRelation> relations);

    int deleteByFromId(@Param("fromId") Long fromId,
                       @Param("fromType") String fromType);

    int deleteByProjectId(@Param("projectId") String projectId);

    /** 查询某个方法/类发出的关系（出边） */
    List<CodeRelation> selectByFrom(@Param("projectId") String projectId,
                                    @Param("fromId") Long fromId,
                                    @Param("fromType") String fromType);

    /** 查询指向某个目标 ref 的关系（入边） */
    List<CodeRelation> selectByToRef(@Param("projectId") String projectId,
                                     @Param("toRef") String toRef);

    List<CodeRelation> selectByProjectId(@Param("projectId") String projectId);
}
