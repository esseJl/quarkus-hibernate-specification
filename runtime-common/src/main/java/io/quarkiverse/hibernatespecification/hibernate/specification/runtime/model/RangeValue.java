package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model;

import java.util.Objects;

public record RangeValue(
        Object lower,
        Object upper) implements FilterValue {

    public RangeValue {
        Objects.requireNonNull(lower, "lower must not be null");
        Objects.requireNonNull(upper, "upper must not be null");
    }

    @Override
    public ValueType type() {
        return ValueType.RANGE;
    }
}
