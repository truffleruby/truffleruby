/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.loader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.truffleruby.RubyLanguage;

/** Loads source files that have been stored as resources (in the Java jar file sense). */
public abstract class ResourceLoader {

    public static byte[] readResource(String path) throws IOException {
        assert path.startsWith(RubyLanguage.RESOURCE_SCHEME);
        assert path.endsWith(".rb");

        final String resourcePath = path.substring(RubyLanguage.RESOURCE_SCHEME.length());

        try (InputStream stream = ResourceLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new FileNotFoundException(resourcePath);
            }

            return stream.readAllBytes();
        }
    }

}
