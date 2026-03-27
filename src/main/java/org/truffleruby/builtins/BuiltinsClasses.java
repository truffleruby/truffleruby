/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.builtins;

import java.util.Arrays;
import java.util.List;

import org.truffleruby.cext.CExtNodesBuiltins;
import org.truffleruby.cext.CExtNodesFactory;
import org.truffleruby.core.GCNodesBuiltins;
import org.truffleruby.core.GCNodesFactory;
import org.truffleruby.core.MainNodesBuiltins;
import org.truffleruby.core.MainNodesFactory;
import org.truffleruby.core.MathNodesBuiltins;
import org.truffleruby.core.MathNodesFactory;
import org.truffleruby.core.ProcessNodesBuiltins;
import org.truffleruby.core.ProcessNodesFactory;
import org.truffleruby.core.TruffleSystemNodesBuiltins;
import org.truffleruby.core.TruffleSystemNodesFactory;
import org.truffleruby.core.VMPrimitiveNodesBuiltins;
import org.truffleruby.core.VMPrimitiveNodesFactory;
import org.truffleruby.core.string.StringPrimitiveNodesBuiltins;
import org.truffleruby.core.string.StringPrimitiveNodesFactory;
import org.truffleruby.core.array.ArrayIndexNodesBuiltins;
import org.truffleruby.core.array.ArrayIndexNodesFactory;
import org.truffleruby.core.array.ArrayNodesBuiltins;
import org.truffleruby.core.array.ArrayNodesFactory;
import org.truffleruby.core.basicobject.BasicObjectNodesBuiltins;
import org.truffleruby.core.basicobject.BasicObjectNodesFactory;
import org.truffleruby.core.binding.BindingNodesBuiltins;
import org.truffleruby.core.binding.BindingNodesFactory;
import org.truffleruby.core.binding.TruffleBindingNodesBuiltins;
import org.truffleruby.core.binding.TruffleBindingNodesFactory;
import org.truffleruby.core.bool.FalseClassNodesBuiltins;
import org.truffleruby.core.bool.FalseClassNodesFactory;
import org.truffleruby.core.bool.NilClassNodesBuiltins;
import org.truffleruby.core.bool.NilClassNodesFactory;
import org.truffleruby.core.bool.TrueClassNodesBuiltins;
import org.truffleruby.core.bool.TrueClassNodesFactory;
import org.truffleruby.core.encoding.EncodingConverterNodesBuiltins;
import org.truffleruby.core.encoding.EncodingConverterNodesFactory;
import org.truffleruby.core.encoding.EncodingNodesBuiltins;
import org.truffleruby.core.encoding.EncodingNodesFactory;
import org.truffleruby.core.exception.ExceptionNodesBuiltins;
import org.truffleruby.core.exception.ExceptionNodesFactory;
import org.truffleruby.core.exception.FrozenErrorNodesBuiltins;
import org.truffleruby.core.exception.FrozenErrorNodesFactory;
import org.truffleruby.core.exception.NameErrorNodesBuiltins;
import org.truffleruby.core.exception.NameErrorNodesFactory;
import org.truffleruby.core.exception.NoMethodErrorNodesBuiltins;
import org.truffleruby.core.exception.NoMethodErrorNodesFactory;
import org.truffleruby.core.exception.SyntaxErrorNodesBuiltins;
import org.truffleruby.core.exception.SyntaxErrorNodesFactory;
import org.truffleruby.core.exception.SystemCallErrorNodesBuiltins;
import org.truffleruby.core.exception.SystemCallErrorNodesFactory;
import org.truffleruby.core.exception.SystemExitNodesBuiltins;
import org.truffleruby.core.exception.SystemExitNodesFactory;
import org.truffleruby.core.fiber.FiberNodesBuiltins;
import org.truffleruby.core.fiber.FiberNodesFactory;
import org.truffleruby.core.hash.HashNodesBuiltins;
import org.truffleruby.core.hash.HashNodesFactory;
import org.truffleruby.core.kernel.KernelNodesBuiltins;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.kernel.TruffleKernelNodesBuiltins;
import org.truffleruby.core.kernel.TruffleKernelNodesFactory;
import org.truffleruby.core.klass.ClassNodesBuiltins;
import org.truffleruby.core.klass.ClassNodesFactory;
import org.truffleruby.core.method.MethodNodesBuiltins;
import org.truffleruby.core.method.MethodNodesFactory;
import org.truffleruby.core.method.UnboundMethodNodesBuiltins;
import org.truffleruby.core.method.UnboundMethodNodesFactory;
import org.truffleruby.core.module.ModuleNodesBuiltins;
import org.truffleruby.core.module.ModuleNodesFactory;
import org.truffleruby.core.objectspace.WeakKeyMapNodesBuiltins;
import org.truffleruby.core.objectspace.WeakKeyMapNodesFactory;
import org.truffleruby.core.refinement.RefinementNodesBuiltins;
import org.truffleruby.core.refinement.RefinementNodesFactory;
import org.truffleruby.core.monitor.TruffleMonitorNodesBuiltins;
import org.truffleruby.core.monitor.TruffleMonitorNodesFactory;
import org.truffleruby.core.mutex.ConditionVariableNodesBuiltins;
import org.truffleruby.core.mutex.ConditionVariableNodesFactory;
import org.truffleruby.core.mutex.MutexNodesBuiltins;
import org.truffleruby.core.mutex.MutexNodesFactory;
import org.truffleruby.core.numeric.FloatNodesBuiltins;
import org.truffleruby.core.numeric.FloatNodesFactory;
import org.truffleruby.core.numeric.IntegerNodesBuiltins;
import org.truffleruby.core.numeric.IntegerNodesFactory;
import org.truffleruby.core.objectspace.ObjectSpaceNodesBuiltins;
import org.truffleruby.core.objectspace.ObjectSpaceNodesFactory;
import org.truffleruby.core.objectspace.WeakMapNodesBuiltins;
import org.truffleruby.core.objectspace.WeakMapNodesFactory;
import org.truffleruby.core.proc.ProcNodesBuiltins;
import org.truffleruby.core.proc.ProcNodesFactory;
import org.truffleruby.core.queue.QueueNodesBuiltins;
import org.truffleruby.core.queue.QueueNodesFactory;
import org.truffleruby.core.queue.SizedQueueNodesBuiltins;
import org.truffleruby.core.queue.SizedQueueNodesFactory;
import org.truffleruby.core.range.RangeNodesBuiltins;
import org.truffleruby.core.range.RangeNodesFactory;
import org.truffleruby.core.regexp.MatchDataNodesBuiltins;
import org.truffleruby.core.regexp.MatchDataNodesFactory;
import org.truffleruby.core.regexp.RegexpNodesBuiltins;
import org.truffleruby.core.regexp.RegexpNodesFactory;
import org.truffleruby.core.regexp.TruffleRegexpNodesBuiltins;
import org.truffleruby.core.regexp.TruffleRegexpNodesFactory;
import org.truffleruby.core.string.StringNodesBuiltins;
import org.truffleruby.core.string.StringNodesFactory;
import org.truffleruby.core.support.ByteArrayNodesBuiltins;
import org.truffleruby.core.support.ByteArrayNodesFactory;
import org.truffleruby.core.support.CustomRandomizerNodesBuiltins;
import org.truffleruby.core.support.CustomRandomizerNodesFactory;
import org.truffleruby.core.support.IONodesBuiltins;
import org.truffleruby.core.support.IONodesFactory;
import org.truffleruby.core.support.PRNGRandomizerNodesBuiltins;
import org.truffleruby.core.support.PRNGRandomizerNodesFactory;
import org.truffleruby.core.support.RandomizerNodesBuiltins;
import org.truffleruby.core.support.RandomizerNodesFactory;
import org.truffleruby.core.support.SecureRandomizerNodesBuiltins;
import org.truffleruby.core.support.SecureRandomizerNodesFactory;
import org.truffleruby.core.support.TypeNodesBuiltins;
import org.truffleruby.core.support.TypeNodesFactory;
import org.truffleruby.core.support.WeakRefNodesBuiltins;
import org.truffleruby.core.support.WeakRefNodesFactory;
import org.truffleruby.core.symbol.SymbolNodesBuiltins;
import org.truffleruby.core.symbol.SymbolNodesFactory;
import org.truffleruby.core.thread.ThreadBacktraceLocationNodesBuiltins;
import org.truffleruby.core.thread.ThreadBacktraceLocationNodesFactory;
import org.truffleruby.core.thread.ThreadNodesBuiltins;
import org.truffleruby.core.thread.ThreadNodesFactory;
import org.truffleruby.core.thread.TruffleThreadNodesBuiltins;
import org.truffleruby.core.thread.TruffleThreadNodesFactory;
import org.truffleruby.core.time.TimeNodesBuiltins;
import org.truffleruby.core.time.TimeNodesFactory;
import org.truffleruby.core.tracepoint.TracePointNodesBuiltins;
import org.truffleruby.core.tracepoint.TracePointNodesFactory;
import org.truffleruby.debug.TruffleDebugNodesBuiltins;
import org.truffleruby.debug.TruffleDebugNodesFactory;
import org.truffleruby.extra.AtomicReferenceNodesBuiltins;
import org.truffleruby.extra.AtomicReferenceNodesFactory;
import org.truffleruby.extra.ConcurrentMapNodesBuiltins;
import org.truffleruby.extra.ConcurrentMapNodesFactory;
import org.truffleruby.extra.TruffleGraalNodesBuiltins;
import org.truffleruby.extra.TruffleGraalNodesFactory;
import org.truffleruby.extra.TrufflePosixNodesBuiltins;
import org.truffleruby.extra.TrufflePosixNodesFactory;
import org.truffleruby.extra.TruffleRubyNodesBuiltins;
import org.truffleruby.extra.TruffleRubyNodesFactory;
import org.truffleruby.extra.ffi.PointerNodesBuiltins;
import org.truffleruby.extra.ffi.PointerNodesFactory;
import org.truffleruby.interop.InteropNodesBuiltins;
import org.truffleruby.interop.InteropNodesFactory;
import org.truffleruby.interop.PolyglotNodesBuiltins;
import org.truffleruby.interop.PolyglotNodesFactory;
import org.truffleruby.interop.SourceLocationNodesBuiltins;
import org.truffleruby.interop.SourceLocationNodesFactory;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.TruffleBootNodesBuiltins;
import org.truffleruby.language.TruffleBootNodesFactory;
import org.truffleruby.stdlib.CoverageNodesBuiltins;
import org.truffleruby.stdlib.CoverageNodesFactory;
import org.truffleruby.stdlib.ObjSpaceNodesBuiltins;
import org.truffleruby.stdlib.ObjSpaceNodesFactory;
import org.truffleruby.stdlib.digest.DigestNodesBuiltins;
import org.truffleruby.stdlib.digest.DigestNodesFactory;

