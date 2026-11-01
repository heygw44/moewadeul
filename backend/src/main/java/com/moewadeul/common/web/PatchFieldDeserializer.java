package com.moewadeul.common.web;

import com.moewadeul.common.patch.PatchField;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.util.AccessPattern;

// Jackson은 값이 있으면 deserialize, null이면 getNullValue, 레코드 생성자 인자가 JSON에 없으면 getAbsentValue를 부른다.
final class PatchFieldDeserializer extends ValueDeserializer<PatchField<?>> {

    private static final PatchField<?> EXPLICIT_NULL = PatchField.of(null);

    private final ValueDeserializer<Object> contentDeserializer;

    public PatchFieldDeserializer() {
        this(null);
    }

    private PatchFieldDeserializer(ValueDeserializer<Object> contentDeserializer) {
        this.contentDeserializer = contentDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType patchFieldType = ctxt.getContextualType();
        if (patchFieldType == null && property != null) {
            patchFieldType = property.getType();
        }
        if (patchFieldType == null) {
            return this;
        }
        JavaType contentType = patchFieldType.containedTypeOrUnknown(0);
        return new PatchFieldDeserializer(ctxt.findContextualValueDeserializer(contentType, property));
    }

    @Override
    public PatchField<?> deserialize(JsonParser p, DeserializationContext ctxt) {
        if (contentDeserializer == null) {
            throw new IllegalStateException("PatchField의 안쪽 타입을 알 수 없어 값을 읽을 수 없습니다.");
        }
        return PatchField.of(contentDeserializer.deserialize(p, ctxt));
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return EXPLICIT_NULL;
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return AccessPattern.CONSTANT;
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return PatchField.absent();
    }

    @Override
    public Class<?> handledType() {
        return PatchField.class;
    }
}
