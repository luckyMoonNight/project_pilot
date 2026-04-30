package com.luckymoon.moon_readcode_server.mapper;

import com.luckymoon.moon_readcode_server.entity.QaHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QaHistoryMapper {

    int insert(QaHistory record);

    List<QaHistory> selectByProjectId(@Param("projectId") String projectId);

    /** 统计某项目在指定时间之后新增的问答数 */
    int countAfter(@Param("projectId") String projectId,
                   @Param("afterTime") String afterTime);

    int updateFeedback(@Param("id") Long id, @Param("feedback") String feedback);
}
