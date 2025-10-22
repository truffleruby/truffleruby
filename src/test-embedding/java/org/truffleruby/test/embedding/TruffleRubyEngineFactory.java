/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.embedding;

// From ../graal/docs/reference-manual/embedding/embed-languages.md

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public final class TruffleRubyEngineFactory implements ScriptEngineFactory {
    private static final String LANGUAGE_ID = "ruby";

    /*-*********************************************************/
    /* Everything below is generic and does not need to change */
    /*-*********************************************************/

    private final Engine polyglotEngine = Engine.newBuilder().build();
    private final Language language = polyglotEngine.getLanguages().get(LANGUAGE_ID);

    @Override
    public String getEngineName() {
        return language.getImplementationName();
    }

    @Override
    public String getEngineVersion() {
        return "RUBY_ENGINE_VERSION";
    }

    @Override
    public List<String> getExtensions() {
        return List.of(LANGUAGE_ID);
    }

    @Override
    public List<String> getMimeTypes() {
        return List.copyOf(language.getMimeTypes());
    }

    @Override
    public List<String> getNames() {
        return List.of(language.getName(), LANGUAGE_ID, language.getImplementationName());
    }

    @Override
    public String getLanguageName() {
        return language.getName();
    }

    @Override
    public String getLanguageVersion() {
        return language.getVersion();
    }

    @Override
    public Object getParameter(final String key) {
        switch (key) {
            case ScriptEngine.ENGINE:
                return getEngineName();
            case ScriptEngine.ENGINE_VERSION:
                return getEngineVersion();
            case ScriptEngine.LANGUAGE:
                return getLanguageName();
            case ScriptEngine.LANGUAGE_VERSION:
                return getLanguageVersion();
            case ScriptEngine.NAME:
                return LANGUAGE_ID;
        }
        return null;
    }

    @Override
    public String getMethodCallSyntax(final String obj, final String m, final String... args) {
        throw new UnsupportedOperationException("Unimplemented method 'getMethodCallSyntax'");
    }

    @Override
    public String getOutputStatement(final String toDisplay) {
        throw new UnsupportedOperationException("Unimplemented method 'getOutputStatement'");
    }

    @Override
    public String getProgram(final String... statements) {
        throw new UnsupportedOperationException("Unimplemented method 'getProgram'");
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new PolyglotEngine(this);
    }

    private static final class PolyglotEngine implements ScriptEngine, Compilable, Invocable, AutoCloseable {
        private final ScriptEngineFactory factory;
        private PolyglotContext defaultContext;

        PolyglotEngine(ScriptEngineFactory factory) {
            this.factory = factory;
            this.defaultContext = new PolyglotContext();
        }

        @Override
        public void close() {
            defaultContext.getContext().close();
        }

        @Override
        public CompiledScript compile(String script) throws ScriptException {
            Source src = Source.create(LANGUAGE_ID, script);
            try {
                defaultContext.getContext().parse(src); // only for the side-effect of validating the source
            } catch (PolyglotException e) {
                throw new ScriptException(e);
            }
            return new PolyglotCompiledScript(src, this);
        }

        @Override
        public CompiledScript compile(Reader script) throws ScriptException {
            Source src;
            try {
                src = Source.newBuilder(LANGUAGE_ID, script, "sourcefromreader").build();
                defaultContext.getContext().parse(src); // only for the side-effect of validating the source
            } catch (PolyglotException | IOException e) {
                throw new ScriptException(e);
            }
            return new PolyglotCompiledScript(src, this);
        }

        @Override
        public Object eval(String script, ScriptContext context) throws ScriptException {
            if (context instanceof PolyglotContext) {
                PolyglotContext c = (PolyglotContext) context;
                try {
                    return c.getContext().eval(LANGUAGE_ID, script).as(Object.class);
                } catch (PolyglotException e) {
                    throw new ScriptException(e);
                }
            } else {
                throw new ClassCastException("invalid context");
            }
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) throws ScriptException {
            Source src;
            try {
                src = Source.newBuilder(LANGUAGE_ID, reader, "sourcefromreader").build();
            } catch (IOException e) {
                throw new ScriptException(e);
            }
            if (context instanceof PolyglotContext) {
                PolyglotContext c = (PolyglotContext) context;
                try {
                    return c.getContext().eval(src).as(Object.class);
                } catch (PolyglotException e) {
                    throw new ScriptException(e);
                }
            } else {
                throw new ScriptException("invalid context");
            }
        }

        @Override
        public Object eval(String script) throws ScriptException {
            return eval(script, defaultContext);
        }

        @Override
        public Object eval(Reader reader) throws ScriptException {
            return eval(reader, defaultContext);
        }

        @Override
        public Object eval(String script, Bindings n) throws ScriptException {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public Object eval(Reader reader, Bindings n) throws ScriptException {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public void put(String key, Object value) {
            defaultContext.getBindings(ScriptContext.ENGINE_SCOPE).put(key, value);
        }

        @Override
        public Object get(String key) {
            return defaultContext.getBindings(ScriptContext.ENGINE_SCOPE).get(key);
        }

        @Override
        public Bindings getBindings(int scope) {
            return defaultContext.getBindings(scope);
        }

        @Override
        public void setBindings(Bindings bindings, int scope) {
            defaultContext.setBindings(bindings, scope);
        }

        @Override
        public Bindings createBindings() {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public ScriptContext getContext() {
            return defaultContext;
        }

        @Override
        public void setContext(ScriptContext context) {
            throw new UnsupportedOperationException("The context of a Polyglot ScriptEngine cannot be modified.");
        }

        @Override
        public ScriptEngineFactory getFactory() {
            return factory;
        }

        @Override
        public Object invokeMethod(Object thiz, String name, Object... args)
                throws ScriptException, NoSuchMethodException {
            try {
                Value receiver = defaultContext.getContext().asValue(thiz);
                if (receiver.canInvokeMember(name)) {
                    return receiver.invokeMember(name, args).as(Object.class);
                } else {
                    throw new NoSuchMethodException(name);
                }
            } catch (PolyglotException e) {
                throw new ScriptException(e);
            }
        }

        @Override
        public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInterface(Class<T> interfaceClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInterface(Object thiz, Class<T> interfaceClass) {
            return defaultContext.getContext().asValue(thiz).as(interfaceClass);
        }
    }

    private static final class PolyglotContext implements ScriptContext {
        private Context context;
        private final PolyglotReader in;
        private final PolyglotWriter out;
        private final PolyglotWriter err;
        private Bindings globalBindings;

        PolyglotContext() {
            this.in = new PolyglotReader(new InputStreamReader(System.in));
            this.out = new PolyglotWriter(new OutputStreamWriter(System.out));
            this.err = new PolyglotWriter(new OutputStreamWriter(System.err));
        }

        Context getContext() {
            if (context == null) {
                Context.Builder builder = Context.newBuilder(LANGUAGE_ID)
                        .in(this.in)
                        .out(this.out)
                        .err(this.err)
                        .allowAllAccess(true);
                Bindings globalBindings = getBindings(ScriptContext.GLOBAL_SCOPE);
                if (globalBindings != null) {
                    for (Entry<String, Object> entry : globalBindings.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            builder.option(entry.getKey(), (String) value);
                        }
                    }
                }
                context = builder.build();
            }
            return context;
        }

        @Override
        public void setBindings(Bindings bindings, int scope) {
            if (scope == ScriptContext.GLOBAL_SCOPE) {
                if (context == null) {
                    globalBindings = bindings;
                } else {
                    throw new UnsupportedOperationException(
                            "Global bindings for Polyglot language can only be set before the context is initialized.");
                }
            } else {
                throw new UnsupportedOperationException("Bindings objects for Polyglot language is final.");
            }
        }

        @Override
        public Bindings getBindings(int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                return new PolyglotBindings(getContext().getBindings(LANGUAGE_ID));
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                return globalBindings;
            } else {
                return null;
            }
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                getBindings(scope).put(name, value);
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                if (context == null) {
                    globalBindings.put(name, value);
                } else {
                    throw new IllegalStateException("Cannot modify global bindings after context creation.");
                }
            }
        }

        @Override
        public Object getAttribute(String name, int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                return getBindings(scope).get(name);
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                return globalBindings.get(name);
            }
            return null;
        }

        @Override
        public Object removeAttribute(String name, int scope) {
            Object prev = getAttribute(name, scope);
            if (prev != null) {
                if (scope == ScriptContext.ENGINE_SCOPE) {
                    getBindings(scope).remove(name);
                } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                    if (context == null) {
                        globalBindings.remove(name);
                    } else {
                        throw new IllegalStateException("Cannot modify global bindings after context creation.");
                    }
                }
            }
            return prev;
        }

        @Override
        public Object getAttribute(String name) {
            return getAttribute(name, ScriptContext.ENGINE_SCOPE);
        }

        @Override
        public int getAttributesScope(String name) {
            if (getAttribute(name, ScriptContext.ENGINE_SCOPE) != null) {
                return ScriptContext.ENGINE_SCOPE;
            } else if (getAttribute(name, ScriptContext.GLOBAL_SCOPE) != null) {
                return ScriptContext.GLOBAL_SCOPE;
            }
            return -1;
        }

        @Override
        public Writer getWriter() {
            return this.out.writer;
        }

        @Override
        public Writer getErrorWriter() {
            return this.err.writer;
        }

        @Override
        public void setWriter(Writer writer) {
            this.out.writer = writer;
        }

        @Override
        public void setErrorWriter(Writer writer) {
            this.err.writer = writer;
        }

        @Override
        public Reader getReader() {
            return this.in.reader;
        }

        @Override
        public void setReader(Reader reader) {
            this.in.reader = reader;
        }

        @Override
        public List<Integer> getScopes() {
            return List.of(ScriptContext.ENGINE_SCOPE, ScriptContext.GLOBAL_SCOPE);
        }

        private static final class PolyglotReader extends InputStream {
            private volatile Reader reader;

            public PolyglotReader(InputStreamReader inputStreamReader) {
                this.reader = inputStreamReader;
            }

            @Override
            public int read() throws IOException {
                return reader.read();
            }
        }

        private static final class PolyglotWriter extends OutputStream {
            private volatile Writer writer;

            public PolyglotWriter(OutputStreamWriter outputStreamWriter) {
                this.writer = outputStreamWriter;
            }

            @Override
            public void write(int b) throws IOException {
                writer.write(b);
            }
        }
    }

    private static final class PolyglotCompiledScript extends CompiledScript {
        private final Source source;
        private final ScriptEngine engine;

        public PolyglotCompiledScript(Source src, ScriptEngine engine) {
            this.source = src;
            this.engine = engine;
        }

        @Override
        public Object eval(ScriptContext context) throws ScriptException {
            if (context instanceof PolyglotContext) {
                return ((PolyglotContext) context).getContext().eval(source).as(Object.class);
            }
            throw new UnsupportedOperationException(
                    "Polyglot CompiledScript instances can only be evaluated in Polyglot.");
        }

        @Override
        public ScriptEngine getEngine() {
            return engine;
        }
    }

    private static final class PolyglotBindings implements Bindings {
        private Value languageBindings;

        PolyglotBindings(Value languageBindings) {
            this.languageBindings = languageBindings;
        }

        @Override
        public int size() {
            return keySet().size();
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean containsValue(Object value) {
            for (String s : keySet()) {
                if (get(s) == value) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for (String s : keySet()) {
                remove(s);
            }
        }

        @Override
        public Set<String> keySet() {
            return languageBindings.getMemberKeys();
        }

        @Override
        public Collection<Object> values() {
            List<Object> values = new ArrayList<>();
            for (String s : keySet()) {
                values.add(get(s));
            }
            return values;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> values = new HashSet<>();
            for (String s : keySet()) {
                values.add(new Entry<String, Object>() {
                    @Override
                    public String getKey() {
                        return s;
                    }

                    @Override
                    public Object getValue() {
                        return get(s);
                    }

                    @Override
                    public Object setValue(Object value) {
                        return put(s, value);
                    }
                });
            }
            return values;
        }

        @Override
        public Object put(String name, Object value) {
            Object previous = get(name);
            languageBindings.putMember(name, value);
            return previous;
        }

        @Override
        public void putAll(Map<? extends String, ? extends Object> toMerge) {
            for (Entry<? extends String, ? extends Object> e : toMerge.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public boolean containsKey(Object key) {
            if (key instanceof String) {
                return languageBindings.hasMember((String) key);
            } else {
                return false;
            }
        }

        @Override
        public Object get(Object key) {
            if (key instanceof String) {
                Value value = languageBindings.getMember((String) key);
                if (value != null) {
                    return value.as(Object.class);
                }
            }
            return null;
        }

        @Override
        public Object remove(Object key) {
            Object prev = get(key);
            if (prev != null) {
                languageBindings.removeMember((String) key);
                return prev;
            } else {
                return null;
            }
        }
    }
}
