package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model;

public record FieldMeta(String dtoName, String entityPath, boolean disabled, String disableReason) {
    public boolean isAllowed() {
        return !disabled;
    }
}
