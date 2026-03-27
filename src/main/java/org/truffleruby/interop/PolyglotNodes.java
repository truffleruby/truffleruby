/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import java.io.IOException;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.utils.Utils;

@CoreModule("Polyglot")
public abstract class PolyglotNodes {

    @CoreMethod(names = "eval", onSingleton = true, required = 2)
    @ReportPolymorphism // inline cache
    @ImportStatic(StringOperations.class)
    public abstract static class EvalNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                guards = {
                        "stringsId.isRubyString(this, langId)",
                        "stringsSource.isRubyString(this, code)",
                        "idEqualNode.execute(stringsId, langId, cachedLangId, cachedLangIdEnc)",
                        "codeEqualNode.execute(stringsSource, code, cachedCode, cachedCodeEnc)" },
                limit = "getCacheLimit()")
        static Object evalCached(Object langId, Object code,
                @Cached @Exclusive RubyStringLibrary stringsId,
                @Cached @Exclusive RubyStringLibrary stringsSource,
                @Cached("asTruffleStringUncached(langId)") TruffleString cachedLangId,
                @Cached("stringsId.getEncoding($node, langId)") RubyEncoding cachedLangIdEnc,
                @Cached("asTruffleStringUncached(code)") TruffleString cachedCode,
                @Cached("stringsSource.getEncoding($node, code)") RubyEncoding cachedCodeEnc,
                @Bind Node node,
                @Cached("create(parse(node, getJavaString(langId), getJavaString(code)))") DirectCallNode callNode,
                @Cached StringHelperNodes.EqualNode idEqualNode,
                @Cached StringHelperNodes.EqualNode codeEqualNode) {
            return callNode.call(EMPTY_ARGUMENTS);
        }

        @Specialization(
                guards = { "stringsId.isRubyString(this, langId)", "stringsSource.isRubyString(this, code)" },
                replaces = "evalCached", limit = "1")
        static Object evalUncached(Object langId, Object code,
                @Cached @Exclusive RubyStringLibrary stringsId,
                @Cached @Exclusive RubyStringLibrary stringsSource,
                @Cached ToJavaStringNode toJavaStringLandNode,
                @Cached ToJavaStringNode toJavaStringCodeNode,
                @Cached IndirectCallNode callNode,
                @Bind Node node) {
            return callNode.call(parse(node, toJavaStringLandNode.execute(node, langId),
                    toJavaStringCodeNode.execute(node, code)), EMPTY_ARGUMENTS);
        }

        @TruffleBoundary
        protected static CallTarget parse(Node node, String langId, String code) {
            final Source source = Source.newBuilder(langId, code, "(eval)").build();
            try {
                return getContext(node).getEnv().parsePublic(source);
            } catch (IllegalStateException e) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).argumentError(e.getMessage(), node));
            }
        }

        protected int getCacheLimit() {
            return getLanguage().options.EVAL_CACHE;
        }

    }

    @CoreMethod(
            names = "eval_file",
            onSingleton = true,
            required = 1,
            optional = 1,
            argumentNames = { "file_name_or_id", "file_name" })
    public abstract static class EvalFileNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "stringsId.isRubyString(this, fileName)")
        Object evalFile(Object fileName, NotProvided id,
                @Cached @Shared RubyStringLibrary stringsId) {
            final Source source;
            // intern() to improve footprint
            final String path = StringOperations.getJavaString(fileName).intern();
            try {
                final TruffleFile file = getContext().getEnv().getPublicTruffleFile(path);
                String language = Source.findLanguage(file);
                if (language == null) {
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().argumentError(
                                    "Could not find language of file " + path,
                                    this));
                }
                source = Source.newBuilder(language, file).build();
            } catch (IOException e) {
                throw new RaiseException(getContext(), coreExceptions().ioError(e, this));
            }

            return eval(source);
        }

        @TruffleBoundary
        @Specialization(guards = { "stringsId.isRubyString(this, id)", "stringsFileName.isRubyString(this, fileName)" },
                limit = "1")
        Object evalFile(Object id, Object fileName,
                @Cached @Shared RubyStringLibrary stringsId,
                @Cached @Exclusive RubyStringLibrary stringsFileName) {
            final String idString = StringOperations.getJavaString(id);
            final Source source = getSource(idString, StringOperations.getJavaString(fileName));
            return eval(source);
        }

        private Object eval(Source source) {
            final CallTarget callTarget;
            try {
                callTarget = getContext().getEnv().parsePublic(source);
            } catch (IllegalStateException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return callTarget.call();
        }

        private Source getSource(String language, String fileName) {
            //intern() to improve footprint
            final String path = fileName.intern();
            try {
                final TruffleFile file = getContext().getEnv().getPublicTruffleFile(path);
                return Source.newBuilder(language, file).build();
            } catch (IOException e) {
                throw new RaiseException(getContext(), coreExceptions().ioError(e, this));
            }
        }

    }

    @Primitive(name = "inner_context_new")
    public abstract static class InnerContextNewNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyInnerContext newInnerContext(
                RubyClass rubyClass,
                RubyArray languages,
                RubyArray languageOptions,
                boolean inheritAllAccess,
                Object codeSharing,
                RubyProc onCancelledCallback) {
            String[] permittedLanguages = new String[languages.size];
            int i = 0;
            for (Object language : ArrayOperations.toIterable(languages)) {
                permittedLanguages[i++] = StringOperations.getJavaString(language);
            }

            var builder = getContext().getEnv().newInnerContextBuilder(permittedLanguages);

            var iterator = ArrayOperations.toIterable(languageOptions).iterator();
            while (iterator.hasNext()) {
                Object key = iterator.next();
                Object value = iterator.next();
                builder.option(StringOperations.getJavaString(key), StringOperations.getJavaString(value));
            }

            Boolean codeSharingBoolean = codeSharing == nil ? null : (boolean) codeSharing;

            final TruffleContext innerContext = builder
                    .initializeCreatorContext(false)
                    .inheritAllAccess(inheritAllAccess)
                    .forceSharing(codeSharingBoolean)
                    .onCancelled(() -> CallBlockNode.yieldUncached(onCancelledCallback))
                    .build();

            return new RubyInnerContext(
                    rubyClass,
                    getLanguage().innerContextShape,
                    innerContext);
        }

    }

    // Inlined profiles/nodes are @Exclusive to fix truffle-interpreted-performance warning
    @Primitive(name = "inner_context_eval")
    @ReportPolymorphism // inline cache
    @ImportStatic(StringOperations.class)
    public abstract static class InnerContextEvalNode extends PrimitiveArrayArgumentsNode {
        @Specialization(guards = {
                "idLib.isRubyString(node, langId)",
                "codeLib.isRubyString(node, code)",
                "filenameLib.isRubyString(node, filename)",
                "idEqualNode.execute(idLib, langId, cachedLangId, cachedLangIdEnc)",
                "codeEqualNode.execute(codeLib, code, cachedCode, cachedCodeEnc)",
                "filenameEqualNode.execute(filenameLib, filename, cachedFilename, cachedFilenameEnc)" },
                limit = "getCacheLimit()")
        static Object evalCached(RubyInnerContext rubyInnerContext, Object langId, Object code, Object filename,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary idLib,
                @Cached @Exclusive RubyStringLibrary codeLib,
                @Cached @Exclusive RubyStringLibrary filenameLib,
                @Cached("asTruffleStringUncached(langId)") TruffleString cachedLangId,
                @Cached("idLib.getEncoding(node, langId)") RubyEncoding cachedLangIdEnc,
                @Cached("asTruffleStringUncached(code)") TruffleString cachedCode,
                @Cached("codeLib.getEncoding(node, code)") RubyEncoding cachedCodeEnc,
                @Cached("asTruffleStringUncached(filename)") TruffleString cachedFilename,
                @Cached("filenameLib.getEncoding(node, filename)") RubyEncoding cachedFilenameEnc,
                @Cached("createSource(getJavaString(langId), getJavaString(code), getJavaString(filename))") Source cachedSource,
                @Cached StringHelperNodes.EqualNode idEqualNode,
                @Cached StringHelperNodes.EqualNode codeEqualNode,
                @Cached StringHelperNodes.EqualNode filenameEqualNode,
                @Cached @Exclusive ForeignToRubyNode foreignToRubyNode,
                @Cached @Exclusive InlinedBranchProfile errorProfile) {
            return eval(node, rubyInnerContext, cachedSource, foreignToRubyNode, errorProfile);
        }

        @Specialization(
                guards = { "idLib.isRubyString(node, langId)", "codeLib.isRubyString(node, code)" }, limit = "1",
                replaces = "evalCached")
        static Object evalUncached(RubyInnerContext rubyInnerContext, Object langId, Object code, Object filename,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary idLib,
                @Cached @Exclusive RubyStringLibrary codeLib,
                @Cached @Exclusive RubyStringLibrary filenameLib,
                @Cached ToJavaStringNode toJavaStringIDNode,
                @Cached ToJavaStringNode toJavaStringCodeNode,
                @Cached ToJavaStringNode toJavaStringFileNode,
                @Cached @Exclusive ForeignToRubyNode foreignToRubyNode,
                @Cached @Exclusive InlinedBranchProfile errorProfile) {
            final String idString = toJavaStringIDNode.execute(node, langId);
            final String codeString = toJavaStringCodeNode.execute(node, code);
            final String filenameString = toJavaStringFileNode.execute(node, filename);

            final Source source = createSource(idString, codeString, filenameString);

            return eval(node, rubyInnerContext, source, foreignToRubyNode, errorProfile);
        }

        private static Object eval(Node node, RubyInnerContext rubyInnerContext, Source source,
                ForeignToRubyNode foreignToRubyNode, InlinedBranchProfile errorProfile) {
            final Object result;
            try {
                result = rubyInnerContext.innerContext.evalPublic(node, source);
            } catch (IllegalStateException closed) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).runtimeError("This Polyglot::InnerContext is closed", node));
            } catch (ThreadDeath closed) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).runtimeError("Polyglot::InnerContext was terminated forcefully",
                                node));
            } catch (IllegalArgumentException unknownLanguage) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).argumentError(Utils.concat("Unknown language: ", source.getLanguage()),
                                node));
            }
            return foreignToRubyNode.execute(node, result);
        }

        @TruffleBoundary
        protected static Source createSource(String idString, String codeString, String filename) {
            return Source.newBuilder(idString, codeString, filename).build();
        }

        protected int getCacheLimit() {
            return getLanguage().options.EVAL_CACHE;
        }
    }

    @Primitive(name = "inner_context_close")
    public abstract static class InnerContextCloseNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object close(RubyInnerContext rubyInnerContext) {
            rubyInnerContext.innerContext.close();
            return nil;
        }
    }

    @Primitive(name = "inner_context_close_force")
    public abstract static class InnerContextCloseExitedNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object closeExited(RubyInnerContext rubyInnerContext) {
            rubyInnerContext.innerContext.closeCancelled(this, "force terminate");
            return nil;
        }
    }

}
