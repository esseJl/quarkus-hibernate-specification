package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FilterValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.MultiValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.RangeValue;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.SingleValue;

@ApplicationScoped
public class ValueConverter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final ZoneId zoneId;

    public ValueConverter(
            @ConfigProperty(name = "quarkus.hibernate-specification.timezone", defaultValue = "UTC") String zone) {
        this.zoneId = ZoneId.of(zone);
    }

    public Object convertSingle(FilterValue filterValue, Class<?> targetType) {
        if (filterValue.type() != FilterValue.ValueType.SINGLE) {
            throw new IllegalArgumentException("Expected SINGLE value, got " + filterValue.type());
        }
        return convertValue(((SingleValue) filterValue).value(), targetType);
    }

    public Collection<?> convertMulti(FilterValue filterValue, Class<?> targetType) {
        if (filterValue.type() != FilterValue.ValueType.MULTI) {
            throw new IllegalArgumentException("Expected MULTI value, got " + filterValue.type());
        }
        return ((MultiValue) filterValue).values().stream()
                .map(v -> convertValue(v, targetType))
                .collect(Collectors.toList());
    }

    public RangeValue requireRange(FilterValue filterValue) {
        if (filterValue.type() != FilterValue.ValueType.RANGE) {
            throw new IllegalArgumentException("Expected RANGE value, got " + filterValue.type());
        }
        return (RangeValue) filterValue;
    }

    public Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        try {
            if (value instanceof String strValue) {
                Object temporal = convertTemporal(strValue, targetType);
                if (temporal != null) {
                    return temporal;
                }
            }

            if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
                return convertNumber(value, targetType);
            }

            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value.toString());
            }

            if (Enum.class.isAssignableFrom(targetType)) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                return Enum.valueOf(enumType, value.toString().trim().toUpperCase(Locale.ROOT));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Cannot convert value '" + value + "' to " + targetType.getSimpleName()
                            + ". Error: " + ex.getMessage(),
                    ex);
        }

        if (!(value instanceof String)) {
            return convertValue(value.toString(), targetType);
        }

        throw new IllegalArgumentException(
                "Unsupported target type: " + targetType.getName()
                        + " or conversion failed for value: " + value);
    }

    private Object convertTemporal(String strValue, Class<?> targetType) {
        if (targetType == Instant.class) {
            return Instant.parse(normalizeToInstantCompatible(strValue));
        }
        if (targetType == LocalDateTime.class) {
            if (strValue.contains("T") || strValue.endsWith("Z")) {
                return LocalDateTime.ofInstant(
                        Instant.parse(normalizeToInstantCompatible(strValue)), zoneId);
            }
            return LocalDateTime.parse(strValue, DATE_TIME_FORMATTER);
        }
        if (targetType == LocalDate.class) {
            if (strValue.contains("T") || strValue.endsWith("Z") || strValue.contains("+")) {
                return LocalDate.ofInstant(
                        Instant.parse(normalizeToInstantCompatible(strValue)), zoneId);
            }
            if (strValue.contains("/")) {
                return LocalDate.parse(strValue, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            }
            return LocalDate.parse(strValue);
        }
        if (targetType == LocalTime.class) {
            if (strValue.contains("T")) {
                return LocalTime.ofInstant(
                        Instant.parse(normalizeToInstantCompatible(strValue)), zoneId);
            }
            if (strValue.length() == 5) {
                return LocalTime.parse(strValue, DateTimeFormatter.ofPattern("HH:mm"));
            }
            return LocalTime.parse(strValue);
        }
        if (targetType == OffsetDateTime.class) {
            return OffsetDateTime.parse(strValue);
        }
        if (targetType == ZonedDateTime.class) {
            return ZonedDateTime.parse(strValue);
        }
        return null;
    }

    private Object convertNumber(Object value, Class<?> targetType) {
        String s = value.toString();
        if (targetType == Long.class || targetType == long.class)
            return Long.valueOf(s);
        if (targetType == Integer.class || targetType == int.class)
            return Integer.valueOf(s);
        if (targetType == Short.class || targetType == short.class)
            return Short.valueOf(s);
        if (targetType == Byte.class || targetType == byte.class)
            return Byte.valueOf(s);
        if (targetType == Double.class || targetType == double.class)
            return Double.valueOf(s);
        if (targetType == Float.class || targetType == float.class)
            return Float.valueOf(s);
        if (targetType == BigDecimal.class)
            return new BigDecimal(s);
        if (targetType == BigInteger.class)
            return new BigInteger(s);
        return new BigDecimal(s);
    }

    private String normalizeToInstantCompatible(String str) {
        if (str == null || str.isBlank())
            return str;
        str = str.trim();
        if (str.length() == 10 && (str.contains("-") || str.contains("/"))) {
            return str.replace('/', '-') + "T00:00:00Z";
        }
        if (str.endsWith("Z") || str.contains("+") || str.matches(".*[+-]\\d{2}:\\d{2}$")) {
            return str;
        }
        if (str.contains("T")) {
            int dotIndex = str.indexOf('.');
            if (dotIndex > 0) {
                str = str.substring(0, Math.min(str.length(), dotIndex + 4));
            }
            return str + "Z";
        }
        return str;
    }
}
