/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.core.mutex.MutexOperations;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.library.RubyStringLibrary;

public final class AutoloadConstant {

    private final Object feature;
    private final String autoloadPath;
    private volatile ReentrantLock autoloadLock;

    /** The value assigned to the constant by the thread autoloading it. It is only visible to that thread until the
     * autoload completes, at which point it is published, i.e., it replaces the autoload constant in the module. That
     * way other threads never observe a partially-initialized value, e.g. a class with only some of its methods
     * defined. These 3 fields are only accessed while holding the {@link #autoloadLock}. */
    private Object unpublishedValue = null;
    private Node unpublishedValueNode = null;
    private boolean published = false;

    public AutoloadConstant(Object feature) {
        assert RubyStringLibrary.isRubyStringUncached(feature);
        this.feature = feature;
        this.autoloadPath = StringOperations.getJavaString(this.feature);
    }

    public String getAutoloadPath() {
        return autoloadPath;
    }

    public Object getFeature() {
        return feature;
    }

    private ReentrantLock getAutoloadLock() {
        synchronized (this) {
            if (autoloadLock == null) {
                autoloadLock = new ReentrantLock();
            }
        }
        return autoloadLock;
    }

    @TruffleBoundary
    public void startAutoLoad(RubyContext context, Node currentNode) {
        MutexOperations.lockInternal(context, getAutoloadLock(), currentNode);
    }

    @TruffleBoundary
    public void stopAutoLoad() {
        getAutoloadLock().unlock();
    }

    public boolean isAutoloading() {
        return autoloadLock != null && autoloadLock.isLocked();
    }

    public boolean isAutoloadingThread() {
        return autoloadLock != null && autoloadLock.isHeldByCurrentThread();
    }

    /** Whether the current thread is autoloading this constant and did not assign it yet. In that case the constant is
     * considered missing for that thread. */
    public boolean isAutoloadingThreadAndUnset() {
        return isAutoloadingThread() && !hasUnpublishedValue();
    }

    /** Whether the current thread is autoloading this constant and already assigned it. In that case the constant is
     * resolved to {@link #getUnpublishedValue()} for that thread. */
    public boolean isAutoloadingThreadAndSet() {
        return isAutoloadingThread() && hasUnpublishedValue();
    }

    public boolean shouldPublish() {
        return hasUnpublishedValue() && !isPublished();
    }

    private boolean hasUnpublishedValue() {
        assert isAutoloadingThread();
        return unpublishedValue != null;
    }

    public Object getUnpublishedValue() {
        assert isAutoloadingThread();
        return unpublishedValue;
    }

    public SourceSection getUnpublishedValueSourceSection() {
        assert isAutoloadingThread();
        return unpublishedValueNode != null ? unpublishedValueNode.getSourceSection() : null;
    }

    public void setUnpublishedValue(Object unpublishedValue, Node currentNode) {
        assert isAutoloadingThread();
        assert !isPublished();
        assert RubyGuards.assertIsValidRubyValue(unpublishedValue);
        this.unpublishedValue = unpublishedValue;
        this.unpublishedValueNode = currentNode;
    }

    public boolean isPublished() {
        assert isAutoloadingThread();
        return published;
    }

    /** Replaces the autoload constant by a regular constant with the value assigned during the autoload, which makes it
     * visible to all threads. */
    @TruffleBoundary
    public void publish(RubyContext context, RubyConstant constant) {
        assert isAutoloadingThread();
        assert shouldPublish();
        this.published = true;
        constant.getDeclaringModule().fields.publishAutoloadedConstant(context, unpublishedValueNode,
                constant.getName(), this, unpublishedValue);
    }

}
