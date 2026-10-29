package com.moewadeul.member.application;

public final class MemberGuards {

    private MemberGuards() {}

    public static void requireRequestIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) {
            throw new IllegalArgumentException("요청 IP가 비어 있습니다.");
        }
    }

    public static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("회원이 없습니다. memberId=" + memberId);
    }
}
