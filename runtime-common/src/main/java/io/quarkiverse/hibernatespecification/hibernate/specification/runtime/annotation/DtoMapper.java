package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation;

import java.lang.annotation.*;

/**
 * Maps a DTO class to its corresponding Entity class.
 * Used by FieldMetaRegistry for projection and path resolution.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DtoMapper {
    Class<?> value();
}
