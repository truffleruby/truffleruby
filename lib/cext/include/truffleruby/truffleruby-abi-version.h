/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

#ifndef TRUFFLERUBY_ABI_VERSION_H
#define TRUFFLERUBY_ABI_VERSION_H

// The TruffleRuby ABI version must be of the form:
// * For releases, i.e. on a release branch:
//   $TRUFFLERUBY_VERSION.$ABI_NUMBER e.g. 34.0.0.1
// * For non-release:
//   $RUBY_VERSION.$ABI_NUMBER e.g. 3.4.5.1
//
// $TRUFFLERUBY_VERSION is the version in suite.py.
// $RUBY_VERSION is TruffleRuby.LANGUAGE_VERSION.
// $ABI_NUMBER starts at 1 and is incremented for every ABI-incompatible change.
//
// With this scheme the ABI version for releases and for dev builds are completely separate
// and cannot reach the same version while having different ABI
// ($TRUFFLERUBY_VERSION is never the same as $RUBY_VERSION).

#define TRUFFLERUBY_ABI_VERSION "3.3.7.11"

#endif
