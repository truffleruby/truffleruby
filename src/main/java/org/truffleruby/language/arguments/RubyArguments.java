/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.FrameOnStackMarker;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/** Helper methods to build "frame arguments" from the various values which go into it. By convention, Object[] which
 * are frame arguments are named {@code rubyArgs} or {@code frameArguments}. All other usages of "arguments" mean user
 * arguments, i.e., the arguments to a method call like foo(*arguments). */
public final class RubyArguments {

    private enum ArgumentIndicies {
        DECLARATION_FRAME,          // 0 MaterializedFrame or null
        CALLER_SPECIAL_VARIABLES,   // 1 SpecialVariableStorage or null
        METHOD,                     // 2 InternalMethod
        DECLARATION_CONTEXT,        // 3 DeclarationContext
        FRAME_ON_STACK_MARKER,      // 4 FrameOnStackMarker or null
        SELF,                       // 5 RubyGuards.assertIsValidRubyValue
        BLOCK,                      // 6 RubyProc or Nil
        DESCRIPTOR                  // 7 ArgumentsDescriptor
        // user arguments follow, each RubyGuards.assertIsValidRubyValue
    }

    static final int RUNTIME_ARGUMENT_COUNT = ArgumentIndicies.values().length;

    public static boolean assertFrameArguments(Object[] rubyArgs) {
        assert rubyArgs.length >= RUNTIME_ARGUMENT_COUNT;

        final Object declarationFrame = rubyArgs[ArgumentIndicies.DECLARATION_FRAME.ordinal()];
        assert declarationFrame == null || declarationFrame instanceof MaterializedFrame : declarationFrame;

        final Object callerVariables = rubyArgs[ArgumentIndicies.CALLER_SPECIAL_VARIABLES.ordinal()];
        assert callerVariables == null || callerVariables instanceof SpecialVariableStorage : callerVariables;

        final Object internalMethod = rubyArgs[ArgumentIndicies.METHOD.ordinal()];
        assert internalMethod instanceof InternalMethod : internalMethod;

        final Object declarationContext = rubyArgs[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()];
        assert declarationContext instanceof DeclarationContext : declarationContext;

        final Object frameOnStackMarker = rubyArgs[ArgumentIndicies.FRAME_ON_STACK_MARKER.ordinal()];
        assert frameOnStackMarker == null || frameOnStackMarker instanceof FrameOnStackMarker : frameOnStackMarker;

        assert RubyGuards.assertIsValidRubyValue(rubyArgs[ArgumentIndicies.SELF.ordinal()]);

        final Object block = rubyArgs[ArgumentIndicies.BLOCK.ordinal()];
        assert block instanceof RubyProc || block == Nil.INSTANCE : block;

        Object descriptor = rubyArgs[ArgumentIndicies.DESCRIPTOR.ordinal()];
        assert descriptor instanceof ArgumentsDescriptor : descriptor;

        if (descriptor instanceof KeywordArgumentsDescriptor) {
            final Object lastArgument = getLastArgument(rubyArgs);
            assert lastArgument instanceof RubyHash : "the last frame argument must be a Hash with a kwargs descriptor";
            assert !((RubyHash) lastArgument).empty() : "empty kwargs should not have been passed";
        }

        final int userArgumentsCount = rubyArgs.length - RUNTIME_ARGUMENT_COUNT;
        assert ArrayUtils.assertValidElements(rubyArgs, RUNTIME_ARGUMENT_COUNT, userArgumentsCount);

        return true;
    }

    /** In most cases the DeclarationContext is the one of the InternalMethod. */
    public static Object[] pack(
            MaterializedFrame declarationFrame,
            SpecialVariableStorage callerSpecialVariables,
            InternalMethod method,
            FrameOnStackMarker frameOnStackMarker,
            Object self,
            Object block,
            ArgumentsDescriptor descriptor,
            Object[] arguments) {
        final DeclarationContext declarationContext = method.getDeclarationContext();
        return pack(declarationFrame, callerSpecialVariables, method, declarationContext, frameOnStackMarker, self,
                block, descriptor, arguments);
    }

