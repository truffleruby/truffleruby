/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.processor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;

import org.truffleruby.annotations.CExtUpcall;

/** Generates the Native Image {@code foreign.directUpcalls} reachability metadata for every method annotated with
 * {@link CExtUpcall}, so that the FFM upcall stubs created for them use fast specialized direct upcall stubs instead of
 * the generic method-handle upcall machinery. No {@code foreign.upcalls} entries are needed: registering a direct
 * upcall also registers the generic upcall stub for its signature, and SVM silently falls back to that stub if the
 * method handle passed at runtime does not match the registered shape.
 *
 * <p>
 * Type names use the JNI-style names ({@code jlong}, {@code jint}, ...) which map to the same layouts on every
 * platform, unlike e.g. {@code long} which is 32-bit on Windows. */
@SupportedAnnotationTypes("org.truffleruby.annotations.CExtUpcall")
public class CExtUpcallProcessor extends TruffleRubyProcessor {

    private static final String METADATA_FILE = "META-INF/native-image/dev.truffleruby.internal/cext-upcalls/reachability-metadata.json";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        // class name -> sorted method name -> signature, for deterministic output
        final TreeMap<String, TreeMap<String, ExecutableElement>> upcalls = new TreeMap<>();

        for (Element element : roundEnvironment.getElementsAnnotatedWith(CExtUpcall.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error("@CExtUpcall is only applicable to methods", element);
                continue;
            }
            final ExecutableElement method = (ExecutableElement) element;
            if (!verifyPrimitiveSignature(method)) {
                continue;
            }
            final Element enclosing = method.getEnclosingElement();
            if (enclosing.getKind() != ElementKind.CLASS) {
                error("@CExtUpcall methods must be enclosed in a class", element);
                continue;
            }
            final String className = processingEnv.getElementUtils().getBinaryName((TypeElement) enclosing).toString();
            TreeMap<String, ExecutableElement> methods = upcalls.get(className);
            if (methods == null) {
                methods = new TreeMap<>();
                upcalls.put(className, methods);
            }
            methods.put(method.getSimpleName().toString(), method);
        }

        if (upcalls.isEmpty()) {
            return true;
        }

        try {
            generateMetadata(upcalls);
        } catch (IOException e) {
            error("IOException generating " + METADATA_FILE + ": " + e.getMessage(), null);
        }
        return true;
    }

    private boolean verifyPrimitiveSignature(ExecutableElement method) {
        final TypeKind returnKind = method.getReturnType().getKind();
        if (returnKind != TypeKind.VOID && !returnKind.isPrimitive()) {
            error("@CExtUpcall methods must have a primitive or void return type but was " + method.getReturnType(),
                    method);
            return false;
        }
        for (VariableElement parameter : method.getParameters()) {
            if (!parameter.asType().getKind().isPrimitive()) {
                error("@CExtUpcall methods must have only primitive parameters but got " + parameter.asType(),
                        parameter);
                return false;
            }
        }
        return true;
    }

    private void generateMetadata(TreeMap<String, TreeMap<String, ExecutableElement>> upcalls) throws IOException {
        final List<String> directUpcalls = new ArrayList<>();

        for (var classEntry : upcalls.entrySet()) {
            for (var methodEntry : classEntry.getValue().entrySet()) {
                final ExecutableElement method = methodEntry.getValue();
                final StringBuilder parameters = new StringBuilder();
                for (VariableElement parameter : method.getParameters()) {
                    if (parameters.length() > 0) {
                        parameters.append(", ");
                    }
                    parameters.append('"').append(jniName(parameter.asType())).append('"');
                }
                final String returnType = jniName(method.getReturnType());

                directUpcalls.add("""
                        {
                          "class": "%s",
                          "method": "%s",
                          "returnType": "%s",
                          "parameterTypes": [%s]
                        }""".formatted(classEntry.getKey(), methodEntry.getKey(), returnType, parameters));
            }
        }

        final var file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", METADATA_FILE);
        try (PrintStream stream = new PrintStream(file.openOutputStream(), true, "UTF-8")) {
            stream.println("{");
            stream.println("  \"foreign\": {");
            stream.println("    \"directUpcalls\": [");
            stream.println(String.join(",\n", directUpcalls));
            stream.println("    ]");
            stream.println("  }");
            stream.println("}");
        }
    }

    private static String jniName(TypeMirror type) {
        if (type.getKind() == TypeKind.VOID) {
            return "void";
        }
        assert type.getKind().isPrimitive();
        return "j" + type;
    }

}
