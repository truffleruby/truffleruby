#!/usr/bin/env ruby

# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Generates, from tool/cext-upcalls.rb:
# * src/main/c/cext/upcalls.h        - extern function pointers + inline wrappers
# * src/main/c/cext/upcalls_init.c   - fills the function pointers from a void*[]
# * src/main/java/org/truffleruby/cext/CExtUpcallTargets.java - the Java upcall targets
#
# Also verifies that the set of rb_tr_up_* calls in src/main/c/cext/*.c matches the spec.

require_relative 'cext-upcalls'

COPYRIGHT = <<~COPYRIGHT
  Copyright (c) #{Time.now.year} TruffleRuby contributors

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice, this
     list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright notice,
     this list of conditions and the following disclaimer in the documentation
     and/or other materials provided with the distribution.

  3. Neither the name of the copyright holder nor the names of its
     contributors may be used to endorse or promote products derived from
     this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
COPYRIGHT

C_TYPES = {
  'V' => 'VALUE', 'W' => 'VALUE', 'I' => 'int', 'B' => 'int', 'L' => 'long', 'D' => 'double',
  'P' => 'void *', 'F' => 'void *', 'Y' => 'ID', 'O' => 'void'
}.freeze

JAVA_TYPES = {
  'V' => 'long', 'W' => 'long', 'I' => 'int', 'B' => 'int', 'L' => 'long', 'D' => 'double',
  'P' => 'long', 'F' => 'long', 'Y' => 'long', 'O' => 'void'
}.freeze

# Carrier letters for FFM FunctionDescriptors (FFMSupport carrier signature format)
FFM_CARRIERS = {
  'V' => 'L', 'W' => 'L', 'I' => 'I', 'B' => 'I', 'L' => 'L', 'D' => 'D',
  'P' => 'L', 'F' => 'L', 'Y' => 'L', 'O' => 'V'
}.freeze

def expand_c_params(carriers)
  params = []
  carriers.each_with_index do |c, i|
    if c == 'A'
      params << "const VALUE *v#{i}p"
      params << "long v#{i}n"
    elsif c == 'P'
      # const so that pointers to const can be passed without a cast
      params << "const void *v#{i}"
    else
      params << "#{C_TYPES.fetch(c)} v#{i}"
    end
  end
  params
end

def expand_c_args(carriers)
  args = []
  carriers.each_with_index do |c, i|
    if c == 'A'
      args << "v#{i}p" << "v#{i}n"
    else
      args << "v#{i}"
    end
  end
  args
end

def c_signature(ret, carriers)
  params = expand_c_params(carriers)
  params = ['void'] if params.empty?
  [C_TYPES.fetch(ret), params]
end

def java_params(kind, carriers)
  params = []
  params << 'long recv' << 'long name' if kind == :invoke
  carriers.each_with_index do |c, i|
    if c == 'A'
      params << "long v#{i}p" << "long v#{i}n"
    else
      params << "#{JAVA_TYPES.fetch(c)} v#{i}"
    end
  end
  params
end

def ffm_signature(kind, ret, carriers)
  args = ''
  args << 'LL' if kind == :invoke
  carriers.each do |c|
    args << (c == 'A' ? 'LL' : FFM_CARRIERS.fetch(c))
  end
  "#{FFM_CARRIERS.fetch(ret)}(#{args})"
end

UPCALLS = CExtUpcalls::UPCALLS
RUBY_NAMES = CExtUpcalls::RUBY_NAMES
SEND_NAMES = CExtUpcalls::SEND_NAMES

