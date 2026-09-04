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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager.WrapperToHandleNode;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;

/** Executes one C extension upcall (see tool/cext-upcalls.rb) with cached nodes, so the argument and result conversions
 * and the dispatch to the Truffle::CExt method partial-evaluate. Called from the corresponding
 * {@link CExtUpcallTargets} method through {@link CExtFFMLayer#upcall(int, long[])}. The single frame argument is the
 * long[] of raw upcall arguments (doubles as raw bits), so the primitive arguments need no boxing: only the converted
 * Ruby call arguments are boxed where needed, and VALUE handles are unwrapped straight from the longs. */
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
        this.executeUpcall = new ExecuteUpcallNode(spec);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeUpcall.execute((long[]) frame.getArguments()[0]);
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

    public static final class ExecuteUpcallNode extends RubyBaseNode {

        private final String rubyName;
        /** Unwraps the receiver (the first argument, a VALUE) for {@link Kind#SEND}, null for {@link Kind#CEXT} */
        @Child private UpcallArgumentNode receiverNode;
        @Children private final UpcallArgumentNode[] argumentNodes;
        @Child private DispatchNode dispatchNode = DispatchNode.create();
        @Child private UpcallResultNode resultNode;

        ExecuteUpcallNode(UpcallSpec spec) {
            this.rubyName = spec.rubyName;
            final Carrier[] carriers = spec.argumentCarriers;
            final int argumentsOffset;
            if (spec.kind == Kind.SEND) {
                assert carriers[0] == Carrier.VALUE;
                this.receiverNode = UpcallArgumentNode.create(Carrier.VALUE);
                argumentsOffset = 1;
            } else {
                argumentsOffset = 0;
            }
            final var argumentNodes = new UpcallArgumentNode[carriers.length - argumentsOffset];
            for (int i = 0; i < argumentNodes.length; i++) {
                argumentNodes[i] = UpcallArgumentNode.create(carriers[argumentsOffset + i]);
            }
            this.argumentNodes = argumentNodes;
            this.resultNode = UpcallResultNode.create(spec.returnCarrier);
        }

        @ExplodeLoop
        public Object execute(long[] rawArguments) {
            final Object receiver;
            final int argumentsOffset;
            if (receiverNode != null) {
                receiver = receiverNode.execute(rawArguments[0]);
                argumentsOffset = 1;
            } else {
                receiver = coreLibrary().truffleCExtModule;
                argumentsOffset = 0;
            }

            final Object[] arguments = new Object[argumentNodes.length];
            for (int i = 0; i < arguments.length; i++) {
                int index = argumentsOffset + i;
                CompilerAsserts.partialEvaluationConstant(index);
                arguments[i] = argumentNodes[i].execute(rawArguments[index]);
            }

            final Object result = dispatchNode.call(DispatchConfiguration.PRIVATE, receiver, rubyName, arguments);

            return resultNode.execute(result);
        }
    }

    /** Converts one raw long upcall argument (see {@link CExtFFMLayer#upcall(int, long[])}) to the corresponding Ruby
     * value, per its {@link Carrier} */
    public abstract static class UpcallArgumentNode extends RubyBaseNode {

        static UpcallArgumentNode create(Carrier carrier) {
            return switch (carrier) {
                case VALUE -> CExtUpcallRootNodeFactory.ValueArgumentNodeGen.create();
                case INT -> new IntArgumentNode();
                case LONG, POINTER -> new LongArgumentNode();
                case DOUBLE -> new DoubleArgumentNode();
                case ID -> CExtUpcallRootNodeFactory.IDArgumentNodeGen.create();
                default -> throw CompilerDirectives.shouldNotReachHere(carrier.name());
            };
        }

        public abstract Object execute(long rawArgument);
    }

    static final class LongArgumentNode extends UpcallArgumentNode {
        @Override
        public Object execute(long rawArgument) {
            return rawArgument;
        }
    }

    static final class IntArgumentNode extends UpcallArgumentNode {
        @Override
        public Object execute(long rawArgument) {
            return (int) rawArgument;
        }
    }

    static final class DoubleArgumentNode extends UpcallArgumentNode {
        @Override
        public Object execute(long rawArgument) {
            return Double.longBitsToDouble(rawArgument);
        }
    }

    abstract static class ValueArgumentNode extends UpcallArgumentNode {
        @Specialization
        static Object unwrap(long rawArgument,
                @Bind Node node,
                @Cached UnwrapNode unwrapNode) {
            return unwrapNode.execute(node, rawArgument);
        }
    }

    abstract static class IDArgumentNode extends UpcallArgumentNode {
        @Specialization
        static RubySymbol idToSymbol(long rawArgument,
                @Cached IDToSymbolNode idToSymbolNode) {
            return idToSymbolNode.execute(rawArgument);
        }
    }

    /** Converts the Ruby result of an upcall to the raw value the FFM upcall stub returns, per the return
     * {@link Carrier}. The Truffle::CExt methods only return values these conversions handle, no need for anything
     * fancier like InteropLibrary here. */
    public abstract static class UpcallResultNode extends RubyBaseNode {

        static UpcallResultNode create(Carrier carrier) {
            return switch (carrier) {
                case VOID -> new VoidResultNode();
                case VALUE -> CExtUpcallRootNodeFactory.ValueResultNodeGen.create();
                case WRAPPED -> CExtUpcallRootNodeFactory.WrappedResultNodeGen.create();
                case ID -> CExtUpcallRootNodeFactory.IDResultNodeGen.create();
                case INT -> CExtUpcallRootNodeFactory.IntResultNodeGen.create();
                case LONG, POINTER -> CExtUpcallRootNodeFactory.LongResultNodeGen.create();
                case DOUBLE -> CExtUpcallRootNodeFactory.DoubleResultNodeGen.create();
                case BOOL -> CExtUpcallRootNodeFactory.BoolResultNodeGen.create();
            };
        }

        public abstract Object execute(Object value);
    }

    static final class VoidResultNode extends UpcallResultNode {
        @Override
        public Object execute(Object value) {
            return Nil.INSTANCE;
        }
    }

    abstract static class ValueResultNode extends UpcallResultNode {
        @Specialization
        static long wrap(Object value,
                @Bind Node node,
                @Cached WrapNode wrapNode,
                @Cached WrapperToHandleNode wrapperToHandleNode) {
            return wrapperToHandleNode.execute(node, wrapNode.execute(value));
        }
    }

    abstract static class WrappedResultNode extends UpcallResultNode {
        @Specialization
        static long handle(long value) {
            return value;
        }

        @Specialization
        static long wrapperToHandle(ValueWrapper value,
                @Bind Node node,
                @Cached WrapperToHandleNode wrapperToHandleNode) {
            return wrapperToHandleNode.execute(node, value);
        }
    }

    abstract static class IDResultNode extends UpcallResultNode {
        @Specialization
        static Object symbolToID(Object value,
                @Bind Node node,
                @Cached SymbolToIDNode symbolToIDNode,
                @Cached WrapperToHandleNode wrapperToHandleNode,
                @Cached InlinedConditionProfile longProfile) {
            final Object id = symbolToIDNode.execute(value);
            if (longProfile.profile(node, id instanceof Long)) {
                return id;
            } else {
                return wrapperToHandleNode.execute(node, (ValueWrapper) id);
            }
        }
    }

    abstract static class IntResultNode extends UpcallResultNode {
        @Specialization
        static int doInt(int value) {
            return value;
        }

        @Specialization
        static int doLong(long value) {
            return (int) value;
        }
    }

    abstract static class LongResultNode extends UpcallResultNode {
        /** Also accepts int results through the implicit int-to-long cast of RubyTypes */
        @Specialization
        static long doLong(long value) {
            return value;
        }
    }

    abstract static class DoubleResultNode extends UpcallResultNode {
        @Specialization
        static double doDouble(double value) {
            return value;
        }
    }

    abstract static class BoolResultNode extends UpcallResultNode {
        @Specialization
        static int doBoolean(boolean value) {
            return value ? 1 : 0;
        }
    }

}