    public static Object[] pack(
            MaterializedFrame declarationFrame,
            SpecialVariableStorage callerSpecialVariables,
            InternalMethod method,
            DeclarationContext declarationContext,
            FrameOnStackMarker frameOnStackMarker,
            Object self,
            Object block,
            ArgumentsDescriptor descriptor,
            Object[] arguments) {
        final Object[] rubyArgs = new Object[RUNTIME_ARGUMENT_COUNT + arguments.length];

        rubyArgs[ArgumentIndicies.DECLARATION_FRAME.ordinal()] = declarationFrame;
        rubyArgs[ArgumentIndicies.CALLER_SPECIAL_VARIABLES.ordinal()] = callerSpecialVariables;
        rubyArgs[ArgumentIndicies.METHOD.ordinal()] = method;
        rubyArgs[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()] = declarationContext;
        rubyArgs[ArgumentIndicies.FRAME_ON_STACK_MARKER.ordinal()] = frameOnStackMarker;
        rubyArgs[ArgumentIndicies.SELF.ordinal()] = self;
        rubyArgs[ArgumentIndicies.BLOCK.ordinal()] = block;
        rubyArgs[ArgumentIndicies.DESCRIPTOR.ordinal()] = descriptor;

        ArrayUtils.arraycopy(arguments, 0, rubyArgs, RUNTIME_ARGUMENT_COUNT, arguments.length);

        assert assertFrameArguments(rubyArgs);

        return rubyArgs;
    }

    public static Object[] allocate(int count) {
        return new Object[RUNTIME_ARGUMENT_COUNT + count];
    }

    public static Object[] repack(Object[] rubyArgs, Object receiver) {
        // Duplicate logic for this case since it is significantly simpler
        final Object[] newArgs = new Object[rubyArgs.length];
        newArgs[ArgumentIndicies.SELF.ordinal()] = receiver;
        newArgs[ArgumentIndicies.BLOCK.ordinal()] = getBlock(rubyArgs);
        newArgs[ArgumentIndicies.DESCRIPTOR.ordinal()] = getDescriptor(rubyArgs);
        int count = rubyArgs.length - RUNTIME_ARGUMENT_COUNT;
        System.arraycopy(rubyArgs, RUNTIME_ARGUMENT_COUNT, newArgs, RUNTIME_ARGUMENT_COUNT, count);
        return newArgs;
    }

    public static Object[] repack(Object[] rubyArgs, Object receiver, int from) {
        return repack(rubyArgs, receiver, from, 0);
    }

    /** Same as
     * {@code pack(null, null, null, null, receiver, getBlock(rubyArgs), getDescriptor(rubyArgs), getRawArguments(rubyArgs))}
     * but without the intermediary Object[] allocation and arraycopy. */
    public static Object[] repack(Object[] rubyArgs, Object receiver, int from, int to) {
        final int count = getRawArgumentsCount(rubyArgs) - from;
        final Object[] newArgs = new Object[RUNTIME_ARGUMENT_COUNT + to + count];
        newArgs[ArgumentIndicies.SELF.ordinal()] = receiver;
        newArgs[ArgumentIndicies.BLOCK.ordinal()] = getBlock(rubyArgs);
        newArgs[ArgumentIndicies.DESCRIPTOR.ordinal()] = getDescriptor(rubyArgs);
        System.arraycopy(rubyArgs, RUNTIME_ARGUMENT_COUNT + from, newArgs, RUNTIME_ARGUMENT_COUNT + to, count);
        return newArgs;
    }

    /** Clone the argument array before making a call in PE code, if its length is PE constant. This is done to handle
     * the case where two methods might be called from a single call site (a polymorphic call site) but Truffle has only
     * inlined one of the callees. Cloning the arguments allows these two uses of the arguments to have different
     * lifetimes, and hence be escape analysed separately (otherwise the array would always escape, even for the inlined
     * callee). For the case none of the callees are inlined, this has a separate allocation for each callee which might
     * not look optimal in the graph but is still only one allocation per call at that call site. */
    public static Object[] repackForCall(Object[] rubyArgs) {
        return (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(rubyArgs.length))
                ? rubyArgs.clone()
                : rubyArgs;
    }

    // Getters

