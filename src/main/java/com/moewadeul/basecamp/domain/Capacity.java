package com.moewadeul.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Capacity {

    public static final int MIN = 2;
    public static final int MAX = 6;

    @Column(name = "capacity")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private short value;

    private Capacity(int value) {
        this.value = (short) value;
    }

    public static Capacity of(int value) {
        if (value < MIN || value > MAX) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID);
        }
        return new Capacity(value);
    }

    public boolean isLessThan(Capacity other) {
        return value < other.value;
    }

    public boolean isFilledBy(int headcount) {
        return headcount >= value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Capacity capacity && value == capacity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
