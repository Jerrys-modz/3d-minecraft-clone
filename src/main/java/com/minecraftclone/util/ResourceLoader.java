package com.minecraftclone.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads text resources (e.g. GLSL shader sources) bundled on the classpath. */
public final class ResourceLoader {

    private ResourceLoader() {
    }

    public static String loadAsString(String classpathResource) {
        try (InputStream in = ResourceLoader.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + classpathResource, e);
        }
    }
}
