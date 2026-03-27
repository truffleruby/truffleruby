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
package org.truffleruby.test.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.truffleruby.RubyFileTypeDetector;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyRootNode;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;

public class RubyFileTypeDetectorTest extends RubyTest {

    private static final TestCase[] EMPTY_TEST_CASE_ARRAY = new TestCase[0];

    @Test
    public void testDirect() {
        final RubyFileTypeDetector fileTypeDetector = new RubyFileTypeDetector();
        testWithAST("", new Consumer<RubyRootNode>() {
            @Override
            public void accept(RubyRootNode rootNode) {
                TruffleLanguage.Env env = RubyLanguage.getCurrentContext().getEnv();
                try {
                    for (TestCase testCase : getTestCases()) {
                        TruffleFile file = env.getPublicTruffleFile(testCase.path.toString());
                        if (testCase.hasRubyMimeType) {
                            assertEquals(
                                    testCase.path.toString(),
                                    RubyLanguage.MIME_TYPE,
                                    fileTypeDetector.findMimeType(file));
                        } else {
                            assertNotEquals(
                                    testCase.path.toString(),
                                    RubyLanguage.MIME_TYPE,
                                    fileTypeDetector.findMimeType(file));
                        }
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        });
    }

    @Test
    public void testIndirect() throws IOException {
        for (TestCase testCase : getTestCases()) {
            if (testCase.hasRubyMimeType) {
                assertEquals(
                        testCase.path.toString(),
                        RubyLanguage.MIME_TYPE,
                        Source.findMimeType(testCase.path.toFile()));
            } else {
                assertNotEquals(
                        testCase.path.toString(),
                        RubyLanguage.MIME_TYPE,
                        Source.findMimeType(testCase.path.toFile()));
            }
        }
    }

    @Test
    public void testEncoding() {
        final RubyFileTypeDetector fileTypeDetector = new RubyFileTypeDetector();
        testWithAST("", rootNode -> {
            TruffleLanguage.Env env = RubyLanguage.getCurrentContext().getEnv();
            try {
                for (TestCase testCase : getTestCases()) {
                    if (testCase.hasRubyMimeType) {
                        TruffleFile file = env.getPublicTruffleFile(testCase.path.toString());
                        assertEquals(testCase.encoding, fileTypeDetector.findEncoding(file));
                    }
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
    }

    private static TestCase[] getTestCases() throws IOException {
        final Path tempDirectory = Files.createTempDirectory("truffleruby");
        tempDirectory.toFile().deleteOnExit();

        final List<TestCase> testCases = new ArrayList<>();

        testCases.add(new TestCase(createFile(tempDirectory, "test.rb", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "TESTUP.RB", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "Gemfile", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "Rakefile", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "Mavenfile", "puts 'hello'"), false, null));
        testCases.add(new TestCase(createFile(tempDirectory, "test.rake", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "test.gemspec", "puts 'hello'"), true, null));
        testCases.add(new TestCase(createFile(tempDirectory, "shebang", "#!/usr/bin/ruby\nputs 'hello'"), true, null));
        testCases.add(
                new TestCase(
                        createFile(tempDirectory, "env-shebang", "#!/usr/bin/env ruby\nputs 'hello'"),
                        true,
                        null));
        testCases.add(
                new TestCase(createFile(tempDirectory, "test.norb", "# encoding: UTF-8\nputs 'hello'"), false, null));
        testCases.add(
                new TestCase(
                        createFile(tempDirectory, "encoding1.rb", "# encoding: UTF-8\nputs 'hello'"),
                        true,
                        StandardCharsets.UTF_8));
        testCases.add(
                new TestCase(
                        createFile(tempDirectory, "encoding2.rb", "# coding: UTF-8\nputs 'hello'"),
                        true,
                        StandardCharsets.UTF_8));
        testCases.add(
                new TestCase(
                        createFile(tempDirectory, "encoding3.rb", "# -*- coding: UTF-8 -*-\nputs 'hello'"),
                        true,
                        StandardCharsets.UTF_8));
        testCases.add(
                new TestCase(
                        createFile(
                                tempDirectory,
                                "shebang-encoding",
                                "#!/usr/bin/ruby\n# encoding: UTF-8\nputs 'hello'"),
                        true,
                        StandardCharsets.UTF_8));
        testCases.add(
                new TestCase(
                        createFile(
                                tempDirectory,
                                "env-shebang-encoding",
                                "#!/usr/bin/env ruby\n# encoding: UTF-8\nputs 'hello'"),
                        true,
                        StandardCharsets.UTF_8));
        return testCases.toArray(EMPTY_TEST_CASE_ARRAY);
    }

    private static Path createFile(Path parent, String name, String contents) throws IOException {
        final Path file = Files.createFile(parent.resolve(name));
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        return file;
    }

    private static final class TestCase {
        final Path path;
        final boolean hasRubyMimeType;
        final Charset encoding;

        private TestCase(Path path, boolean hasRubyMimeType, Charset encoding) {
            this.path = path;
            this.hasRubyMimeType = hasRubyMimeType;
            this.encoding = encoding;
        }
    }

}
