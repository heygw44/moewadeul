package com.moewadeul.common.patch;

public record PatchField<T>(boolean present, T value) {

    private static final PatchField<?> ABSENT = new PatchField<>(false, null);

    public PatchField {
        if (!present && value != null) {
            throw new IllegalArgumentException("요청에 없는 필드는 값을 가질 수 없습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchField<T> absent() {
        return (PatchField<T>) ABSENT;
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }
}
