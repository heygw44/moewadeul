package com.moewadeul.common.image;

// 받침에 따라 조사가 달라서(사진을/이미지를) 조사를 붙인 꼴을 함께 둔다.
public record ImageNoun(String word, String object, String topic, String subject) {

    public static final ImageNoun PHOTO = new ImageNoun("사진", "사진을", "사진은", "사진이");

    public static final ImageNoun IMAGE = new ImageNoun("이미지", "이미지를", "이미지는", "이미지가");
}
