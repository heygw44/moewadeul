package com.moewadeul.spot.application.moderation;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

record SpotAuditDetail(String spotType, @JsonUnwrapped Object transition) {}
