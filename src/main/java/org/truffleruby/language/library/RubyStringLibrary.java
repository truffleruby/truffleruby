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
package org.truffleruby.language.library;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.InlineSupport;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;

/** A profiling utility for {@link RubyString} and {@link ImmutableRubyString} and the encoding used. Profiling the
 * encoding can help speedup {@link TruffleString} operations. Profiling the string classes helps to simplify PE code
 * and avoid branches. */
public abstract class RubyStringLibrary {

    public static RubyStringLibrary getUncached() {
        CompilerAsserts.neverPartOfCompilation("uncached libraries must not be used in PE code");
        return Uncached.INSTANCE;
    }

    /** Used to create separate specialization instances for RubyString and ImmutableRubyString */
    public abstract boolean seen(Node node, Object object);

    public abstract boolean isRubyString(Node node, Object object);

    @NeverDefault
    public abstract AbstractTruffleString getTString(Node node, Object object);

    @NeverDefault
    public abstract RubyEncoding getEncoding(Node node, Object object);

    @NeverDefault
    public final TruffleString.Encoding getTEncoding(Node node, Object object) {
        return getEncoding(node, object).tencoding;
    }

    public abstract int byteLength(Node node, Object object);

    public abstract RubyEncoding profileEncoding(Node node, RubyEncoding encoding);

    public static RubyStringLibrary inline(
            @RequiredField(value = InlineSupport.StateField.class,
                    bits = Cached.REQUIRED_STATE_BITS) InlineTarget target) {
        return new Cached(target);
    }

    static final class Cached extends RubyStringLibrary {

        private static final int REQUIRED_STATE_BITS = 6;

        private static final int ENCODING_MASK = 0b111;
        // 0b000 uninitialized 0b001 BINARY 0b010 UTF-8 0b011 US-ASCII
        private static final int GENERIC = 0b100;

        private static final int SEEN_MUTABLE = 0b1 << 3;
        private static final int SEEN_IMMUTABLE = 0b10 << 3;
        private static final int SEEN_OTHER = 0b100 << 3;

        private final InlineSupport.StateField stateField;

        Cached(InlineTarget target) {
            this.stateField = target.getState(0, REQUIRED_STATE_BITS);
        }

        @Override
        public boolean seen(Node node, Object object) {
            assert object instanceof RubyString || object instanceof ImmutableRubyString;
            int state = stateField.get(node);
            if ((state & SEEN_MUTABLE) != 0) {
                return object instanceof RubyString;
            } else if ((state & SEEN_IMMUTABLE) != 0) {
                return object instanceof ImmutableRubyString;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTString(node, object); // specialize
                return true;
            }
        }

