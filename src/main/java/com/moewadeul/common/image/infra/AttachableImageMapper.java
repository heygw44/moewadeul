package com.moewadeul.common.image.infra;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

// 모듈의 MyBatis 매퍼가 이 인터페이스를 상속한다. MyBatis는 상속한 메서드의 문장도 모듈 매퍼의 XML에서 찾는다.
public interface AttachableImageMapper {

    Long lockMember(@Param("memberId") long memberId);

    List<Instant> selectUnattachedCreatedAtsAfter(
            @Param("memberId") long memberId, @Param("createdAfter") Instant createdAfter, @Param("limit") int limit);

    List<UnattachedImageRow> selectUnattachedCreatedBefore(
            @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    int deleteIfUnattached(@Param("imageId") long imageId);
}
