package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation;

import java.lang.annotation.*;

/**
 * Marks a field as not allowed in dynamic filter/sort queries.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableSpecificationQuery {
    String reason() default "Field is not allowed in dynamic queries";
}
