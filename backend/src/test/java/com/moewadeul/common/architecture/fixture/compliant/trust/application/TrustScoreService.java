package com.moewadeul.common.architecture.fixture.compliant.trust.application;

import com.moewadeul.common.architecture.fixture.compliant.member.application.MemberLookup;
import com.moewadeul.common.transaction.OutsideTransaction;

public class TrustScoreService {

    private final TrustScoreApplier applier;

    public TrustScoreService(MemberLookup lookup, TrustScoreApplier applier) {
        this.applier = applier;
    }

    public void recalculate() {
        OutsideTransaction.require("점수마다 따로 커밋해야 한다.");
        applier.apply();
    }
}
