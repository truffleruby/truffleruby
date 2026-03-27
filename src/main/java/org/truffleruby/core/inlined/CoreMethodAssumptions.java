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
package org.truffleruby.core.inlined;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.oracle.truffle.api.Assumption;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.MethodEntry;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.InternalMethod;

/** We inline basic operations directly in the AST (instead of a method call) as it makes little sense to compile them
 * in isolation without the surrounding method and it delays more interesting compilations by filling the compilation
 * queue. The performance in interpreter is typically also improved, making inlined basic operations an optimization
 * useful mostly for warmup. The choice of inlining a basic operation is based on running benchmarks and observing which
 * basic operations methods are compiled early. AST inlining is also useful to guarantee splitting for small operations
 * which make sense to always split.
 * <p>
 * Each inlined basic operation has its own Node to conveniently express guards and assumptions. Inlined basic
 * operations execute in the caller frame, which may be useful for some operations accessing the caller frame like
 * Kernel#block_given?.
 * <p>
 * IMPORTANT: The specialization guards must ensure no exception (e.g.: division by 0) can happen during the inlined
 * operation (and therefore no nested Ruby call as that could raise an exception), as that would make the inlined
 * operation NOT appear in the backtrace which would be incorrect and confusing. Inlined nodes should use as few nodes
 * as possible to save on footprint. In trivial cases it is better to inline the logic directly (e.g. for Integer#==
 * with two int's).
 * <p>
 * Two strategies are used to check method re-definition.
 * <li>If the class is a leaf class (there cannot be instances of a subclass of that class), then we only need to check
 * the receiver is an instance of that class and register an Assumption for the given method name (see
 * {@link ModuleFields#registerAssumption}). In such cases the method must be public as we do not check visibility.</li>
 * <li>Otherwise, we need to do a method lookup and verify the method that would be called is the standard definition we
 * expect.</li>
 * <p>
 * Every specialization should use {@code assumptions = "assumptions",} to check at least the tracing Assumption. When
 * adding a new node, it is a good idea to add a debug print in the non-inlined method and try calling it with and
 * without a {@code set_trace_func proc{};} before to see if the inlined version is used correctly. */
public final class CoreMethodAssumptions {

    final RubyLanguage language;

    final Assumption integerNegAssumption, floatNegAssumption;
    final Assumption integerAddAssumption, floatAddAssumption;
    final Assumption integerSubAssumption, floatSubAssumption;
    final Assumption integerMulAssumption, floatMulAssumption;
    final Assumption integerDivAssumption, floatDivAssumption;
    final Assumption integerModAssumption, floatModAssumption;
    public final Assumption integerCmpAssumption, floatCmpAssumption;

    final Assumption integerLeftShiftAssumption;
    final Assumption integerRightShiftAssumption;
    final Assumption integerBitOrAssumption;
    final Assumption integerBitAndAssumption;

    final Assumption integerCaseEqualAssumption;
    final Assumption integerEqualAssumption, floatEqualAssumption;
    final Assumption integerLessThanAssumption, floatLessThanAssumption;
    final Assumption integerLessOrEqualAssumption, floatLessOrEqualAssumption;
    final Assumption integerGreaterThanAssumption, floatGreaterThanAssumption;
    final Assumption integerGreaterOrEqualAssumption, floatGreaterOrEqualAssumption;

    final Assumption nilClassIsNilAssumption;

    public final Assumption symbolToProcAssumption;

    private final List<Consumer<CoreLibrary>> classAssumptionsToRegister;

