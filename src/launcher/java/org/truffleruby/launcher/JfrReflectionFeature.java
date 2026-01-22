/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.launcher;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleFinder;
import java.util.function.Consumer;

/** GraalVM Native Image Feature that registers all JFR classes for reflection access. This enables JFR event streaming
 * from TruffleRuby via polyglot interop.
 *
 * Dynamically discovers and registers all public classes in the jdk.jfr module, making them accessible via Java.type()
 * in Ruby code. */
public class JfrReflectionFeature implements Feature {

    /** Packages in jdk.jfr module to register for reflection. */
    private static final String[] JFR_PACKAGES = {
            "jdk.jfr",
            "jdk.jfr.consumer",
    };

    @Override
    public String getDescription() {
        return "Registers JFR classes for reflection to enable JFR event streaming from Ruby";
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // Dynamically discover and register all classes in jdk.jfr packages
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleReference ref = finder.find("jdk.jfr").orElse(null);
        if (ref == null) {
            return; // JFR module not available
        }

        try (ModuleReader reader = ref.open()) {
            reader.list()
                    .filter(resource -> resource.endsWith(".class"))
                    .filter(resource -> !resource.contains("internal"))  // Skip internal classes
                    .filter(resource -> isInPackage(resource, JFR_PACKAGES))
                    .forEach(resource -> {
                        String className = resource.replace("/", ".").replace(".class", "");
                        try {
                            Class<?> clazz = Class.forName(className);
                            registerClass(clazz);
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Skip classes that can't be loaded
                        }
                    });
        } catch (IOException e) {
            // Module couldn't be read, skip JFR registration
        }

        // Register proxy for Consumer interface (used for JFR event callbacks)
        RuntimeProxyCreation.register(Consumer.class);
    }

    /** Check if a resource path is in one of the specified packages. */
    private static boolean isInPackage(String resource, String[] packages) {
        for (String pkg : packages) {
            String pkgPath = pkg.replace(".", "/") + "/";
            if (resource.startsWith(pkgPath)) {
                // Ensure it's directly in the package, not a subpackage
                String remainder = resource.substring(pkgPath.length());
                if (!remainder.contains("/")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Register a class and all its members for reflection access. */
    private static void registerClass(Class<?> clazz) {
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getConstructors());
        RuntimeReflection.register(clazz.getDeclaredConstructors());
        RuntimeReflection.register(clazz.getMethods());
        RuntimeReflection.register(clazz.getDeclaredMethods());
        RuntimeReflection.register(clazz.getFields());
        RuntimeReflection.register(clazz.getDeclaredFields());
    }
}
