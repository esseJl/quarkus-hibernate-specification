package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.criteria.Path;

public class FilterValueConverter {

    private static final Map<Class<?>, Function<String, Object>> CONVERTERS = Map.ofEntries(
            Map.entry(String.class, s -> s),
            Map.entry(Integer.class, Integer::valueOf),
            Map.entry(int.class, Integer::valueOf),
            Map.entry(Long.class, Long::valueOf),
            Map.entry(long.class, Long::valueOf),
            Map.entry(Double.class, Double::valueOf),
            Map.entry(double.class, Double::valueOf),
            Map.entry(Float.class, Float::valueOf),
            Map.entry(float.class, Float::valueOf),
            Map.entry(Boolean.class, Boolean::valueOf),
            Map.entry(boolean.class, Boolean::valueOf),
            Map.entry(BigDecimal.class, BigDecimal::new),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(LocalDate.class, LocalDate::parse),
            Map.entry(LocalDateTime.class, LocalDateTime::parse),
            Map.entry(Instant.class, Instant::parse),
            Map.entry(OffsetDateTime.class, OffsetDateTime::parse));

    public Object convert(Object raw, Path<?> path) {
        if (raw == null) {
            return null;
        }
        Class<?> target = path.getJavaType();
        if (target.isInstance(raw)) {
            return raw;
        }

        String s = String.valueOf(raw).trim();

        Function<String, Object> converter = CONVERTERS.get(target);
        if (converter != null) {
            return converter.apply(s);
        }

        if (Enum.class.isAssignableFrom(target)) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object e = Enum.valueOf((Class<? extends Enum>) target, s);
            return e;
        }

        return raw;
    }
}
