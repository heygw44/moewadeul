package com.moewadeul.common.image.domain;

public enum ImageMessages {
    PHOTO("사진", "이", "은", "을"),
    IMAGE("이미지", "가", "는", "를");

    private static final long MEGABYTE = 1024L * 1024;

    private final String noun;
    private final String subjectParticle;
    private final String topicParticle;
    private final String objectParticle;

    ImageMessages(String noun, String subjectParticle, String topicParticle, String objectParticle) {
        this.noun = noun;
        this.subjectParticle = subjectParticle;
        this.topicParticle = topicParticle;
        this.objectParticle = objectParticle;
    }

    public String unsupportedType() {
        return "허용하지 않는 " + noun + " 형식이에요.";
    }

    public String sizeOutOfRange(long maxSizeBytes) {
        return noun + " 크기는 1바이트 이상 " + sizeText(maxSizeBytes) + " 이하여야 해요.";
    }

    public String tooMany(int maxPerParent) {
        return noun + topicParticle + " " + maxPerParent + "장까지 붙일 수 있어요.";
    }

    public String duplicated() {
        return "같은 " + noun + objectParticle + " 두 번 붙일 수 없어요.";
    }

    public String unusable() {
        return "붙일 수 없는 " + noun + subjectParticle + " 있어요.";
    }

    String missingValue() {
        return noun + objectParticle + " 만드는 데 필요한 값이 비었습니다.";
    }

    String disallowedType() {
        return "허용하지 않는 " + noun + " 형식입니다.";
    }

    String disallowedSize(long maxSizeBytes) {
        return noun + " 크기는 1바이트 이상 " + maxSizeBytes + "바이트 이하여야 합니다.";
    }

    String orderOutOfRange(int maxPerParent) {
        return noun + " 순서는 0 이상 " + maxPerParent + " 미만이어야 합니다.";
    }

    private static String sizeText(long bytes) {
        return bytes % MEGABYTE == 0 ? bytes / MEGABYTE + "MB" : bytes + "바이트";
    }
}
