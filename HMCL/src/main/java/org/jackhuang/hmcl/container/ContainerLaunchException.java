package org.jackhuang.hmcl.container;

import java.util.Collections;
import java.util.List;

public final class ContainerLaunchException extends Exception {
    private final List<String> deployFailures;

    public ContainerLaunchException(String message) {
        super(message);
        this.deployFailures = Collections.emptyList();
    }

    public ContainerLaunchException(String message, Throwable cause) {
        super(message, cause);
        this.deployFailures = Collections.emptyList();
    }

    public ContainerLaunchException(List<String> deployFailures) {
        super("Deploy completed with failures");
        this.deployFailures = deployFailures != null ? deployFailures : Collections.emptyList();
    }

    public boolean hasDeployFailures() {
        return !deployFailures.isEmpty();
    }

    public List<String> getDeployFailures() {
        return deployFailures;
    }
}
