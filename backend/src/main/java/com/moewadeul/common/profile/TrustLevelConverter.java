package com.moewadeul.common.profile;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TrustLevelConverter implements AttributeConverter<TrustLevel, Integer> {

    @Override
    public Integer convertToDatabaseColumn(TrustLevel attribute) {
        return attribute == null ? null : attribute.level();
    }

    @Override
    public TrustLevel convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : TrustLevel.of(dbData);
    }
}
