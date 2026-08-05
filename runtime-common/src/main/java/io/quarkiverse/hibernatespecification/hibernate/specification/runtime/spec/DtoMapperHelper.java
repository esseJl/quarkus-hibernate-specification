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

    // FIX: کش thread-safe برای constructor کانونیکِ رکوردها تا هم باگ زیر رفع شود
    // و هم reflection lookup هزینه‌بر برای هر ردیف/tuple تکرار نشود.
    private final ConcurrentHashMap<Class<?>, Constructor<?>> canonicalCtorCache = new ConcurrentHashMap<>();

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

    /**
     * FIX (باگ): نسخه‌ی قبلی با {@code dtoClass.getDeclaredConstructors()[0]} اولین
     * constructor برگشتی از JVM را انتخاب می‌کرد. ترتیب آرایه‌ی
     * {@code getDeclaredConstructors()} در جاوا هیچ تضمین مستندشده‌ای ندارد؛ اگر
     * DTO رکورد یک constructor کمکی (غیر canonical) هم داشته باشد - چیزی که در
     * رکوردهای جاوا کاملاً مجاز و رایج است - ممکن بود همان constructor اشتباه
     * انتخاب و با آرگومان‌های نادرست (تعداد/ترتیب/نوع پارامتر متفاوت) صدا زده شود؛
     * نتیجه یا ClassCastException/IllegalArgumentException در زمان اجرا بود یا حتی
     * بدتر، مقداردهی نادرستِ بی‌صدا به فیلدها.
     * <p>
     * راه‌حل: constructor کانونیک را دقیقاً بر اساس نوع و تعداد پارامترهای
     * {@code getRecordComponents()} (که ترتیب آن‌ها توسط JLS تضمین شده) پیدا
     * می‌کنیم، و نتیجه را برای فراخوانی‌های بعدی کش می‌کنیم.
     */
    @SuppressWarnings("unchecked")
    private <D> D mapToRecord(Tuple tuple, Class<D> dtoClass, List<FieldMeta> metas) throws Exception {
        RecordComponent[] components = dtoClass.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            String alias = SpecificationBuilder.sanitizeAlias(name);
            try {
                args[i] = tuple.get(alias);
            } catch (IllegalArgumentException ex) {
                // fallback: تلاش با نام کامل از metas
                args[i] = null;
                for (FieldMeta m : metas) {
                    if (m.dtoName().equals(name) || m.dtoName().endsWith("." + name)) {
                        args[i] = tuple.get(SpecificationBuilder.sanitizeAlias(m.dtoName()));
                        break;
                    }
                }
            }
        }

        Constructor<D> ctor = (Constructor<D>) canonicalCtorCache.computeIfAbsent(dtoClass, cls -> {
            try {
                Class<?>[] paramTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    paramTypes[i] = components[i].getType();
                }
                Constructor<?> canonical = cls.getDeclaredConstructor(paramTypes);
                canonical.setAccessible(true);
                return canonical;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(
                        "Canonical constructor not found for record: " + cls.getName(), e);
            }
        });

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