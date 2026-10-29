package com.moewadeul.common.profile;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AgeGroupConverter implements AttributeConverter<AgeGroup, Integer> {

    @Override
    public Integer convertToDatabaseColumn(AgeGroup attribute) {
        return attribute == null ? null : attribute.decade();
    }

    @Override
    public AgeGroup convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : AgeGroup.ofDecade(dbData);
    }
}
