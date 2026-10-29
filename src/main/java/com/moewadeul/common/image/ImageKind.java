package com.moewadeul.common.image;

public record ImageKind(String name, ImageNoun noun, int maxPerParent) {

    // 정책(MM-05·CM-06·RI-01)이 상한을 대상 하나에 붙이는 장수의 3배로 정한다.
    public int maxUnattachedPerMember() {
        return maxPerParent * 3;
    }
}
