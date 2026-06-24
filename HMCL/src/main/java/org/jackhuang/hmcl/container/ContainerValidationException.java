package org.jackhuang.hmcl.container;

public final class ContainerValidationException extends Exception {
    public ContainerValidationException(String message) {
        super(message);
    }

    public ContainerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
