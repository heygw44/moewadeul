package com.moewadeul.member.infra;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Nickname;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberJpaRepository extends JpaRepository<Member, Long>, MemberRepository {

    @Override
    Member save(Member member);

    @Override
    Optional<Member> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") long id);

    @Override
    @Query("SELECT COUNT(m) > 0 FROM Member m WHERE m.nickname = :#{#nickname.value()}")
    boolean existsByNickname(@Param("nickname") Nickname nickname);

    @Override
    @Query("SELECT COUNT(m) > 0 FROM Member m WHERE m.nickname = :#{#nickname.value()} AND m.id <> :id")
    boolean existsByNicknameAndIdNot(@Param("nickname") Nickname nickname, @Param("id") Long id);
}
