/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.core.objectspace;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.Layouts;
import org.truffleruby.RubyLanguage;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.truffleruby.language.RubyDynamicObject;

/** Supports the Ruby {@code ObjectSpace} module. Object IDs are lazily allocated {@code long} values, mapped to objects
 * with a weak hash map. */
public final class ObjectSpaceManager {

    // behaves as volatile by piggybacking on Assumption semantics
    @CompilationFinal private boolean isTracing = false;

    private final AtomicInteger tracingAssumptionActivations = new AtomicInteger(0);
    private final AtomicInteger tracingGeneration = new AtomicInteger(0);
    private final ThreadLocal<Boolean> tracingPaused = ThreadLocal.withInitial(() -> false);

    public static final long INITIAL_LANGUAGE_OBJECT_ID = 16;
    public static final long OBJECT_ID_INCREMENT_BY = 16;
    private static final long INITIAL_CONTEXT_OBJECT_ID = 8;

    private final AtomicLong nextObjectID = new AtomicLong(INITIAL_CONTEXT_OBJECT_ID);

    public void traceAllocationsStart(RubyLanguage language) {
        if (tracingAssumptionActivations.incrementAndGet() == 1) {
            isTracing = true;
            language.invalidateTracingAssumption();
        }
    }

    public void traceAllocationsStop(RubyLanguage language) {
        if (tracingAssumptionActivations.decrementAndGet() == 0) {
            isTracing = false;
            language.invalidateTracingAssumption();
        }
    }

    public void traceAllocationsClear() {
        tracingGeneration.incrementAndGet();
    }

    public boolean isTracing(RubyLanguage language) {
        CompilerAsserts.partialEvaluationConstant(language);

        if (!language.getTracingAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Read the value in the interpreter
        }
        return isTracing;
    }

    public boolean isTracingPaused() {
        return tracingPaused.get();
    }

    public void setTracingPaused(boolean tracingPaused) {
        this.tracingPaused.set(tracingPaused);
    }

    public int getTracingGeneration() {
        return tracingGeneration.get();
    }

    public static long readObjectID(RubyDynamicObject object, DynamicObjectLibrary objectLibrary) {
        try {
            return objectLibrary.getLongOrDefault(object, Layouts.OBJECT_ID_IDENTIFIER, 0L);
        } catch (UnexpectedResultException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public long getNextObjectID() {
        final long id = nextObjectID.getAndAdd(OBJECT_ID_INCREMENT_BY);

        if (id == INITIAL_CONTEXT_OBJECT_ID - OBJECT_ID_INCREMENT_BY) {
            throw CompilerDirectives.shouldNotReachHere("Object IDs exhausted");
        }

        return id;
    }

    public static int getCollectionCount() {
        int count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.toIntExact(bean.getCollectionCount());
        }
        return count;
    }

}
