package com.moewadeul.trust.application.identity;

import com.moewadeul.trust.domain.identity.CiHash;

public interface CiHasher {

    CiHash hash(String ci);
}