    public CoreMethodAssumptions(RubyLanguage language) {
        this.language = language;
        this.classAssumptionsToRegister = new ArrayList<>();

        integerNegAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "-@");
        floatNegAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "-@");

        integerAddAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "+");
        floatAddAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "+");

        integerSubAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "-");
        floatSubAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "-");

        integerMulAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "*");
        floatMulAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "*");

        integerDivAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "/");
        floatDivAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "/");

        integerModAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "%");
        floatModAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "%");

        integerCmpAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "<=>");
        floatCmpAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "<=>");

        integerLeftShiftAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "<<");
        integerRightShiftAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", ">>");
        integerBitOrAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "|");
        integerBitAndAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "&");

        integerCaseEqualAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "===");

        integerEqualAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "==");
        floatEqualAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "==");
        integerLessThanAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "<");
        floatLessThanAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "<");
        integerLessOrEqualAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", "<=");
        floatLessOrEqualAssumption = registerAssumption((cl) -> cl.floatClass, "Float", "<=");
        integerGreaterThanAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", ">");
        floatGreaterThanAssumption = registerAssumption((cl) -> cl.floatClass, "Float", ">");
        integerGreaterOrEqualAssumption = registerAssumption((cl) -> cl.integerClass, "Integer", ">=");
        floatGreaterOrEqualAssumption = registerAssumption((cl) -> cl.floatClass, "Float", ">=");

        nilClassIsNilAssumption = registerAssumption((cl) -> cl.nilClass, "Nil", "nil?");

        symbolToProcAssumption = registerAssumption((cl) -> cl.symbolClass, "Symbol", "to_proc");
    }

    @FunctionalInterface
    public interface ContextGetClass {
        RubyClass apply(CoreLibrary c);
    }

    private Assumption registerAssumption(ContextGetClass classGetter, String className, String methodName) {
        final Assumption assumption = Assumption.create(MethodEntry.CORE_METHOD_IS_NOT_OVERRIDDEN);
        classAssumptionsToRegister.add((cl) -> {
            classGetter.apply(cl).fields.registerAssumption(cl, methodName, assumption);
        });
        return assumption;
    }

    /** Must be called before any method is defined, otherwise these assumptions will not properly be reused in
     * {@link ModuleFields#addMethod(RubyContext, Node, InternalMethod)}. */
    public void registerAssumptions(CoreLibrary coreLibrary) {
        for (Consumer<CoreLibrary> registerer : classAssumptionsToRegister) {
            registerer.accept(coreLibrary);
        }
    }

    public RubyContextSourceNode createCallNode(RubyCallNodeParameters callParameters) {
        if (!language.options.BASICOPS_INLINE || callParameters.isSplatted() || callParameters.isSafeNavigation()) {
            return new RubyCallNode(callParameters);
        }

        final RubyNode self = callParameters.getReceiver();
        final RubyNode[] args = callParameters.getArguments();
        int n = 1 /* self */ + args.length;

        if (callParameters.getBlock() != null) {
            if (callParameters.getMethodName().equals("lambda") &&
                    (callParameters.getBlock() instanceof BlockDefinitionNode blockDefinitionNode)) {
                if (callParameters.isIgnoreVisibility() && n == 1) {
                    return InlinedLambdaNodeGen.create(language, callParameters,
                            blockDefinitionNode.getFrameOnStackMarkerSlot(), self, blockDefinitionNode);
                }

                // The block definition node had a default lambda call target, must be converted to proc.
                final RubyNode blockNode = new LambdaToProcNode(blockDefinitionNode);
                return new RubyCallNode(callParameters.withBlock(blockNode));

            } else {
                // no special lambda handling needed
                return new RubyCallNode(callParameters);
            }
        }

        if (n == 1) {
            switch (callParameters.getMethodName()) {
                case "!":
                    return InlinedNotNodeGen.create(language, callParameters, self);
                case "-@":
                    return InlinedNegNodeGen.create(language, callParameters, self);
                case "nil?":
                    return InlinedIsNilNodeGen.create(language, callParameters, self);
                case "bytesize":
                    return InlinedByteSizeNodeGen.create(language, callParameters, self);
                default:
            }
        } else if (n == 2) {
            switch (callParameters.getMethodName()) {
                case "+":
                    return InlinedAddNodeGen.create(language, callParameters, self, args[0]);
                case "-":
                    return InlinedSubNodeGen.create(language, callParameters, self, args[0]);
                case "*":
                    return InlinedMulNodeGen.create(language, callParameters, self, args[0]);
                case "/":
                    return InlinedDivNodeGen.create(language, callParameters, self, args[0]);
                case "%":
                    return InlinedModNodeGen.create(language, callParameters, self, args[0]);
                case "<<":
                    return InlinedLeftShiftNodeGen.create(language, callParameters, self, args[0]);
                case ">>":
                    return InlinedRightShiftNodeGen.create(language, callParameters, self, args[0]);
                case "&":
                    return InlinedBitAndNodeGen.create(language, callParameters, self, args[0]);
                case "|":
                    return InlinedBitOrNodeGen.create(language, callParameters, self, args[0]);
                case "==":
                    return InlinedEqualNodeGen.create(language, callParameters, self, args[0]);
                case "===":
                    return InlinedCaseEqualNodeGen.create(language, callParameters, self, args[0]);
                case "<":
                    return InlinedLessThanNodeGen.create(language, callParameters, self, args[0]);
                case "<=":
                    return InlinedLessOrEqualNodeGen.create(language, callParameters, self, args[0]);
                case ">":
                    return InlinedGreaterThanNodeGen.create(language, callParameters, self, args[0]);
                case ">=":
                    return InlinedGreaterOrEqualNodeGen.create(language, callParameters, self, args[0]);
                case "[]":
                    return InlinedIndexGetNodeGen.create(language, callParameters, self, args[0]);
                case "at":
                    return InlinedAtNodeGen.create(language, callParameters, self, args[0]);
                case "is_a?":
                    return InlinedIsANodeGen.create(language, callParameters, self, args[0]);
                case "kind_of?":
                    return InlinedKindOfNodeGen.create(language, callParameters, self, args[0]);
                default:
            }
        } else if (n == 3) {
            switch (callParameters.getMethodName()) {
                case "[]=":
                    return InlinedIndexSetNodeGen.create(language, callParameters, self, args[0], args[1]);
                default:
            }
        }

        return new RubyCallNode(callParameters);
    }
}
