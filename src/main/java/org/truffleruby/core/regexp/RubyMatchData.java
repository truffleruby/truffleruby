/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import java.util.Set;

import dev.truffleruby.shadowed.org.joni.MultiRegion;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.Shape;

public final class RubyMatchData extends RubyDynamicObject implements ObjectGraphNode {

    /** A group which was not matched */
    public static final int MISSING = -1;
    /** A group for which the offsets were not computed yet */
    public static final int LAZY = -2;

    /** Either a Regexp or a String for the case of String#gsub(String) */
    public Object regexp;
    public Object source;
    /** Group bounds as byte offsets. We avoid the abstract Region class because it has virtual methods calls which
     * cannot be inlined by PE. */
    public MultiRegion region;
    /** Group bounds as character offsets. We avoid the abstract Region class because it has virtual methods calls which
     * cannot be inlined by PE. */
    public MultiRegion charOffsets = null;
    public Object tRegexResult = null;

    public RubyMatchData(
            RubyClass rubyClass,
            Shape shape,
            Object regexp,
            Object source,
            MultiRegion region) {
        super(rubyClass, shape);
        assert regexp instanceof RubyRegexp || regexp instanceof RubyString || regexp instanceof ImmutableRubyString ||
                regexp == null;
        this.regexp = regexp;
        assert source == null || source instanceof RubyString || source instanceof ImmutableRubyString;
        this.source = source;
        this.region = region;
    }

    public void getAdjacentObjects(Set<Object> reachable) {
        if (regexp != null) {
            reachable.add(regexp);
        }
        if (source != null) {
            reachable.add(source);
        }
    }

}
