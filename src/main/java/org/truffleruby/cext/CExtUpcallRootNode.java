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
package org.truffleruby.cext;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager.WrapperToHandleNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;

/** Executes one C extension upcall (see tool/cext-upcalls.rb) with cached nodes, so the argument and result conversions
 * and the dispatch to the Truffle::CExt method partial-evaluate. Called from the corresponding
 * {@link CExtUpcallTargets} method through {@link CExtFFMLayer#upcall(int, Object...)}. The frame arguments are the raw
 * boxed primitive upcall arguments. */
public final class CExtUpcallRootNode extends RubyBaseRootNode {

    /** The kind of a native value in an upcall signature, from the carrier letters in tool/cext-upcalls.rb */
    public enum Carrier {
        VALUE, // V: a VALUE handle, converted with UnwrapNode/WrapNode
        WRAPPED, // W: a result which is already a VALUE handle or a ValueWrapper
        INT, // I
        BOOL, // B: Ruby true/false as int 1/0
        LONG, // L
        DOUBLE, // D
        POINTER, // P: a raw long address, including function pointers and VALUE arrays
        ID, // Y: converted from/to a Ruby Symbol
        VOID; // O

        static Carrier parse(char carrier) {
            return switch (carrier) {
                case 'V' -> VALUE;
                case 'W' -> WRAPPED;
                case 'I' -> INT;
                case 'B' -> BOOL;
                case 'L' -> LONG;
                case 'D' -> DOUBLE;
                case 'P' -> POINTER;
                case 'Y' -> ID;
                case 'O' -> VOID;
                default -> throw CompilerDirectives.shouldNotReachHere(String.valueOf(carrier));
            };
        }
    }

    /** How an upcall determines its receiver and method name */
    public enum Kind {
        /** call the method {@code rubyName} on Truffle::CExt */
        CEXT,
        /** call the method {@code rubyName} on the receiver, which is the first argument */
        SEND
    }

    /** Parsed form of a CExtUpcallTargets.UPCALLS entry: the kind, Ruby method name, return carrier and argument
     * carrier letters strings. */
    public static final class UpcallSpec {
        final Kind kind;
        final String rubyName;
        final Carrier returnCarrier;
        @CompilationFinal(dimensions = 1) final Carrier[] argumentCarriers;

        public static UpcallSpec parse(String kindString, String rubyName, String returnCarrierString, String args) {
            final Kind kind = switch (kindString) {
                case "cext" -> Kind.CEXT;
                case "send" -> Kind.SEND;
                default -> throw CompilerDirectives.shouldNotReachHere(kindString);
            };
            final Carrier returnCarrier = Carrier.parse(returnCarrierString.charAt(0));
            final Carrier[] carriers = new Carrier[args.length()];
            for (int i = 0; i < carriers.length; i++) {
                carriers[i] = Carrier.parse(args.charAt(i));
            }
            return new UpcallSpec(kind, rubyName, returnCarrier, carriers);
        }

        UpcallSpec(Kind kind, String rubyName, Carrier returnCarrier, Carrier[] argumentCarriers) {
            this.kind = kind;
            this.rubyName = rubyName;
            this.returnCarrier = returnCarrier;
            this.argumentCarriers = argumentCarriers;
        }
    }

    private final String name;
    @Child private ExecuteUpcallNode executeUpcall;

    public CExtUpcallRootNode(RubyLanguage language, String name, UpcallSpec spec) {
        super(language, language.EMPTY_DECLARATION_DESCRIPTOR, null);
        this.name = name;
        this.executeUpcall = CExtUpcallRootNodeFactory.ExecuteUpcallNodeGen.create(spec);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeUpcall.execute(frame.getArguments());
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public abstract static class ExecuteUpcallNode extends RubyBaseNode {

        protected final UpcallSpec spec;

        protected ExecuteUpcallNode(UpcallSpec spec) {
            this.spec = spec;
        }

        public abstract Object execute(Object[] rawArguments);

        @ExplodeLoop
        @Specialization
        Object upcall(Object[] rawArguments,
                @Cached UnwrapNode unwrapNode,
                @Cached WrapNode wrapNode,
                @Cached IDToSymbolNode idToSymbolNode,
                @Cached SymbolToIDNode symbolToIDNode,
                @Cached DispatchNode dispatchNode,
                @Cached WrapperToHandleNode wrapperToHandleNode) {
            final Object receiver;
            final String methodName;
            final int argumentsOffset;
            if (spec.kind == Kind.CEXT) {
                receiver = coreLibrary().truffleCExtModule;
                methodName = spec.rubyName;
                argumentsOffset = 0;
            } else {
                receiver = unwrapNode.execute(this, rawArguments[0]);
                methodName = spec.rubyName;
                argumentsOffset = 1;
            }
            CompilerAsserts.partialEvaluationConstant(argumentsOffset);

            final Object[] arguments = new Object[spec.argumentCarriers.length - argumentsOffset];
            for (int i = 0; i < arguments.length; i++) {
                final int carrierIndex = argumentsOffset + i;
                CompilerAsserts.partialEvaluationConstant(carrierIndex);
                arguments[i] = convertArgument(spec.argumentCarriers[carrierIndex],
                        rawArguments[carrierIndex], unwrapNode, idToSymbolNode);
            }

            final Object result = dispatchNode.call(DispatchConfiguration.PRIVATE, receiver, methodName, arguments);

            return convertResult(result, wrapNode, symbolToIDNode, wrapperToHandleNode);
        }

        private Object convertArgument(Carrier carrier, Object raw, UnwrapNode unwrapNode,
                IDToSymbolNode idToSymbolNode) {
            CompilerAsserts.partialEvaluationConstant(carrier);
            return switch (carrier) {
                case VALUE -> unwrapNode.execute(this, raw);
                case INT, LONG, DOUBLE, POINTER -> raw;
                case ID -> idToSymbolNode.execute(raw);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        /** The Truffle::CExt methods only return values these conversions handle, no need for anything fancier like
         * InteropLibrary here. */
        private Object convertResult(Object result, WrapNode wrapNode, SymbolToIDNode symbolToIDNode,
                WrapperToHandleNode wrapperToHandleNode) {
            return switch (spec.returnCarrier) {
                case VOID -> Nil.INSTANCE;
                case VALUE -> wrapperToHandleNode.execute(this, wrapNode.execute(result));
                case WRAPPED, ID -> {
                    final Object value = spec.returnCarrier == Carrier.WRAPPED
                            ? result
                            : symbolToIDNode.execute(result);
                    yield value instanceof Long longValue
                            ? longValue
                            : wrapperToHandleNode.execute(this, (ValueWrapper) value);
                }
                case INT -> switch (result) {
                    case Integer intValue -> intValue;
                    case Long longValue -> (int) (long) longValue;
                    default -> throw unexpectedValue(result);
                };
                case LONG, POINTER -> switch (result) {
                    case Long longValue -> longValue;
                    case Integer intValue -> (long) intValue;
                    default -> throw unexpectedValue(result);
                };
                case DOUBLE -> (double) result;
                case BOOL -> (boolean) result ? 1 : 0;
            };
        }

        private RuntimeException unexpectedValue(Object result) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return unexpectedValueException(result);
        }

        @TruffleBoundary
        private RuntimeException unexpectedValueException(Object result) {
            throw CompilerDirectives.shouldNotReachHere(
                    "unexpected value for carrier " + spec.returnCarrier + " of " + spec.rubyName + ": " + result);
        }
    }

}
