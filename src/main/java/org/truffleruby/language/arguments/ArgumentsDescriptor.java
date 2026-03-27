/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

/** An arguments descriptor describes what kind of arguments are passed at a given call site. Most importantly they
 * describe whether some keywords arguments are passed or none. Argument descriptors are stable, immutable objects,
 * suitable to be cached and guarded by identity. They'd normally convey some kind of static information from call site
 * to callee. */
public abstract class ArgumentsDescriptor {
}
