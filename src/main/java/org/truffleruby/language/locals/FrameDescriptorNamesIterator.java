/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import java.util.Iterator;

import com.oracle.truffle.api.frame.FrameDescriptor;
import org.truffleruby.annotations.SuppressFBWarnings;

public final class FrameDescriptorNamesIterator implements Iterator<Object> {

    public static Iterable<Object> iterate(FrameDescriptor descriptor) {
        return () -> new FrameDescriptorNamesIterator(descriptor);
    }

    private final FrameDescriptor descriptor;
    private final int slots;
    int slot = 0;

    private FrameDescriptorNamesIterator(FrameDescriptor descriptor) {
        assert descriptor.getNumberOfAuxiliarySlots() == 0;
        this.descriptor = descriptor;
        this.slots = descriptor.getNumberOfSlots();
    }

    @Override
    public boolean hasNext() {
        return slot < slots;
    }

    @SuppressFBWarnings("IT_NO_SUCH_ELEMENT")
    @Override
    public Object next() {
        Object identifier = descriptor.getSlotName(slot);
        slot++;
        return identifier;
    }
}
