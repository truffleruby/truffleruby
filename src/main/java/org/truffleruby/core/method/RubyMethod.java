/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.method;

import java.util.Set;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.interop.ForeignToRubyArgumentsNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.language.RubyBaseNode.nil;

@ExportLibrary(InteropLibrary.class)
public final class RubyMethod extends RubyDynamicObject implements ObjectGraphNode {

    public RubyMethod(RubyClass metaClass, Shape shape, Object receiver, InternalMethod method) {
        super(metaClass, shape);
        this.receiver = receiver;
        this.method = method;
    }

    public final Object receiver;
    public final InternalMethod method;

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, receiver);
        ObjectGraph.addProperty(reachable, method);
    }

    // region SourceLocation
    @ExportMessage
    public boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage
    public SourceSection getSourceLocation() {
        return method.getSharedMethodInfo().getSourceSection();
    }
    // endregion

    // region Executable
    @ExportMessage
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(Object[] arguments,
            @Cached ForeignToRubyArgumentsNode foreignToRubyArgumentsNode,
            @Cached CallInternalMethodNode callInternalMethodNode,
            @Bind Node node) {
        final Object[] convertedArguments = foreignToRubyArgumentsNode.executeConvert(node, arguments);
        final Object[] frameArgs = RubyArguments.pack(null, null, method, null, receiver, nil,
                NoKeywordArgumentsDescriptor.INSTANCE, convertedArguments);
        return callInternalMethodNode.execute(null, method, receiver, frameArgs);
    }
    // endregion

}
