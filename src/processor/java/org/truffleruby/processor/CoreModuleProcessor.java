/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.processor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;

import com.oracle.truffle.api.dsl.Specialization;

@SupportedAnnotationTypes("org.truffleruby.annotations.CoreModule")
public class CoreModuleProcessor extends TruffleRubyProcessor {

    private static final String SUFFIX = "Builtins";
    private static final Set<String> KEYWORDS;
    static {
        KEYWORDS = new HashSet<>();
        KEYWORDS.addAll(Arrays.asList(
                "alias",
                "and",
                "begin",
                "break",
                "case",
                "class",
                "def",
                "defined?",
                "do",
                "else",
                "elsif",
                "end",
                "ensure",
                "false",
                "for",
                "if",
                "in",
                "module",
                "next",
                "nil",
                "not",
                "or",
                "redo",
                "rescue",
                "retry",
                "return",
                "self",
                "super",
                "then",
                "true",
                "undef",
                "unless",
                "until",
                "when",
                "while",
                "yield"));
    }

    private final Set<String> processed = new HashSet<>();

    TypeMirror virtualFrameType;
    TypeMirror objectType;
    TypeMirror nilType;
    TypeMirror notProvidedType;
    TypeMirror rubyProcType;
    TypeMirror rootCallTargetType;
    // node types
    TypeMirror nodeType;
    TypeMirror rubyNodeType;
    TypeMirror rubyBaseNodeType;
    TypeMirror primitiveNodeType;
    TypeMirror coreMethodNodeType;
    TypeMirror alwaysInlinedMethodNodeType;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Elements elementUtils = processingEnv.getElementUtils();
        virtualFrameType = elementUtils.getTypeElement("com.oracle.truffle.api.frame.VirtualFrame").asType();
        objectType = elementUtils.getTypeElement("java.lang.Object").asType();
        nilType = elementUtils.getTypeElement("org.truffleruby.language.Nil").asType();
        notProvidedType = elementUtils.getTypeElement("org.truffleruby.language.NotProvided").asType();
        rubyProcType = elementUtils.getTypeElement("org.truffleruby.core.proc.RubyProc").asType();
        rootCallTargetType = elementUtils.getTypeElement("com.oracle.truffle.api.RootCallTarget").asType();
        nodeType = elementUtils.getTypeElement("com.oracle.truffle.api.nodes.Node").asType();
        rubyNodeType = elementUtils.getTypeElement("org.truffleruby.language.RubyNode").asType();
        rubyBaseNodeType = elementUtils.getTypeElement("org.truffleruby.language.RubyBaseNode").asType();
        primitiveNodeType = elementUtils.getTypeElement("org.truffleruby.builtins.PrimitiveNode").asType();
        coreMethodNodeType = elementUtils.getTypeElement("org.truffleruby.builtins.CoreMethodNode").asType();
        alwaysInlinedMethodNodeType = elementUtils
                .getTypeElement("org.truffleruby.core.inlined.AlwaysInlinedMethodNode").asType();


