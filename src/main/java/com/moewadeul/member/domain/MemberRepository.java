package com.moewadeul.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByIdForUpdate(long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(Nickname nickname);

    boolean existsByNicknameAndIdNot(Nickname nickname, Long id);

    void flush();
}
