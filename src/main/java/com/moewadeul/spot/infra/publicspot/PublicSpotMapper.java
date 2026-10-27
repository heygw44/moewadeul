package com.moewadeul.spot.infra.publicspot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 공공데이터 동기화는 원천 API 한 페이지(수십~수백 건)를 한 번에 적재한다. JPA로 저장하면 행마다 INSERT나 UPDATE가 따로 나가고,
// 기존 행을 엔티티로 읽어 영속성 컨텍스트에 올리는 비용도 든다. 그래서 이 매퍼는 기존 행 조회, 새 행 추가, 바뀐 행 갱신을
// 테이블마다 SQL 한 문장으로 처리한다. 이 매퍼의 메서드는 호출하는 쪽(PublicSpotSyncService)이 연 트랜잭션 안에서 실행해야 하고,
// 목록 인자가 비어 있으면 호출하지 않는다.
@Mapper
public interface PublicSpotMapper {

    /** 호출하면 source 출처에서 externalIds에 해당하는 장소를 spot과 public_spot_detail을 묶어 읽는다. */
    List<PublicSpotRow> selectBySourceAndExternalIds(
            @Param("source") String source, @Param("externalIds") Collection<String> externalIds);

    /** 호출하면 spot에 ACTIVE 상태로 새 행을 추가하고, DB가 만든 ID를 각 행의 spotId에 채운다. */
    int insertSpots(
            @Param("spotType") String spotType,
            @Param("spots") List<PublicSpotInsertRow> spots,
            @Param("now") Instant now);

    /** 호출하면 insertSpots로 ID를 받은 행마다 public_spot_detail을 추가한다. */
    int insertDetails(
            @Param("source") String source, @Param("spots") List<PublicSpotInsertRow> spots, @Param("now") Instant now);

    /**
     * 호출하면 spotIds의 spot 행을 상태와 상관없이 ID 순서로 잠그며 읽고, 잠근 행의 ID를 돌려준다. 같은 장소의 public_spot_detail 행보다 먼저
     * 잠가야 한다.
     */
    List<Long> lockSpots(@Param("spotIds") Collection<Long> spotIds);

    /**
     * 호출하면 spot의 이름, 주소, 좌표, 기상청 격자를 새 값으로 바꾼다. 상태, 공원 경고, 생성 시각은 건드리지 않는다. 그래서 관리자가 숨긴 장소는
     * 동기화 뒤에도 숨겨진 채로 남는다.
     */
    int updateSpots(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);

    /**
     * 호출하면 public_spot_detail의 분류, 시설, 전화번호, 홈페이지, 운영 상태, 휴장 기간을 새 값으로 바꾸고 synced_at을 now로 기록한다.
     * 원천 삭제 표시(source_removed_at)는 건드리지 않는다.
     */
    int updateDetails(@Param("spots") List<PublicSpotUpdateRow> spots, @Param("now") Instant now);

    /**
     * 호출하면 원천 삭제로 숨겨진 장소(source_removed_at이 있는 행) 중 spotIds에 해당하고 지금 HIDDEN인 장소를 ACTIVE로 되돌리고 updated_at을
     * now로 기록한다. 이미 ACTIVE인 장소는 건드리지 않는다. 호출하는 쪽은 {@link #clearSourceRemoved}보다 먼저 호출해야 한다. 그 뒤에는 삭제 표시가
     * 없어서 어느 행을 되돌릴지 이 문장이 알 수 없다.
     */
    int restoreSpots(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    /** 호출하면 spotIds 중 source_removed_at이 있는 행의 표시를 지우고 updated_at을 now로 기록한다. */
    int clearSourceRemoved(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    /**
     * 호출하면 source 출처에서 externalIds에 해당하는 장소 중 지금 ACTIVE이고 원천 삭제 표시가 없는 장소의 spot ID를 잠그지 않고 읽는다. 없는
     * 외부 ID는 결과에 나오지 않는다.
     */
    List<Long> selectRemovalCandidateIds(
            @Param("source") String source, @Param("externalIds") Collection<String> externalIds);

    /**
     * 호출하면 source 출처에서 지금 ACTIVE이고 원천 삭제 표시가 없는 장소의 외부 ID를 모두 잠그지 않고 읽는다.
     */
    List<String> selectLiveExternalIds(@Param("source") String source);

    /**
     * 호출하면 spotIds의 spot 행을 ID 순서로 잠그며 읽고, 그중 지금 ACTIVE인 장소의 ID를 돌려준다. 호출하는 쪽이 연 트랜잭션이 끝날 때까지 다른
     * 트랜잭션은 이 행들을 바꾸지 못한다. 같은 장소의 public_spot_detail 행보다 먼저 잠가야 한다.
     */
    List<Long> lockActiveSpots(@Param("spotIds") Collection<Long> spotIds);

    /**
     * 호출하면 spotIds의 public_spot_detail 행을 spot ID 순서로 잠그며 읽고, 그중 원천 삭제 표시가 없는 장소의 ID를 돌려준다. 같은 장소의 spot
     * 행을 {@link #lockActiveSpots}로 먼저 잠근 뒤에 부른다.
     */
    List<Long> lockUnremovedDetails(@Param("spotIds") Collection<Long> spotIds);

    /** 호출하면 spotIds 중 지금 ACTIVE인 장소를 HIDDEN으로 바꾸고 updated_at을 now로 기록한 뒤, 바뀐 행 수를 돌려준다. */
    int hideSpots(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    /** 호출하면 spotIds의 public_spot_detail에 원천 삭제로 숨긴 시각을 now로 기록하고 updated_at도 now로 바꾼다. */
    int markSourceRemoved(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    /**
     * 호출하면 spotIds 중 원천 삭제 표시(source_removed_at)가 없는 행에만 표시를 now로 기록하고 updated_at도 now로 바꾼다. 이미 표시가 있는 행은
     * 건드리지 않는다. 관리자가 복구한 장소를 동기화가 다시 숨기지 않도록 표시를 남길 때 쓴다.
     */
    int markSourceRemovedIfAbsent(@Param("spotIds") Collection<Long> spotIds, @Param("now") Instant now);

    /** 호출하면 spotIds 장소가 가진 입지를 읽는다. 입지가 없는 장소는 결과에 나오지 않는다. */
    List<PublicSpotSettingRow> selectSettings(@Param("spotIds") Collection<Long> spotIds);

    /** 호출하면 spotIds 장소의 입지 행을 모두 지운다. */
    int deleteSettings(@Param("spotIds") Collection<Long> spotIds);

    /** 호출하면 settings의 입지 행을 한 문장으로 추가한다. */
    int insertSettings(@Param("settings") List<PublicSpotSettingRow> settings);

    /** 호출하면 source 출처의 모든 장소(상태와 원천 삭제 표시와 상관없이)의 외부 ID, 이름, 좌표를 읽는다. */
    List<PublicSpotLocationRow> selectLocationsBySource(@Param("source") String source);
}