import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class BuiltinsClasses {

    // These three lists need to be kept in sync

    // Sorted alphabetically to avoid duplicates
    public static void setupBuiltinsLazy(CoreMethodNodeManager coreManager) {
        ArrayIndexNodesBuiltins.setup(coreManager);
        ArrayNodesBuiltins.setup(coreManager);
        AtomicReferenceNodesBuiltins.setup(coreManager);
        BasicObjectNodesBuiltins.setup(coreManager);
        BindingNodesBuiltins.setup(coreManager);
        ByteArrayNodesBuiltins.setup(coreManager);
        CExtNodesBuiltins.setup(coreManager);
        ClassNodesBuiltins.setup(coreManager);
        ConcurrentMapNodesBuiltins.setup(coreManager);
        ConditionVariableNodesBuiltins.setup(coreManager);
        CoverageNodesBuiltins.setup(coreManager);
        CustomRandomizerNodesBuiltins.setup(coreManager);
        DigestNodesBuiltins.setup(coreManager);
        EncodingConverterNodesBuiltins.setup(coreManager);
        EncodingNodesBuiltins.setup(coreManager);
        ExceptionNodesBuiltins.setup(coreManager);
        FalseClassNodesBuiltins.setup(coreManager);
        FiberNodesBuiltins.setup(coreManager);
        FloatNodesBuiltins.setup(coreManager);
        FrozenErrorNodesBuiltins.setup(coreManager);
        GCNodesBuiltins.setup(coreManager);
        HashNodesBuiltins.setup(coreManager);
        IntegerNodesBuiltins.setup(coreManager);
        InteropNodesBuiltins.setup(coreManager);
        IONodesBuiltins.setup(coreManager);
        KernelNodesBuiltins.setup(coreManager);
        MainNodesBuiltins.setup(coreManager);
        MatchDataNodesBuiltins.setup(coreManager);
        MathNodesBuiltins.setup(coreManager);
        MethodNodesBuiltins.setup(coreManager);
        ModuleNodesBuiltins.setup(coreManager);
        MutexNodesBuiltins.setup(coreManager);
        NameErrorNodesBuiltins.setup(coreManager);
        NilClassNodesBuiltins.setup(coreManager);
        NoMethodErrorNodesBuiltins.setup(coreManager);
        ObjectSpaceNodesBuiltins.setup(coreManager);
        ObjSpaceNodesBuiltins.setup(coreManager);
        PointerNodesBuiltins.setup(coreManager);
        PolyglotNodesBuiltins.setup(coreManager);
        PRNGRandomizerNodesBuiltins.setup(coreManager);
        ProcessNodesBuiltins.setup(coreManager);
        ProcNodesBuiltins.setup(coreManager);
        QueueNodesBuiltins.setup(coreManager);
        RandomizerNodesBuiltins.setup(coreManager);
        RangeNodesBuiltins.setup(coreManager);
        RefinementNodesBuiltins.setup(coreManager);
        RegexpNodesBuiltins.setup(coreManager);
        SecureRandomizerNodesBuiltins.setup(coreManager);
        SizedQueueNodesBuiltins.setup(coreManager);
        SourceLocationNodesBuiltins.setup(coreManager);
        StringNodesBuiltins.setup(coreManager);
        SymbolNodesBuiltins.setup(coreManager);
        SyntaxErrorNodesBuiltins.setup(coreManager);
        SystemCallErrorNodesBuiltins.setup(coreManager);
        SystemExitNodesBuiltins.setup(coreManager);
        ThreadBacktraceLocationNodesBuiltins.setup(coreManager);
        ThreadNodesBuiltins.setup(coreManager);
        TimeNodesBuiltins.setup(coreManager);
        TracePointNodesBuiltins.setup(coreManager);
        TrueClassNodesBuiltins.setup(coreManager);
        TruffleBindingNodesBuiltins.setup(coreManager);
        TruffleBootNodesBuiltins.setup(coreManager);
        TruffleDebugNodesBuiltins.setup(coreManager);
        TruffleGraalNodesBuiltins.setup(coreManager);
        TruffleKernelNodesBuiltins.setup(coreManager);
        TruffleMonitorNodesBuiltins.setup(coreManager);
        TrufflePosixNodesBuiltins.setup(coreManager);
        TruffleRegexpNodesBuiltins.setup(coreManager);
        TruffleRubyNodesBuiltins.setup(coreManager);
        TruffleSystemNodesBuiltins.setup(coreManager);
        TruffleThreadNodesBuiltins.setup(coreManager);
        TypeNodesBuiltins.setup(coreManager);
        UnboundMethodNodesBuiltins.setup(coreManager);
        VMPrimitiveNodesBuiltins.setup(coreManager);
        StringPrimitiveNodesBuiltins.setup(coreManager);
        WeakKeyMapNodesBuiltins.setup(coreManager);
        WeakMapNodesBuiltins.setup(coreManager);
        WeakRefNodesBuiltins.setup(coreManager);
    }

    // Sorted alphabetically to avoid duplicates
    public static void setupBuiltinsLazyPrimitives(PrimitiveManager primitiveManager) {
        ArrayIndexNodesBuiltins.setupPrimitives(primitiveManager);
        ArrayNodesBuiltins.setupPrimitives(primitiveManager);
        AtomicReferenceNodesBuiltins.setupPrimitives(primitiveManager);
        BasicObjectNodesBuiltins.setupPrimitives(primitiveManager);
        BindingNodesBuiltins.setupPrimitives(primitiveManager);
        ByteArrayNodesBuiltins.setupPrimitives(primitiveManager);
        CExtNodesBuiltins.setupPrimitives(primitiveManager);
        ClassNodesBuiltins.setupPrimitives(primitiveManager);
        CustomRandomizerNodesBuiltins.setupPrimitives(primitiveManager);
        ConcurrentMapNodesBuiltins.setupPrimitives(primitiveManager);
        ConditionVariableNodesBuiltins.setupPrimitives(primitiveManager);
        CoverageNodesBuiltins.setupPrimitives(primitiveManager);
        DigestNodesBuiltins.setupPrimitives(primitiveManager);
        EncodingConverterNodesBuiltins.setupPrimitives(primitiveManager);
        EncodingNodesBuiltins.setupPrimitives(primitiveManager);
        ExceptionNodesBuiltins.setupPrimitives(primitiveManager);
        FalseClassNodesBuiltins.setupPrimitives(primitiveManager);
        FiberNodesBuiltins.setupPrimitives(primitiveManager);
        FloatNodesBuiltins.setupPrimitives(primitiveManager);
        FrozenErrorNodesBuiltins.setupPrimitives(primitiveManager);
        GCNodesBuiltins.setupPrimitives(primitiveManager);
        HashNodesBuiltins.setupPrimitives(primitiveManager);
        IntegerNodesBuiltins.setupPrimitives(primitiveManager);
        InteropNodesBuiltins.setupPrimitives(primitiveManager);
        IONodesBuiltins.setupPrimitives(primitiveManager);
        KernelNodesBuiltins.setupPrimitives(primitiveManager);
        MainNodesBuiltins.setupPrimitives(primitiveManager);
        MatchDataNodesBuiltins.setupPrimitives(primitiveManager);
        MathNodesBuiltins.setupPrimitives(primitiveManager);
        MethodNodesBuiltins.setupPrimitives(primitiveManager);
        ModuleNodesBuiltins.setupPrimitives(primitiveManager);
        MutexNodesBuiltins.setupPrimitives(primitiveManager);
        NameErrorNodesBuiltins.setupPrimitives(primitiveManager);
        NilClassNodesBuiltins.setupPrimitives(primitiveManager);
        NoMethodErrorNodesBuiltins.setupPrimitives(primitiveManager);
        ObjectSpaceNodesBuiltins.setupPrimitives(primitiveManager);
        ObjSpaceNodesBuiltins.setupPrimitives(primitiveManager);
        PointerNodesBuiltins.setupPrimitives(primitiveManager);
        PolyglotNodesBuiltins.setupPrimitives(primitiveManager);
        PRNGRandomizerNodesBuiltins.setupPrimitives(primitiveManager);
        ProcessNodesBuiltins.setupPrimitives(primitiveManager);
        ProcNodesBuiltins.setupPrimitives(primitiveManager);
        QueueNodesBuiltins.setupPrimitives(primitiveManager);
        RandomizerNodesBuiltins.setupPrimitives(primitiveManager);
        RangeNodesBuiltins.setupPrimitives(primitiveManager);
        RefinementNodesBuiltins.setupPrimitives(primitiveManager);
        RegexpNodesBuiltins.setupPrimitives(primitiveManager);
        SecureRandomizerNodesBuiltins.setupPrimitives(primitiveManager);
        SizedQueueNodesBuiltins.setupPrimitives(primitiveManager);
        SourceLocationNodesBuiltins.setupPrimitives(primitiveManager);
        StringNodesBuiltins.setupPrimitives(primitiveManager);
        SymbolNodesBuiltins.setupPrimitives(primitiveManager);
        SyntaxErrorNodesBuiltins.setupPrimitives(primitiveManager);
        SystemCallErrorNodesBuiltins.setupPrimitives(primitiveManager);
        SystemExitNodesBuiltins.setupPrimitives(primitiveManager);
        ThreadBacktraceLocationNodesBuiltins.setupPrimitives(primitiveManager);
        ThreadNodesBuiltins.setupPrimitives(primitiveManager);
        TimeNodesBuiltins.setupPrimitives(primitiveManager);
        TracePointNodesBuiltins.setupPrimitives(primitiveManager);
        TrueClassNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleBindingNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleBootNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleDebugNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleGraalNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleKernelNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleMonitorNodesBuiltins.setupPrimitives(primitiveManager);
        TrufflePosixNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleRegexpNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleRubyNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleSystemNodesBuiltins.setupPrimitives(primitiveManager);
        TruffleThreadNodesBuiltins.setupPrimitives(primitiveManager);
        TypeNodesBuiltins.setupPrimitives(primitiveManager);
        UnboundMethodNodesBuiltins.setupPrimitives(primitiveManager);
        VMPrimitiveNodesBuiltins.setupPrimitives(primitiveManager);
        StringPrimitiveNodesBuiltins.setupPrimitives(primitiveManager);
        WeakKeyMapNodesBuiltins.setupPrimitives(primitiveManager);
        WeakMapNodesBuiltins.setupPrimitives(primitiveManager);
        WeakRefNodesBuiltins.setupPrimitives(primitiveManager);
    }

    // Sorted alphabetically to avoid duplicates
    public static List<List<? extends NodeFactory<? extends RubyBaseNode>>> getCoreNodeFactories() {
        return Arrays.asList(
                ArrayIndexNodesFactory.getFactories(),
                ArrayNodesFactory.getFactories(),
                AtomicReferenceNodesFactory.getFactories(),
                BasicObjectNodesFactory.getFactories(),
                BindingNodesFactory.getFactories(),
                ByteArrayNodesFactory.getFactories(),
                CExtNodesFactory.getFactories(),
                ClassNodesFactory.getFactories(),
                ConcurrentMapNodesFactory.getFactories(),
                ConditionVariableNodesFactory.getFactories(),
                CoverageNodesFactory.getFactories(),
                CustomRandomizerNodesFactory.getFactories(),
                DigestNodesFactory.getFactories(),
                EncodingConverterNodesFactory.getFactories(),
                EncodingNodesFactory.getFactories(),
                ExceptionNodesFactory.getFactories(),
                FalseClassNodesFactory.getFactories(),
                FiberNodesFactory.getFactories(),
                FloatNodesFactory.getFactories(),
                FrozenErrorNodesFactory.getFactories(),
                GCNodesFactory.getFactories(),
                HashNodesFactory.getFactories(),
                IntegerNodesFactory.getFactories(),
                InteropNodesFactory.getFactories(),
                IONodesFactory.getFactories(),
                KernelNodesFactory.getFactories(),
                MainNodesFactory.getFactories(),
                MatchDataNodesFactory.getFactories(),
                MathNodesFactory.getFactories(),
                MethodNodesFactory.getFactories(),
                ModuleNodesFactory.getFactories(),
                MutexNodesFactory.getFactories(),
                NameErrorNodesFactory.getFactories(),
                NilClassNodesFactory.getFactories(),
                NoMethodErrorNodesFactory.getFactories(),
                ObjectSpaceNodesFactory.getFactories(),
                ObjSpaceNodesFactory.getFactories(),
                PointerNodesFactory.getFactories(),
                PolyglotNodesFactory.getFactories(),
                PRNGRandomizerNodesFactory.getFactories(),
                ProcessNodesFactory.getFactories(),
                ProcNodesFactory.getFactories(),
                QueueNodesFactory.getFactories(),
                RandomizerNodesFactory.getFactories(),
                RangeNodesFactory.getFactories(),
                RefinementNodesFactory.getFactories(),
                RegexpNodesFactory.getFactories(),
                SecureRandomizerNodesFactory.getFactories(),
                SizedQueueNodesFactory.getFactories(),
                SourceLocationNodesFactory.getFactories(),
                StringNodesFactory.getFactories(),
                SymbolNodesFactory.getFactories(),
                SyntaxErrorNodesFactory.getFactories(),
                SystemCallErrorNodesFactory.getFactories(),
                SystemExitNodesFactory.getFactories(),
                ThreadBacktraceLocationNodesFactory.getFactories(),
                ThreadNodesFactory.getFactories(),
                TimeNodesFactory.getFactories(),
                TracePointNodesFactory.getFactories(),
                TrueClassNodesFactory.getFactories(),
                TruffleBindingNodesFactory.getFactories(),
                TruffleBootNodesFactory.getFactories(),
                TruffleDebugNodesFactory.getFactories(),
                TruffleGraalNodesFactory.getFactories(),
                TruffleKernelNodesFactory.getFactories(),
                TruffleMonitorNodesFactory.getFactories(),
                TrufflePosixNodesFactory.getFactories(),
                TruffleRegexpNodesFactory.getFactories(),
                TruffleRubyNodesFactory.getFactories(),
                TruffleSystemNodesFactory.getFactories(),
                TruffleThreadNodesFactory.getFactories(),
                TypeNodesFactory.getFactories(),
                UnboundMethodNodesFactory.getFactories(),
                VMPrimitiveNodesFactory.getFactories(),
                StringPrimitiveNodesFactory.getFactories(),
                WeakKeyMapNodesFactory.getFactories(),
                WeakMapNodesFactory.getFactories(),
                WeakRefNodesFactory.getFactories());
    }

}