    public static MaterializedFrame getDeclarationFrame(Frame frame) {
        return (MaterializedFrame) frame.getArguments()[ArgumentIndicies.DECLARATION_FRAME.ordinal()];
    }

    public static void setDeclarationFrame(Frame frame, MaterializedFrame declarationFrame) {
        frame.getArguments()[ArgumentIndicies.DECLARATION_FRAME.ordinal()] = declarationFrame;
    }

    public static void setCallerSpecialVariables(Object[] args, SpecialVariableStorage callerSpecialVariables) {
        args[ArgumentIndicies.CALLER_SPECIAL_VARIABLES.ordinal()] = callerSpecialVariables;
    }

    public static SpecialVariableStorage getCallerSpecialVariables(Frame frame) {
        return (SpecialVariableStorage) frame.getArguments()[ArgumentIndicies.CALLER_SPECIAL_VARIABLES.ordinal()];
    }

    public static InternalMethod getMethod(Object[] args) {
        return (InternalMethod) args[ArgumentIndicies.METHOD.ordinal()];
    }

    public static InternalMethod getMethod(Frame frame) {
        return (InternalMethod) frame.getArguments()[ArgumentIndicies.METHOD.ordinal()];
    }

    public static void setMethod(Object[] args, InternalMethod method) {
        args[ArgumentIndicies.METHOD.ordinal()] = method;
        args[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()] = method.getDeclarationContext();
    }

    public static DeclarationContext getDeclarationContext(Frame frame) {
        return (DeclarationContext) frame.getArguments()[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()];
    }

    public static DeclarationContext getDeclarationContext(Object[] rubyArgs) {
        return (DeclarationContext) rubyArgs[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()];
    }

    public static void setDeclarationContext(Frame frame, DeclarationContext declarationContext) {
        frame.getArguments()[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()] = declarationContext;
    }

    public static FrameOnStackMarker getFrameOnStackMarker(Frame frame) {
        return (FrameOnStackMarker) frame.getArguments()[ArgumentIndicies.FRAME_ON_STACK_MARKER.ordinal()];
    }

    public static Object getSelf(Object[] args) {
        return args[ArgumentIndicies.SELF.ordinal()];
    }

    /** Should only be used when we just allocated the Object[] and we know nothing else is using it. Consider
     * {@link #repack} instead. */
    public static void setSelf(Object[] args, Object self) {
        args[ArgumentIndicies.SELF.ordinal()] = self;
    }

    public static Object getSelf(Frame frame) {
        return frame.getArguments()[ArgumentIndicies.SELF.ordinal()];
    }

    public static Object getBlock(Object[] args) {
        final Object block = args[ArgumentIndicies.BLOCK.ordinal()];
        /* We put into the frame arguments either a Nil or RubyProc, so that's all we'll get out at this point. */
        assert block instanceof Nil || block instanceof RubyProc : StringUtils.toString(block);
        return block;
    }

    public static void setBlock(Object[] args, Object block) {
        // We put into the frame arguments either a Nil or RubyProc.
        assert block instanceof Nil || block instanceof RubyProc : StringUtils.toString(block);
        args[ArgumentIndicies.BLOCK.ordinal()] = block;
    }

    public static Object getBlock(Frame frame) {
        final Object block = frame.getArguments()[ArgumentIndicies.BLOCK.ordinal()];
        /* We put into the frame arguments either a Nil or RubyProc, so that's all we'll get out at this point. */
        assert block instanceof Nil || block instanceof RubyProc : StringUtils.toString(block);
        return block;
    }

    public static void setDescriptor(Object[] args, ArgumentsDescriptor descriptor) {
        assert descriptor != null;
        args[ArgumentIndicies.DESCRIPTOR.ordinal()] = descriptor;
    }

    public static ArgumentsDescriptor getDescriptor(Frame frame) {
        return getDescriptor(frame.getArguments());
    }

    public static ArgumentsDescriptor getDescriptor(Object[] args) {
        final Object descriptor = args[ArgumentIndicies.DESCRIPTOR.ordinal()];
        assert descriptor instanceof ArgumentsDescriptor : descriptor;
        return (ArgumentsDescriptor) descriptor;
    }