        if (!annotations.isEmpty()) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(CoreModule.class)) {
                try {
                    processCoreModule((TypeElement) element);
                } catch (IOException e) {
                    error(e.getClass() + " " + e.getMessage(), element);
                }
            }
        }

        return true;
    }

    private void processCoreModule(TypeElement coreModuleElement) throws IOException {
        final CoreModule coreModule = coreModuleElement.getAnnotation(CoreModule.class);

        final PackageElement packageElement = (PackageElement) coreModuleElement.getEnclosingElement();
        final String packageName = packageElement.getQualifiedName().toString();

        final String qualifiedName = coreModuleElement.getQualifiedName().toString();
        if (!processed.add(qualifiedName)) {
            // Already processed, do nothing. This seems an Eclipse bug.
            return;
        }

        final JavaFileObject output = processingEnv
                .getFiler()
                .createSourceFile(qualifiedName + SUFFIX, coreModuleElement);
        final FileObject rubyFile = processingEnv.getFiler().createResource(
                StandardLocation.SOURCE_OUTPUT,
                "core_module_stubs",
                coreModule.value().replace("::", "/") + ".rb",
                (Element[]) null);

        final CoreModuleChecks coreModuleChecks = new CoreModuleChecks(this);

        try (PrintStream stream = new PrintStream(output.openOutputStream(), true, "UTF-8")) {
            try (PrintStream rubyStream = new PrintStream(rubyFile.openOutputStream(), true, "UTF-8")) {

                final List<? extends Element> enclosedElements = coreModuleElement.getEnclosedElements();
                final boolean anyCoreMethod = anyCoreMethod(enclosedElements);

                stream.println("package " + packageName + ";");
                stream.println();
                stream.println("import org.truffleruby.builtins.CoreMethodNodeManager;");
                stream.println("import org.truffleruby.builtins.PrimitiveManager;");
                if (anyCoreMethod) {
                    stream.println("import org.truffleruby.annotations.Visibility;");
                    stream.println("import org.truffleruby.annotations.Split;");
                }
                stream.println();
                stream.println("// GENERATED BY " + getClass().getName());
                stream.println("public class " + coreModuleElement.getSimpleName() + SUFFIX + " {");
                stream.println();
                stream.println(
                        "    public static void setup(CoreMethodNodeManager coreMethodManager) {");

                rubyStream.println("raise 'this file is a stub file for development and should never be loaded'");
                rubyStream.println();
                rubyStream.println((coreModule.isClass() ? "class" : "module") + " " + coreModule.value());
                rubyStream.println();

                final StringBuilder rubyPrimitives = new StringBuilder();

                for (Element e : enclosedElements) {
                    if (e instanceof TypeElement) {
                        final TypeElement klass = (TypeElement) e;

                        final CoreMethod coreMethod = klass.getAnnotation(CoreMethod.class);
                        if (coreMethod != null) {
                            // Do not use needsSelf=true in module functions, it is either the module/class or the instance.
                            // Usage of needsSelf is quite rare for singleton methods (except constructors).
                            boolean hasSelfArgument = coreMethod.constructor() ||
                                    (!coreMethod.isModuleFunction() && !coreMethod.onSingleton() &&
                                            coreMethod.needsSelf());

                            coreModuleChecks.checks(coreMethod.lowerFixnum(), coreMethod, klass, hasSelfArgument);
                            if (!inherits(e.asType(), coreMethodNodeType) &&
                                    !inherits(e.asType(), alwaysInlinedMethodNodeType)) {
                                error(e +
                                        " should inherit from CoreMethodArrayArgumentsNode, CoreMethodNode, AlwaysInlinedMethodNode",
                                        e);
                            }
                            processCoreMethod(stream, rubyStream, coreModuleElement, coreModule, klass, coreMethod,
                                    hasSelfArgument);
                        }
                    }
                }

                stream.println("    }");
                stream.println();

                stream.println(
                        "    public static void setupPrimitives(PrimitiveManager primitiveManager) {");

                for (Element e : enclosedElements) {
                    if (e instanceof TypeElement) {
                        final TypeElement klass = (TypeElement) e;
                        final Primitive primitive = e.getAnnotation(Primitive.class);
                        if (primitive != null) {
                            processPrimitive(stream, rubyPrimitives, coreModuleElement, klass, primitive);
                            coreModuleChecks.checks(primitive.lowerFixnum(), null, klass, true);
                            if (!inherits(e.asType(), primitiveNodeType)) {
                                error(e +
                                        " should inherit from PrimitiveArrayArgumentsNode, PrimitiveNode",
                                        e);
                            }
                        }
                    }
                }

                stream.println("    }");
                stream.println();
                stream.println("}");

                rubyStream.println("end");
                rubyStream.println();

                rubyStream.println("module Primitive");
                rubyStream.print(rubyPrimitives);
                rubyStream.println("end");
                rubyStream.println();
            }
        }
    }

    private void processPrimitive(
            PrintStream stream,
            StringBuilder rubyPrimitives,
            TypeElement element,
            TypeElement klass,
            Primitive primitive) {
        List<String> argumentNames = getArgumentNames(klass, primitive.argumentNames(), false, false, -1);

        final String nodeFactory = nodeFactoryName(element, klass);
        stream.println("        primitiveManager.addLazyPrimitive(" +
                quote(primitive.name()) + ", " + quote(nodeFactory) + ");");

        final StringJoiner arguments = new StringJoiner(", ");
        for (String argument : argumentNames) {
            arguments.add(argument);
        }

        rubyPrimitives
                .append("  def self.")
                .append(primitive.name())
                .append("(")
                .append(arguments)
                .append(")")
                .append('\n');
        rubyPrimitives.append("    # language=java").append('\n');
        rubyPrimitives
                .append("    \"/** @see ")
                .append(klass.getQualifiedName().toString())
                .append(" */\"")
                .append('\n');
        rubyPrimitives.append("  end").append('\n');
        rubyPrimitives.append('\n');
    }

    private void processCoreMethod(
            PrintStream stream,
            PrintStream rubyStream,
            TypeElement element,
            CoreModule coreModule,
            TypeElement klass,
            CoreMethod coreMethod,
            boolean hasSelfArgument) {
        final StringJoiner names = new StringJoiner(", ");
        for (String name : coreMethod.names()) {
            names.add(quote(name));
        }
        // final String className = klass.getQualifiedName().toString();
        final String nodeFactory = nodeFactoryName(element, klass);
        final boolean onSingleton = coreMethod.onSingleton() || coreMethod.constructor();
        stream.println("        coreMethodManager.addLazyCoreMethod(" + quote(nodeFactory) + ",");
        stream.println("                " +
                quote(coreModule.value()) + ", " +
                coreModule.isClass() + ", " +
                "Visibility." + coreMethod.visibility().name() + ", " +
                coreMethod.isModuleFunction() + ", " +
                onSingleton + ", " +
                coreMethod.alwaysInlined() + ", " +
                "Split." + coreMethod.split().name() + ", " +
                coreMethod.required() + ", " +
                coreMethod.optional() + ", " +
                coreMethod.rest() + ", " +
                coreMethod.needsBlock() + ", " +
                names + ");");

        int numberOfArguments = getNumberOfArguments(coreMethod);
        String[] argumentNamesFromAnnotation = coreMethod.argumentNames();
        final List<String> argumentNames = getArgumentNames(klass, argumentNamesFromAnnotation, hasSelfArgument,
                coreMethod.alwaysInlined(),
                numberOfArguments);

        if (argumentNames.isEmpty() && numberOfArguments > 0) {
            error(
                    "Did not find argument names. If the class has inherited Specializations use org.truffleruby.annotations.CoreMethod.argumentNames",
                    klass);

            for (int i = 0; i < coreMethod.required(); i++) {
                argumentNames.add("req" + (i + 1));
            }
            for (int i = 0; i < coreMethod.optional(); i++) {
                argumentNames.add("opt" + (i + 1));
            }
            if (coreMethod.rest()) {
                argumentNames.add("args");
            }
            if (coreMethod.needsBlock()) {
                argumentNames.add("block");
            }
        }

        int index = 0;

        final StringJoiner args = new StringJoiner(", ");

        try {
            for (int i = 0; i < coreMethod.required(); i++) {
                args.add(argumentNames.get(index));
                index++;
            }
            for (int i = 0; i < coreMethod.optional(); i++) {
                args.add(argumentNames.get(index) + " = nil");
                index++;
            }
            if (coreMethod.rest()) {
                args.add("*" + argumentNames.get(index));
                index++;
            }
            if (coreMethod.needsBlock()) {
                args.add("&" + argumentNames.get(index));
            }
        } catch (IndexOutOfBoundsException e) {
            error(
                    "Not enough arguments found compared to declared numbers, check required, optional etc. declarations",
                    klass);
        }

        rubyStream.println("  def " + (onSingleton ? "self." : "") + coreMethod.names()[0] + "(" + args + ")");
        rubyStream.println("    # language=java");
        rubyStream.println("    \"/** @see " + klass.getQualifiedName().toString() + " */\"");
        rubyStream.println("  end");

        for (int i = 1; i < coreMethod.names().length; i++) {
            rubyStream.println("  alias_method :" + coreMethod.names()[i] + ", :" + coreMethod.names()[0]);
        }
        if (coreMethod.isModuleFunction()) {
            rubyStream.println("  module_function :" + coreMethod.names()[0]);
        }
        rubyStream.println();
    }

    private List<String> getArgumentNames(TypeElement klass, String[] argumentNamesFromAnnotation,
            boolean hasSelfArgument, boolean isAlwaysInlinedMethod, int numberOfArguments) {

        List<String> argumentNames;
        if (argumentNamesFromAnnotation.length == 0) {
            if (isAlwaysInlinedMethod) {
                argumentNames = getArgumentNamesForAlwaysInlined(numberOfArguments);
            } else {
                argumentNames = getArgumentNamesFromSpecializations(klass, hasSelfArgument);
            }
        } else {
            if (argumentNamesFromAnnotation.length != numberOfArguments && numberOfArguments >= 0) {
                error("The size of argumentNames does not match declared number of arguments.", klass);
                argumentNames = new ArrayList<>();
            } else {
                argumentNames = Arrays.asList(argumentNamesFromAnnotation);
            }
        }
        return argumentNames;
    }

    private int getNumberOfArguments(CoreMethod coreMethod) {
        return coreMethod.required() + coreMethod.optional() + (coreMethod.rest() ? 1 : 0) +
                (coreMethod.needsBlock() ? 1 : 0);
    }

    private List<String> getArgumentNamesForAlwaysInlined(int argCount) {
        List<String> argumentNames = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            argumentNames.add(String.format("arg%d", i + 1));
        }
        return argumentNames;
    }

    private List<String> getArgumentNamesFromSpecializations(TypeElement klass, boolean hasSelfArgument) {
        List<String> argumentNames = new ArrayList<>();
        List<VariableElement> argumentElements = new ArrayList<>();

        TypeElement klassIt = klass;
        while (!isNodeBaseType(klassIt)) {
            for (Element el : klassIt.getEnclosedElements()) {
                if (!(el instanceof ExecutableElement)) {
                    continue; // we are interested only in executable elements
                }

                final ExecutableElement specializationMethod = (ExecutableElement) el;

                Specialization specializationAnnotation = specializationMethod.getAnnotation(Specialization.class);
                if (specializationAnnotation == null) {
                    continue; // we are interested only in Specialization methods
                }

                boolean addingArguments = argumentNames.isEmpty();

                int index = 0;
                boolean skippedSelf = false;
                for (VariableElement parameter : specializationMethod.getParameters()) {
                    if (!parameter.getAnnotationMirrors().isEmpty()) {
                        continue; // we ignore arguments having annotations like @Cached
                    }

                    if (isSameType(parameter.asType(), virtualFrameType)) {
                        continue;
                    }

                    if (hasSelfArgument && !skippedSelf) {
                        skippedSelf = true;
                        continue;
                    }

                    String nameCanBeKeyword = parameter
                            .getSimpleName()
                            .toString()
                            .replaceAll("(.)(\\p{Upper})", "$1_$2")
                            .toLowerCase(Locale.ENGLISH)
                            .replaceAll("^(maybe|unused)_", "");
                    String name = KEYWORDS.contains(nameCanBeKeyword) ? nameCanBeKeyword + "_" : nameCanBeKeyword;


                    if (addingArguments) {
                        argumentNames.add(name);
                        argumentElements.add(parameter);
                    } else {
                        if (!argumentNames.get(index).equals(name)) {
                            error(
                                    "The argument does not match with the first occurrence of this argument which was '" +
                                            argumentElements.get(index).getSimpleName() + "' (translated to Ruby as '" +
                                            argumentNames.get(index) + "').",
                                    parameter);
                        }
                    }
                    index++;
                }

            }

            klassIt = processingEnv.getElementUtils().getTypeElement(klassIt.getSuperclass().toString());
        }

        return argumentNames;
    }

    public boolean isNodeBaseType(TypeElement typeElement) {
        return isSameType(typeElement.asType(), rubyNodeType) ||
                isSameType(typeElement.asType(), rubyBaseNodeType);
    }

    private boolean anyCoreMethod(List<? extends Element> enclosedElements) {
        for (Element e : enclosedElements) {
            if (e instanceof TypeElement && e.getAnnotation(CoreMethod.class) != null) {
                return true;
            }
        }
        return false;
    }

    private String nodeFactoryName(TypeElement element, TypeElement klass) {
        return element.getQualifiedName() + "Factory$" + klass.getSimpleName() + "Factory";
    }

    private static String quote(String str) {
        return '"' + str + '"';
    }

}
