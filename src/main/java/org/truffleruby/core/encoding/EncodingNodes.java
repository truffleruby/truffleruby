/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Contains code modified from JRuby's RubyConverter.java
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Config;
import org.graalvm.shadowed.org.jcodings.EncodingDB;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.ToRubyEncodingNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringGuards;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

import static com.oracle.truffle.api.strings.TruffleString.CodeRange.ASCII;

@CoreModule(value = "Encoding", isClass = true)
public abstract class EncodingNodes {

    @CoreMethod(names = "ascii_compatible?")
    public abstract static class AsciiCompatibleNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isAsciiCompatible(RubyEncoding encoding) {
            return encoding.isAsciiCompatible;
        }
    }

    // MRI: enc_compatible_str and enc_compatible_latter
    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class NegotiateCompatibleStringEncodingNode extends RubyBaseNode {

        public abstract RubyEncoding execute(Node node, AbstractTruffleString first, RubyEncoding firstEncoding,
                AbstractTruffleString second, RubyEncoding secondEncoding);

        @Specialization
        static RubyEncoding negotiateEncoding(
                Node node,
                AbstractTruffleString first,
                RubyEncoding firstEncoding,
                AbstractTruffleString second,
                RubyEncoding secondEncoding,
                @Cached InlinedConditionProfile equalEncodingsProfile,
                @Cached InlinedConditionProfile first7BitProfile,
                @Cached InlinedConditionProfile firstAsciiCompatProfile,
                @Cached InlinedConditionProfile second7BitProfile,
                @Cached InlinedConditionProfile secondAsciiCompatProfile,
                @Cached(inline = false) TruffleString.GetByteCodeRangeNode codeRangeNode) {
            assert first.isCompatibleToUncached(firstEncoding.tencoding) &&
                    second.isCompatibleToUncached(secondEncoding.tencoding);

            if (equalEncodingsProfile.profile(node, firstEncoding == secondEncoding)) {
                return firstEncoding;
            }

            // We handle encoding negotiation for all ASCII-compatible encodings on the fast path.
            // We also noticed that in yjit-bench only the 3 standard encodings are ever seen here.
            boolean second7Bit = StringGuards.is7Bit(second, secondEncoding, codeRangeNode);
            if (firstAsciiCompatProfile.profile(node, firstEncoding.isAsciiCompatible)) {
                if (second7BitProfile.profile(node, second7Bit)) {
                    return firstEncoding;
                } else if (secondAsciiCompatProfile.profile(node, secondEncoding.isAsciiCompatible)) {
                    if (first7BitProfile.profile(node, StringGuards.is7Bit(first, firstEncoding, codeRangeNode))) {
                        return secondEncoding;
                    } else {
                        return null;
                    }
                }
            }

            // both encodings are non-standard (that's not US-ASCII, UTF-8 or ASCII-8BIT)
            return compatibleEncodingForStrings(first, firstEncoding, second, secondEncoding, second7Bit,
                    codeRangeNode);
        }

        /** This method returns non-null if either:
         * <ul>
         * <li>one side is empty</li>
         * <li>one side is 7-bit and both encodings are ascii-compatible</li>
         * </ul>
         */
        @TruffleBoundary
        protected static RubyEncoding compatibleEncodingForStrings(AbstractTruffleString first,
                RubyEncoding firstEncoding,
                AbstractTruffleString second, RubyEncoding secondEncoding, boolean second7Bit,
                TruffleString.GetByteCodeRangeNode codeRangeNode) {
            // MRI: enc_compatible_latter
            assert firstEncoding != secondEncoding : "this method assumes the encodings are different";

            if (second.isEmpty()) {
                return firstEncoding;
            }
            if (first.isEmpty()) {
                return (firstEncoding.isAsciiCompatible && second7Bit) ? firstEncoding : secondEncoding;
            }

            if (!firstEncoding.isAsciiCompatible || !secondEncoding.isAsciiCompatible) {
                return null;
            }

            if (second7Bit) {
                return firstEncoding;
            }
            if (StringGuards.is7Bit(first, firstEncoding, codeRangeNode)) {
                return secondEncoding;
            }

            return null;
        }

    }

    // MRI: enc_compatible_latter
    /** Use {@link NegotiateCompatibleStringEncodingNode} instead, this should only be used for
     * {@code Encoding.compatible?} */
    @GenerateCached(false)
    @GenerateInline
    public abstract static class NegotiateCompatibleEncodingNode extends RubyBaseNode {

        public abstract RubyEncoding execute(Node node, Object first, RubyEncoding firstEncoding, Object second,
                RubyEncoding secondEncoding);

        @Specialization
        static RubyEncoding negotiateEncoding(
                Node node, Object first, RubyEncoding firstEncoding, Object second, RubyEncoding secondEncoding,
                @Cached RubyStringLibrary libFirst,
                @Cached RubyStringLibrary libSecond,
                @Cached InlinedConditionProfile equalEncodingsProfile,
                @Cached InlinedConditionProfile stringStringProfile,
                @Cached InlinedConditionProfile incompatibleProfile,
                @Cached InlinedConditionProfile objectStringProfile,
                @Cached(inline = false) TruffleString.GetByteCodeRangeNode codeRangeNode,
                @Cached NegotiateCompatibleStringEncodingNode negotiateForStringsNode) {
            assert firstEncoding != null;
            assert secondEncoding != null;

            if (equalEncodingsProfile.profile(node, firstEncoding == secondEncoding)) {
                return firstEncoding;
            }

            boolean firstIsString = libFirst.isRubyString(node, first);
            boolean secondIsString = libSecond.isRubyString(node, second);

            // String, String
            if (stringStringProfile.profile(node, firstIsString && secondIsString)) {
                return negotiateForStringsNode.execute(node, libFirst.getTString(node, first), firstEncoding,
                        libSecond.getTString(node, second), secondEncoding);
            }

            if (incompatibleProfile.profile(node,
                    !firstEncoding.isAsciiCompatible || !secondEncoding.isAsciiCompatible)) {
                return null;
            }

            // Object, String
            if (objectStringProfile.profile(node, secondIsString && !firstIsString)) {
                return compatibleEncoding(node, second, secondEncoding, secondIsString, firstEncoding, codeRangeNode,
                        libSecond);
            }

            // String, Object or Object, Object
            return compatibleEncoding(node, first, firstEncoding, firstIsString, secondEncoding, codeRangeNode,
                    libFirst);
        }

        protected static RubyEncoding compatibleEncoding(Node node,
                Object first, RubyEncoding firstEncoding, boolean firstIsString,
                RubyEncoding secondEncoding,
                TruffleString.GetByteCodeRangeNode codeRangeNode,
                RubyStringLibrary libFirst) {
            assert firstEncoding != secondEncoding;

            if (secondEncoding == Encodings.US_ASCII) {
                return firstEncoding;
            }

            if (firstIsString) {
                if (codeRangeNode.execute(libFirst.getTString(node, first),
                        libFirst.getTEncoding(node, first)) == ASCII) {
                    return secondEncoding;
                }
            } else {
                if (firstEncoding == Encodings.US_ASCII) {
                    return secondEncoding;
                }
            }

            return null;
        }

    }

    // MRI: rb_enc_compatible
    @Primitive(name = "encoding_compatible?")
    public abstract static class CompatibleQueryNode extends PrimitiveArrayArgumentsNode {

        public static CompatibleQueryNode create() {
            return EncodingNodesFactory.CompatibleQueryNodeFactory.create(null);
        }

        @Specialization
        Object isCompatible(Object first, Object second,
                @Cached NegotiateCompatibleEncodingNode negotiateCompatibleEncodingNode,
                @Cached ToRubyEncodingNode toRubyEncodingNode,
                @Cached InlinedConditionProfile nullEncodingProfile,
                @Cached InlinedConditionProfile noNegotiatedEncodingProfile) {

            var firstEncoding = toRubyEncodingNode.execute(this, first);
            var secondEncoding = toRubyEncodingNode.execute(this, second);

            if (nullEncodingProfile.profile(this, firstEncoding == null || secondEncoding == null)) {
                return nil;
            }

            RubyEncoding negotiatedEncoding = negotiateCompatibleEncodingNode.execute(this, first,
                    firstEncoding, second, secondEncoding);

            if (noNegotiatedEncodingProfile.profile(this, negotiatedEncoding == null)) {
                return nil;
            }

            return negotiatedEncoding;
        }
    }

    // encoding_compatible? but only accepting Strings for better footprint
    // Like Primitive.encoding_ensure_compatible_str but returns nil if incompatible
    @Primitive(name = "strings_compatible?")
    public abstract static class AreStringsCompatibleNode extends PrimitiveArrayArgumentsNode {
        public static AreStringsCompatibleNode create() {
            return EncodingNodesFactory.AreStringsCompatibleNodeFactory.create(null);
        }

        @Specialization
        Object areCompatible(Object first, Object second,
                @Cached RubyStringLibrary libFirst,
                @Cached RubyStringLibrary libSecond,
                @Cached NegotiateCompatibleStringEncodingNode negotiateCompatibleStringEncodingNode,
                @Cached InlinedConditionProfile noNegotiatedEncodingProfile) {
            final RubyEncoding negotiatedEncoding = negotiateCompatibleStringEncodingNode.execute(this,
                    libFirst.getTString(this, first), libFirst.getEncoding(this, first),
                    libSecond.getTString(this, second), libSecond.getEncoding(this, second));

            if (noNegotiatedEncodingProfile.profile(this, negotiatedEncoding == null)) {
                return nil;
            }

            return negotiatedEncoding;
        }
    }

    @CoreMethod(names = "list", onSingleton = true)
    public abstract static class ListNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray list() {
            return createArray(getContext().getEncodingManager().getEncodingList());
        }
    }


    @CoreMethod(names = "locale_charmap", onSingleton = true)
    public abstract static class LocaleCharacterMapNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        ImmutableRubyString localeCharacterMap() {
            final RubyEncoding rubyEncoding = getContext().getEncodingManager().getLocaleEncoding();
            return rubyEncoding.name;
        }
    }

    @CoreMethod(names = "dummy?")
    public abstract static class DummyNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isDummy(RubyEncoding encoding) {
            return encoding.isDummy;
        }
    }

    @CoreMethod(names = { "name", "to_s" })
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        ImmutableRubyString toS(RubyEncoding encoding) {
            return encoding.name;
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }
    }

    @Primitive(name = "encoding_each_alias")
    public abstract static class EachAliasNode extends PrimitiveArrayArgumentsNode {

        @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

        @TruffleBoundary
        @Specialization
        Object eachAlias(RubyProc block,
                @Cached CallBlockNode yieldNode) {
            var iterator = EncodingDB.getAliases().entryIterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                final RubyString aliasName = createString(
                        fromByteArrayNode,
                        ArrayUtils.extractRange(entry.bytes, entry.p, entry.end),
                        Encodings.US_ASCII); // CR_7BIT
                yieldNode.yield(
                        this,
                        block,
                        aliasName,
                        Encodings.getBuiltInEncoding(entry.value.getEncoding()));
            }
            return nil;
        }
    }

    @Primitive(name = "encoding_define_alias")
    public abstract static class DefineAliasNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyEncoding defineAlias(RubyEncoding encoding, RubySymbol aliasName) {
            getContext().getEncodingManager().defineAlias(encoding, aliasName.getString());
            return encoding;
        }
    }

    @Primitive(name = "encoding_is_unicode?")
    public abstract static class IsUnicodeNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isUnicode(RubyEncoding encoding) {
            return encoding.isUnicode;
        }
    }

    @Primitive(name = "encoding_get_default_encoding")
    public abstract static class GetDefaultEncodingNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object getDefaultEncoding(Object name) {
            final RubyEncoding encoding = getEncoding(StringOperations.getJavaString(name));
            if (encoding == null) {
                return nil;
            } else {
                return encoding;
            }
        }

        @TruffleBoundary
        private RubyEncoding getEncoding(String name) {
            switch (name) {
                case "internal":
                    return getContext().getEncodingManager().getDefaultInternalEncoding();
                case "external":
                case "filesystem":
                    return getContext().getEncodingManager().getDefaultExternalEncoding();
                case "locale":
                    return getContext().getEncodingManager().getLocaleEncoding();
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Primitive(name = "encoding_set_default_external")
    public abstract static class SetDefaultExternalNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyEncoding setDefaultExternal(RubyEncoding encoding) {
            getContext().getEncodingManager().setDefaultExternalEncoding(encoding);
            return encoding;
        }

        @Specialization
        RubyEncoding noDefaultExternal(Nil encoding) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().argumentError("default external can not be nil", this));
        }

    }

    @Primitive(name = "encoding_set_default_internal")
    public abstract static class SetDefaultInternalNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyEncoding setDefaultInternal(RubyEncoding encoding) {
            getContext().getEncodingManager().setDefaultInternalEncoding(encoding);
            return encoding;
        }

        @Specialization
        Object noDefaultInternal(Nil encoding) {
            getContext().getEncodingManager().setDefaultInternalEncoding(null);
            return nil;
        }

    }

    // MRI: rb_obj_encoding and rb_enc_get_index
    @Primitive(name = "encoding_get_object_encoding")
    public abstract static class EncodingGetObjectEncodingNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object getObjectEncoding(Object object,
                @Cached ToRubyEncodingNode toRubyEncodingNode,
                @Cached InlinedConditionProfile nullProfile) {
            var rubyEncoding = toRubyEncodingNode.execute(this, object);
            if (nullProfile.profile(this, rubyEncoding == null)) {
                return nil;
            } else {
                return rubyEncoding;
            }
        }
    }

    public abstract static class EncodingCreationNode extends PrimitiveArrayArgumentsNode {

        public static RubyArray setIndexOrRaiseError(Node node, String name, RubyEncoding newEncoding) {
            if (newEncoding == null) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).argumentErrorEncodingAlreadyRegistered(name, node));
            }

            final int index = newEncoding.index;
            return createArray(node, new Object[]{ newEncoding, index });
        }

    }

    @Primitive(name = "encoding_create_dummy")
    public abstract static class DummyEncodingNode extends EncodingCreationNode {

        @Specialization(guards = "strings.isRubyString(this, nameObject)", limit = "1")
        static RubyArray createDummyEncoding(Object nameObject,
                @Cached RubyStringLibrary strings,
                @Cached ToJavaStringNode toJavaStringNode,
                @Bind Node node) {
            final String name = toJavaStringNode.execute(node, nameObject);

            final RubyEncoding newEncoding = createDummy(node, name);
            return setIndexOrRaiseError(node, name, newEncoding);
        }

        @TruffleBoundary
        private static RubyEncoding createDummy(Node node, String name) {
            if (getContext(node).getEncodingManager().getNumberOfEncodings() >= Encodings.MAX_NUMBER_OF_ENCODINGS) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).encodingErrorTooManyEncodings(Encodings.MAX_NUMBER_OF_ENCODINGS, node));
            }

            return getContext(node).getEncodingManager().createDummyEncoding(name);
        }

    }

    @Primitive(name = "encoding_get_encoding_by_index", lowerFixnum = 0)
    public abstract static class GetEncodingObjectByIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "isSingleContext()", "index == cachedIndex" }, limit = "getCacheLimit()")
        RubyEncoding getEncoding(int index,
                @Cached("index") int cachedIndex,
                @Cached("getContext().getEncodingManager().getRubyEncoding(index)") RubyEncoding cachedEncoding) {
            return cachedEncoding;
        }

        @Specialization(replaces = "getEncoding")
        RubyEncoding getEncodingUncached(int index) {
            return getContext().getEncodingManager().getRubyEncoding(index);
        }

        protected int getCacheLimit() {
            return getLanguage().options.ENCODING_LOADED_CLASSES_CACHE;
        }
    }

    @Primitive(name = "encoding_get_encoding_index")
    public abstract static class GetEncodingIndexNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        int getIndex(RubyEncoding encoding) {
            return encoding.index;
        }
    }

    // MRI: rb_enc_check_str / rb_encoding_check (with Ruby String arguments)
    // Like strings_compatible? but raises if incompatible
    @Primitive(name = "encoding_ensure_compatible_str")
    public abstract static class CheckStringEncodingPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization(guards = { "libFirst.isRubyString(this, first)", "libSecond.isRubyString(this, second)", },
                limit = "1")
        static RubyEncoding checkEncodingStringString(Object first, Object second,
                @Cached RubyStringLibrary libFirst,
                @Cached RubyStringLibrary libSecond,
                @Cached InlinedBranchProfile errorProfile,
                @Cached NegotiateCompatibleStringEncodingNode negotiateCompatibleStringEncodingNode,
                @Bind Node node) {
            final RubyEncoding firstEncoding = libFirst.getEncoding(node, first);
            final RubyEncoding secondEncoding = libSecond.getEncoding(node, second);

            final RubyEncoding negotiatedEncoding = negotiateCompatibleStringEncodingNode
                    .execute(node, libFirst.getTString(node, first), firstEncoding, libSecond.getTString(node, second),
                            secondEncoding);

            if (negotiatedEncoding == null) {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node),
                        coreExceptions(node).encodingCompatibilityErrorIncompatible(firstEncoding, secondEncoding,
                                node));
            }

            return negotiatedEncoding;
        }
    }

    // MRI: rb_enc_check_str / rb_encoding_check (with RopeWithEncoding arguments)
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CheckStringEncodingNode extends RubyBaseNode {

        public abstract RubyEncoding execute(Node node, AbstractTruffleString first,
                RubyEncoding firstEncoding, AbstractTruffleString second, RubyEncoding secondEncoding);

        @Specialization
        static RubyEncoding checkEncoding(
                Node node,
                AbstractTruffleString first,
                RubyEncoding firstEncoding,
                AbstractTruffleString second,
                RubyEncoding secondEncoding,
                @Cached InlinedBranchProfile errorProfile,
                @Cached NegotiateCompatibleStringEncodingNode negotiateCompatibleEncodingNode) {
            var negotiatedEncoding = negotiateCompatibleEncodingNode.execute(node, first, firstEncoding, second,
                    secondEncoding);

            if (negotiatedEncoding == null) {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node),
                        coreExceptions(node).encodingCompatibilityErrorIncompatible(firstEncoding, secondEncoding,
                                node));
            }

            return negotiatedEncoding;
        }

    }

    @Primitive(name = "encoding_unicode_version")
    public abstract static class UnicodeVersionNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyString getUnicodeVersion(
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return createString(fromJavaStringNode, Config.UNICODE_VERSION_STRING, Encodings.UTF_8);
        }
    }

    @Primitive(name = "encoding_unicode_emoji_version")
    public abstract static class UnicodeEmojiVersionNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyString getUnicodeEmojiVersion(
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return createString(fromJavaStringNode, Config.UNICODE_EMOJI_VERSION_STRING, Encodings.UTF_8);
        }
    }
}
