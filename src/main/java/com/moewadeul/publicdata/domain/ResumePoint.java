package com.moewadeul.publicdata.domain;

// skipNext가 참이면 cursor 다음 단위가 이어받은 직후 또 실패한 것이므로, 그 단위를 건너뛰고 이어간다.
public record ResumePoint(String cursor, boolean skipNext) {}
