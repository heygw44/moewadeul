package com.moewadeul.common.image;

public enum ImageMessages {
    PHOTO,
    IMAGE;

    public String unsupportedType() {
        return switch (this) {
            case PHOTO -> "허용하지 않는 사진 형식이에요.";
            case IMAGE -> "허용하지 않는 이미지 형식이에요.";
        };
    }

    public String sizeOutOfRange() {
        return switch (this) {
            case PHOTO -> "사진 크기는 1바이트 이상 5MB 이하여야 해요.";
            case IMAGE -> "이미지 크기는 1바이트 이상 5MB 이하여야 해요.";
        };
    }

    public String tooMany(int maxPerParent) {
        return switch (this) {
            case PHOTO -> "사진은 " + maxPerParent + "장까지 붙일 수 있어요.";
            case IMAGE -> "이미지는 " + maxPerParent + "장까지 붙일 수 있어요.";
        };
    }

    public String duplicated() {
        return switch (this) {
            case PHOTO -> "같은 사진을 두 번 붙일 수 없어요.";
            case IMAGE -> "같은 이미지를 두 번 붙일 수 없어요.";
        };
    }

    public String unusable() {
        return switch (this) {
            case PHOTO -> "붙일 수 없는 사진이 있어요.";
            case IMAGE -> "붙일 수 없는 이미지가 있어요.";
        };
    }

    String missingValue() {
        return switch (this) {
            case PHOTO -> "사진을 만드는 데 필요한 값이 비었습니다.";
            case IMAGE -> "이미지를 만드는 데 필요한 값이 비었습니다.";
        };
    }

    String disallowedType() {
        return switch (this) {
            case PHOTO -> "허용하지 않는 사진 형식입니다.";
            case IMAGE -> "허용하지 않는 이미지 형식입니다.";
        };
    }

    String disallowedSize(long maxSizeBytes) {
        return switch (this) {
            case PHOTO -> "사진 크기는 1바이트 이상 " + maxSizeBytes + "바이트 이하여야 합니다.";
            case IMAGE -> "이미지 크기는 1바이트 이상 " + maxSizeBytes + "바이트 이하여야 합니다.";
        };
    }

    String orderOutOfRange(int maxPerParent) {
        return switch (this) {
            case PHOTO -> "사진 순서는 0 이상 " + maxPerParent + " 미만이어야 합니다.";
            case IMAGE -> "이미지 순서는 0 이상 " + maxPerParent + " 미만이어야 합니다.";
        };
    }
}
