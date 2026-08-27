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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager.AllocateHandleNode;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.backtrace.InternalRootNode;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;

/** Executes one C extension upcall (see tool/cext-upcalls.rb) with cached nodes, so the argument and result conversions
 * and the dispatch to the Truffle::CExt method partial-evaluate. Called from the corresponding
 * {@link CExtUpcallTargets} method through {@link CExtFFMLayer#upcall(int, Object...)}. The frame arguments are the raw
 * boxed primitive upcall arguments. */
public final class CExtUpcallRootNode extends RubyBaseRootNode implements InternalRootNode {

    /** The kind of a native value in an upcall signature, from the carrier letters in tool/cext-upcalls.rb */
    public enum Carrier {
        VALUE, // V: a VALUE handle, converted with UnwrapNode/WrapNode
        WRAPPED, // W: a result which is already a VALUE handle or a ValueWrapper
        INT, // I
        BOOL, // B: Ruby true/false as int 1/0
        LONG, // L
        DOUBLE, // D
        POINTER, // P
        FUNCTION, // F: a function pointer
        ID, // Y: converted from/to a Ruby Symbol
        VALUE_ARRAY, // A: a VALUE* and its length, passed as two native arguments
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
                case 'F' -> FUNCTION;
                case 'Y' -> ID;
                case 'A' -> VALUE_ARRAY;
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
        SEND,
        /** call a dynamically-named method: the receiver handle and the method name pointer are passed before the
         * arguments and are not part of {@code argumentCarriers} */
        INVOKE
    }

    /** Parsed form of a CExtUpcallTargets.UPCALLS entry: the kind, Ruby method name, return carrier and argument
     * carrier letters strings. */
    public static final class UpcallSpec {
        final Kind kind;
        final String rubyName;
        final Carrier returnCarrier;
        @CompilationFinal(dimensions = 1) final Carrier[] argumentCarriers;
        /** index of each converted argument in the raw arguments (VALUE_ARRAY carriers take two raw arguments) */
        @CompilationFinal(dimensions = 1) final int[] rawIndices;

        public static UpcallSpec parse(String kindString, String rubyName, String returnCarrierString, String args) {
            final Kind kind = switch (kindString) {
                case "cext" -> Kind.CEXT;
                case "send" -> Kind.SEND;
                case "invoke" -> Kind.INVOKE;
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
            this.rawIndices = new int[argumentCarriers.length];
            int raw = kind == Kind.INVOKE ? 2 : 0;
            for (int i = 0; i < argumentCarriers.length; i++) {
                rawIndices[i] = raw;
                raw += argumentCarriers[i] == Carrier.VALUE_ARRAY ? 2 : 1;
            }
        }
    }

    private final String name;
    @Child private ExecuteUpcallNode executeUpcall;

