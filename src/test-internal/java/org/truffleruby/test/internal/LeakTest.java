/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.internal;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import com.sun.management.HotSpotDiagnosticMXBean;

public class LeakTest extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        String languageId = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--lang")) {
                languageId = args[i + 1];
                break;
            }
        }
        new LeakTest(languageId).launch(args);
    }

    LeakTest(String languageId) {
        super();
        if (languageId == null) {
            printHelp(null);
            System.exit(255);
        }
        this.languageId = languageId;
    }

    // sharedEngine is an instance variable explicitly so we definitely keep the ASTs alive. This is
    // to ensure that we actually test that even when the engine is still alive, as the Context is
    // closed, no objects should still be reachable
    private Engine engine;

    private boolean sharedEngine = false;
    private boolean keepDump = false;
    private String languageId;
    private String code;
    private List<String> forbiddenClasses = new ArrayList<>();

    @SuppressWarnings("serial")
    private final class SystemExit extends RuntimeException {
        public SystemExit() {
            super(null, null);
        }

        @Override
        public synchronized Throwable getCause() {
            dumpAndAnalyze();
            System.exit(0);
            return null;
        }

        private void dumpAndAnalyze() {
            if (sharedEngine && engine == null) {
                throw new AssertionError("the engine is no longer alive!");
            }

            MBeanServer server = doFullGC();
            Path dumpFile = dumpHeap(server);
            boolean fail = checkForLeaks(dumpFile);
            if (fail) {
                System.exit(255);
            } else {
                System.exit(0);
            }
        }

        private boolean checkForLeaks(Path dumpFile) {
            boolean fail = false;
            try {
                Heap heap = HeapFactory.createHeap(dumpFile.toFile());
                for (String fqn : forbiddenClasses) {
                    List<String> errors = new ArrayList<>();
                    JavaClass cls = heap.getJavaClassByName(fqn);
                    if (cls == null) {
                        System.err.println("No such class: " + fqn);
                        fail = true;
                        continue;
                    }
                    int cnt = getCountAndErrors(cls, errors);
                    for (Object subCls : cls.getSubClasses()) {
                        cnt += getCountAndErrors((JavaClass) subCls, errors);
                    }
                    if (cnt > 0) {
                        fail = true;
                        System.err.println("More instances of " + fqn + " than expected: " + cnt);
                        for (String e : errors) {
                            System.err.println(e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return fail;
        }

        private Path dumpHeap(MBeanServer server) {
            Path dumpFile = null;
            try {
                Path p = Files.createTempDirectory("leakTest");
                if (!keepDump) {
                    p.toFile().deleteOnExit();
                }
                dumpFile = p.resolve("heapdump.hprof");
                if (!keepDump) {
                    dumpFile.toFile().deleteOnExit();
                } else {
                    System.out.println("Dump file: " + dumpFile.toString());
                }
                HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                        server,
                        "com.sun.management:type=HotSpotDiagnostic",
                        HotSpotDiagnosticMXBean.class);
                mxBean.dumpHeap(dumpFile.toString(), true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return dumpFile;
        }

        private MBeanServer doFullGC() {
            // do this a few times to dump a small heap if we can
            MBeanServer server = null;
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // do nothing
                }
                System.gc();
                Runtime.getRuntime().freeMemory();
                server = ManagementFactory.getPlatformMBeanServer();
                try {
                    ObjectName objectName = new ObjectName("com.sun.management:type=DiagnosticCommand");
                    server.invoke(objectName, "gcRun", new Object[]{ null }, new String[]{ String[].class.getName() });
                } catch (MalformedObjectNameException | InstanceNotFoundException | ReflectionException
                        | MBeanException e) {
                    throw new RuntimeException(e);
                }
            }
            return server;
        }

        private int getCountAndErrors(JavaClass cls, List<String> errors) {
            int count = cls.getInstancesCount();
            if (count > 0) {
                boolean realLeak = false;
                for (Object i : cls.getInstances()) {
                    Instance inst = (Instance) i;
                    if (inst.isGCRoot() || inst.getNearestGCRootPointer() != null) {
                        realLeak = true;
                        break;
                    }
                }
                if (!realLeak) {
                    return 0;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(cls.getName()).append(" ").append(count).append(" instance(s)");
                errors.add(sb.toString());
            }
            return count;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.equals("--shared-engine")) {
                sharedEngine = true;
            } else if (arg.equals("--lang")) {
                // already parsed
                i++;
            } else if (arg.equals("--keep-dump")) {
                keepDump = true;
            } else if (arg.equals("--code")) {
                code = arguments.get(++i);
            } else if (arg.equals("--forbidden-class")) {
                forbiddenClasses.add(arguments.get(++i));
            } else {
                unrecognized.add(arg);
            }
        }
        unrecognized.add("--experimental-options");
        return unrecognized;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        if (sharedEngine) {
            engine = Engine.newBuilder()
                    .allowExperimentalOptions(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .option("engine.TraceCodeSharing", "true")
                    .build();
            contextBuilder.engine(engine);
        }

        contextBuilder
                .allowExperimentalOptions(true)
                .allowAllAccess(true)
                .option("ruby.experimental-engine-caching", "true");

        try (Context context = contextBuilder.build()) {
            try {
                context.eval(getLanguageId(), code);
            } catch (PolyglotException e) {
                if (e.isExit()) {
                    if (e.getExitStatus() == 0) {
                        throw new SystemExit();
                    } else {
                        exit(e.getExitStatus());
                    }
                } else {
                    e.printStackTrace();
                    exit(255);
                }
            }
        }
        throw new SystemExit();
    }

    @Override
    protected String getLanguageId() {
        return languageId;
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println(
                "--lang ID --code CODE --forbidden-class FQN [--forbidden-class FQN]* [--shared-engine] [--keep-dump] [POLYGLOT-OPTIONS]");
    }
}
