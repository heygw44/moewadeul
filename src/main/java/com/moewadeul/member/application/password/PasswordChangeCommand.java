package com.moewadeul.member.application.password;

/**
 * 로그인한 회원의 비밀번호 변경 요청이다.
 *
 * @param ip 현재 비밀번호를 틀렸을 때 로그인 실패 기록에 남길 요청 IP
 * @param currentSessionId 변경 뒤에도 지우지 않고 남길 요청 세션의 ID
 */
public record PasswordChangeCommand(
        long memberId, String currentPassword, String newPassword, String ip, String currentSessionId) {

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 비밀번호와 세션 ID가 로그에 새지 않도록 재정의했다.
    @Override
    public String toString() {
        return "PasswordChangeCommand[memberId=" + memberId + "]";
    }
}
