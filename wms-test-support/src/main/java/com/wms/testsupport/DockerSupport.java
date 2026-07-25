package com.wms.testsupport;

import org.testcontainers.DockerClientFactory;

/**
 * Testcontainers/Docker kullanılabilirliği — {@code @EnabledIf} koşulları için.
 */
public final class DockerSupport {

    private DockerSupport() {
    }

    public static boolean isAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
