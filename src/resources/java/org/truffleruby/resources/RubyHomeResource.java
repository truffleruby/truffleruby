/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.resources;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;

import java.io.IOException;
import java.nio.file.Path;

@InternalResource.Id(value = "ruby-home", componentId = "ruby", optional = true)
public final class RubyHomeResource implements InternalResource {

    private static final Path BASE_PATH = Path.of("META-INF", "resources", "ruby", "ruby-home");
    private static final Path ARCH_SPECIFIC_PATH = BASE_PATH.resolve(OS.getCurrent().toString())
            .resolve(CPUArchitecture.getCurrent().toString());
    private static final Path ARCH_AGNOSTIC_PATH = BASE_PATH.resolve("common");

    @Override
    public void unpackFiles(Env env, Path targetDirectory) throws IOException {
        env.unpackResourceFiles(ARCH_AGNOSTIC_PATH.resolve("file-list"), targetDirectory, ARCH_AGNOSTIC_PATH);
        env.unpackResourceFiles(ARCH_SPECIFIC_PATH.resolve("file-list"), targetDirectory, ARCH_SPECIFIC_PATH);
    }

    @Override
    public String versionHash(Env env) {
        try {
            return env.readResourceLines(ARCH_AGNOSTIC_PATH.resolve("sha256")).get(0) +
                    env.readResourceLines(ARCH_SPECIFIC_PATH.resolve("sha256")).get(0);
        } catch (IOException ioe) {
            throw CompilerDirectives.shouldNotReachHere(ioe);
        }
    }

}
