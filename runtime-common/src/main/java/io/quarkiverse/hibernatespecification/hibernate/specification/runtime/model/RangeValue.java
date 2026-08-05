package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model;

public record RangeValue(Object lower, Object upper) implements FilterValue {

    public RangeValue {
        if (lower == null && upper == null) {
            throw new IllegalArgumentException("At least one of lower/upper must be non-null");
        }
    }

    @Override
    public ValueType type() {
        return ValueType.RANGE;
    }
}
