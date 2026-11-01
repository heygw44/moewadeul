package com.moewadeul.common.moderation;

import java.time.Instant;

public interface Moderatable {

    ModerationStatus getStatus();

    void hide(Instant now);

    void restore(Instant now);
}
