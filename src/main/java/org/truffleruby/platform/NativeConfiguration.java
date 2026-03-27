/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * This file contains configuration values translated from Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.shared.Platform;
import org.truffleruby.shared.TruffleRuby;

public final class NativeConfiguration {

    public static final String PREFIX = "platform.";

    private final Map<String, Object> configuration = new HashMap<>(); // Only written to by create() once per RubyContext.

    public static NativeConfiguration loadNativeConfiguration(RubyContext context) {
        final NativeConfiguration nativeConfiguration = new NativeConfiguration();

        switch (Platform.OS) {
            case LINUX:
                switch (Platform.ARCHITECTURE) {
                    case AMD64:
                        LinuxAMD64NativeConfiguration.load(nativeConfiguration, context);
                        return nativeConfiguration;
                    case AARCH64:
                        LinuxAArch64NativeConfiguration.load(nativeConfiguration, context);
                        return nativeConfiguration;
                }
                break;
            case DARWIN:
                switch (Platform.ARCHITECTURE) {
                    case AARCH64:
                        DarwinAArch64NativeConfiguration.load(nativeConfiguration, context);
                        return nativeConfiguration;
                }
                break;
        }

        RubyLanguage.LOGGER.severe("no native configuration for platform " + TruffleRuby.RUBY_PLATFORM);
        return nativeConfiguration;
    }

    public void config(String key, Object value) {
        configuration.put(key, value);
    }

    @TruffleBoundary
    public Object get(String key) {
        return configuration.get(key);
    }

    @TruffleBoundary
    public Collection<Entry<String, Object>> getSection(String section) {
        final Collection<Entry<String, Object>> entries = new ArrayList<>();

        for (Entry<String, Object> entry : configuration.entrySet()) {
            if (entry.getKey().startsWith(section)) {
                entries.add(entry);
            }
        }

        return entries;
    }

    @TruffleBoundary
    public Collection<Object> objectGraphValues() {
        final Collection<Object> values = configuration.values();
        final ArrayList<Object> objects = new ArrayList<>(values.size());
        for (Object value : values) {
            if (ObjectGraph.isRubyObject(value)) {
                objects.add(value);
            }
        }
        return objects;
    }

}
