package com.moewadeul.spot.domain;

import java.util.Optional;

public interface SpotRepository {

    Spot save(Spot spot);

    Optional<Spot> findById(Long id);

    /** 장소 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 행을 잠그려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다. */
    Optional<Spot> findByIdForUpdate(long id);

    /**
     * 장소 행을 잠그지 않고 읽고, 이 트랜잭션이 커밋할 때 낙관적 잠금 번호(version)를 하나 올린다. JPA는 읽은 때의 번호를 조건으로 걸고 번호를
     * 올리므로, 그 사이 다른 트랜잭션이 이 행을 바꿔 커밋했으면 커밋이 낙관적 잠금 충돌로 실패한다. 장소 행은 그대로 두고 딸린 행만 고치는 쓰기가 이
     * 메서드로 읽는다.
     */
    Optional<Spot> findByIdForVersionIncrement(long id);
}
