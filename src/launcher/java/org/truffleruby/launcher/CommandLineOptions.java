/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2007-2011 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2009 Joseph LaFata <joe@quibb.org>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionType;
import org.truffleruby.shared.options.RubyOptionTypes;
import org.truffleruby.shared.options.StringArrayOptionType;

public class CommandLineOptions {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Options which are only meaningful when using the TruffleRuby launcher (not when using the Context API)
    boolean showVersion = false;
    boolean showCopyright = false;
    ShowHelp showHelp = ShowHelp.NONE;
    /** All command-line arguments passed to truffleruby */
    final String[] initialArguments;
    boolean logProcessArguments = false;
    /** Read the RUBYOPT and TRUFFLERUBYOPT environment variables */
    boolean readRubyOptEnv = true;
    /** What should be done after context is created */
    ExecutionAction executionAction = ExecutionAction.UNSET;
    /** A thing to be executed: a file, inline script, etc. Used by executionAction when applicable. */
    String toExecute = "";

    private final Map<String, String> options;
    /** Application arguments */
    private String[] arguments;
    private final List<String> unknownArguments;
    private Boolean gemOrBundle = null;

    public CommandLineOptions(List<String> initialArguments) {
        this.initialArguments = initialArguments.toArray(EMPTY_STRING_ARRAY);
        this.options = new HashMap<>();
        this.arguments = EMPTY_STRING_ARRAY;
        this.unknownArguments = new ArrayList<>();
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public <T> void setOption(OptionDescriptor descriptor, T value) {
        setOptionRaw(descriptor, RubyOptionTypes.valueToString(value));
    }

    public <T> void appendOptionValue(OptionDescriptor descriptor, String newValue) {
        final OptionType<?> type = descriptor.getKey().getType();

        final String appended;

        if (type == OptionType.defaultType(String.class)) {
            appended = getOptionRaw(descriptor) + newValue;
        } else if (type == StringArrayOptionType.INSTANCE) {
            appended = StringArrayOptionType.append(getOptionRaw(descriptor), newValue);
        } else {
            throw new UnsupportedOperationException();
        }

        setOptionRaw(descriptor, appended);
    }

    private void setOptionRaw(OptionDescriptor descriptor, String value) {
        options.put(descriptor.getName(), value);
    }

    public String getOptionRaw(OptionDescriptor descriptor) {
        return options.getOrDefault(
                descriptor.getName(),
                RubyOptionTypes.valueToString(descriptor.getKey().getDefaultValue()));
    }

    public boolean containsOption(OptionDescriptor descriptor) {
        return options.containsKey(descriptor.getName());
    }

    public String[] getArguments() {
        return arguments;
    }

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
    }

    public List<String> getUnknownArguments() {
        return unknownArguments;
    }

    /** Whether we are executing a gem or bundle command that would benefit from faster warmup and lower peak
     * performance. False for 'bundle exec'. */
    boolean isGemOrBundle() {
        if (gemOrBundle == null) {
            gemOrBundle = detectGemOrBundle();
        }
        return gemOrBundle;
    }

    private boolean detectGemOrBundle() {
        String executable = new File(toExecute).getName();
        if (executable.equals("gem")) {
            // All gem commands seem fine with --engine.Mode=latency.
            return true;
        } else if (executable.equals("bundle") || executable.equals("bundler")) {
            // Exclude 'bundle exec' and aliases as they should run with the default --engine.Mode.
            // Other bundle commands seem fine with --engine.Mode=latency.
            return !contains(arguments, "exec") && !contains(arguments, "exe") &&
                    !contains(arguments, "ex") && !contains(arguments, "e");
        } else {
            return false;
        }
    }

    private boolean contains(String[] array, String element) {
        for (String e : array) {
            if (e.equals(element)) {
                return true;
            }
        }
        return false;
    }
}
