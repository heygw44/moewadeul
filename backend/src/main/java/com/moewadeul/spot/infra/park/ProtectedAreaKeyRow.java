package com.moewadeul.spot.infra.park;

import com.moewadeul.spot.application.park.ProtectedAreaType;

public record ProtectedAreaKeyRow(long id, ProtectedAreaType areaType, String name) {}
