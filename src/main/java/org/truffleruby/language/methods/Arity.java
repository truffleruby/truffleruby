/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.parser.ArgumentDescriptor;
import org.truffleruby.parser.ArgumentType;

public final class Arity {

    public static final String[] NO_KEYWORDS = StringUtils.EMPTY_STRING_ARRAY;
    public static final Arity NO_ARGUMENTS = new Arity(0, 0, false);
    public static final Arity ONE_REQUIRED = new Arity(1, 0, false);
    public static final Arity ANY_ARGUMENTS = new Arity(0, 0, true);

    // Used as a marker for Symbol#to_proc Proc's
    public static final Arity SYMBOL_TO_PROC_ARITY = new Arity(1, 0, true);

    // Same as NO_ARGUMENTS but used as a marker for module body methods
    public static final Arity MODULE_BODY = new Arity(0, 0, false);

    private final int preRequired;
    private final int optional;
    private final boolean hasRest;
    /* Block with parameters |a, | has implicit rest argument, but it's ignored for `lambda {}`. `Proc#arity` ignores it
     * as well even for blocks (see https://bugs.ruby-lang.org/issues/19971). */
    private final boolean isImplicitRest;
    private final int postRequired;
    private final boolean hasKeywordsRest;
    private final String[] keywordArguments;
    private final int requiredKeywordArgumentsCount;
    /** During parsing we cannot know if this Arity object belongs to proc or to lambda. So we calculate the arity
     * number for both cases and provide a ProcType-dependent interface. */
    private final int arityNumber;
    private final int procArityNumber;

    public Arity(int preRequired, int optional, boolean hasRest) {
        this(preRequired, optional, hasRest, false, 0, NO_KEYWORDS, 0, false);
    }

    public Arity(
            int preRequired,
            int optional,
            boolean hasRest,
            boolean isImplicitRest,
            int postRequired,
            String[] keywordArguments,
            int requiredKeywordArgumentsCount,
            boolean hasKeywordsRest) {
        this.preRequired = preRequired;
        this.optional = optional;
        this.hasRest = hasRest;
        this.isImplicitRest = isImplicitRest;
        this.postRequired = postRequired;
        // Required keywords are located at the beginning of the `keywordArguments` array.
        // So we can specify them with only one `int` field (`requiredKeywordArgumentsCount`).
        this.keywordArguments = keywordArguments;
        this.requiredKeywordArgumentsCount = requiredKeywordArgumentsCount;
        this.hasKeywordsRest = hasKeywordsRest;
        this.arityNumber = computeArityNumber(false);
        this.procArityNumber = computeArityNumber(true);

        assert keywordArguments != null && preRequired >= 0 && optional >= 0 && postRequired >= 0 : toString();
    }

    public Arity consumingFirstRequired() {
        return new Arity(
                Integer.max(preRequired - 1, 0),
                optional,
                hasRest,
                isImplicitRest,
                postRequired,
                keywordArguments,
                requiredKeywordArgumentsCount,
                hasKeywordsRest);
    }

    public boolean checkPositionalArguments(int given) {
        CompilerAsserts.partialEvaluationConstant(this);

        int required = getRequired();
        // assume this check is used only for method/lambda (and not used for block)
        // so implicit rest parameter should be ignored (if block was converted to lambda/method)
        return given >= required && ((hasRest && !isImplicitRest) || given <= required + optional);
    }

    public int getPreRequired() {
        return preRequired;
    }

    public int getRequired() {
        return preRequired + postRequired;
    }

    public int getOptional() {
        return optional;
    }

    public boolean hasRest() {
        return hasRest;
    }

    public boolean isImplicitRest() {
        return isImplicitRest;
    }

    public boolean acceptsKeywords() {
        return hasKeywords() || hasKeywordsRest();
    }

    public boolean acceptsPositionalArguments() {
        return getRequired() > 0 || getOptional() > 0 || hasRest();
    }

    public boolean hasKeywords() {
        return keywordArguments.length != 0;
    }

    public boolean hasKeywordsRest() {
        return hasKeywordsRest;
    }

    public boolean allKeywordsOptional() {
        return requiredKeywordArgumentsCount == 0;
    }

    private int computeArityNumber(boolean isProc) {
        int count = getRequired();

        if (acceptsKeywords() && !allKeywordsOptional()) {
            count++;
        }

        if ((hasRest && !isImplicitRest) ||
                (!isProc && (optional > 0 || (acceptsKeywords() && allKeywordsOptional())))) {
            count = -count - 1;
        }

        return count;
    }

    public int getProcArityNumber(ProcType type) {
        return type == ProcType.PROC ? procArityNumber : arityNumber;
    }

    public int getMethodArityNumber() {
        return arityNumber;
    }

    public String[] getKeywordArguments() {
        return keywordArguments;
    }

    public String[] getRequiredKeywordArguments() {
        final String[] requiredKeywords = new String[requiredKeywordArgumentsCount];

        for (int i = 0; i < requiredKeywords.length; i++) {
            requiredKeywords[i] = keywordArguments[i];
        }

        return requiredKeywords;
    }

    /** Generate argument descriptors for a method/proc that doesn't provide parameter names, e.g a core method
     * implemented in Java. */
    public ArgumentDescriptor[] toUnnamedArgumentDescriptors() {
        List<ArgumentDescriptor> descs = new ArrayList<>();

        for (int i = 0; i < preRequired; i++) {
            descs.add(new ArgumentDescriptor(ArgumentType.unnamedreq));
        }

        for (int i = 0; i < optional; i++) {
            descs.add(new ArgumentDescriptor(ArgumentType.unnamedopt));
        }

        if (hasRest) {
            descs.add(new ArgumentDescriptor(ArgumentType.unnamedrest));
        }

        for (int i = 0; i < postRequired; i++) {
            descs.add(new ArgumentDescriptor(ArgumentType.unnamedreq));
        }

        for (String keyword : keywordArguments) {
            descs.add(new ArgumentDescriptor(ArgumentType.key, keyword));
        }

        if (hasKeywordsRest) {
            descs.add(new ArgumentDescriptor(ArgumentType.unnamedkeyrest));
        }

        return descs.toArray(ArgumentDescriptor.EMPTY_ARRAY);
    }

    @Override
    public String toString() {
        return "Arity{" +
                "preRequired = " + preRequired +
                ", optional = " + optional +
                ", hasRest = " + hasRest +
                ", isImplicitRest = " + isImplicitRest +
                ", postRequired = " + postRequired +
                ", keywordArguments = " + Arrays.toString(keywordArguments) +
                ", requiredKeywordArgumentsCount = " + requiredKeywordArgumentsCount +
                ", hasKeywordsRest = " + hasKeywordsRest +
                '}';
    }
}
