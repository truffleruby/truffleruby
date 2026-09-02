/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.shared;

import org.graalvm.nativeimage.ImageInfo;

public final class TruffleRuby {

    public static final String FORMAL_NAME = "TruffleRuby";
    public static final String LANGUAGE_ID = "ruby";
    public static final String EXTENSION = ".rb";
    public static final String ENGINE_ID = "truffleruby";
    public static final String LANGUAGE_VERSION = "4.0.2";
    public static final String BOOT_SOURCE_NAME = "main_boot_source";
    public static final String RUBY_COPYRIGHT = "truffleruby - Copyright (c) 2013-2025 Oracle and/or its affiliates; 2026-present TruffleRuby contributors";

    public static String getRubyPlatform(BuildInformation buildInformation) {
        return String.format(
                "%s-%s%s",
                Platform.getArchName(),
                Platform.getOSName(),
                Platform.OS == Platform.OS_TYPE.DARWIN ? buildInformation.kernelMajorVersion : "");
    }

    public static String getVersionString(String implementationName, BuildInformation buildInformation) {
        final String buildName = buildInformation.buildName;
        final String nameExtra;

        if (buildName == null) {
            nameExtra = "";
        } else {
            nameExtra = String.format(" (%s)", buildName);
        }

        return String.format(
                "%s%s %s%s (%s), like ruby %s, %s %s [%s]",
                ENGINE_ID,
                nameExtra,
                getTruffleRubyVersion(buildInformation),
                buildInformation.isDirty ? "*" : "",
                buildInformation.commitDate,
                LANGUAGE_VERSION,
                implementationName,
                ImageInfo.inImageCode() ? "Native" : "JVM",
                getRubyPlatform(buildInformation));
    }

    public static String getTruffleRubyVersion(BuildInformation buildInformation) {
        final String version = buildInformation.truffleRubyVersion;

        // A "-dev" version number - append the commit as well
        if (version.endsWith("-dev") && buildInformation != BuildInformation.UNKNOWN) {
            return version + "-" + buildInformation.shortRevision;
        }

        return version;
    }

}