    public CExtUpcallRootNode(RubyLanguage language, CExtFFMLayer layer, String name, UpcallSpec spec) {
        super(language, language.EMPTY_DECLARATION_DESCRIPTOR, null);
        this.name = name;
        this.executeUpcall = CExtUpcallRootNodeFactory.ExecuteUpcallNodeGen.create(layer, spec);
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

        private final CExtFFMLayer layer;
        protected final UpcallSpec spec;

        protected ExecuteUpcallNode(CExtFFMLayer layer, UpcallSpec spec) {
            this.layer = layer;
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
                @Cached AllocateHandleNode allocateHandleNode,
                @Cached KeepAliveNode keepAliveNode,
                @Cached InlinedBranchProfile createHandleProfile,
                @Cached InlinedBranchProfile taggedObjectProfile) {
            final Object receiver;
            final String methodName;
            final int argumentsOffset;
            switch (spec.kind) {
                case CEXT -> {
                    receiver = coreLibrary().truffleCExtModule;
                    methodName = spec.rubyName;
                    argumentsOffset = 0;
                }
                case SEND -> {
                    receiver = unwrapNode.execute(this, rawArguments[0]);
                    methodName = spec.rubyName;
                    argumentsOffset = 1;
                }
                case INVOKE -> {
                    receiver = unwrapNode.execute(this, rawArguments[0]);
                    methodName = layer.readMethodName((long) rawArguments[1]);
                    argumentsOffset = 0;
                }
                default -> throw CompilerDirectives.shouldNotReachHere();
            }

            final Object[] arguments = new Object[spec.argumentCarriers.length - argumentsOffset];
            for (int i = 0; i < arguments.length; i++) {
                final int carrierIndex = argumentsOffset + i;
                arguments[i] = convertArgument(spec.argumentCarriers[carrierIndex], spec.rawIndices[carrierIndex],
                        rawArguments, unwrapNode, idToSymbolNode);
            }

            final Object result = dispatchNode.call(DispatchConfiguration.PRIVATE, receiver, methodName, arguments);

            return convertResult(result, wrapNode, symbolToIDNode, allocateHandleNode, keepAliveNode,
                    createHandleProfile, taggedObjectProfile);
        }

        private Object convertArgument(Carrier carrier, int rawIndex, Object[] rawArguments,
                UnwrapNode unwrapNode, IDToSymbolNode idToSymbolNode) {
            final Object raw = rawArguments[rawIndex];
            return switch (carrier) {
                case VALUE -> unwrapNode.execute(this, raw);
                case INT, LONG, DOUBLE -> raw;
                case ID -> idToSymbolNode.execute(raw);
                case POINTER, FUNCTION -> pointer((long) raw);
                case VALUE_ARRAY -> new NativeValueArray(getContext(), (long) raw,
                        (int) (long) rawArguments[rawIndex + 1]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        private Object pointer(long address) {
            if (address == 0) {
                return Nil.INSTANCE;
            }
            return new RubyPointer(
                    coreLibrary().truffleFFIPointerClass,
                    getLanguage().truffleFFIPointerShape,
                    new Pointer(getContext(), address));
        }

        /** The Truffle::CExt methods only return values these conversions handle, no need for anything fancier like
         * InteropLibrary here. */
        private Object convertResult(Object result, WrapNode wrapNode, SymbolToIDNode symbolToIDNode,
                AllocateHandleNode allocateHandleNode, KeepAliveNode keepAliveNode,
                InlinedBranchProfile createHandleProfile, InlinedBranchProfile taggedObjectProfile) {
            return switch (spec.returnCarrier) {
                case VOID -> Nil.INSTANCE;
                case VALUE -> wrapperToHandle(wrapNode.execute(result), allocateHandleNode, keepAliveNode,
                        createHandleProfile, taggedObjectProfile);
                case WRAPPED, ID -> {
                    final Object value = spec.returnCarrier == Carrier.WRAPPED
                            ? result
                            : symbolToIDNode.execute(result);
                    yield value instanceof Long longValue
                            ? longValue
                            : wrapperToHandle((ValueWrapper) value, allocateHandleNode, keepAliveNode,
                                    createHandleProfile, taggedObjectProfile);
                }
                case INT -> switch (result) {
                    case Integer intValue -> intValue;
                    case Long longValue -> (int) (long) longValue;
                    default -> throw unexpectedValue(result);
                };
                case BOOL -> (boolean) result ? 1 : 0;
                case LONG -> switch (result) {
                    case Long longValue -> longValue;
                    case Integer intValue -> (long) intValue;
                    default -> throw unexpectedValue(result);
                };
                case DOUBLE -> (double) result;
                case POINTER, FUNCTION -> switch (result) {
                    case Nil _ -> 0L;
                    case Long longValue -> longValue;
                    case RubyPointer rubyPointer -> rubyPointer.pointer.getAddress();
                    default -> throw unexpectedValue(result);
                };
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }

        /** The same conversion as ValueWrapper's toNative and asPointer interop messages */
        private long wrapperToHandle(ValueWrapper wrapper, AllocateHandleNode allocateHandleNode,
                KeepAliveNode keepAliveNode,
                InlinedBranchProfile createHandleProfile, InlinedBranchProfile taggedObjectProfile) {
            long handle = wrapper.getHandle();
            if (handle == ValueWrapperManager.UNSET_HANDLE) {
                createHandleProfile.enter(this);
                handle = allocateHandleNode.execute(this, wrapper);
            }
            if (ValueWrapperManager.isTaggedObject(handle)) {
                taggedObjectProfile.enter(this);
                keepAliveNode.execute(this, wrapper);
            }
            return handle;
        }

        @TruffleBoundary
        private RuntimeException unexpectedValue(Object result) {
            throw CompilerDirectives.shouldNotReachHere(
                    "unexpected value for carrier " + spec.returnCarrier + " of " + spec.rubyName + ": " + result);
        }
    }

}
