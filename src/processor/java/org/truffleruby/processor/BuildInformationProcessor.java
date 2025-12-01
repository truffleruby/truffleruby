/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import org.truffleruby.annotations.PopulateBuildInformation;

@SupportedAnnotationTypes("org.truffleruby.annotations.PopulateBuildInformation")
public class BuildInformationProcessor extends TruffleRubyProcessor {

    private static final String SUFFIX = "Impl";
    private static final Pattern VERSION_PATTERN = Pattern.compile("""
            "version":\\s*"([^"]+)\"""");
    private static final Pattern RELEASE_PATTERN = Pattern.compile("""
            "release":\\s*(True|False),""");

    private final Set<String> processed = new HashSet<>();

    private File trufflerubyHome;
    private String trufflerubyVersion;
    private String buildName;
    private String shortRevision;
    private String fullRevision;
    private boolean isDirty;
    private String commitDate;
    private int copyrightYear;
    private String kernelMajorVersion;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            trufflerubyHome = findHome();
            trufflerubyVersion = readTruffleRubyVersion();
            buildName = System.getenv("TRUFFLERUBY_BUILD_NAME");
            fullRevision = runCommand("git rev-parse HEAD")
                    .orElseThrow(() -> new Error("git rev-parse command failed"));
            shortRevision = fullRevision.substring(0, 8);
            isDirty = runCommand("git diff --quiet").isEmpty();
            commitDate = runCommand("git log -1 --date=short --pretty=format:%cd")
                    .orElseThrow(() -> new Error("git log command failed"));
            copyrightYear = Integer.parseInt(commitDate.split("\\-")[0]);
            kernelMajorVersion = findKernelMajorVersion();
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    private File findHome() throws URISyntaxException {
        final CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
        final File jarOrClassPath;
        if (codeSource == null || codeSource.getLocation() == null) {
            String className = getClass().getName().replace('.', '/') + ".class";
            URL location = getClass().getClassLoader().getResource(className);
            if (location == null) {
                throw new RuntimeException("Could not find the source code for " + getClass());
            }
            Matcher matcher = Pattern.compile("^file:(.+)!/.+\\.class$").matcher(location.getPath());
            if (!matcher.matches()) {
                throw new RuntimeException("Could not parse URL " + location.getPath());
            }
            jarOrClassPath = new File(matcher.group(1));
        } else {
            jarOrClassPath = new File(codeSource.getLocation().toURI());
        }

        // this is probably `mxbuild/org.truffleruby.processor/bin` or `mxbuild/dists/jdk1.8/truffleruby-processor.jar`
        // let's try to find `mxbuild`
        File source = jarOrClassPath;
        while (!source.getName().equals("mxbuild")) {
            source = source.getParentFile();
            if (source == null) {
                throw new RuntimeException(
                        "Could not find `mxbuild` in the source path for " + getClass() + ": " + jarOrClassPath);
            }
        }
        return source.getParentFile();
    }

    private String readTruffleRubyVersion() {
        String suiteFile = new File(trufflerubyHome, "mx.truffleruby/suite.py").getPath();
        String versionField = null;
        Boolean isRelease = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(suiteFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = VERSION_PATTERN.matcher(line);
                if (m.find()) {
                    versionField = m.group(1);
                }

                m = RELEASE_PATTERN.matcher(line);
                if (m.find()) {
                    isRelease = Boolean.parseBoolean(m.group(1));
                }

                if (versionField != null && isRelease != null) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new Error(e);
        }

        if (versionField == null || isRelease == null) {
            throw new Error("Could not find version and release in " + suiteFile);
        }

        if (isRelease) {
            return versionField;
        } else {
            return versionField + "-dev";
        }
    }

    private String findKernelMajorVersion() throws IOException, InterruptedException {
        final String kernelVersion = runCommand("uname -r").orElseThrow(() -> new Error("uname -r command failed"));
        return kernelVersion.split(Pattern.quote("."))[0];
    }

    private Optional<String> runCommand(String command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command.split("\\s+")).directory(trufflerubyHome).start();

        String firstLine;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            firstLine = reader.readLine();

            while (process.isAlive()) {
                reader.readLine();
            }
        }

        if (process.waitFor() != 0) {
            return Optional.empty();
        }

        if (firstLine == null) {
            firstLine = "";
        }

        return Optional.of(firstLine);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        assert isInitialized();
        if (!annotations.isEmpty()) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(PopulateBuildInformation.class)) {
                try {
                    processBuildInformation((TypeElement) element);
                } catch (Exception e) {
                    error(e.getClass() + " " + e.getMessage(), element);
                }
            }
        }

        return true;
    }

    private void processBuildInformation(TypeElement element) throws Exception {
        final PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        final String packageName = packageElement.getQualifiedName().toString();

        final String qualifiedName = element.getQualifiedName().toString();

        if (!processed.add(qualifiedName)) {
            // Already processed, do nothing. This seems an Eclipse bug.
            return;
        }

        final JavaFileObject output = processingEnv.getFiler().createSourceFile(qualifiedName + SUFFIX, element);

        try (PrintStream stream = new PrintStream(output.openOutputStream(), true, "UTF-8")) {
            stream.println("/*\n" +
                    " * Copyright (c) " + Calendar.getInstance().get(Calendar.YEAR) +
                    " Oracle and/or its affiliates. All rights reserved. This\n" +
                    " * code is released under a tri EPL/GPL/LGPL license. You can use it,\n" +
                    " * redistribute it and/or modify it under the terms of the:\n" +
                    " *\n" +
                    " * Eclipse Public License version 2.0, or\n" +
                    " * GNU General Public License version 2, or\n" +
                    " * GNU Lesser General Public License version 2.1.\n" +
                    " */");
            stream.println("package " + packageName + ";");
            stream.println();
            stream.println("// GENERATED BY " + getClass().getName());
            stream.println();
            stream.println(
                    "public class " + element.getSimpleName() + SUFFIX + " implements " + element.getSimpleName() +
                            " {");
            stream.println();
            stream.println(
                    "    public static final " + element.getSimpleName() + " INSTANCE = new " +
                            element.getSimpleName() + SUFFIX + "();");
            stream.println();

            for (Element e : element.getEnclosedElements()) {
                if (e instanceof ExecutableElement) {
                    final String name = e.getSimpleName().toString();

                    final Object value = switch (name) {
                        case "getTruffleRubyVersion" -> trufflerubyVersion;
                        case "getBuildName" -> buildName;
                        case "getShortRevision" -> shortRevision;
                        case "getFullRevision" -> fullRevision;
                        case "isDirty" -> isDirty;
                        case "getCopyrightYear" -> copyrightYear;
                        case "getCommitDate" -> commitDate;
                        case "getKernelMajorVersion" -> kernelMajorVersion;
                        default -> throw new UnsupportedOperationException(name + " method not understood");
                    };

                    stream.println("    @Override");
                    stream.println("    public " + ((ExecutableElement) e).getReturnType() + " " + name + "() {");
                    if (value == null) {
                        stream.println("        return null;");
                    } else if (value instanceof String) {
                        stream.println("        return \"" + value + "\";");
                    } else {
                        stream.println("        return " + value + ";");
                    }
                    stream.println("    }");
                    stream.println();
                }
            }

            stream.println("}");
        }
    }
}
