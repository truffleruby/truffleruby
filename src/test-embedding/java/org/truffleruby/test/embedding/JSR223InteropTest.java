/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.truffleruby.test.embedding.fixtures.FluidForce;

public class JSR223InteropTest {

    private ScriptEngine scriptEngine = null;

    @Before
    public void before() {
        scriptEngine = new TruffleRubyEngineFactory().getScriptEngine();
    }

    @After
    public void after() {
        if (scriptEngine != null) {
            close(scriptEngine);
            scriptEngine = null;
        }
    }

    private static void close(ScriptEngine scriptEngine) {
        try {
            ((AutoCloseable) scriptEngine).close();
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @Test
    public void testVersion() {
        assertNotNull(new TruffleRubyEngineFactory().getEngineVersion());
    }

    @Test
    public void testCreateEngine() throws ScriptException {
        assertEquals(42, scriptEngine.eval("6 * 7"));
    }

    @Test
    public void testAllAccess() throws ScriptException {
        assertTrue(scriptEngine.eval("Process.pid") instanceof Integer);
    }

    @Test
    public void testParameters() throws ScriptException {
        final Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("a", 14);
        bindings.put("b", 2);
        assertEquals(16, scriptEngine.eval("Truffle::Boot::INTERACTIVE_BINDING.eval('a + b')"));
    }

    @Test
    public void testCallingMethods() throws ScriptException, NoSuchMethodException {
        assertEquals(
                0.909,
                (double) ((Invocable) scriptEngine).invokeMethod(scriptEngine.eval("Math"), "sin", 2),
                0.01);
    }

    @Test
    public void testCreatingObjects() throws ScriptException, NoSuchMethodException {
        final Object time = ((Invocable) scriptEngine).invokeMethod(scriptEngine.eval("Time"), "new", 2021, 3, 18);
        final Object year = ((Invocable) scriptEngine).invokeMethod(time, "year");
        assertEquals(2021, year);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAccessingArrays() throws ScriptException {
        assertEquals(4, ((List<Object>) scriptEngine.eval("[3, 4, 5]")).get(1));
    }

    @Test
    public void testAccessingHashes() throws ScriptException, NoSuchMethodException {
        assertEquals(
                4,
                (int) ((Invocable) scriptEngine).invokeMethod(
                        scriptEngine.eval("{'a' => 3, 'b' => 4, 'c' => 5}"),
                        "fetch",
                        'b'));
    }

    @Test
    public void testImplementInterface() throws ScriptException {
        final FluidForce fluidForce = ((Invocable) scriptEngine)
                .getInterface(scriptEngine.eval(FluidForce.RUBY_SOURCE), FluidForce.class);
        assertEquals(5587.008375144088, fluidForce.getFluidForce(2.0, 3.0, 6.0), 0.01);
    }

    @Test
    public void testParseOnceRunMany() throws ScriptException {
        final CompiledScript compiled = ((Compilable) scriptEngine).compile("14");
        assertEquals(14, compiled.eval());
    }

}
