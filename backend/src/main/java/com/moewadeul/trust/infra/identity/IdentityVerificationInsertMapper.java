package com.moewadeul.trust.infra.identity;

import com.moewadeul.trust.domain.identity.IdentityVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdentityVerificationInsertMapper {

    void insertIgnoringDuplicate(@Param("verification") IdentityVerification verification);

    boolean existsByMemberIdCommitted(@Param("memberId") long memberId);
}
