package com.moewadeul.member.application.password;

public record PasswordChangeCommand(
        long memberId, String currentPassword, String newPassword, String ip, String currentSessionId) {

    @Override
    public String toString() {
        return "PasswordChangeCommand[memberId=" + memberId + "]";
    }
}
