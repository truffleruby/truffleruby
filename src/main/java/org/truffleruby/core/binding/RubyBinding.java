/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.binding;

import java.util.Set;

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;

/** Bindings capture the frame from where they are called, which is initially stored in {@code frame}, but may have
 * their own frame(s) to store variables added in the Binding, which must not be added in the captured frame (MRI
 * semantics). Each new frame is chained to the current frame and then replaces it in the {#code frame} field. New
 * frame(s) are added as required to prevent evaluation using the binding from leaking new variables into the captured
 * frame, and when cloning a binding to stop variables from the clone leaking to the original or vice versa. */
public final class RubyBinding extends RubyDynamicObject implements ObjectGraphNode {

    private MaterializedFrame frame;
    public final SourceSection sourceSection;

    public RubyBinding(RubyClass rubyClass, Shape shape, MaterializedFrame frame, SourceSection sourceSection) {
        super(rubyClass, shape);
        assert frame != null;
        this.frame = frame;
        this.sourceSection = sourceSection;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public void setFrame(MaterializedFrame frame) {
        this.frame = frame;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.getObjectsInFrame(frame, reachable);
    }

}
