package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class FieldMetaWarmupRecorder {

    private static final Logger LOG = Logger.getLogger(FieldMetaWarmupRecorder.class);

    public void warmUp(BeanContainer beanContainer, List<String> dtoClassNames) {
        if (dtoClassNames == null || dtoClassNames.isEmpty()) {
            return;
        }

        FieldMetaRegistry registry = beanContainer.beanInstance(FieldMetaRegistry.class);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (String className : dtoClassNames) {
            try {
                Class<?> clazz = Class.forName(className, false, cl);
                registry.warmUp(clazz);
            } catch (Throwable t) {
                LOG.warnf(t, "Failed to warm up field metadata cache for class '%s'", className);
            }
        }
    }
}