    /** Get the raw number of user arguments inside the frame arguments, which might include keyword arguments */
    public static int getRawArgumentsCount(Frame frame) {
        return frame.getArguments().length - RUNTIME_ARGUMENT_COUNT;
    }

    /** Get the raw number of user arguments inside the frame arguments, which might include keyword arguments */
    public static int getRawArgumentsCount(Object[] rubyArgs) {
        return rubyArgs.length - RUNTIME_ARGUMENT_COUNT;
    }

    /** Get the number of positional user arguments inside the frame arguments */
    public static int getPositionalArgumentsCount(Frame frame, boolean methodHasKeywordParameters) {
        return getPositionalArgumentsCount(frame.getArguments(), methodHasKeywordParameters);
    }

    /** Get the number of positional user arguments inside the frame arguments */
    public static int getPositionalArgumentsCount(Object[] rubyArgs, boolean methodHasKeywordParameters) {
        CompilerAsserts.partialEvaluationConstant(methodHasKeywordParameters);
        final int argumentsCount = rubyArgs.length - RUNTIME_ARGUMENT_COUNT;

        if (methodHasKeywordParameters && getDescriptor(rubyArgs) instanceof KeywordArgumentsDescriptor) {
            return argumentsCount - 1; // the last argument is kwargs
        } else {
            return argumentsCount;
        }
    }

    /** Get the number of positional user arguments inside the frame arguments. Assumes
     * methodHasKeywordParameters=false. */
    public static int getPositionalArgumentsCount(Object[] rubyArgs) {
        return rubyArgs.length - RUNTIME_ARGUMENT_COUNT;
    }

    /** Get the user argument at given index out of frame arguments */
    public static Object getArgument(Frame frame, int index) {
        assert index >= 0 && index < getRawArgumentsCount(frame);
        return frame.getArguments()[RUNTIME_ARGUMENT_COUNT + index];
    }

    /** Get the user argument at given index out of frame arguments */
    public static Object getArgument(Object[] rubyArgs, int index) {
        assert index >= 0 && index < getRawArgumentsCount(rubyArgs);
        return rubyArgs[RUNTIME_ARGUMENT_COUNT + index];
    }

    public static Object getLastArgument(Frame frame) {
        return getLastArgument(frame.getArguments());
    }

    public static Object getLastArgument(Object[] rubyArgs) {
        assert getRawArgumentsCount(rubyArgs) > 0;
        return rubyArgs[rubyArgs.length - 1];
    }

    public static void setLastArgument(Frame frame, Object value) {
        setLastArgument(frame.getArguments(), value);
    }

    public static void setLastArgument(Object[] rubyArgs, Object value) {
        assert getRawArgumentsCount(rubyArgs) > 0;
        rubyArgs[rubyArgs.length - 1] = value;
    }

    /** Set the user argument at given index inside frame arguments */
    public static void setArgument(Object[] rubyArgs, int index, Object value) {
        assert index >= 0 && index < getRawArgumentsCount(rubyArgs);
        rubyArgs[RUNTIME_ARGUMENT_COUNT + index] = value;
    }

    /** Get the positional user arguments out of frame arguments. Should only be used when strictly necessary,
     * {@link #repack} or {@link #getArgument} avoid the extra allocation. Assumes methodHasKeywordParameters=false. */
    public static Object[] getPositionalArguments(Object[] rubyArgs) {
        int count = getPositionalArgumentsCount(rubyArgs);
        return ArrayUtils.extractRange(rubyArgs, RUNTIME_ARGUMENT_COUNT, RUNTIME_ARGUMENT_COUNT + count);
    }

    /** Get the user arguments out of frame arguments, from start to start+length. Only used by *rest arg nodes. */
    public static Object[] getRawArguments(Frame frame, int start, int length) {
        Object[] rubyArgs = frame.getArguments();
        return ArrayUtils.extractRange(rubyArgs, RUNTIME_ARGUMENT_COUNT + start,
                RUNTIME_ARGUMENT_COUNT + start + length);
    }

    /** Get the user arguments and also potentially the keyword Hash, this should only be used for delegation, and the
     * descriptor should be preserved as well. */
    public static Object[] getRawArguments(Object[] rubyArgs) {
        return ArrayUtils.extractRange(rubyArgs, RUNTIME_ARGUMENT_COUNT, rubyArgs.length);
    }

