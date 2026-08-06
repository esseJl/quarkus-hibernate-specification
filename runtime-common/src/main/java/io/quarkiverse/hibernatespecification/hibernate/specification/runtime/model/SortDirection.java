package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SortDirection {
    ASC,
    DESC;

    @JsonCreator
    public static SortDirection from(String value) {
        if (value == null)
            return DESC;
        return SortDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
