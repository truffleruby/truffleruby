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
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
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
package org.truffleruby.parser;

import java.util.ArrayList;

import dev.truffleruby.shadowed.org.joni.WarnCallback;

public final class RubyDeferredWarnings implements WarnCallback {

    public ArrayList<WarningMessage> warnings = new ArrayList<>();

    public enum Verbosity {
        VERBOSE,   // -W2
        NON_VERBOSE  // -W1
    }

    public static final class WarningMessage {
        public final Verbosity verbosity;
        private final String fileName;
        private final Integer lineNumber;
        private final String message;

        public WarningMessage(Verbosity verbosity, String fileName, Integer lineNumber, String message) {
            this.verbosity = verbosity;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.message = message;
        }

        public String getWarningMessage() {
            StringBuilder buffer = new StringBuilder();
            if (fileName != null) {
                buffer.append(fileName);
                if (lineNumber != null) {
                    buffer.append(':').append(lineNumber).append(": ");
                } else {
                    buffer.append(' ');
                }
            }
            buffer.append("warning: ").append(message).append('\n');
            return buffer.toString();
        }

        @Override
        public String toString() {
            return "WarningMessage(" +
                    "message = '" + message + "'" +
                    ", verbosity = " + verbosity +
                    ", fileName = '" + fileName + "'" +
                    ", lineNumber = " + lineNumber +
                    ")";
        }

    }

    @Override
    public void warn(String message) {
        warn(null, message);
    }

    /** Prints a warning, unless $VERBOSE is nil. */
    public void warn(String fileName, int lineNumber, String message) {
        warnings.add(new WarningMessage(Verbosity.NON_VERBOSE, fileName, lineNumber, message));
    }

    /** Prints a warning, unless $VERBOSE is nil. */
    public void warn(String fileName, String message) {
        warnings.add(new WarningMessage(Verbosity.NON_VERBOSE, fileName, null, message));
    }

    /** Prints a warning, only if $VERBOSE is true. */
    public void warning(String message) {
        warnings.add(
                new WarningMessage(
                        Verbosity.VERBOSE,
                        null,
                        null,
                        message));
    }

    /** Prints a warning, only if $VERBOSE is true. */
    public void warning(String fileName, int lineNumber, String message) {
        warnings.add(
                new WarningMessage(
                        Verbosity.VERBOSE,
                        fileName,
                        lineNumber,
                        message));
    }

    @Override
    public String toString() {
        var strings = new ArrayList<String>();

        for (var warning : warnings) {
            strings.add(warning.toString());
        }

        String messages = String.join(", ", strings);
        return "RubyDeferredWarnings(" + messages + ")";
    }

}
