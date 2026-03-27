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
package org.truffleruby.tck;

import static org.graalvm.polyglot.tck.TypeDescriptor.ANY;
import static org.graalvm.polyglot.tck.TypeDescriptor.ARRAY;
import static org.graalvm.polyglot.tck.TypeDescriptor.BOOLEAN;
import static org.graalvm.polyglot.tck.TypeDescriptor.DATE;
import static org.graalvm.polyglot.tck.TypeDescriptor.HASH;
import static org.graalvm.polyglot.tck.TypeDescriptor.ITERABLE;
import static org.graalvm.polyglot.tck.TypeDescriptor.NULL;
import static org.graalvm.polyglot.tck.TypeDescriptor.NUMBER;
import static org.graalvm.polyglot.tck.TypeDescriptor.OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.STRING;
import static org.graalvm.polyglot.tck.TypeDescriptor.TIME;
import static org.graalvm.polyglot.tck.TypeDescriptor.TIME_ZONE;
import static org.graalvm.polyglot.tck.TypeDescriptor.array;
import static org.graalvm.polyglot.tck.TypeDescriptor.executable;
import static org.graalvm.polyglot.tck.TypeDescriptor.intersection;
import static org.graalvm.polyglot.tck.TypeDescriptor.union;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.Assert;

public class RubyTCKLanguageProvider implements LanguageProvider {

    @Override
    public String getId() {
        return "ruby";
    }

    @Override
    public Collection<? extends Source> createInvalidSyntaxScripts(Context context) {
        final List<Source> res = new ArrayList<>();
        res.add(Source.create(getId(), "{"));
        res.add(Source.create(getId(), "("));
        res.add(Source.create(getId(), "if no_end"));
        return Collections.unmodifiableList(res);
    }

    @Override
    public Value createIdentityFunction(Context context) {
        return context.eval(getId(), "-> v { v }");
    }

    @Override
    public Collection<? extends Snippet> createValueConstructors(Context context) {
        var emptyArrayType = TypeDescriptor.forValue(context.eval(Source.create(getId(), "[]")));

        final List<Snippet> vals = new ArrayList<>();
        // Interop Primitives
        vals.add(createValueConstructor(context, "nil", NULL)); // should also be OBJECT?
        vals.add(createValueConstructor(context, "false", BOOLEAN));
        // NOTE: NUMBER means InteropLibrary#isNumber
        vals.add(createValueConstructor(context, "0", NUMBER));
        vals.add(createValueConstructor(context, "7", NUMBER)); // int
        vals.add(createValueConstructor(context, "1 << 42", NUMBER)); // long
        vals.add(createValueConstructor(context, "1 << 84", NUMBER)); // Bignum
        vals.add(createValueConstructor(context, "3.14", NUMBER));
        vals.add(createValueConstructor(context, "'test'", STRING));
        vals.add(createValueConstructor(context, "'0123456789' + '0123456789'", STRING));

        // Everything but interop primitives have members in Ruby, so they are also OBJECT
        vals.add(createValueConstructor(context, "Rational(1, 3)", OBJECT));
        vals.add(createValueConstructor(context, "Complex(1, 2)", OBJECT));
        vals.add(createValueConstructor(context, "Time.now", DATE_TIME_ZONE_OBJECT));
        vals.add(createValueConstructor(context, "[1, 2]", NUMBER_ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "[1.2, 3.4]", NUMBER_ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "[1<<33, 1<<34]", NUMBER_ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "[]", emptyArrayType));
        vals.add(createValueConstructor(context, "[Object.new]", ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "[true, false]", BOOLEAN_ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "[Object.new, 65]", ARRAY_OBJECT));
        vals.add(createValueConstructor(context, "{ 'name' => 'test' }", HASH_ITERABLE_OBJECT));
        vals.add(createValueConstructor(context, "Struct.new(:foo, :bar).new(1, 'two')", ITERABLE_OBJECT));
        String objectWithIVar = "Object.new.tap { |obj| obj.instance_variable_set(:@name, 'test') }";
        vals.add(createValueConstructor(context, objectWithIVar, OBJECT));
        vals.add(createValueConstructor(context, "proc { }", intersection(OBJECT, executable(ANY, true))));
        vals.add(createValueConstructor(context, "lambda { }", intersection(OBJECT, executable(ANY, false))));
        // vals.add(createValueConstructor(context, ":itself.to_proc", alsoRegularObject(executable(ANY, false, ANY))));
        // vals.add(createValueConstructor(context, "1.method(:itself)", alsoRegularObject(executable(NUMBER, false))));
        return Collections.unmodifiableList(vals);
    }

