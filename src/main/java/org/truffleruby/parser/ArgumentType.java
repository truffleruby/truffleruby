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

/** Reflects semantic of different method/proc declared parameters types.
 *
 * Symbolic names match type names returned by the {Method, Proc}#parameters methods. */
public enum ArgumentType {

    /** optional keyword argument */
    key("key", false),
    /** required keyword argument */
    keyreq("keyreq", false),
    /** keyword rest parameter */
    keyrest("keyrest", false),
    /** block parameter */
    block("block", false),
    /** optional positional parameter */
    opt("opt", false),
    /** rest parameter */
    rest("rest", false),
    /** required positional parameter */
    req("req", false),

    /* Parameters declared in a method/proc explicitly without name, e.g. anonymous *, ** , and &. Required parameter
     * can be anonymous only in case of parameters nesting (e.g. `def foo(a, (b, c)) end`). Similarly, an optional
     * parameter can be anonymous only in case of parameter nesting in a proc (e.g. proc {|a, (b)|}) */
    anonreq("req", true),
    anonopt("opt", true),
    anonrest("rest", true),
    anonkeyrest("keyrest", true),
    anonblock("block", true),

    /* Parameters in a method that doesn't provide parameter names, e.g. implemented using #method_missing or a core
     * method implemented in Java.
     * 
     * A tiny difference between unnamed and anonymous parameters is that anonymous are reported by the #parameters
     * method with names (*, ** or &). But unnamed are reported without names. */
    unnamedreq("req", true),
    unnamedopt("opt", true),
    unnamedrest("rest", true),
    unnamedkeyrest("keyrest", true),

    /** no-keyword-arguments parameter (**nil) */
    nokey("nokey", true);

    ArgumentType(String symbolicName, boolean anonymous) {
        this.symbolicName = symbolicName;
        this.anonymous = anonymous;
    }

    public final String symbolicName;
    public final boolean anonymous;
}
