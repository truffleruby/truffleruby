/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.collections.ConcurrentWeakSet;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.MethodEntry;
import org.truffleruby.core.method.MethodFilter;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.PerformanceWarningNode;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.constants.ConstantEntry;
import org.truffleruby.language.constants.GetConstantNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.loader.ReentrantLockFreeingMap;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.IsFrozenNodeGen;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;
import org.truffleruby.language.objects.classvariables.ClassVariableStorage;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class ModuleFields extends ModuleChain implements ObjectGraphNode {

    public static void debugModuleChain(RubyModule module) {
        ModuleChain chain = module.fields;
        final StringBuilder builder = new StringBuilder();
        while (chain != null) {
            builder.append(chain.getClass());
            if (!(chain instanceof PrependMarker)) {
                RubyModule real = chain.getActualModule();
                builder.append(" " + real.fields.getName());
            }
            builder.append(System.lineSeparator());
            chain = chain.getParentModule();
        }
        RubyLanguage.LOGGER.info(builder.toString());
    }

    public final RubyModule rubyModule;

    /** The language is stored here so that we don't need to pass the language to the many callers of getName() (some of
     * which we can't like toString()). It shouldn't be used for anything else than {@link #setName(String)} (instead
     * pass the RubyLanguage as an argument). */
    private final RubyLanguage languageForSetName;
    private final SourceSection sourceSection;

    private final PrependMarker start;

    /**
     * <pre>
     * Naming modules is complicated, we try to summarize the transitions here.
     * fully named (simple): module M; end -> module with givenBaseName="M" hasFullName=true
     * fully named (nested): module N; module M; end; end -> module with name="N::M" hasFullName=true
     * anonymous: Module.new -> #name is nil, #inspect is #<Module:0x...>
     * nested-anonymous: m = Module.new; module m::N; end -> givenBaseName="N" hasFullName=false hasPartialName=true #name is "#<Module:0x...>::N"
     *
     * Transitions:
     * * anonymous to nested-anonymous, by constant assignment
     * * anonymous/nested-anonymous to full, by constant assignment (for anonymous), or lexical parent gets fully named (for nested-anonymous)
     * * temporary to full, by constant assignment or lexical parent gets fully named
     * * anonymous/nested-anonymous to temporary, by set_temporary_name("...") or set_temporary_name(nil)
     * * temporary to temporary, by set_temporary_name("...") or set_temporary_name(nil)
     * They form a lattice/total order, as it's never possible to go back from a transition
     *
     * Note there is no temporary to anonymous/nested-anonymous, it just stays temporary in that case.
     * </pre>
     */

    private final RubyModule lexicalParent;
    /** Module (or class) own explicitly specified name: either `class Foo` or `module Foo` or a core module/class */
    public final String givenBaseName;
    /** Means whether a fully qualified name (with parent lexical scope names) is already set */
    private boolean hasFullName = false;
    /** Either fully qualified name or lazily evaluated anonymous name, or temporary name */
    private String name = null;
    /** Same as {@link #name} except null if {@link #hasPartialName()} */
    private ImmutableRubyString rubyStringName;
    /** A temporary name assigned to an anonymous module */
    private String temporaryName = null;
    /** Whether a temporary name is assigned. Is needed to distinguish a case when nil value is assigned. */
    private boolean isTemporaryNameAssigned = false;

    /** Whether this is a refinement module (R), created by #refine */
    private boolean isRefinement = false;
    /** The module or class (C) refined by this refinement module */
    private RubyModule refinedModule;
    /** The namespace module (M) around the #refine call */
    private RubyModule refinementNamespace;

    private final ConcurrentMap<String, MethodEntry> methods = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConstantEntry> constants = new ConcurrentHashMap<>();
    private final ClassVariableStorage classVariables;

    /** The refinements (calls to Module#refine) nested under/contained in this namespace module (M). Represented as a
     * map of refined classes and modules (C) to refinement modules (R). */
    private final ConcurrentMap<RubyModule, RubyModule> refinements = new ConcurrentHashMap<>();

    /** Only set for a Module and not a Class since there is no usage of it for a Class */
    private final CyclicAssumption hierarchyUnmodifiedAssumption;

    // Concurrency: only modified during boot
    private final Map<String, Assumption> inlinedBuiltinsAssumptions = new HashMap<>();

    /** A weak set of all classes (for methods) or modules (for constants) include-ing or prepend-ing this module M, and
     * of modules which where already prepended before to such classes. Used by method and constant invalidation so that
     * lookups before the include/prepend is done can be invalidated when a method is added later in M. When M is an
     * included module, defining a method on M only needs to invalidate classes in which M is included. However, when M
     * is a prepended module defining a method on M needs to invalidate classes and other modules which were prepended
     * before M (since the method could be defined there, and then the assumption on M would not be checked). Not that
     * there is no need to be transitive here, include/prepend snapshot the modules to include alongside M, and later
     * M.include/M.prepend have no effect on classes already including M. In other words,
     * {@code module A; end; module B; end; class C; end; C.include A; A.include B; C.ancestors.include?(B) => false} */
    private final ConcurrentWeakSet<RubyModule> includedBy;

    @TruffleBoundary
    public ModuleFields(
            RubyLanguage language,
            SourceSection sourceSection,
            RubyModule lexicalParent,
            String givenBaseName,
            RubyModule rubyModule /* not fully initialized yet, should not access any field of it */) {
        super(null);
        this.languageForSetName = language;
        this.sourceSection = sourceSection;
        this.lexicalParent = lexicalParent;
        this.givenBaseName = givenBaseName;
        this.rubyModule = rubyModule;
        this.hierarchyUnmodifiedAssumption = rubyModule instanceof RubyClass
                ? null
                : new CyclicAssumption("hierarchy is unmodified");
        classVariables = new ClassVariableStorage(language);
        start = new PrependMarker(this);
        this.includedBy = rubyModule instanceof RubyClass ? null : new ConcurrentWeakSet<>();

        if (lexicalParent == null && givenBaseName != null) {
            setFullName(givenBaseName);
        }
    }

    /** Compute the name eagerly now as it can cause a Shape transition. We need to do so because
     * InteropLibrary$Asserts.assertMetaObject calls getMetaQualifiedName() and if getName() at that point adds the
     * object_id property then accepts() asserts that follow will fail. We cannot call this in the ModuleFields
     * constructor as the name depends on fields of RubyClass (e.g., isSingleton). */
    public void afterConstructed() {
        getName();
    }

    private void nameModule(
            RubyContext context,
            RubyModule lexicalParent,
            String name) {
        assert name != null;

        if (!hasFullName()) {
            if (lexicalParent == getObjectClass()) {
                this.setFullName(name);
                nameChildrenModules(context);
            } else if (lexicalParent.fields.hasFullName()) {
                this.setFullName(lexicalParent.fields.getName() + "::" + name);
                nameChildrenModules(context);
            } else {
                // Our lexicalParent is also an anonymous module
                // and will name us when it gets named via nameChildrenModules(),
                // or we'll compute an anonymous name on #getName() if needed
            }
        }
    }

    private void nameChildrenModules(RubyContext context) {
        for (Map.Entry<String, ConstantEntry> entry : constants.entrySet()) {
            ConstantEntry constantEntry = entry.getValue();
            RubyConstant constant = constantEntry.getConstant();

            if (constant != null && constant.hasValue() && constant.getValue() instanceof RubyModule childModule) {
                childModule.fields.nameModule(context, rubyModule, entry.getKey());
            }
        }
    }

    public boolean hasPrependedModules() {
        return start.getParentModule() != this;
    }

    public ModuleChain getFirstModuleChain() {
        return start.getParentModule();
    }

    @TruffleBoundary
    public void initCopy(RubyModule from) {
        // Do not copy name, the copy is an anonymous module
        final ModuleFields fromFields = from.fields;

        for (MethodEntry methodEntry : fromFields.methods.values()) {
            if (methodEntry.getMethod() != null) {
                MethodEntry newMethodEntry = new MethodEntry(methodEntry
                        .getMethod()
                        .withDeclaringModule(rubyModule)
                        .withOwner(rubyModule));
                this.methods.put(methodEntry.getMethod().getName(), newMethodEntry);
            }
        }

        for (Entry<String, ConstantEntry> entry : fromFields.constants.entrySet()) {
            final RubyConstant constant = entry.getValue().getConstant();
            if (constant != null) {
                this.constants.put(entry.getKey(), new ConstantEntry(constant));
            }
        }

        for (Object key : fromFields.classVariables.getShape().getKeys()) {
            final Object value = fromFields.classVariables.read((String) key, DynamicObjectLibrary.getUncached());
            if (value != null) { // do not copy if it was removed concurrently
                this.classVariables.put((String) key, value, DynamicObjectLibrary.getUncached());
            }
        }

        if (fromFields.hasPrependedModules()) {
            // Then the parent is the first in the prepend chain
            this.parentModule = fromFields.start.getParentModule();
        } else {
            this.parentModule = fromFields.parentModule;
        }
    }

    public void checkFrozen(RubyContext context, Node currentNode) {
        if (context.getCoreLibrary() != null && IsFrozenNodeGen.getUncached().execute(rubyModule)) {
            String name;
            Object receiver = rubyModule;
            if (rubyModule instanceof RubyClass) {
                final RubyClass cls = (RubyClass) rubyModule;
                name = "object";
                if (cls.isSingleton) {
                    receiver = cls.attached;
                    if (cls.attached instanceof RubyClass) {
                        name = "Class";
                    } else if (cls.attached instanceof RubyModule) {
                        name = "Module";
                    }
                } else {
                    name = "class";
                }
            } else {
                name = "module";
            }
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().frozenError(
                            receiver,
                            name,
                            currentNode));
        }
    }

    @TruffleBoundary
    public void include(RubyContext context, Node currentNode, RubyModule module) {
        checkFrozen(context, currentNode);

        // If the module we want to include already includes us, it is cyclic
        if (ModuleOperations.includesModule(module, rubyModule)) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError("cyclic include detected", currentNode));
        }

        SharedObjects.propagate(context.getLanguageSlow(), rubyModule, module);

        // We need to include the module ancestors in reverse order for a given inclusionPoint
        ModuleChain inclusionPoint = this;
        Deque<RubyModule> modulesToInclude = new ArrayDeque<>();
        for (RubyModule ancestor : module.fields.ancestors()) {
            if (ModuleOperations.includesModule(rubyModule, ancestor)) {
                if (isIncludedModuleBeforeSuperClass(ancestor)) {
                    // Include the modules at the appropriate inclusionPoint
                    performIncludes(context, inclusionPoint, modulesToInclude, currentNode);
                    modulesToInclude.clear();

                    // We need to include the others after that module
                    inclusionPoint = parentModule;
                    while (inclusionPoint.getActualModule() != ancestor) {
                        inclusionPoint = inclusionPoint.getParentModule();
                    }
                } else {
                    // Just ignore this module, as it is included above the superclass
                }
            } else {
                modulesToInclude.push(ancestor);
            }
        }

        performIncludes(context, inclusionPoint, modulesToInclude, currentNode);

        // update ancestors chains of classes/modules that include current module
        // and add a new included module (and its ancestors)
        if (!(rubyModule instanceof RubyClass)) { // it should be RubyModule only
            for (RubyModule child : includedBy) {
                child.fields.include(context, currentNode, rubyModule);
            }
        }

        newHierarchyVersion();
    }

    private void performIncludes(RubyContext context, ModuleChain target, Deque<RubyModule> modules,
            Node node) {
        for (RubyModule module : modules) {
            target.insertAfter(module);

            // Module#include only adds modules behind the current class/module, so invalidating
            // the current class/module is enough as all affected lookups would go through the current class/module.
            module.fields.includedBy.add(rubyModule);
            newConstantsVersion(module.fields.getConstantNames());
            if (rubyModule instanceof RubyClass) {
                // M.include(N) just registers N but does nothing for methods until C.include/prepend(M)
                newMethodsVersion(context, module.fields.getMethodNames(), node);
            }
        }
    }

    private boolean isIncludedModuleBeforeSuperClass(RubyModule module) {
        ModuleChain included = parentModule;
        while (included instanceof IncludedModule) {
            if (included.getActualModule() == module) {
                return true;
            }
            included = included.getParentModule();
        }
        return false;
    }

    @TruffleBoundary
    public void prepend(RubyContext context, Node currentNode, RubyModule module) {
        checkFrozen(context, currentNode);

        // If the module we want to prepend already includes us, it is cyclic
        if (ModuleOperations.includesModule(module, rubyModule)) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError("cyclic prepend detected", currentNode));
        }

        SharedObjects.propagate(context.getLanguageSlow(), rubyModule, module);

        /* We need to invalidate all prepended modules and the current class/module, because call sites which looked up
         * methods/constants before only check the current class/module *OR* one of the prepended module (if the
         * method/constant is defined there). */
        final List<RubyModule> prependedModulesAndSelf = getPrependedModulesAndSelf();

        ModuleChain mod = module.fields.start;
        ModuleChain cur = start;
        while (mod != null &&
                !(mod instanceof ModuleFields && ((ModuleFields) mod).rubyModule instanceof RubyClass)) {
            if (!(mod instanceof PrependMarker)) {
                final RubyModule toPrepend = mod.getActualModule();
                if (!ModuleOperations.includesModule(rubyModule, toPrepend)) {
                    cur.insertAfter(toPrepend);

                    final List<String> constantsToInvalidate = toPrepend.fields.getConstantNames();
                    final boolean isClass = rubyModule instanceof RubyClass;
                    final List<String> methodsToInvalidate = isClass ? toPrepend.fields.getMethodNames() : null;
                    for (RubyModule moduleToInvalidate : prependedModulesAndSelf) {
                        toPrepend.fields.includedBy.add(moduleToInvalidate);
                        moduleToInvalidate.fields.newConstantsVersion(constantsToInvalidate);
                        if (isClass) {
                            // M.prepend(N) just registers N but does nothing for methods until C.prepend/include(M)
                            moduleToInvalidate.fields.newMethodsVersion(context, methodsToInvalidate, currentNode);
                        }
                    }

                    cur = cur.getParentModule();
                }
            }
            mod = mod.getParentModule();
        }

        // If there were already prepended modules, invalidate the first of them
        newHierarchyVersion();
    }

    private List<RubyModule> getPrependedModulesAndSelf() {
        final List<RubyModule> prependedModulesAndSelf = new ArrayList<>();
        ModuleChain chain = getFirstModuleChain();
        while (chain != this) {
            prependedModulesAndSelf.add(chain.getActualModule());
            chain = chain.getParentModule();
        }
        prependedModulesAndSelf.add(rubyModule);
        return prependedModulesAndSelf;
    }

    /** Set the value of a constant, possibly redefining it. */
    @TruffleBoundary
    public RubyConstant setConstant(RubyContext context, Node currentNode, String name, Object value) {
        // A module fully qualified name should replace a temporary one before assigning a constant,
        // and before calling in the #const_added callback.
        if (value instanceof RubyModule childModule) {
            childModule.fields.nameModule(context, rubyModule, name);
        }

        return setConstantInternal(context, currentNode, name, value, false);
    }

    @TruffleBoundary
    public void setAutoloadConstant(RubyLanguage language, RubyContext context, Node currentNode, String name,
            Object filename, String javaFilename) {
        RubyConstant autoloadConstant = setConstantInternal(context, currentNode, name, filename, true);
        if (autoloadConstant == null) {
            return;
        }

        if (context.getOptions().LOG_AUTOLOAD) {
            RubyLanguage.LOGGER.info(() -> String.format(
                    "%s: setting up autoload %s with %s",
                    language.fileLine(context.getCallStack().getTopMostUserSourceSection()),
                    autoloadConstant,
                    filename));
        }
        final ReentrantLockFreeingMap<String> fileLocks = context.getFeatureLoader().getFileLocks();
        final ReentrantLock lock = fileLocks.get(javaFilename);
        if (lock.isLocked()) {
            // We need to handle the new autoload constant immediately
            // if Object.autoload(name, filename) is executed from filename.rb
            GetConstantNode.autoloadConstantStart(context, autoloadConstant, currentNode);
        }

        context.getFeatureLoader().addAutoload(autoloadConstant);
    }

    @TruffleBoundary
    private RubyConstant setConstantInternal(RubyContext context, Node currentNode, String name, Object value,
            boolean autoload) {
        checkFrozen(context, currentNode);

        SharedObjects.propagate(context.getLanguageSlow(), rubyModule, value);

        final String autoloadPath = autoload
                ? StringOperations.getJavaString(value)
                : null;
        RubyConstant previous;
        RubyConstant newConstant;
        ConstantEntry previousEntry;
        do {
            previousEntry = constants.get(name);
            previous = previousEntry != null ? previousEntry.getConstant() : null;
            if (autoload && previous != null) {
                if (previous.hasValue()) {
                    // abort, do not set an autoload constant, the constant already has a value
                    return null;
                } else if (previous.isAutoload() &&
                        previous.getAutoloadConstant().getAutoloadPath().equals(autoloadPath)) {
                    // already an autoload constant with the same path,
                    // do nothing so we don't replace the AutoloadConstant#autoloadLock which might be already acquired
                    return null;
                }
            }
            newConstant = newConstant(currentNode, name, value, autoload, previous);
        } while (!ConcurrentOperations.replace(constants, name, previousEntry, new ConstantEntry(newConstant)));

        if (previousEntry != null) {
            previousEntry.invalidate("set", rubyModule, name);
        }

        if (includedBy != null) {
            invalidateConstantIncludedBy(name);
        }

        if (context.isConstAddedEverDefined()) {
            final RubySymbol nameSymbol = context.getLanguageSlow().getSymbol(name);
            RubyContext.send(currentNode, rubyModule, "const_added", nameSymbol);
        }

        return autoload ? newConstant : previous;
    }

    private RubyConstant newConstant(Node currentNode, String name, Object value, boolean autoload,
            RubyConstant previous) {
        final boolean isPrivate = previous != null && previous.isPrivate();
        final boolean isDeprecated = previous != null && previous.isDeprecated();
        final SourceSection sourceSection = currentNode != null ? currentNode.getSourceSection() : null;
        return new RubyConstant(rubyModule, name, value, isPrivate, autoload, isDeprecated, sourceSection);
    }

    @TruffleBoundary
    public RubyConstant removeConstant(RubyContext context, Node currentNode, String name) {
        checkFrozen(context, currentNode);
        final ConstantEntry oldConstant = constants.remove(name);
        if (oldConstant != null) {
            oldConstant.invalidate("remove", rubyModule, name);
            return oldConstant.getConstant();
        } else {
            return null;
        }
    }

    @TruffleBoundary
    public void addMethod(RubyContext context, Node currentNode, InternalMethod method) {
        assert context.getCoreLibrary().state != CoreLibrary.State.CREATED : "should not add methods yet";
        assert ModuleOperations.canBindMethodTo(method, rubyModule) ||
                ModuleOperations.assignableTo(context.getCoreLibrary().objectClass, method.getDeclaringModule()) ||
                // TODO (pitr-ch 24-Jul-2016): find out why undefined methods sometimes do not match above assertion
                // e.g. "block in _routes route_set.rb:525" in rails/actionpack/lib/action_dispatch/routing/
                (method.isUndefined() && methods.get(method.getName()) != null);

        final String name = method.getName();
        checkFrozen(context, currentNode);

        method = method.withOwner(rubyModule);

        if (SharedObjects.isShared(rubyModule)) {
            Set<Object> adjacent = ObjectGraph.newObjectSet();
            ObjectGraph.addProperty(adjacent, method);
            for (Object object : adjacent) {
                SharedObjects.writeBarrier(context.getLanguageSlow(), object);
            }
        }

        MethodEntry methodEntry;
        Assumption assumption;
        if (context.getCoreLibrary().isInitializing() && (assumption = inlinedBuiltinsAssumptions.get(name)) != null) {
            methodEntry = new MethodEntry(method, assumption);
        } else {
            methodEntry = new MethodEntry(method);
        }

        MethodEntry previousMethodEntry = methods.put(name, methodEntry);

        if (!context.getCoreLibrary().isInitializing()) {
            if (previousMethodEntry != null) {
                previousMethodEntry.invalidate(context, rubyModule, name, currentNode);
            }

            if (includedBy != null) {
                invalidateMethodIncludedBy(context, name, currentNode);
            }

            if (refinedModule != null) {
                refinedModule.fields.refinedMethod(context, method.getName(), currentNode);
            }
        }

        if (context.getCoreLibrary().isLoaded() && !method.isUndefined()) {
            /* method_added/singleton_method_added should only be called if there wasn't already the same method
             * definition for the given name on this rubyModule. Otherwise, it's just a change of visibility or so, not
             * "a new method", and that should not call method_added/singleton_method_added. */
            if (previousMethodEntry == null || previousMethodEntry.getMethod() == null ||
                    previousMethodEntry.getMethod().getSharedMethodInfo() != method.getSharedMethodInfo()) {

                final RubySymbol methodSymbol = context.getLanguageSlow().getSymbol(name);
                if (RubyGuards.isSingletonClass(rubyModule)) {
                    RubyDynamicObject receiver = ((RubyClass) rubyModule).attached;
                    RubyContext.send(currentNode, receiver, "singleton_method_added", methodSymbol);
                } else {
                    RubyContext.send(currentNode, rubyModule, "method_added", methodSymbol);
                }
            }
        }

        // track if ever a custom Module.const_add callback is defined and ignore a default one
        if (context.getCoreLibrary().isLoaded() && name.equals("const_added")) {
            context.constAddedIsDefined();
        }
    }

    @TruffleBoundary
    public boolean removeMethod(RubyContext context, String methodName, Node node) {
        final InternalMethod method = getMethod(methodName);
        if (method == null) {
            return false;
        }

        MethodEntry removedEntry = methods.remove(methodName);
        if (removedEntry != null) {
            removedEntry.invalidate(context, rubyModule, methodName, node);
        }

        return true;
    }

    @TruffleBoundary
    public void undefMethod(RubyLanguage language, RubyContext context, Node currentNode, String methodName) {
        checkFrozen(context, currentNode);

        final InternalMethod method = ModuleOperations.lookupMethodUncached(rubyModule, methodName, null);
        if (method == null || method.isUndefined()) {
            final RubyModule moduleForError;
            if (RubyGuards.isMetaClass(rubyModule)) {
                moduleForError = (RubyModule) ((RubyClass) rubyModule).attached;
            } else {
                moduleForError = rubyModule;
            }

            throw new RaiseException(context, context.getCoreExceptions().nameErrorUndefinedMethod(
                    methodName,
                    moduleForError,
                    currentNode));
        } else {
            addMethod(context, currentNode, method.undefined());

            final RubySymbol methodSymbol = language.getSymbol(methodName);
            if (RubyGuards.isSingletonClass(rubyModule)) {
                final RubyDynamicObject receiver = ((RubyClass) rubyModule).attached;
                RubyContext.send(currentNode, receiver, "singleton_method_undefined", methodSymbol);
            } else {
                RubyContext.send(currentNode, rubyModule, "method_undefined", methodSymbol);
            }
        }
    }

    /** Also searches on Object for modules. Used for alias_method, visibility changes, etc. */
    @TruffleBoundary
    public InternalMethod deepMethodSearch(RubyContext context, String name) {
        InternalMethod method = ModuleOperations.lookupMethodUncached(rubyModule, name, null);
        if (method != null && !method.isUndefined()) {
            return method;
        }

        // Also search on Object if we are a Module. JRuby calls it deepMethodSearch().
        if (!(rubyModule instanceof RubyClass)) { // TODO: handle undefined methods
            method = ModuleOperations.lookupMethodUncached(context.getCoreLibrary().objectClass, name, null);

            if (method != null && !method.isUndefined()) {
                return method;
            }
        }

        if (isRefinement()) {
            return getRefinedModule().fields.deepMethodSearch(context, name);
        } else {
            return null;
        }
    }

    @TruffleBoundary
    public void changeConstantVisibility(
            final RubyContext context,
            final Node currentNode,
            final String name,
            final boolean isPrivate) {

        while (true) {
            final ConstantEntry previousEntry = constants.get(name);
            final RubyConstant previous = previousEntry != null ? previousEntry.getConstant() : null;

            if (previous == null) {
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().nameErrorUninitializedConstant(
                                rubyModule,
                                name,
                                currentNode));
            }

            if (constants.replace(name, previousEntry, new ConstantEntry(previous.withPrivate(isPrivate)))) {
                previousEntry.invalidate("change visibility", rubyModule, name);
                break;
            }
        }
    }

    @TruffleBoundary
    public void deprecateConstant(RubyContext context, Node currentNode, String name) {
        while (true) {
            final ConstantEntry previousEntry = constants.get(name);
            final RubyConstant previous = previousEntry != null ? previousEntry.getConstant() : null;

            if (previous == null) {
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().nameErrorUninitializedConstant(
                                rubyModule,
                                name,
                                currentNode));
            }

            if (constants.replace(name, previousEntry, new ConstantEntry(previous.withDeprecated()))) {
                if (previousEntry != null) {
                    previousEntry.invalidate("deprecate", rubyModule, name);
                }
                break;
            }
        }
    }

    @TruffleBoundary
    public boolean undefineConstantIfStillAutoload(RubyConstant autoloadConstant) {
        final ConstantEntry constantEntry = constants.get(autoloadConstant.getName());
        final boolean replace = constantEntry != null && constantEntry.getConstant() == autoloadConstant;
        if (replace &&
                constants.replace(
                        autoloadConstant.getName(),
                        constantEntry,
                        new ConstantEntry(autoloadConstant.undefined()))) {
            constantEntry.invalidate("undefine if still autoload", rubyModule, autoloadConstant.getName());
            return true;
        } else {
            return false;
        }
    }

    public String getName() {
        // Lazily compute the anonymous name because it is expensive
        if (name == null) {
            recomputeAnonymousOrTemporaryName();
        }

        assert name != null;
        return name;
    }

    @TruffleBoundary
    public String getSimpleName() {
        String name = getName();
        int i = name.lastIndexOf("::");
        if (i == -1) {
            return name;
        } else {
            return name.substring(i + "::".length());
        }
    }

    @TruffleBoundary
    private void recomputeAnonymousOrTemporaryName() {
        if (!isAnonymousOrTemporary()) {
            return;
        }

        final String newName;
        if (temporaryName != null) {
            newName = temporaryName;
        } else {
            newName = createFullyQualifiedAnonymousName();
        }

        setName(newName);
    }

    public void setFullName(String name) {
        assert name != null;
        hasFullName = true;
        setName(name);
        resetTemporaryName();
    }

    private void setName(String name) {
        this.name = name;
        if (hasPartialName()) {
            this.rubyStringName = languageForSetName.getImmutableString(TStringUtils.utf8TString(name),
                    Encodings.UTF_8);
        } else {
            this.rubyStringName = null;
        }
    }

    public void setTemporaryName(String name) {
        this.temporaryName = name;
        this.isTemporaryNameAssigned = true;
        recomputeAnonymousOrTemporaryName();
    }

    private void resetTemporaryName() {
        temporaryName = null;
        isTemporaryNameAssigned = false;
    }

    public Object getRubyStringName() {
        if (hasPartialName()) {
            if (rubyStringName == null) {
                getName(); // compute the name
            }
            assert rubyStringName != null;
            return rubyStringName;
        } else {
            return Nil.INSTANCE;
        }
    }

    @TruffleBoundary
    private String createFullyQualifiedAnonymousName() {
        if (givenBaseName != null) {
            if (lexicalParent == getObjectClass()) {
                return givenBaseName;
            } else {
                return lexicalParent.fields.getName() + "::" + givenBaseName;
            }
        } else if (getLogicalClass() == rubyModule) { // For the case of class Class during initialization
            return "#<cyclic>";
        } else if (RubyGuards.isSingletonClass(rubyModule)) {
            final RubyDynamicObject attached = ((RubyClass) rubyModule).attached;
            final String attachedName;
            if (attached instanceof RubyModule) {
                attachedName = ((RubyModule) attached).fields.getName();
            } else {
                attachedName = KernelNodes.ToSNode.uncachedBasicToS(attached);
            }
            return "#<Class:" + attachedName + ">";
        } else if (isRefinement) {
            return getRefinementName();
        } else {
            return KernelNodes.ToSNode.uncachedBasicToS(rubyModule);
        }
    }

    @TruffleBoundary
    public String getRefinementName() {
        assert isRefinement;
        return "#<refinement:" + refinedModule.fields.getName() + "@" + refinementNamespace.fields.getName() + ">";
    }

    public boolean hasFullName() {
        return hasFullName;
    }

    /** Whether Module#name Ruby method returns a value other than nil */
    public boolean hasPartialName() {
        if (isTemporaryNameAssigned) {
            return temporaryName != null;
        }
        return hasFullName() || givenBaseName != null;
    }

    public boolean isAnonymousOrTemporary() {
        return !this.hasFullName;
    }

    private boolean isClass() {
        return rubyModule instanceof RubyClass;
    }

    public boolean isRefinement() {
        return isRefinement;
    }

    public void setupRefinementModule(RubyModule refinedModule, RubyModule refinementNamespace) {
        this.isRefinement = true;
        this.refinedModule = refinedModule;
        this.refinementNamespace = refinementNamespace;
        this.parentModule = refinedModule.fields.start;
    }

    public RubyModule getRefinedModule() {
        return refinedModule;
    }

    public RubyModule getRefinementNamespace() {
        return refinementNamespace;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + getName() + ")";
    }

    public void newHierarchyVersion() {
        if (!isClass()) {
            hierarchyUnmodifiedAssumption.invalidate(getName());
        }
    }

    private void invalidateMethodIncludedBy(RubyContext context, String method, Node node) {
        for (RubyModule module : includedBy) {
            module.fields.newMethodVersion(context, method, node);
        }
    }

    private void invalidateConstantIncludedBy(String constantName) {
        for (RubyModule module : includedBy) {
            module.fields.newConstantVersion(constantName);
        }
    }

    public void newConstantsVersion(Collection<String> constantsToInvalidate) {
        for (String name : constantsToInvalidate) {
            newConstantVersion(name);
        }
    }

    public void newConstantVersion(String constantToInvalidate) {
        while (true) {
            final ConstantEntry constantEntry = constants.get(constantToInvalidate);
            if (constantEntry == null) {
                return;
            } else {
                if (constants.replace(constantToInvalidate, constantEntry, constantEntry.withNewAssumption())) {
                    constantEntry.invalidate("newConstantVersion", rubyModule, constantToInvalidate);
                    return;
                }
            }
        }
    }

    public void newMethodsVersion(RubyContext context, Collection<String> methodsToInvalidate, Node node) {
        for (String name : methodsToInvalidate) {
            newMethodVersion(context, name, node);
        }
    }

    private void newMethodVersion(RubyContext context, String methodToInvalidate, Node node) {
        while (true) {
            final MethodEntry methodEntry = methods.get(methodToInvalidate);
            if (methodEntry == null) {
                return;
            } else {
                if (methods.replace(methodToInvalidate, methodEntry, methodEntry.withNewAssumption())) {
                    methodEntry.invalidate(context, rubyModule, methodToInvalidate, node);
                    return;
                }
            }
        }
    }

    public Assumption getHierarchyUnmodifiedAssumption() {
        assert !isClass();
        return hierarchyUnmodifiedAssumption.getAssumption();
    }

    public Iterable<Entry<String, ConstantEntry>> getConstants() {
        return constants.entrySet();
    }

    @TruffleBoundary
    public ConstantEntry getOrComputeConstantEntry(String name) {
        return ConcurrentOperations.getOrCompute(constants, name, (n) -> new ConstantEntry());
    }

    @TruffleBoundary
    public RubyConstant getConstant(String name) {
        final ConstantEntry constantEntry = constants.get(name);
        return constantEntry != null ? constantEntry.getConstant() : null;
    }

    private static final class MethodsIterator implements Iterator<InternalMethod> {
        final Iterator<MethodEntry> methodEntries;
        InternalMethod nextElement;

        MethodsIterator(Collection<MethodEntry> methodEntries) {
            this.methodEntries = methodEntries.iterator();
            computeNext();
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @Override
        public InternalMethod next() {
            final InternalMethod element = nextElement;
            if (element == null) {
                throw new NoSuchElementException();
            }
            computeNext();
            return element;
        }

        private void computeNext() {
            if (methodEntries.hasNext()) {
                MethodEntry methodEntry = methodEntries.next();
                while (methodEntries.hasNext() && methodEntry.getMethod() == null) {
                    methodEntry = methodEntries.next();
                }
                nextElement = methodEntry.getMethod();
            } else {
                nextElement = null;
            }
        }
    }

    public Iterable<InternalMethod> getMethods() {
        return () -> new MethodsIterator(methods.values());
    }

    public List<String> getConstantNames() {
        List<String> results = new ArrayList<>();
        for (Entry<String, ConstantEntry> entry : constants.entrySet()) {
            if (entry.getValue().getConstant() != null) {
                results.add(entry.getKey());
            }
        }
        return results;
    }

    public List<String> getMethodNames() {
        List<String> results = new ArrayList<>();
        for (Entry<String, MethodEntry> entry : methods.entrySet()) {
            if (entry.getValue().getMethod() != null) {
                results.add(entry.getKey());
            }
        }
        return results;
    }

    @TruffleBoundary
    public InternalMethod getMethod(String name) {
        MethodEntry methodEntry = methods.get(name);
        if (methodEntry != null) {
            return methodEntry.getMethod();
        } else {
            return null;
        }
    }

    @TruffleBoundary
    public InternalMethod getMethodAndAssumption(String name, List<Assumption> assumptions) {
        MethodEntry methodEntry = ConcurrentOperations.getOrCompute(methods, name, n -> new MethodEntry());
        assumptions.add(methodEntry.getAssumption());
        return methodEntry.getMethod();
    }

    @TruffleBoundary
    public Assumption getOrCreateMethodAssumption(String name) {
        return ConcurrentOperations.getOrCompute(methods, name, n -> new MethodEntry()).getAssumption();
    }

    /** All write accesses to this object should use {@code synchronized (getClassVariables()) { ... }}, or check that
     * the ClassVariableStorage Shape is not shared */
    public ClassVariableStorage getClassVariables() {
        return classVariables;
    }

    public ConcurrentMap<RubyModule, RubyModule> getRefinements() {
        return refinements;
    }

    /** Must be called inside the RubyClass constructor */
    public void setSuperClass(RubyClass superclass) {
        assert rubyModule instanceof RubyClass;
        this.parentModule = superclass.fields.start;
    }

    @Override
    public RubyModule getActualModule() {
        return rubyModule;
    }

    /** Iterate over all ancestors, skipping PrependMarker and resolving IncludedModule. */
    public Iterable<RubyModule> ancestors() {
        return () -> new AncestorIterator(start);
    }

    /** Iterates over prepend'ed and include'd modules. */
    public Iterable<RubyModule> prependedAndIncludedModules() {
        return () -> new IncludedModulesIterator(start, this);
    }

    public Collection<RubySymbol> filterMethods(RubyLanguage language, boolean includeAncestors, MethodFilter filter) {
        final Map<String, InternalMethod> allMethods;
        if (includeAncestors) {
            allMethods = ModuleOperations.getAllMethods(rubyModule);
        } else {
            allMethods = getInternalMethodMap();
        }
        return filterMethods(language, allMethods, filter);
    }

    public Collection<RubySymbol> filterMethodsOnObject(
            RubyLanguage language,
            boolean includeAncestors,
            MethodFilter filter) {
        final Map<String, InternalMethod> allMethods;
        if (includeAncestors) {
            allMethods = ModuleOperations.getAllMethods(rubyModule);
        } else {
            allMethods = ModuleOperations.getMethodsUntilLogicalClass(rubyModule);
        }
        return filterMethods(language, allMethods, filter);
    }

    public Collection<RubySymbol> filterSingletonMethods(
            RubyLanguage language,
            boolean includeAncestors,
            MethodFilter filter) {
        final Map<String, InternalMethod> allMethods;
        if (includeAncestors) {
            allMethods = ModuleOperations.getMethodsBeforeLogicalClass(rubyModule);
        } else {
            allMethods = getInternalMethodMap();
        }
        return filterMethods(language, allMethods, filter);
    }

    public Collection<RubySymbol> filterMethods(
            RubyLanguage language,
            Map<String, InternalMethod> allMethods,
            MethodFilter filter) {
        final Map<String, InternalMethod> methods = ModuleOperations.withoutUndefinedMethods(allMethods);

        final Set<RubySymbol> filtered = new HashSet<>();
        for (InternalMethod method : methods.values()) {
            if (filter.filter(method)) {
                filtered.add(language.getSymbol(method.getName()));
            }
        }

        return filtered;
    }

    public boolean anyMethodDefined() {
        for (var value : methods.values()) {
            if (value.getMethod() != null) {
                return true;
            }
        }
        return false;
    }

    private Map<String, InternalMethod> getInternalMethodMap() {
        Map<String, InternalMethod> map = new HashMap<>();
        for (Entry<String, MethodEntry> e : methods.entrySet()) {
            if (e.getValue().getMethod() != null) {
                map.put(e.getKey(), e.getValue().getMethod());
            }
        }
        return map;
    }

    public RubyClass getLogicalClass() {
        return rubyModule.getLogicalClass();
    }

    @Override
    public void getAdjacentObjects(Set<Object> adjacent) {
        if (lexicalParent != null) {
            adjacent.add(lexicalParent);
        }

        for (RubyModule module : prependedAndIncludedModules()) {
            ObjectGraph.addProperty(adjacent, module);
        }

        if (rubyModule instanceof RubyClass) {
            ObjectGraph.addProperty(adjacent, ((RubyClass) rubyModule).superclass);
        }

        for (ConstantEntry constant : constants.values()) {
            if (constant.getConstant() != null) {
                ObjectGraph.addProperty(adjacent, constant.getConstant());
            }
        }

        for (Object key : classVariables.getShape().getKeys()) {
            final Object value = classVariables.read((String) key, DynamicObjectLibrary.getUncached());
            if (value != null) {
                ObjectGraph.addProperty(adjacent, value);
            }
        }

        for (MethodEntry methodEntry : methods.values()) {
            if (methodEntry.getMethod() != null) {
                ObjectGraph.addProperty(adjacent, methodEntry.getMethod());
            }
        }
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /** Registers an Assumption for a given method name, which is invalidated when a method with same name is defined or
     * undefined in this class or in a prepended module. This does not check re-definitions in subclasses. */
    public void registerAssumption(CoreLibrary coreLibrary, String methodName, Assumption assumption) {
        assert coreLibrary.state == CoreLibrary.State.CREATED;
        Assumption old = inlinedBuiltinsAssumptions.put(methodName, assumption);
        assert old == null;
    }

    private void refinedMethod(RubyContext context, String methodName, Node currentNode) {
        Assumption assumption = inlinedBuiltinsAssumptions.get(methodName);
        if (assumption != null) {
            var moduleAndMethodName = SharedMethodInfo.moduleAndMethodName(rubyModule, methodName);
            assumption.invalidate("method is refined: " + moduleAndMethodName);
            PerformanceWarningNode.warn(context,
                    StringUtils.format("Refining '%s' disables interpreter and JIT optimizations", moduleAndMethodName),
                    currentNode);
        }
    }

    private RubyClass getObjectClass() {
        // Tricky, we need to compare with the Object class, but we only have a Module at hand.
        final RubyClass classClass = getLogicalClass().getLogicalClass();
        final RubyClass objectClass = (RubyClass) ((RubyClass) classClass.superclass).superclass;
        assert objectClass.fields.givenBaseName == "Object";
        return objectClass;
    }

}
