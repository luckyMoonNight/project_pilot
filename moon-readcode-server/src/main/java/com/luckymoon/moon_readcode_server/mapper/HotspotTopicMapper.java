package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.HotspotTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HotspotTopicMapper {

    int insert(HotspotTopic topic);

    int updateById(HotspotTopic topic);

    HotspotTopic selectById(@Param("id") Long id);

    List<HotspotTopic> selectByProjectId(@Param("projectId") String projectId);

    List<HotspotTopic> selectByProjectIdAndStatus(@Param("projectId") String projectId,
                                                   @Param("status") String status);

    int deleteByProjectId(@Param("projectId") String projectId);
}
