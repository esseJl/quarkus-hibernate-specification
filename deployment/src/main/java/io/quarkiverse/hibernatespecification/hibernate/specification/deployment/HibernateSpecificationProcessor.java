package io.quarkiverse.hibernatespecification.hibernate.specification.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class HibernateSpecificationProcessor {

    private static final String FEATURE = "hibernate-specification";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
