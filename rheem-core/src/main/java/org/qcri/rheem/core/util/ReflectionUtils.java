package org.qcri.rheem.core.util;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 * Utilities for reflection code.
 */
public class ReflectionUtils {

    private static final Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

    /**
     * Identifies and returns the JAR file declaring the {@link Class} of the given {@code object} or {@code null} if
     * no such file could be determined.
     */
    public static String getDeclaringJar(Object object) {
        Validate.notNull(object);

        return getDeclaringJar(object.getClass());
    }

    /**
     * Identifies and returns the JAR file declaring the given {@link Class} if no such file could be determined.
     */
    public static String getDeclaringJar(Class<?> cls) {
        try {
            final URL location = cls.getProtectionDomain().getCodeSource().getLocation();
            final URI uri = location.toURI();
            final String path = uri.getPath();
            if (path.endsWith(".jar")) {
                return path;
            } else {
                logger.warn("Class {} is not loaded from a JAR file, but from {}. Thus, cannot provide the JAR file.", cls, path);
            }
        } catch (Exception e) {
            logger.error(String.format("Could not determine JAR file declaring %s.", cls), e);
        }
        return null;
    }

    /**
     * Provides a resource as an {@link InputStream}.
     *
     * @param resourceName the name or path of the resource
     * @return an {@link InputStream} with the contents of the resource or {@code null} if the resource could not be found
     */
    public static InputStream loadResource(String resourceName) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
    }

    /**
     * Provides a resource as {@link URL}.
     *
     * @param resourceName the name or path of the resource
     * @return a {@link URL} describing the path to the resource or {@code null} if the resource could not be found
     */
    public static URL getResourceURL(String resourceName) {
        return Thread.currentThread().getContextClassLoader().getResource(resourceName);
    }

}
