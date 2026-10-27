package com.moewadeul.spot.infra.publicspot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PublicSpotMapper {

    List<PublicSpotRow> selectBySourceAndExternalIds(
            @Param("source") String source, @Param("externalIds") Collection<String> externalIds);

    int insertSpots(
            @Param("spotType") String spotType,
            @Param("spots") List<PublicSpotInsertRow> spots,
            @Param("now") Instant now);

    int insertDetails(
            @Param("source") String source, @Param("spots") List<PublicSpotInsertRow> spots, @Param("now") Instant now);

    List<Long> lockSpots(@Param("spotIds") Collection<Long> spotIds);

    int updateSpots(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);

    int updateDetails(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);

    int restoreSpots(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    int clearSourceRemoved(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    List<Long> selectRemovalCandidateIds(
            @Param("source") String source, @Param("externalIds") Collection<String> externalIds);

    List<String> selectLiveExternalIds(@Param("source") String source);

    List<Long> lockActiveSpots(@Param("spotIds") Collection<Long> spotIds);

    List<Long> lockUnremovedDetails(@Param("spotIds") Collection<Long> spotIds);

    int hideSpots(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    int markSourceRemoved(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    int markSourceRemovedIfAbsent(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    List<PublicSpotSettingRow> selectSettings(@Param("spotIds") Collection<Long> spotIds);

    int deleteSettings(@Param("spotIds") Collection<Long> spotIds);

    int insertSettings(@Param("settings") List<PublicSpotSettingRow> settings);

    List<PublicSpotLocationRow> selectLocationsBySource(@Param("source") String source);
}