# ---- verification: C call sites match the spec ----
used = Hash.new(0)
Dir[File.expand_path('../src/main/c/cext/*.c', __dir__)].sort.each do |file|
  next if %w[ruby.c cext_constants.c upcalls_init.c].include?(File.basename(file))
  File.read(file).scan(/\brb_tr_up_([A-Za-z0-9_]+)\s*\(/) { used[$1] += 1 }
end
spec_names = UPCALLS.map(&:first)
missing = used.keys - spec_names
unused = spec_names - used.keys
abort "upcalls used in C but missing from tool/cext-upcalls.rb: #{missing}" unless missing.empty?
abort "upcalls in tool/cext-upcalls.rb but not used in C: #{unused}" unless unused.empty?

# ---- upcalls.h ----
upcalls_h = +''
upcalls_h << "/*\n#{COPYRIGHT.gsub(/^/, ' * ').gsub(/ +$/, '')} */\n"
upcalls_h << <<~HEADER
  // GENERATED BY tool/generate-cext-upcalls.rb - DO NOT EDIT

  // Declarations of the native-to-Java upcalls used to implement the Ruby C API.
  // Each upcall is an FFM upcall stub function pointer, filled in by
  // rb_tr_init_ffm_upcalls(). The inline wrappers check for a pending Ruby
  // exception after each upcall and unwind via the setjmp/longjmp mechanism,
  // like MRI's rb_raise() does.

  #ifndef TRUFFLERUBY_UPCALLS_H
  #define TRUFFLERUBY_UPCALLS_H

  #ifndef UNLIKELY
  #define UNLIKELY(x) __builtin_expect(!!(x), 0)
  #endif

  // A pending Ruby exception was stored by the Java side of an upcall; longjmp to
  // the innermost rb_tr_setjmp_wrapper_* frame.
  extern __thread int rb_tr_pending_exception;
  NORETURN(void rb_tr_longjmp_from_java_exception(void));

HEADER

UPCALLS.each do |cname, kind, ret, args|
  carriers = args.chars
  ret_type, params = c_signature(ret, carriers)
  invoke_params = kind == :invoke ? ['VALUE recv', 'const char *name'] : []
  all_params = invoke_params + expand_c_params(carriers)
  all_params = ['void'] if all_params.empty?
  arg_names = (kind == :invoke ? ['recv', 'name'] : []) + expand_c_args(carriers)

  upcalls_h << "extern #{ret_type} (*rb_tr_up_impl_#{cname})(#{all_params.join(', ')});\n"
  upcalls_h << "static inline #{ret_type} rb_tr_up_#{cname}(#{all_params.join(', ')}) {\n"
  call = "rb_tr_up_impl_#{cname}(#{arg_names.join(', ')})"
  if ret == 'O'
    upcalls_h << "  #{call};\n"
  else
    upcalls_h << "  #{ret_type} result = #{call};\n"
  end
  upcalls_h << "  if (UNLIKELY(rb_tr_pending_exception)) {\n"
  upcalls_h << "    rb_tr_longjmp_from_java_exception();\n"
  upcalls_h << "  }\n"
  upcalls_h << "  return result;\n" unless ret == 'O'
  upcalls_h << "}\n\n"
end
upcalls_h << "void rb_tr_init_ffm_upcalls(void **upcalls);\n"
upcalls_h << "\n#endif // TRUFFLERUBY_UPCALLS_H\n"

# ---- upcalls_init.c ----
init_c = +''
init_c << "/*\n#{COPYRIGHT.gsub(/^/, ' * ').gsub(/ +$/, '')} */\n"
init_c << "// GENERATED BY tool/generate-cext-upcalls.rb - DO NOT EDIT\n\n"
init_c << "#include <truffleruby-impl.h>\n\n"
init_c << "__thread int rb_tr_pending_exception = 0;\n\n"
init_c << "int *rb_tr_pending_exception_address(void) {\n  return &rb_tr_pending_exception;\n}\n\n"

decls = +''
assigns = +''
UPCALLS.each_with_index do |(cname, kind, ret, args), index|
  carriers = args.chars
  ret_type, = c_signature(ret, carriers)
  invoke_params = kind == :invoke ? ['VALUE recv', 'const char *name'] : []
  all_params = invoke_params + expand_c_params(carriers)
  all_params = ['void'] if all_params.empty?
  decls << "#{ret_type} (*rb_tr_up_impl_#{cname})(#{all_params.join(', ')});\n"
  assigns << "  rb_tr_up_impl_#{cname} = (#{ret_type} (*)(#{all_params.join(', ')})) upcalls[#{index}];\n"
end
init_c << decls
init_c << "\nvoid rb_tr_init_ffm_upcalls(void **upcalls) {\n"
init_c << assigns
init_c << "}\n"

# ---- CExtUpcallTargets.java ----
java = +''
java << "/*\n#{COPYRIGHT.gsub(/^/, ' * ').gsub(/ +$/, '')} */\n"
java << <<~JAVA
  package org.truffleruby.cext;

  // GENERATED BY tool/generate-cext-upcalls.rb - DO NOT EDIT

  import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

  import org.truffleruby.annotations.CExtUpcall;

  /** The Java targets of the native-to-Java upcalls used to implement the Ruby C API. Each method is turned into a
   * native function pointer with an FFM upcall stub. Everything is static since the upcall stubs are created once per
   * process (in Arena.global(), see CExtFFMLayer).
   *
   * <p>
   * IMPORTANT: for Native Image direct upcalls the MethodHandle for each target must be exactly
   * {@code findStatic(CExtUpcallTargets.class, name, type)} with no other adaptation, and every parameter and return
   * type must be primitive. See {@code FFMSupport#createUpcallStub}. */
  public abstract class CExtUpcallTargets {

      /** The layer of the Ruby context which currently has C extension support loaded, or null if none. The runtime is
       * swapped in when a context loads C extension support and swapped out when that context is disposed, to not keep
       * the RubyContext alive from the process-wide upcall stubs (see CExtFFMLayer). */
      private static volatile CExtFFMLayer runtime;

      public static void setRuntime(CExtFFMLayer runtime) {
          CExtUpcallTargets.runtime = runtime;
      }

      @TruffleBoundary
      private static void reportException(CExtFFMLayer runtime, Throwable throwable) {
          if (runtime == null) {
              // No Ruby context has C extension support loaded (e.g. a stale upcall from native code after the
              // context which loaded it was disposed): there is no Ruby caller to rethrow the exception in.
              System.err.println("C extension upcall failed but no Ruby context has C extension support loaded:");
              throwable.printStackTrace();
          } else {
              runtime.reportException(throwable);
          }
      }

JAVA

# How the generated method body unboxes the result of CExtFFMLayer#upcall per return carrier
RET_UNBOX = {
  'V' => '(long) ', 'W' => '(long) ', 'L' => '(long) ', 'P' => '(long) ',
  'F' => '(long) ', 'Y' => '(long) ',
  'I' => '(int) ', 'B' => '(int) ',
  'D' => '(double) '
}.freeze

SENTINELS = { 'V' => '0L', 'W' => '0L', 'I' => '0', 'B' => '0', 'L' => '0L', 'D' => '0.0', 'P' => '0L',
              'F' => '0L', 'Y' => '0L' }.freeze

UPCALLS.each_with_index do |(cname, kind, ret, args), index|
  if cname == 'ruby_native_thread_p'
    # Hand-routed: it is called from native threads not entered in the context, where no upcall is possible.
    # It must also work when no Ruby context has C extension support loaded (runtime == null).
    java << "    @CExtUpcall\n"
    java << "    public static int upcall_ruby_native_thread_p() {\n"
    java << "        final CExtFFMLayer runtime = CExtUpcallTargets.runtime;\n"
    java << "        return runtime == null ? 0 : runtime.isRubyThread();\n"
    java << "    }\n\n"
    next
  end

  carriers = args.chars
  params = java_params(kind, carriers)

  raw_names = []
  raw_names << 'recv' << 'name' if kind == :invoke
  carriers.each_with_index do |c, i|
    if c == 'A'
      raw_names << "v#{i}p" << "v#{i}n"
    else
      raw_names << "v#{i}"
    end
  end
  upcall_arguments = ([index] + raw_names).join(', ')

  ret_type = JAVA_TYPES.fetch(ret)

  java << "    @CExtUpcall\n"
  # wrap the signature at 120 columns like the Eclipse formatter (continuation indent 12)
  signature = "    public static #{ret_type} upcall_#{cname}(#{params.join(', ')}) {"
  if signature.length <= 120
    java << signature << "\n"
  else
    line = "    public static #{ret_type} upcall_#{cname}("
    params.each_with_index do |param, i|
      piece = param + (i == params.size - 1 ? ') {' : ',')
      if (line + piece).length > 120
        java << line.rstrip << "\n"
        line = '            '
      end
      line << piece << ' '
    end
    java << line.rstrip << "\n"
  end
  java << "        try {\n"
  if ret == 'O'
    java << "            runtime.upcall(#{upcall_arguments});\n"
  else
    call_line = "            return #{RET_UNBOX.fetch(ret)}runtime.upcall(#{upcall_arguments});"
    if call_line.length <= 120
      java << call_line << "\n"
    else
      java << "            return #{RET_UNBOX.fetch(ret)}runtime\n"
      java << "                    .upcall(#{upcall_arguments});\n"
    end
  end
  java << "        } catch (Throwable t) {\n"
  java << "            reportException(runtime, t);\n"
  if ret == 'O'
    java << "        }\n"
  else
    java << "            return #{SENTINELS.fetch(ret)};\n"
    java << "        }\n"
  end
  java << "    }\n\n"
end

java << "    /** Groups of 6 strings per upcall: the upcall method name, the FFM carrier signature (FFMSupport\n"
java << "     * format), the kind, the Ruby method name, the return carrier and the argument carrier letters (see\n"
java << "     * CExtUpcallRootNode.UpcallSpec), in the same order as the pointer array passed to\n"
java << "     * rb_tr_init_ffm_upcalls() */\n"
java << "    // @formatter:off\n"
java << "    public static final String[] UPCALLS = {\n"
UPCALLS.each do |cname, kind, ret, args|
  ruby_name =
    case kind
    when :invoke then ''
    when :send then SEND_NAMES.fetch(cname)
    else RUBY_NAMES.fetch(cname, cname)
    end
  java << "            \"upcall_#{cname}\", \"#{ffm_signature(kind, ret, args.chars)}\","
  java << " \"#{kind}\", \"#{ruby_name}\", \"#{ret}\", \"#{args}\",\n"
end
java << "    };\n"
java << "    // @formatter:on\n"
java << "}\n"

# ---- CExtInvokePrimitives.java: one primitive per downcall signature ----
# Each Primitive.cext_invoke_<ret>_<args> invokes a native function of that carrier signature:
# the first argument is the address of the native function (usually a rb_tr_setjmp_wrapper_*), the
# rest are its arguments. The MethodHandles are static finals created at image build time and each
# signature has its own monomorphic invokeExact call site, so Native Image intrinsifies them (like
# TrufflePosixNodes); MethodHandles adapted at run time would run in the SVM method handle
# interpreter (microseconds per call), and dispatching over signatures at run time would need a
# PE-constant receiver to fold.
base = File.expand_path('..', __dir__)
CARRIER_JAVA = { 'L' => 'long', 'I' => 'int', 'D' => 'double' }.freeze

def downcall_method_name(signature)
  "invoke#{signature[0]}_#{signature[(signature.index('(') + 1)...-1]}"
end

def primitive_name(signature)
  "cext_invoke_#{signature[0].downcase}_#{signature[(signature.index('(') + 1)...-1].downcase}"
end

def signature_args(signature)
  signature[(signature.index('(') + 1)...-1].chars
end

invoke_java = +''
invoke_java << "/*\n#{COPYRIGHT.gsub(/^/, ' * ').gsub(/ +$/, '')} */\n"
invoke_java << <<~JAVA
  package org.truffleruby.cext;

  // GENERATED BY tool/generate-cext-upcalls.rb - DO NOT EDIT

  import java.lang.invoke.MethodHandle;

  import com.oracle.truffle.api.CompilerDirectives;
  import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
  import com.oracle.truffle.api.dsl.Cached;
  import com.oracle.truffle.api.dsl.Specialization;
  import com.oracle.truffle.api.profiles.InlinedBranchProfile;

  import org.truffleruby.annotations.CoreModule;
  import org.truffleruby.annotations.Primitive;
  import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
  import org.truffleruby.core.fiber.RubyFiber;
  import org.truffleruby.platform.FFMSupport;

  // @formatter:off
  /** The Primitive.cext_invoke_<signature> primitives calling native functions for the C extension support: the
   * rb_tr_setjmp_wrapper_* functions and the native helper functions of libtruffleruby. The first argument is the address
   * of the native function to call, the following ones its arguments (VALUE handles, pointers and integers, coerced by
   * {@link CExtDowncallArgumentNode}).
   *
   * <p>
   * After the native call returns, the pending C extension exception (captured at an FFM upcall boundary while the native
   * code was running, see {@link CExtFFMLayer#reportException}) is rethrown, replacing the Truffle NFI
   * exceptionCheck mechanism. */
  @CoreModule("Truffle::CExtInvoke")
  public abstract class CExtInvokePrimitives {

      public abstract static class CExtInvokeNode extends PrimitiveArrayArgumentsNode {
          protected final void checkPendingException(InlinedBranchProfile exceptionProfile) {
              final RubyFiber fiber = getLanguage().getCurrentFiber();
              if (fiber.pendingCExtException != null) {
                  exceptionProfile.enter(this);
                  CExtFFMLayer.checkPendingException(getContext(), fiber);
              }
          }
      }

JAVA

CExtUpcalls::DOWNCALL_SIGNATURES.each do |signature|
  name = downcall_method_name(signature)
  ret = signature[0]
  args = signature_args(signature)
  constant = "HANDLE_#{ret}_#{args.join}"
  params = ['long function'] + args.each_with_index.map { |c, i| "#{CARRIER_JAVA.fetch(c)} a#{i}" }
  arg_names = ['function'] + args.each_with_index.map { |_, i| "a#{i}" }
  ret_type = ret == 'V' ? 'void' : CARRIER_JAVA.fetch(ret)
  cast = ret == 'V' ? '' : "(#{ret_type}) "

  invoke_java << "    private static final MethodHandle #{constant} = FFMSupport.createDowncallHandle(\"#{signature}\");\n\n"
  invoke_java << "    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)\n"
  invoke_java << "    private static #{ret_type} #{name}(#{params.join(', ')}) {\n"
  invoke_java << "        try {\n"
  invoke_java << "            #{ret == 'V' ? '' : 'return '}#{cast}#{constant}.invokeExact(#{arg_names.join(', ')});\n"
  invoke_java << "        } catch (Throwable t) {\n"
  invoke_java << "            throw CompilerDirectives.shouldNotReachHere(t);\n"
  invoke_java << "        }\n"
  invoke_java << "    }\n\n"

  # the primitive node
  node_name = "Invoke#{ret}_#{args.join}Node"
  lower_fixnum = args.each_index.select { |i| args[i] == 'I' }.map { |i| i + 1 }
  annotation = "    @Primitive(name = \"#{primitive_name(signature)}\""
  unless lower_fixnum.empty?
    annotation += lower_fixnum.size == 1 ? ", lowerFixnum = #{lower_fixnum[0]}" : ", lowerFixnum = { #{lower_fixnum.join(', ')} }"
  end
  annotation += ')'
  invoke_java << annotation << "\n"
  invoke_java << "    public abstract static class #{node_name} extends CExtInvokeNode {\n"

  spec_params = ['long function'] + args.each_with_index.map do |c, i|
    c == 'L' ? "Object a#{i}" : "#{CARRIER_JAVA.fetch(c)} a#{i}"
  end
  cached_params = args.each_with_index.select { |c, _| c == 'L' }.map { |_, i| "@Cached CExtDowncallArgumentNode arg#{i}Node" }
  cached_params << '@Cached InlinedBranchProfile exceptionProfile'
  invoke_ret_type = ret == 'V' ? 'Object' : CARRIER_JAVA.fetch(ret)

  invoke_java << "        @Specialization\n"
  invoke_java << "        #{invoke_ret_type} invoke(#{spec_params.join(', ')},\n"
  cached_params.each_with_index do |param, i|
    invoke_java << "                #{param}#{i == cached_params.size - 1 ? ') {' : ','}\n"
  end
  call_args = ['function'] + args.each_with_index.map do |c, i|
    c == 'L' ? "arg#{i}Node.execute(a#{i})" : "a#{i}"
  end
  if ret == 'V'
    invoke_java << "            #{name}(#{call_args.join(', ')});\n"
    invoke_java << "            checkPendingException(exceptionProfile);\n"
    invoke_java << "            return nil;\n"
  else
    invoke_java << "            final #{CARRIER_JAVA.fetch(ret)} result = #{name}(#{call_args.join(', ')});\n"
    invoke_java << "            checkPendingException(exceptionProfile);\n"
    invoke_java << "            return result;\n"
  end
  invoke_java << "        }\n"
  invoke_java << "    }\n\n"
end
invoke_java.sub!(/\n\n\z/, "\n")
invoke_java << "}\n"
invoke_java << "// @formatter:on\n"

files = {
  "#{base}/src/main/c/cext/upcalls.h" => upcalls_h,
  "#{base}/src/main/c/cext/upcalls_init.c" => init_c,
  "#{base}/src/main/java/org/truffleruby/cext/CExtUpcallTargets.java" => java,
  "#{base}/src/main/java/org/truffleruby/cext/CExtInvokePrimitives.java" => invoke_java,
}
files.each do |path, contents|
  if File.exist?(path) && File.read(path) == contents
    puts "unchanged: #{path}"
  else
    File.write(path, contents)
    puts "wrote: #{path}"
  end
end