    @Override
    public Collection<? extends Snippet> createExpressions(Context context) {
        final List<Snippet> ops = new ArrayList<>();

        // arithmetic
        ops.add(createBinaryOperator(context, "a + b", NUMBER, NUMBER, NUMBER));
        ops.add(createBinaryOperator(context, "a + b", STRING, STRING, STRING));
        // ops.add(createBinaryOperator(context, "a + b", ARRAY, ARRAY, ARRAY));
        ops.add(createBinaryOperator(context, "a - b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a - b", ARRAY, ARRAY, ARRAY));
        // ops.add(createBinaryOperator(context, "a * b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a * b", STRING, NUMBER, STRING));
        // ops.add(createBinaryOperator(context, "a * b", ARRAY, NUMBER, ARRAY));
        ops.add(createBinaryOperator(context, "a / b", NUMBER, NUMBER, NUMBER, run -> {
            Value rhs = run.getParameters().get(1);
            // Ideally we should test if both LHS and RHS are integral but interop gives us no way currently
            if (rhs.fitsInInt() && rhs.asInt() == 0 && run.getException() != null &&
                    run.getException().getMessage().equals("divided by 0")) {
                // OK, division by 0 raises
            } else {
                ResultVerifier.getDefaultResultVerifier().accept(run);
            }
        }));
        // ops.add(createBinaryOperator(context, "a % b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a ** b", NUMBER, NUMBER, NUMBER));

        // comparison
        // ops.add(createBinaryOperator(context, "a.equal?(b)", ANY, ANY, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a == b", ANY, ANY, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a != b", ANY, ANY, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a < b", NUMBER, NUMBER, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a > b", NUMBER, NUMBER, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a <= b", NUMBER, NUMBER, BOOLEAN));
        // ops.add(createBinaryOperator(context, "a >= b", NUMBER, NUMBER, BOOLEAN));

        // bitwise
        // ops.add(createBinaryOperator(context, "a << b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a >> b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a & b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a | b", NUMBER, NUMBER, NUMBER));
        // ops.add(createBinaryOperator(context, "a ^ b", NUMBER, NUMBER, NUMBER));

        // logical
        // ops.add(createBinaryOperator(context, "a && b", ANY, ANY, ANY));
        // ops.add(createBinaryOperator(context, "a || b", ANY, ANY, ANY));

        // unary operators
        // ops.add(createUnaryOperator(context, "+a", NUMBER, NUMBER));
        // ops.add(createUnaryOperator(context, "-a", NUMBER, NUMBER));
        // ops.add(createUnaryOperator(context, "~a", NUMBER, NUMBER));
        // ops.add(createUnaryOperator(context, "!a", ANY, BOOLEAN));

        return Collections.unmodifiableList(ops);
    }

    @Override
    public Collection<? extends Snippet> createStatements(Context context) {
        final List<Snippet> res = new ArrayList<>();
        res.add(createStatement(context, "if", "-> c { if c; true else false end };", ANY, BOOLEAN));
        res.add(createStatement(context, "unless", "-> c { unless c; true else false end };", ANY, BOOLEAN));
        res.add(createStatement(context, "postfix if", "-> c { true if c };", ANY, union(BOOLEAN, NULL)));
        res.add(createStatement(context, "postfix unless", "-> c { true unless c };", ANY, union(BOOLEAN, NULL)));
        res.add(createStatement(context, "while", "-> c { while c; break; end }", ANY, NULL));
        res.add(createStatement(context, "until", "-> c { until c; break; end }", ANY, NULL));
        res.add(createStatement(context, "do while", "-> c { begin; break; end while c }", ANY, NULL));
        res.add(createStatement(context, "do until", "-> c { begin; break; end until c }", ANY, NULL));
        // res.add(createStatement(context, "for", "-> array { for e in array do; end }", ARRAY, ARRAY));
        res.add(createStatement(context, "case", "-> e { case e; when Integer; 1; else 2; end }", ANY, NUMBER));
        // res.add(createStatement(context, "raise", "-> msg { begin; raise msg; rescue => e; e; end}", STRING, OBJECT));
        return Collections.unmodifiableList(res);
    }