        @Override
        public boolean isRubyString(Node node, Object object) {
            int state = stateField.get(node);
            if ((state & SEEN_MUTABLE) != 0 && object instanceof RubyString) {
                return true;
            } else if ((state & SEEN_IMMUTABLE) != 0 && object instanceof ImmutableRubyString) {
                return true;
            } else if ((state & SEEN_OTHER) != 0 && RubyGuards.isNotRubyString(object)) {
                return false;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            return specializeIsRubyString(node, object);
        }

        private boolean specializeIsRubyString(Node node, Object object) {
            int state = stateField.get(node);
            if (object instanceof RubyString) {
                stateField.set(node, state | SEEN_MUTABLE);
                return true;
            } else if (object instanceof ImmutableRubyString) {
                stateField.set(node, state | SEEN_IMMUTABLE);
                return true;
            } else if (RubyGuards.isNotRubyString(object)) {
                stateField.set(node, state | SEEN_OTHER);
                return false;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public AbstractTruffleString getTString(Node node, Object object) {
            int state = stateField.get(node);
            if ((state & SEEN_MUTABLE) != 0 && object instanceof RubyString) {
                return ((RubyString) object).tstring;
            } else if ((state & SEEN_IMMUTABLE) != 0 && object instanceof ImmutableRubyString) {
                return ((ImmutableRubyString) object).tstring;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            return specializeGetTString(node, object);
        }

        private AbstractTruffleString specializeGetTString(Node node, Object object) {
            int state = stateField.get(node);
            if (object instanceof RubyString) {
                stateField.set(node, state | SEEN_MUTABLE);
                return ((RubyString) object).tstring;
            } else if (object instanceof ImmutableRubyString) {
                stateField.set(node, state | SEEN_IMMUTABLE);
                return ((ImmutableRubyString) object).tstring;
            } else {
                var context = RubyContext.get(node);
                throw new RaiseException(context,
                        context.getCoreExceptions().typeErrorNoImplicitConversion(object, "String", node));
            }
        }

        @Override
        public RubyEncoding profileEncoding(Node node, RubyEncoding encoding) {
            int localCachedEncoding = stateField.get(node) & ENCODING_MASK;

            if (localCachedEncoding == GENERIC) {
                return encoding;
            } else if (encoding.index == localCachedEncoding - 1) {
                return Encodings.STANDARD_ENCODINGS[localCachedEncoding - 1];
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return specializeProfileEncoding(node, encoding);
            }
        }

        private RubyEncoding specializeProfileEncoding(Node node, RubyEncoding encoding) {
            int state = stateField.get(node);
            int localCachedEncoding = state & ENCODING_MASK;

            if (localCachedEncoding == 0) {
                if (Encodings.isStandardEncoding(encoding)) {
                    assert encoding.index >= 0 && encoding.index <= 2;
                    this.stateField.set(node, state | (encoding.index + 1));
                } else {
                    this.stateField.set(node, state | GENERIC);
                }
            } else if (encoding.index != localCachedEncoding - 1) {
                this.stateField.set(node, (state & ~ENCODING_MASK) | GENERIC);
            }
            return encoding;
        }

        @Override
        public RubyEncoding getEncoding(Node node, Object object) {
            int state = stateField.get(node);
            final RubyEncoding encoding;
            if ((state & SEEN_MUTABLE) != 0 && object instanceof RubyString) {
                encoding = ((RubyString) object).getEncodingUnprofiled();
            } else if ((state & SEEN_IMMUTABLE) != 0 && object instanceof ImmutableRubyString) {
                encoding = ((ImmutableRubyString) object).getEncodingUnprofiled();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return specializeGetEncoding(node, object);
            }

            return profileEncoding(node, encoding);
        }

        private RubyEncoding specializeGetEncoding(Node node, Object object) {
            int state = stateField.get(node);
            final RubyEncoding encoding;
            if (object instanceof RubyString) {
                stateField.set(node, state | SEEN_MUTABLE);
                encoding = ((RubyString) object).getEncodingUnprofiled();
            } else if (object instanceof ImmutableRubyString) {
                stateField.set(node, state | SEEN_IMMUTABLE);
                encoding = ((ImmutableRubyString) object).getEncodingUnprofiled();
            } else {
                var context = RubyContext.get(node);
                throw new RaiseException(context,
                        context.getCoreExceptions().typeErrorNoImplicitConversion(object, "String", node));
            }

            return specializeProfileEncoding(node, encoding);
        }

        @Override
        public int byteLength(Node node, Object object) {
            int state = stateField.get(node);
            if ((state & SEEN_MUTABLE) != 0 && object instanceof RubyString mutable) {
                return getTString(node, mutable).byteLength(getTEncoding(node, mutable));
            } else if ((state & SEEN_IMMUTABLE) != 0 && object instanceof ImmutableRubyString immutable) {
                return getTString(node, immutable).byteLength(getTEncoding(node, immutable));
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            return specializeByteLength(node, object);
        }

        private int specializeByteLength(Node node, Object object) {
            // getTString() and getTEncoding() will specialize as needed
            return getTString(node, object).byteLength(getTEncoding(node, object));
        }
    }

    static final class Uncached extends RubyStringLibrary {

        static final Uncached INSTANCE = new Uncached();

        @TruffleBoundary
        @Override
        public boolean seen(Node node, Object object) {
            assert object instanceof RubyString || object instanceof ImmutableRubyString;
            return true;
        }

        @TruffleBoundary
        @Override
        public boolean isRubyString(Node node, Object object) {
            return object instanceof RubyString || object instanceof ImmutableRubyString;
        }

        @TruffleBoundary
        @Override
        public AbstractTruffleString getTString(Node node, Object object) {
            if (object instanceof RubyString) {
                return ((RubyString) object).tstring;
            } else if (object instanceof ImmutableRubyString) {
                return ((ImmutableRubyString) object).tstring;
            } else {
                var context = RubyContext.get(node);
                throw new RaiseException(context,
                        context.getCoreExceptions().typeErrorNoImplicitConversion(object, "String", node));
            }
        }

        @TruffleBoundary
        @Override
        public RubyEncoding profileEncoding(Node node, RubyEncoding encoding) {
            return encoding;
        }

        @TruffleBoundary
        @Override
        public RubyEncoding getEncoding(Node node, Object object) {
            if (object instanceof RubyString) {
                return ((RubyString) object).getEncodingUncached();
            } else if (object instanceof ImmutableRubyString) {
                return ((ImmutableRubyString) object).getEncodingUncached();
            } else {
                var context = RubyContext.get(node);
                throw new RaiseException(context,
                        context.getCoreExceptions().typeErrorNoImplicitConversion(object, "String", node));
            }
        }

        @TruffleBoundary
        @Override
        public int byteLength(Node node, Object object) {
            return getTString(node, object).byteLength(getTEncoding(node, object));
        }
    }

    // Convenience static methods when there is no Node available.

    public static boolean isRubyStringUncached(Object object) {
        return getUncached().isRubyString(null, object);
    }

    public static AbstractTruffleString getTStringUncached(Object object) {
        return getUncached().getTString(null, object);
    }

    public static RubyEncoding getEncodingUncached(Object object) {
        return getUncached().getEncoding(null, object);
    }

}
