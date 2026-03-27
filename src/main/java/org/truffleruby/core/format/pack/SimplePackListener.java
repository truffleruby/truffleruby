/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.pack;

import org.truffleruby.language.control.DeferredRaiseException;

import java.nio.ByteOrder;

public interface SimplePackListener {

    void integer(int size, boolean signed, ByteOrder byteOrder, int count);

    void floatingPoint(int size, ByteOrder byteOrder, int count);

    void utf8Character(int count);

    void berInteger(int count);

    void binaryStringSpacePadded(int count);

    void binaryStringNullPadded(int count);

    void binaryStringNullStar(int count);

    void bitStringMSBFirst(int count);

    void bitStringMSBLast(int count);

    void hexStringHighFirst(int count);

    void hexStringLowFirst(int count);

    void uuString(int count);

    void mimeString(int count);

    void base64String(int count);

    void pointer(int count, int limit);

    void at(int position);

    void back(int count);

    void nullByte(int count);

    void startSubSequence();

    void finishSubSequence(int count);

    void error(String message) throws DeferredRaiseException;

    void warn(String message);

    String packListenerMode();
}
