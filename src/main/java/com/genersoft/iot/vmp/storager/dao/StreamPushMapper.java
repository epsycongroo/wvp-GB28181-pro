package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.gb28181.bean.GbStream;
import com.genersoft.iot.vmp.media.zlm.dto.StreamPushItem;
import org.apache.ibatis.annotations.*;
// import org.omg.PortableInterceptor.INACTIVE;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface StreamPushMapper {

    @Insert("INSERT INTO stream_push (app, stream, totalReaderCount, originType, originTypeStr, " +
            "pushTime, aliveSecond, mediaServerId, serverId, updateTime, createTime) VALUES" +
            "('${app}', '${stream}', '${totalReaderCount}', '${originType}', '${originTypeStr}', " +
            "'${pushTime}', '${aliveSecond}', '${mediaServerId}' , '${serverId}' , '${updateTime}' , '${createTime}' )")
    int add(StreamPushItem streamPushItem);


    @Update(value = {" <script>" +
            "UPDATE stream_push " +
            "SET updateTime='${updateTime}'" +
            "<if test=\"mediaServerId != null\">, mediaServerId='${mediaServerId}'</if>" +
            "<if test=\"totalReaderCount != null\">, totalReaderCount='${totalReaderCount}'</if>" +
            "<if test=\"originType != null\">, originType=${originType}</if>" +
            "<if test=\"originTypeStr != null\">, originTypeStr='${originTypeStr}'</if>" +
            "<if test=\"pushTime != null\">, pushTime='${pushTime}'</if>" +
            "<if test=\"aliveSecond != null\">, aliveSecond='${aliveSecond}'</if>" +
            "WHERE app=#{app} AND stream=#{stream}"+
            " </script>"})
    int update(StreamPushItem streamPushItem);

    @Delete("DELETE FROM stream_push WHERE app=#{app} AND stream=#{stream}")
    int del(String app, String stream);

    @Delete("<script> "+
            "DELETE sp FROM stream_push sp left join gb_stream gs on sp.app = gs.app AND sp.stream = gs.stream where " +
            "<foreach collection='streamPushItems' item='item' separator='or'>" +
            "(sp.app=#{item.app} and sp.stream=#{item.stream} and gs.gbId is null) " +
            "</foreach>" +
            "</script>")
    int delAllWithoutGBId(List<StreamPushItem> streamPushItems);

    @Delete("<script> "+
            "DELETE FROM stream_push where " +
            "<foreach collection='streamPushItems' item='item' separator='or'>" +
            "(app=#{item.app} and stream=#{item.stream}) " +
            "</foreach>" +
            "</script>")
    int delAll(List<StreamPushItem> streamPushItems);

    @Delete("<script> "+
            "DELETE FROM stream_push where " +
            "<foreach collection='gbStreams' item='item' separator='or'>" +
            "(app=#{item.app} and stream=#{item.stream}) " +
            "</foreach>" +
            "</script>")
    int delAllForGbStream(List<GbStream> gbStreams);


    @Select(value = {" <script>" +
            "SELECT " +
            "st.*, " +
            "gs.gbId, gs.name, gs.longitude, gs.latitude, gs.gbStreamId " +
            "from " +
            "stream_push st " +
            "LEFT JOIN gb_stream gs " +
            "on st.app = gs.app AND st.stream = gs.stream " +
            "WHERE " +
            "1=1 " +
            " <if test='query != null'> AND (st.app LIKE '%${query}%' OR st.stream LIKE '%${query}%' OR gs.gbId LIKE '%${query}%' OR gs.name LIKE '%${query}%')</if> " +
            " <if test='pushing == true' > AND (gs.gbId is null OR st.status=1)</if>" +
            " <if test='pushing == false' > AND st.status=0</if>" +
            " <if test='mediaServerId != null' > AND st.mediaServerId=#{mediaServerId} </if>" +
            "order by st.createTime desc" +
            " </script>"})
    List<StreamPushItem> selectAllForList(String query, Boolean pushing, String mediaServerId);

    @Select("SELECT st.*, gs.gbId, gs.name, gs.longitude, gs.latitude FROM stream_push st LEFT JOIN gb_stream gs on st.app = gs.app AND st.stream = gs.stream order by st.createTime desc")
    List<StreamPushItem> selectAll();

    @Select("SELECT st.*, gs.gbId, gs.name, gs.longitude, gs.latitude FROM stream_push st LEFT JOIN gb_stream gs on st.app = gs.app AND st.stream = gs.stream WHERE st.app=#{app} AND st.stream=#{stream}")
    StreamPushItem selectOne(String app, String stream);

    @Insert("<script>"  +
            "Insert IGNORE INTO stream_push (app, stream, totalReaderCount, originType, originTypeStr, " +
            "createTime, aliveSecond, mediaServerId) " +
            "VALUES <foreach collection='streamPushItems' item='item' index='index' separator=','>" +
            "( '${item.app}', '${item.stream}', '${item.totalReaderCount}', #{item.originType}, " +
            "'${item.originTypeStr}',#{item.createTime}, #{item.aliveSecond}, '${item.mediaServerId}' )" +
            " </foreach>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int addAll(List<StreamPushItem> streamPushItems);

    @Delete("DELETE FROM stream_push")
    void clear();

    @Delete("DELETE sp FROM stream_push sp left join gb_stream gs on gs.app = sp.app and gs.stream= sp.stream WHERE sp.mediaServerId=#{mediaServerId} and gs.gbId is null ")
    void deleteWithoutGBId(String mediaServerId);

    @Select("SELECT * FROM stream_push WHERE mediaServerId=#{mediaServerId}")
    List<StreamPushItem> selectAllByMediaServerId(String mediaServerId);

    @Select("SELECT sp.* FROM stream_push sp left join gb_stream gs on gs.app = sp.app and gs.stream= sp.stream WHERE sp.mediaServerId=#{mediaServerId} and gs.gbId is null")
    List<StreamPushItem> selectAllByMediaServerIdWithOutGbID(String mediaServerId);

    @Update("UPDATE stream_push " +
            "SET status=${status} " +
            "WHERE app=#{app} AND stream=#{stream}")
    int updateStatus(String app, String stream, boolean status);

    @Update("UPDATE stream_push " +
            "SET status=#{status} " +
            "WHERE mediaServerId=#{mediaServerId}")
    void updateStatusByMediaServerId(String mediaServerId, boolean status);
}
