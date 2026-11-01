package com.moewadeul.basecamp.application.membership;

public record BasecampApplyCommand(long basecampId, long memberId, String message) {}
