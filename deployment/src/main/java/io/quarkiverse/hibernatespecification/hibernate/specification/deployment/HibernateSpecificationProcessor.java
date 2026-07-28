package io.quarkiverse.hibernatespecification.hibernate.specification.deployment;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.CriteriaPredicateFactory;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.criteria.FilterValueConverter;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.orm.SpecificationExecutor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class HibernateSpecificationProcessor {

    private static final String FEATURE = "hibernate-specification";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        if (capabilities.isPresent(Capability.HIBERNATE_ORM)) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            SpecificationExecutor.class,
                            CriteriaPredicateFactory.class,
                            FilterValueConverter.class)
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    ReflectiveClassBuildItem registerForReflection() {
        return ReflectiveClassBuildItem.builder(

                QueryRequest.class,
                FilterNode.class,
                FilterPredicate.class,
                FilterGroup.class,
                FilterValue.class,
                SingleValue.class,
                MultiValue.class,
                RangeValue.class,
                PageResponse.class,
                // enums
                ComparisonOperator.class,
                LogicalOperator.class,
                SortDirection.class,
                FilterNode.NodeType.class,
                FilterValue.ValueType.class,
                // page & sort
                PageRequest.class,
                SortRequest.class).methods(true).fields(true).build();
    }
}
