package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DisableSpecificationQuery;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DtoMapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.Mapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FieldMeta;

@ApplicationScoped
public class FieldMetaRegistry {

    private final Map<Class<?>, Map<String, FieldMeta>> cache = new ConcurrentHashMap<>();

    public Class<?> resolveEntityClass(Class<?> dtoOrEntity) {
        if (dtoOrEntity == null) {
            return null;
        }
        DtoMapper ann = dtoOrEntity.getAnnotation(DtoMapper.class);
        return ann != null ? ann.value() : dtoOrEntity;
    }

    public Map<String, FieldMeta> getFieldMeta(Class<?> dtoOrEntity) {
        if (dtoOrEntity == null) {
            return Map.of();
        }
        Class<?> entityClass = resolveEntityClass(dtoOrEntity);
        return cache.computeIfAbsent(dtoOrEntity, cls -> {
            Map<String, FieldMeta> map = new LinkedHashMap<>();
            // Set خالی فقط برای شروعِ مسیر ریشه؛ در هر شاخه یک کپیِ مستقل از آن ساخته می‌شود
            // تا تشخیصِ چرخه (cycle) صرفاً محدود به همان مسیر/شاخه بماند، نه کل درخت.
            collectFields(cls, "", map, entityClass, Set.of());
            return Collections.unmodifiableMap(map);
        });
    }

    public List<FieldMeta> getProjectionFieldMetas(Class<?> dtoOrEntity) {
        if (dtoOrEntity == null) {
            return List.of();
        }
        Map<String, FieldMeta> meta = getFieldMeta(dtoOrEntity);
        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        List<FieldMeta> result = new ArrayList<>();
        for (FieldMeta m : meta.values()) {
            if (m.isAllowed() && seenPaths.add(m.entityPath())) {
                result.add(m);
            }
        }
        return List.copyOf(result);
    }

    public FieldMeta requireAllowed(String clientField, Class<?> dtoOrEntity, String usage) {
        if (clientField == null || clientField.isBlank()) {
            throw new IllegalArgumentException(usage + " field must not be blank");
        }
        if (dtoOrEntity == null) {
            return new FieldMeta(clientField, clientField, false, null);
        }

        Map<String, FieldMeta> allowed = getFieldMeta(dtoOrEntity);
        FieldMeta meta = allowed.get(clientField);
        if (meta == null) {
            Class<?> entityClass = resolveEntityClass(dtoOrEntity);
            throw new IllegalArgumentException(
                    "Field '" + clientField + "' is not part of the declared projection"
                            + (entityClass != null ? " for " + entityClass.getSimpleName() : "")
                            + " and cannot be used for " + usage);
        }
        if (meta.disabled()) {
            String reason = meta.disableReason() != null && !meta.disableReason().isBlank()
                    ? ": " + meta.disableReason()
                    : "";
            throw new IllegalArgumentException(
                    "Field '" + clientField + "' is marked with @DisableSpecificationQuery"
                            + reason + " and cannot be used for " + usage);
        }
        return meta;
    }

    /**
     * FIX (باگ حیاتی): پارامتر {@code visited} پیش از این به‌صورت یک شیء Set مشترک و mutable
     * در کل درخت پیمایش (شامل تمام فیلدهای خواهر/برادر) دست‌به‌دست می‌شد.
     * <p>
     * نتیجه: اگر یک نوع پیچیده (مثلاً {@code Address}) از طریق دو فیلد مختلف در همان کلاس
     * ارجاع می‌شد (مثلاً {@code homeAddress} و {@code workAddress})، به محض پردازش اولین
     * فیلد، کلاس Address برای همیشه (در کل درخت، نه فقط همان شاخه) در visited علامت می‌خورد
     * و زیرفیلدهای شاخه‌ی دوم (مثل {@code workAddress.street}) هرگز به نقشه‌ی فیلدهای مجاز
     * اضافه نمی‌شدند؛ در نتیجه فیلتر/سورت روی {@code workAddress.street} با خطای
     * «فیلد جزو projection نیست» رد می‌شد، درحالی‌که کاملاً معتبر بود.
     * <p>
     * راه‌حل: به‌جای اشتراک‌گذاری همان reference، در ابتدای هر فراخوانی یک کپیِ محلی
     * از visited ساخته می‌شود. این کپی هنوز جلوی بازگشت بی‌نهایت (self-reference /
     * cycle) در طول یک مسیر واحد را می‌گیرد، اما دیگر باعث نادیده گرفتن شاخه‌های خواهر
     * که به همان کلاس اشاره می‌کنند نمی‌شود.
     */
    private void collectFields(
            Class<?> clazz,
            String prefix,
            Map<String, FieldMeta> out,
            Class<?> entityClass,
            Set<Class<?>> visited) {

        if (clazz == null || clazz == Object.class || visited.contains(clazz)) {
            return;
        }

        Set<Class<?>> pathVisited = new HashSet<>(visited);
        pathVisited.add(clazz);

        collectFields(clazz.getSuperclass(), prefix, out, entityClass, pathVisited);

        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }

            String dtoName = prefix.isEmpty() ? f.getName() : prefix + "." + f.getName();

            Mapper mapper = f.getAnnotation(Mapper.class);
            String entityPath = (mapper != null && mapper.value() != null && !mapper.value().isBlank())
                    ? mapper.value()
                    : dtoName;

            DisableSpecificationQuery disable = f.getAnnotation(DisableSpecificationQuery.class);
            if (disable == null && entityClass != null) {
                disable = findDisableOnEntity(entityClass, entityPath);
            }

            boolean isDisabled = disable != null;
            String reason = isDisabled ? disable.reason() : null;

            FieldMeta meta = new FieldMeta(dtoName, entityPath, isDisabled, reason);
            out.put(dtoName, meta);

            FieldMeta existing = out.get(f.getName());
            if (existing == null || existing.entityPath().equals(entityPath)) {
                out.putIfAbsent(f.getName(), meta);
            }

            Class<?> fieldType = f.getType();
            if (isComplexType(fieldType)) {
                collectFields(fieldType, dtoName, out, entityClass, pathVisited);
            }
        }
    }

    private boolean isComplexType(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")) {
            return false;
        }
        if (type == LocalDateTime.class || type == LocalDate.class || type == LocalTime.class
                || type == Instant.class || type == Date.class || type == Calendar.class
                || type == OffsetDateTime.class || type == ZonedDateTime.class) {
            return false;
        }
        if (Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Character.class.isAssignableFrom(type)) {
            return false;
        }
        return true;
    }

    private DisableSpecificationQuery findDisableOnEntity(Class<?> entityClass, String entityPath) {
        try {
            String[] parts = entityPath.split("\\.");
            Class<?> current = entityClass;
            Field target = null;
            for (int i = 0; i < parts.length; i++) {
                target = getFieldFromHierarchy(current, parts[i]);
                if (target == null) {
                    return null;
                }
                if (i < parts.length - 1) {
                    current = target.getType();
                }
            }
            return target.getAnnotation(DisableSpecificationQuery.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Field getFieldFromHierarchy(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}