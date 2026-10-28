package com.moewadeul.common.security;

import java.io.Serial;
import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

// Spring Session JDBC가 이 객체를 JDK 직렬화로 저장한다. serialVersionUID를 올리거나 클래스 이름·패키지를 바꾸면
// 저장된 세션을 역직렬화하지 못하므로, 바꿀 때는 저장된 세션을 비우는 마이그레이션을 함께 넣는다.
// getName()은 Spring Session이 principal 이름 열을 채우고 findByPrincipalName으로 세션을 찾는 값이다.
public record LoginMember(long memberId, String role, boolean emailVerified)
        implements AuthenticatedPrincipal, Serializable {

    public static final String AUTHORITY_EMAIL_VERIFIED = "EMAIL_VERIFIED";

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
