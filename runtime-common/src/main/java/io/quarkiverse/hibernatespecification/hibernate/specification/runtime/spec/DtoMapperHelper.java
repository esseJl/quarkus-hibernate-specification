package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.FieldMeta;

@ApplicationScoped
public class DtoMapperHelper {

    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> fieldCache = new ConcurrentHashMap<>();

    public <D> D mapTupleToDto(Tuple tuple, Class<D> dtoClass, List<FieldMeta> metas) {
        try {
            if (dtoClass.isRecord()) {
                return mapToRecord(tuple, dtoClass, metas);
            }
            D instance = createInstance(dtoClass);
            for (FieldMeta meta : metas) {
                String alias = SpecificationBuilder.sanitizeAlias(meta.dtoName());
                Object value = tuple.get(alias);
                setFieldValue(instance, meta.dtoName(), value);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to map Tuple to DTO: " + dtoClass.getSimpleName()
                            + ". Ensure the DTO has a no-arg constructor or is a record with matching components.",
                    e);
        }
    }

    @SuppressWarnings("unchecked")
    private <D> D mapToRecord(Tuple tuple, Class<D> dtoClass, List<FieldMeta> metas) throws Exception {
        RecordComponent[] components = dtoClass.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            // پیدا کردن meta بر اساس dtoName ساده یا کامل
            String alias = SpecificationBuilder.sanitizeAlias(name);
            try {
                args[i] = tuple.get(alias);
            } catch (IllegalArgumentException ex) {
                // fallback: سعی با نام کامل از metas
                args[i] = null;
                for (FieldMeta m : metas) {
                    if (m.dtoName().equals(name) || m.dtoName().endsWith("." + name)) {
                        args[i] = tuple.get(SpecificationBuilder.sanitizeAlias(m.dtoName()));
                        break;
                    }
                }
            }
        }
        Constructor<D> ctor = (Constructor<D>) dtoClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private <D> D createInstance(Class<D> dtoClass) throws Exception {
        var ctor = dtoClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private void setFieldValue(Object target, String fieldPath, Object value) throws Exception {
        String[] parts = fieldPath.split("\\.");
        Object current = target;

        for (int i = 0; i < parts.length - 1; i++) {
            Field f = getCachedField(current.getClass(), parts[i]);
            f.setAccessible(true);
            Object nested = f.get(current);
            if (nested == null) {
                nested = f.getType().getDeclaredConstructor().newInstance();
                f.set(current, nested);
            }
            current = nested;
        }

        Field last = getCachedField(current.getClass(), parts[parts.length - 1]);
        last.setAccessible(true);
        last.set(current, value);
    }

    private Field getCachedField(Class<?> clazz, String name) {
        return fieldCache
                .computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, n -> {
                    try {
                        return getFieldFromHierarchy(clazz, n);
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Field getFieldFromHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(
                "Field '" + fieldName + "' not found in class hierarchy of " + clazz.getName());
    }
}