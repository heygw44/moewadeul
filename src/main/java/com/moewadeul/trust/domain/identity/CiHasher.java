package com.moewadeul.trust.domain.identity;

public interface CiHasher {

    CiHash hash(String ci);
}
