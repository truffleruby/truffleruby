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

import org.truffleruby.annotations.PopulateBuildInformation;

@PopulateBuildInformation
public interface BuildInformation {

    String getTruffleRubyVersion();

    String getBuildName();

    String getShortRevision();

    String getFullRevision();

    boolean isDirty();

    String getCommitDate();

    int getCopyrightYear();

    String getKernelMajorVersion();

}
