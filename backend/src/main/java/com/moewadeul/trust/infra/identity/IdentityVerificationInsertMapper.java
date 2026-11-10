package com.moewadeul.trust.infra.identity;

import com.moewadeul.trust.domain.identity.IdentityVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdentityVerificationInsertMapper {

    Integer lockCiBucket(@Param("bucket") int bucket);

    void insert(@Param("verification") IdentityVerification verification);
}
