package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.exception;

public class SpecificationException extends RuntimeException {
    public SpecificationException(String message) {
        super(message);
    }

    public SpecificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
