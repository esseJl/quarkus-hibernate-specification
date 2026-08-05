package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation;

import java.lang.annotation.*;

/**
 * Maps a DTO field to a different entity path.
 * Example: @Mapper("user.profile.city") on a DTO field named "cityName"
 */
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Mapper {
    String value() default "";
}
