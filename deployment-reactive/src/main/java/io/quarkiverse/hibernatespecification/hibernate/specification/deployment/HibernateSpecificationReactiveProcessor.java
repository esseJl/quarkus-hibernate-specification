package io.quarkiverse.hibernatespecification.hibernate.specification.deployment;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.annotation.DtoMapper;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model.*;
import io.quarkiverse.hibernatespecification.hibernate.specification.runtime.spec.*;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class HibernateSpecificationReactiveProcessor {

    private static final String FEATURE = "hibernate-specification-reactive";
    private static final DotName DTO_MAPPER = DotName.createSimple(DtoMapper.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerReactiveBeans(Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> beans) {

        if (capabilities.isPresent(Capability.HIBERNATE_REACTIVE)) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            FieldMetaRegistry.class,
                            PathResolver.class,
                            ValueConverter.class,
                            DtoMapperHelper.class,
                            SpecificationBuilder.class)
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void warmupFieldMetaCache(Capabilities capabilities,
            CombinedIndexBuildItem combinedIndex,
            BeanContainerBuildItem beanContainer,
            FieldMetaWarmupRecorder recorder) {

        if (!capabilities.isPresent(Capability.HIBERNATE_REACTIVE)) {
            return;
        }

        List<String> dtoClassNames = new ArrayList<>();
        for (AnnotationInstance ann : combinedIndex.getIndex().getAnnotations(DTO_MAPPER)) {
            if (ann.target().kind() == AnnotationTarget.Kind.CLASS) {
                dtoClassNames.add(ann.target().asClass().name().toString());
            }
        }

        recorder.warmUp(beanContainer.getValue(), dtoClassNames);
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
                ComparisonOperator.class,
                LogicalOperator.class,
                SortDirection.class,
                FilterNode.NodeType.class,
                FilterValue.ValueType.class,
                FieldMeta.class,
                PageRequest.class,
                SortRequest.class).methods(true).fields(true).build();
    }
}