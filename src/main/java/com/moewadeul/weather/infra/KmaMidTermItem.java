package com.moewadeul.weather.infra;

import java.util.Map;

public record KmaMidTermItem(Map<String, String> fields) {

    public KmaMidTermItem {
        fields = Map.copyOf(fields);
    }

    public String get(String field) {
        return fields.get(field);
    }
}