    /** Get the user arguments and also potentially the keyword Hash, this should only be used for delegation, and the
     * descriptor should be preserved as well. */
    public static Object[] getRawArguments(Frame frame) {
        Object[] rubyArgs = frame.getArguments();
        return ArrayUtils.extractRange(rubyArgs, RUNTIME_ARGUMENT_COUNT, rubyArgs.length);
    }

    public static void setArguments(Object[] rubyArgs, Object[] arguments) {
        ArrayUtils.arraycopy(arguments, 0, rubyArgs, RUNTIME_ARGUMENT_COUNT, arguments.length);
    }

    // Getters for the declaration frame that let you reach up several levels

    public static Frame getDeclarationFrame(Frame topFrame, int level) {
        assert topFrame != null;
        assert level >= 0;

        CompilerAsserts.partialEvaluationConstant(level);
        if (level == 0) {
            return topFrame;
        } else {
            return getDeclarationFrame(RubyArguments.getDeclarationFrame(topFrame), level - 1);
        }
    }

    public static MaterializedFrame getDeclarationFrame(MaterializedFrame frame, int level) {
        assert frame != null;
        assert level >= 0;

        CompilerAsserts.partialEvaluationConstant(level);
        return (CompilerDirectives.inCompiledCode() && level <= RubyBaseNode.MAX_EXPLODE_SIZE)
                ? getDeclarationFrameExplode(frame, level)
                : getDeclarationFrameLoop(frame, level);
    }

    @ExplodeLoop
    private static MaterializedFrame getDeclarationFrameExplode(MaterializedFrame frame, int level) {
        for (int n = 0; n < level; n++) {
            frame = RubyArguments.getDeclarationFrame(frame);
        }
        return frame;
    }

    private static MaterializedFrame getDeclarationFrameLoop(MaterializedFrame frame, int level) {
        for (int n = 0; n < level; n++) {
            frame = RubyArguments.getDeclarationFrame(frame);
        }
        return frame;
    }

    // Getters that fail safely for when you aren't even sure if this is a Ruby frame

    public static MaterializedFrame tryGetDeclarationFrame(Frame frame) {
        if (ArgumentIndicies.DECLARATION_FRAME.ordinal() >= frame.getArguments().length) {
            return null;
        }

        final Object declarationFrame = frame.getArguments()[ArgumentIndicies.DECLARATION_FRAME.ordinal()];

        if (declarationFrame instanceof MaterializedFrame) {
            return (MaterializedFrame) declarationFrame;
        }

        return null;
    }

    public static Object tryGetSelf(Frame frame) {
        if (ArgumentIndicies.SELF.ordinal() >= frame.getArguments().length) {
            return null;
        }

        return frame.getArguments()[ArgumentIndicies.SELF.ordinal()];
    }

    public static RubyProc tryGetBlock(Frame frame) {
        if (ArgumentIndicies.BLOCK.ordinal() >= frame.getArguments().length) {
            return null;
        }

        Object proc = frame.getArguments()[ArgumentIndicies.BLOCK.ordinal()];
        return proc instanceof RubyProc ? (RubyProc) proc : null;
    }

    public static InternalMethod tryGetMethod(Frame frame) {
        if (ArgumentIndicies.METHOD.ordinal() >= frame.getArguments().length) {
            return null;
        }

        final Object method = frame.getArguments()[ArgumentIndicies.METHOD.ordinal()];

        if (method instanceof InternalMethod) {
            return (InternalMethod) method;
        }

        return null;
    }

    public static DeclarationContext tryGetDeclarationContext(Frame frame) {
        if (frame == null) {
            return null;
        }

        if (ArgumentIndicies.DECLARATION_CONTEXT.ordinal() >= frame.getArguments().length) {
            return null;
        }

        final Object declarationContext = frame.getArguments()[ArgumentIndicies.DECLARATION_CONTEXT.ordinal()];

        if (declarationContext instanceof DeclarationContext) {
            return (DeclarationContext) declarationContext;
        }

        return null;
    }

}
