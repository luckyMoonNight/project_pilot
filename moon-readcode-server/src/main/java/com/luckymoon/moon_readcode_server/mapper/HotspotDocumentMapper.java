package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.HotspotDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HotspotDocumentMapper {

    int insert(HotspotDocument document);

    int updateById(HotspotDocument document);

    HotspotDocument selectById(@Param("id") Long id);

    HotspotDocument selectByTopicId(@Param("topicId") Long topicId);

    List<HotspotDocument> selectByProjectId(@Param("projectId") String projectId);
}
