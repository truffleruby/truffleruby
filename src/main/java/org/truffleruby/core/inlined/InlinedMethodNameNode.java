/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

public abstract class InlinedMethodNameNode extends UnaryInlinedOperationNode {

    protected final String methodName;
    protected final boolean returnsOriginalName;

    public InlinedMethodNameNode(RubyLanguage language, RubyCallNodeParameters parameters) {
        super(language, parameters);
        methodName = parameters.getMethodName();
        returnsOriginalName = methodName.equals("__method__");
    }

    protected InternalMethod kernelMethod() {
        return returnsOriginalName ? coreMethods().KERNEL_METHOD_NAME : coreMethods().KERNEL_CALLEE_NAME;
    }

    @Specialization(
            guards = "lookup.lookupIgnoringVisibility(frame, self, methodName) == kernelMethod()",
            assumptions = "assumptions",
            limit = "1")
    static Object callerMethodName(VirtualFrame frame, Object self,
            @Bind Node node,
            @Bind("returnsOriginalName") boolean originalName,
            @Cached LookupMethodOnSelfNode lookup,
            @Cached ToSymbolNode toSymbolNode,
            @Cached InlinedConditionProfile moduleBodyProfile) {
        var caller = RubyArguments.getMethod(frame);
        var info = caller.getSharedMethodInfo();

        if (moduleBodyProfile.profile(node, info.isModuleBody())) {
            return nil;
        }

        return toSymbolNode.execute(node, originalName ? info.getMethodName() : caller.getName());
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object self) {
        return rewriteAndCall(frame, self);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedMethodNameNodeGen.create(getLanguage(), parameters, getSelfNode().cloneUninitialized());

        return copy.copyFlags(this);
    }
}