    @Override
    public Collection<? extends Snippet> createScripts(Context context) {
        final List<Snippet> res = new ArrayList<>();

        Snippet.newBuilder("array of points", context.eval(getSource("points.rb")), array(OBJECT)).resultVerifier(
                run -> {
                    ResultVerifier.getDefaultResultVerifier().accept(run);
                    final Value result = run.getResult();
                    Assert.assertEquals("array size", 2, result.getArraySize());
                    Value p1 = result.getArrayElement(0);
                    Value p2 = result.getArrayElement(1);
                    Assert.assertEquals("res[0].x", 30, p1.getMember("x").asInt());
                    Assert.assertEquals("res[0].y", 15, p1.getMember("y").asInt());
                    Assert.assertEquals("res[1].x", 5, p2.getMember("x").asInt());
                    Assert.assertEquals("res[1].y", 7, p2.getMember("y").asInt());
                });

        Snippet.newBuilder("recursion", context.eval(getSource("recursion.rb")), array(NUMBER)).resultVerifier(run -> {
            ResultVerifier.getDefaultResultVerifier().accept(run);
            final Value result = run.getResult();
            Assert.assertEquals("array size", 3, result.getArraySize());
            Assert.assertEquals("res[0]", 3628800, result.getArrayElement(0).asInt());
            Assert.assertEquals("res[1]", 55, result.getArrayElement(1).asInt());
            Assert.assertEquals("res[2]", 125, result.getArrayElement(2).asInt());
        });

        return Collections.unmodifiableList(res);
    }

    @Override
    public Collection<? extends InlineSnippet> createInlineScripts(Context context) {
        List<InlineSnippet> res = new ArrayList<>();
        res.add(createInlineSnippet(
                context,
                getSource("lexical-context.rb"),
                16,
                "a + b + c",
                14 + 2 + 6));
        res.add(createInlineSnippet(
                context,
                getSource("lexical-context.rb"),
                16,
                "binding.local_variable_get(:a) + binding.local_variable_get(:b) + binding.local_variable_get(:c)",
                14 + 2 + 6));
        return Collections.unmodifiableList(res);
    }

    private InlineSnippet createInlineSnippet(Context context, Source mainSource, int line, String inlineSource,
            int expected) {
        final Snippet mainSnippet = Snippet
                .newBuilder(mainSource.getName(), context.eval(mainSource), TypeDescriptor.ANY)
                .build();

        return InlineSnippet
                .newBuilder(mainSnippet, inlineSource)
                .locationPredicate(
                        sourceSection -> sourceSection.getSource().getName().endsWith(mainSource.getName()) &&
                                sourceSection.getStartLine() == line)
                .resultVerifier(snippetRun -> {
                    final PolyglotException exception = snippetRun.getException();
                    if (exception != null) {
                        throw exception;
                    }
                    Assert.assertEquals(expected, snippetRun.getResult().asInt());
                })
                .build();
    }

    private Snippet createValueConstructor(Context context, String value, TypeDescriptor type) {
        return Snippet.newBuilder(value, context.eval(getId(), String.format("-> { %s }", value)), type).build();
    }

    // private Snippet createUnaryOperator(Context context, String operator, TypeDescriptor operandType,
    //         TypeDescriptor returnType) {
    //     final Value function = context.eval(getId(), String.format("-> a { %s }", operator));
    //     return Snippet.newBuilder(operator, function, returnType).parameterTypes(operandType).build();
    // }

    private Snippet createBinaryOperator(Context context, String operator, TypeDescriptor lhsType,
            TypeDescriptor rhsType, TypeDescriptor returnType) {
        return createBinaryOperator(context, operator, lhsType, rhsType, returnType,
                ResultVerifier.getDefaultResultVerifier());
    }

    private Snippet createBinaryOperator(Context context, String operator, TypeDescriptor lhsType,
            TypeDescriptor rhsType, TypeDescriptor returnType, ResultVerifier resultVerifier) {
        final Value function = context.eval(getId(), String.format("-> a, b { %s }", operator));

        return Snippet
                .newBuilder(operator, function, returnType)
                .parameterTypes(lhsType, rhsType)
                .resultVerifier(resultVerifier)
                .build();
    }

    private Snippet createStatement(Context context, String name, String expression, TypeDescriptor argumentType,
            TypeDescriptor returnType) {
        final Value function = context.eval(getId(), expression);
        return Snippet.newBuilder(name, function, returnType).parameterTypes(argumentType).build();
    }

    private Source getSource(String path) {
        try {
            final InputStream stream = ClassLoader.getSystemResourceAsStream(path);
            if (stream == null) {
                throw new FileNotFoundException(path);
            }

            final Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            return Source.newBuilder(getId(), reader, new File(path).getName()).build();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static final TypeDescriptor DATE_TIME_ZONE_OBJECT = intersection(OBJECT, DATE, TIME, TIME_ZONE);
    private static final TypeDescriptor ARRAY_OBJECT = intersection(OBJECT, ARRAY);
    private static final TypeDescriptor NUMBER_ARRAY_OBJECT = intersection(OBJECT, array(NUMBER));
    private static final TypeDescriptor BOOLEAN_ARRAY_OBJECT = intersection(OBJECT, array(BOOLEAN));
    private static final TypeDescriptor ITERABLE_OBJECT = intersection(ITERABLE, OBJECT);
    private static final TypeDescriptor HASH_ITERABLE_OBJECT = intersection(HASH, ITERABLE, OBJECT);
}
