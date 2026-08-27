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

  import org.truffleruby.annotations.CExtUpcall;

  /** The Java targets of the native-to-Java upcalls used to implement the Ruby C API. One instance per Ruby context that
   * loaded C extension support. Each method is turned into a native function pointer with an FFM upcall stub.
   *
   * <p>
   * IMPORTANT: for Native Image direct upcalls the MethodHandle for each target must be exactly
   * {@code findVirtual(CExtUpcallTargets.class, name, type).bindTo(instance)} with no other adaptation, and every
   * parameter and return type must be primitive. See {@code FFMSupport#createUpcallStub}. */
  public final class CExtUpcallTargets {

      private final CExtUpcallRuntime runtime;

      public CExtUpcallTargets(CExtUpcallRuntime runtime) {
          this.runtime = runtime;
      }

JAVA

ARG_CONVERSIONS = {
  'V' => ->(a) { "runtime.unwrap(#{a})" },
  'Y' => ->(a) { "runtime.idToSymbol(#{a})" },
  'P' => ->(a) { "runtime.pointerArg(#{a})" },
  'F' => ->(a) { "runtime.functionArg(#{a})" },
  'L' => ->(a) { a },
  'I' => ->(a) { a },
  'D' => ->(a) { a },
}.freeze

RET_CONVERSIONS = {
  'V' => ->(call) { "return runtime.toValueHandle(#{call});" },
  'W' => ->(call) { "return runtime.wrappedToHandle(#{call});" },
  'I' => ->(call) { "return runtime.toInt(#{call});" },
  'B' => ->(call) { "return runtime.toBooleanInt(#{call});" },
  'L' => ->(call) { "return runtime.toLong(#{call});" },
  'D' => ->(call) { "return runtime.toDouble(#{call});" },
  'P' => ->(call) { "return runtime.toPointer(#{call});" },
  'F' => ->(call) { "return runtime.toPointer(#{call});" },
  'Y' => ->(call) { "return runtime.toID(#{call});" },
  'O' => ->(call) { "#{call};" },
}.freeze

SENTINELS = { 'V' => '0L', 'W' => '0L', 'I' => '0', 'B' => '0', 'L' => '0L', 'D' => '0.0', 'P' => '0L',
              'F' => '0L', 'Y' => '0L' }.freeze

UPCALLS.each do |cname, kind, ret, args|
  carriers = args.chars
  params = java_params(kind, carriers)
  ret_type = JAVA_TYPES.fetch(ret)

  # one conversion per statement, so every line is short and stable under the Java formatter
  conversions = []
  arg_names = []
  carriers.each_with_index do |c, i|
    if c == 'A'
      conversions << "Object a#{i} = runtime.valueArray(v#{i}p, v#{i}n);"
    else
      conversions << "Object a#{i} = #{ARG_CONVERSIONS.fetch(c).call("v#{i}")};"
    end
    arg_names << "a#{i}"
  end

  if kind == :invoke
    conversions.unshift "Object methodName = runtime.readString(name);"
    conversions.unshift "Object self = runtime.unwrap(recv);"
    call = "runtime.dispatchMethod(self, methodName#{arg_names.map { |a| ", #{a}" }.join})"
  elsif kind == :send
    # The receiver is the first argument and the method name is a constant
    self_name, *arg_names = arg_names
    method_name = SEND_NAMES.fetch(cname)
    call = "runtime.dispatchMethod(#{self_name}, #{method_name.inspect}#{arg_names.map { |a| ", #{a}" }.join})"
  else
    ruby_name = RUBY_NAMES.fetch(cname, cname)
    call = "runtime.dispatchCExt(#{ruby_name.inspect}#{arg_names.map { |a| ", #{a}" }.join})"
  end

  java << "    @CExtUpcall\n"
  # wrap the signature at 120 columns like the Eclipse formatter (continuation indent 12)
  signature = "    public #{ret_type} upcall_#{cname}(#{params.join(', ')}) {"
  if signature.length <= 120
    java << signature << "\n"
  else
    line = "    public #{ret_type} upcall_#{cname}("
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
  conversions.each { |stmt| java << "            #{stmt}\n" }
  if ret == 'O'
    java << "            #{call};\n"
  else
    java << "            Object result = #{call};\n"
    RET_CONVERSIONS.fetch(ret).call('result').lines.each { |l| java << "            #{l.chomp}\n" }
  end
  java << "        } catch (Throwable t) {\n"
  java << "            runtime.reportException(t);\n"
  if ret == 'O'
    java << "        }\n"
  else
    java << "            return #{SENTINELS.fetch(ret)};\n"
    java << "        }\n"
  end
  java << "    }\n\n"
end

java << "    /** Pairs of upcall method name and FFM carrier signature (FFMSupport format), in the same order as the\n"
java << "     * pointer array passed to rb_tr_init_ffm_upcalls() */\n"
java << "    // @formatter:off\n"
java << "    public static final String[] UPCALLS = {\n"
UPCALLS.each do |cname, kind, ret, args|
  java << "            \"upcall_#{cname}\", \"#{ffm_signature(kind, ret, args.chars)}\",\n"
end
java << "    };\n"
java << "    // @formatter:on\n"
java << "}\n"

# ---- Native Image foreign.downcalls metadata ----
# FFM downcall handles created at image build time (static final fields) need no metadata, but handles
# created at run time by Primitive.cext_ffm_bind do. Collect the carrier signatures from the Ruby sources.
base = File.expand_path('..', __dir__)
bind_signatures = []
Dir["#{base}/lib/truffle/**/*.rb"].sort.each do |file|
  contents = File.read(file)
  # Match any quoted carrier signature literal like 'L(LL)' (all are arguments of cext_ffm_bind)
  contents.scan(/'([VILD]\([VILD]*\))'/) { bind_signatures << $1 }
  # The interpolated rb_tr_setjmp_wrapper_pointer{1..16}_to_pointer family in cext.rb
  if contents.include?("cext_ffm_bind(lib[:\"rb_tr_setjmp_wrapper_pointer\#{n}_to_pointer\"]")
    (1..16).each { |n| bind_signatures << "L(#{'L' * (n + 1)})" }
  end
end
bind_signatures = bind_signatures.uniq.sort

JNI_NAMES = { 'L' => 'jlong', 'I' => 'jint', 'D' => 'jdouble', 'V' => 'void' }.freeze

downcalls_json = +"{\n  \"foreign\": {\n    \"downcalls\": [\n"
downcalls_json << bind_signatures.map do |signature|
  ret = JNI_NAMES.fetch(signature[0].to_s)
  params = signature[(signature.index('(') + 1)...-1].chars.map { |c| "\"#{JNI_NAMES.fetch(c)}\"" }
  <<~ENTRY.gsub(/^/, '      ').chomp
    {
      "returnType": "#{ret}",
      "parameterTypes": [#{params.join(', ')}]
    }
  ENTRY
end.join(",\n")
downcalls_json << "\n    ]\n  }\n}\n"

files = {
  "#{base}/src/main/c/cext/upcalls.h" => upcalls_h,
  "#{base}/src/main/c/cext/upcalls_init.c" => init_c,
  "#{base}/src/main/java/org/truffleruby/cext/CExtUpcallTargets.java" => java,
  "#{base}/src/main/java/META-INF/native-image/dev.truffleruby.internal/cext-downcalls/reachability-metadata.json" => downcalls_json,
}
files.each do |path, contents|
  if File.exist?(path) && File.read(path) == contents
    puts "unchanged: #{path}"
  else
    File.write(path, contents)
    puts "wrote: #{path}"
  end
end
