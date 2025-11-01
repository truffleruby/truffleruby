/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
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
    public static final String LANGUAGE_VERSION = "3.4.7";
    public static final String LANGUAGE_REVISION = BuildInformationImpl.INSTANCE.getFullRevision();
    public static final String BOOT_SOURCE_NAME = "main_boot_source";
    public static final String RUBY_COPYRIGHT = "truffleruby - Copyright (c) 2013-" +
            BuildInformationImpl.INSTANCE.getCopyrightYear() + " Oracle and/or its affiliates";
    public static final String RUBY_PLATFORM = String.format(
            "%s-%s%s",
            Platform.getArchName(),
            Platform.getOSName(),
            Platform.getKernelMajorVersion());

    public static String getVersionString(String implementationName) {
        final String buildName = BuildInformationImpl.INSTANCE.getBuildName();
        final String nameExtra;

        if (buildName == null) {
            nameExtra = "";
        } else {
            nameExtra = String.format(" (%s)", BuildInformationImpl.INSTANCE.getBuildName());
        }

        return String.format(
                "%s%s %s%s (%s), like ruby %s, %s %s [%s]",
                ENGINE_ID,
                nameExtra,
                getTruffleRubyVersion(),
                BuildInformationImpl.INSTANCE.isDirty() ? "*" : "",
                BuildInformationImpl.INSTANCE.getCommitDate(),
                LANGUAGE_VERSION,
                implementationName,
                ImageInfo.inImageCode() ? "Native" : "JVM",
                RUBY_PLATFORM);
    }

    public static String getTruffleRubyVersion() {
        final String version = BuildInformationImpl.INSTANCE.getTruffleRubyVersion();
        final String revisionString = BuildInformationImpl.INSTANCE.getShortRevision();

        // A "-dev" version number - append the commit as well
        if (version.endsWith("-dev")) {
            return version + "-" + revisionString;
        }

        return version;
    }

}
