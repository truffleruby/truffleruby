# frozen_string_literal: true

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

ruby_file = 'src/main/ruby/truffleruby/core/posix_functions.rb'
c_file = 'src/main/c/truffleposix/posix_errno_wrappers.c.inc'

C_NATIVE_GUARDS = {
  'crypt' => 'HAVE_CRYPT',
  'dup3' => 'HAVE_DUP3',
  'posix_fadvise' => 'HAVE_POSIX_FADVISE',
  'setresgid' => 'HAVE_SETRESGID',
  'setresuid' => 'HAVE_SETRESUID',
  'setruid' => 'HAVE_SETRUID',
}.freeze

Function = Struct.new(:native_name, :argument_types, :return_type, :blocking, :method_name, :guard, :retry_eintr)

FUNCTIONS = []

def native_type_name(type)
  type.to_s
end

def attach_function(native_name, argument_types, return_type, blocking: false, method_name: native_name, guard: nil,
                    retry_eintr: blocking)
  FUNCTIONS << Function.new(native_name.to_s, argument_types.map { |type| native_type_name(type) },
                            native_type_name(return_type), blocking, method_name.to_s, guard, retry_eintr)
end

# Filesystem-related
attach_function :chdir, [:string], :int
attach_function :chmod, [:string, :mode_t], :int
attach_function :chown, [:string, :uid_t, :gid_t], :int
attach_function :chroot, [:string], :int
attach_function :truffleposix_clock_getres, [:int], :int64_t
attach_function :truffleposix_clock_gettime, [:int], :int64_t
attach_function :close, [:int], :int
attach_function :closedir, [:pointer], :int
attach_function :dirfd, [:pointer], :int
attach_function :dup, [:int], :int
attach_function :dup2, [:int, :int], :int
attach_function :fchdir, [:int], :int
attach_function :fchmod, [:int, :mode_t], :int
attach_function :fchown, [:int, :uid_t, :gid_t], :int
attach_function :truffleposix_fcntl, [:int, :int, :int], :int, method_name: :fcntl
attach_function :fdopendir, [:int], :pointer
attach_function :flock, [:int, :int], :int, blocking: true
attach_function :truffleposix_fstat, [:int, :pointer], :int
attach_function :truffleposix_fstat_mode, [:int], :mode_t
attach_function :truffleposix_fstat_size, [:int], :long
attach_function :truffleposix_fstatat, [:int, :string, :pointer, :int], :int
attach_function :truffleposix_fstatat_mode, [:int, :string, :int], :mode_t
attach_function :truffleposix_fstatat_size, [:int, :string, :int], :long
attach_function :posix_fadvise, [:int, :off_t, :off_t, :int], :int
attach_function :fsync, [:int], :int
attach_function :ftruncate, [:int, :off_t], :int
attach_function :getcwd, [:pointer, :size_t], :string
attach_function :truffleposix_ioctl, [:int, :ulong, :pointer], :int, method_name: :ioctl
attach_function :isatty, [:int], :int
attach_function :lchmod, [:string, :mode_t], :int
attach_function :lchown, [:string, :uid_t, :gid_t], :int
attach_function :link, [:string, :string], :int
attach_function :lseek, [:int, :off_t, :int], :off_t
attach_function :truffleposix_lstat, [:string, :pointer], :int
attach_function :truffleposix_lstat_mode, [:string], :mode_t
attach_function :truffleposix_lutimes, [:string, :long, :int, :long, :int], :int
attach_function :truffleposix_major, [:dev_t], :uint
attach_function :truffleposix_minor, [:dev_t], :uint
attach_function :mkdir, [:string, :mode_t], :int
attach_function :mkfifo, [:string, :mode_t], :int
attach_function :mmap, [:pointer, :size_t, :int, :int, :int, :off_t], :pointer
attach_function :munmap, [:pointer, :size_t], :int
attach_function :truffleposix_open, [:string, :int, :mode_t], :int, method_name: :open
attach_function :opendir, [:string], :pointer
attach_function :pipe, [:pointer], :int
attach_function :read, [:int, :pointer, :size_t], :ssize_t, blocking: true
attach_function :pread, [:int, :pointer, :size_t, :off_t], :ssize_t, blocking: true
attach_function :readlink, [:string, :pointer, :size_t], :ssize_t
attach_function :realpath, [:string, :pointer], :pointer
attach_function :truffleposix_readdir_multiple, [:pointer, :int, :int, :int, :pointer], :int
attach_function :truffleposix_readdir_name, [:pointer], :string
attach_function :rename, [:string, :string], :int
attach_function :truffleposix_rewinddir, [:pointer], :void
attach_function :rmdir, [:string], :int
attach_function :seekdir, [:pointer, :long], :void
attach_function :truffleposix_stat, [:string, :pointer], :int
attach_function :truffleposix_stat_mode, [:string], :mode_t
attach_function :truffleposix_stat_size, [:string], :long
attach_function :symlink, [:string, :string], :int
attach_function :telldir, [:pointer], :long
attach_function :truncate, [:string, :off_t], :int
attach_function :umask, [:mode_t], :mode_t
attach_function :unlink, [:string], :int
attach_function :truffleposix_utimes, [:string, :long, :int, :long, :int], :int
attach_function :write, [:int, :pointer, :size_t], :ssize_t, blocking: true
attach_function :pwrite, [:int, :pointer, :size_t, :off_t], :ssize_t, blocking: true

# retry_eintr=false for both poll because the timeout needs to be decreased on EINTR.
attach_function :poll, [:pointer, :nfds_t, :int], :int, blocking: true, method_name: :poll_blocking_no_retry, guard: :native_boot, retry_eintr: false
attach_function :truffleposix_poll_single_fd, [:int, :int, :int], :int, blocking: true, method_name: :poll_single_fd_blocking_no_retry, guard: :native_boot, retry_eintr: false

# Process-related
attach_function :getegid, [], :gid_t
attach_function :getgid, [], :gid_t
attach_function :setresgid, [:gid_t, :gid_t, :gid_t], :int
attach_function :setregid, [:gid_t, :gid_t], :int
attach_function :setegid, [:uid_t], :int
attach_function :setgid, [:gid_t], :int

attach_function :geteuid, [], :uid_t
attach_function :getuid, [], :uid_t
attach_function :setresuid, [:uid_t, :uid_t, :uid_t], :int
attach_function :setreuid, [:uid_t, :uid_t], :int
attach_function :setruid, [:uid_t], :int
attach_function :seteuid, [:uid_t], :int
attach_function :setuid, [:uid_t], :int

attach_function :getpid, [], :pid_t
attach_function :getppid, [], :pid_t
attach_function :kill, [:pid_t, :int], :int
attach_function :getpgrp, [], :pid_t
attach_function :getpgid, [:pid_t], :pid_t
attach_function :setpgid, [:pid_t, :pid_t], :int
attach_function :setsid, [], :pid_t
attach_function :getsid, [:pid_t], :pid_t

attach_function :getgroups, [:int, :pointer], :int
attach_function :setgroups, [:size_t, :pointer], :int

attach_function :getrlimit, [:int, :pointer], :int
attach_function :setrlimit, [:int, :pointer], :int
attach_function :truffleposix_getrusage, [:pointer], :int

attach_function :truffleposix_getpriority, [:int, :id_t], :int
attach_function :setpriority, [:int, :id_t, :int], :int

attach_function :execve, [:string, :pointer, :pointer], :int
attach_function :truffleposix_posix_spawn, [:string, :pointer, :pointer, :int, :pointer, :int, :int, :pointer], :pid_t
attach_function :truffleposix_waitpid, [:pid_t, :int, :pointer], :pid_t, blocking: true

# ENV-related
attach_function :getenv, [:string], :string
attach_function :setenv, [:string, :string, :int], :int, method_name: :setenv_native
attach_function :unsetenv, [:string], :int, method_name: :unsetenv_native

# Other routines
attach_function :crypt, [:string, :string], :string
attach_function :truffleposix_get_current_user_home, [], :pointer
attach_function :truffleposix_get_user_home, [:string], :pointer
attach_function :truffleposix_free, [:pointer], :void
attach_function :truffleposix_page_size, [], :long

# For benchmarking
attach_function :labs, [:long], :long

# Platform-specific
attach_function :dup3, [:int, :int, :int], :int, guard: :not_darwin

SIMPLE_NATIVE_TYPES = {
  'bool' => 'B',
  'char' => 'b',
  'uchar' => 'B',
  'schar' => 'b',
  'short' => 's',
  'ushort' => 'S',
  'int' => 'i',
  'uint' => 'I',
  'long' => 'l',
  'ulong' => 'L',
  'long_long' => 'l',
  'ulong_long' => 'L',
  'int8' => 'b',
  'uint8' => 'B',
  'int16' => 's',
  'uint16' => 'S',
  'int32' => 'i',
  'uint32' => 'I',
  'int64' => 'l',
  'uint64' => 'L',
  'int8_t' => 'b',
  'uint8_t' => 'B',
  'int16_t' => 's',
  'uint16_t' => 'S',
  'int32_t' => 'i',
  'uint32_t' => 'I',
  'int64_t' => 'l',
  'uint64_t' => 'L',
  'float' => nil,
  'double' => nil,
  'pointer' => 'l'
}

SUPPORTED_PRIMITIVES = %w[
  posix_invoke_i
  posix_invoke_I
  posix_invoke_l
  posix_invoke_i_i
  posix_invoke_I_i
  posix_invoke_I_I
  posix_invoke_i_l
  posix_invoke_i_I
  posix_invoke_I_l
  posix_invoke_I_L
  posix_invoke_l_i
  posix_invoke_l_l
  posix_invoke_S_i
  posix_invoke_S_l
  posix_invoke_S_s
  posix_invoke_v_l
  posix_invoke_i_ii
  posix_invoke_i_iI
  posix_invoke_i_il
  posix_invoke_i_ill
  posix_invoke_i_is
  posix_invoke_i_iIi
  posix_invoke_i_li
  posix_invoke_i_lI
  posix_invoke_i_lis
  posix_invoke_i_ll
  posix_invoke_i_Ll
  posix_invoke_i_lL
  posix_invoke_i_ls
  posix_invoke_l_ll
  posix_invoke_l_lL
  posix_invoke_v_ll
  posix_invoke_i_iii
  posix_invoke_i_iII
  posix_invoke_i_II
  posix_invoke_i_III
  posix_invoke_I_ili
  posix_invoke_i_lii
  posix_invoke_i_liI
  posix_invoke_i_lII
  posix_invoke_i_iLl
  posix_invoke_i_lli
  posix_invoke_i_lLi
  posix_invoke_i_lll
  posix_invoke_l_ili
  posix_invoke_l_llL
  posix_invoke_S_ili
  posix_invoke_i_illi
  posix_invoke_i_liiil
  posix_invoke_i_llili
  posix_invoke_l_lLiiil
  posix_invoke_i_llliliil
  posix_invoke_i_ii_blocking
  posix_invoke_i_iii_blocking
  posix_invoke_i_iil_blocking
  posix_invoke_i_lIi_blocking
  posix_invoke_i_lLi_blocking
  posix_invoke_l_ilL_blocking
  posix_invoke_l_ilLl_blocking
]

def posix_typedef_names
  FUNCTIONS.flat_map { |function| function.argument_types + [function.return_type] }
           .uniq
           .reject { |type| SIMPLE_NATIVE_TYPES.key?(type) || %w[string void].include?(type) }
           .sort
end

def platform_typedefs
  platforms = Hash.new { |hash, key| hash[key] = {} }
  typedefs_to_check = posix_typedef_names

  Dir['src/main/java/org/truffleruby/platform/*NativeConfiguration.java'].sort.each do |path|
    typedefs = {}
    File.read(path).scan(/configuration\.config\("platform\.typedef\.([^"]+)", string\(context, "([^"]+)"\)\);/) do |name, type|
      typedefs[name] = type
    end
    next if typedefs.empty?

    platform, arch = case File.basename(path)
                     when /\ALinux(\w+)NativeConfiguration\.java\z/
                       ['linux', Regexp.last_match(1).downcase]
                     when /\ADarwin(\w+)NativeConfiguration\.java\z/
                       ['darwin', Regexp.last_match(1).downcase]
                     else
                       raise "unknown native configuration platform in #{path}"
                     end
    platforms[platform][arch] = typedefs
  end

  platforms.transform_values do |arch_typedefs|
    reference_arch, reference_typedefs = arch_typedefs.first
    arch_typedefs.each do |arch, typedefs|
      differences = typedefs_to_check.filter_map do |name|
        next if reference_typedefs[name] == typedefs[name]

        "#{name}: #{reference_arch}=#{reference_typedefs[name].inspect}, #{arch}=#{typedefs[name].inspect}"
      end
      next if differences.empty?

      raise "native typedefs differ between #{reference_arch} and #{arch}: #{differences.join(', ')}"
    end

    arch_typedefs.values
  end
end

PLATFORM_TYPEDEFS = platform_typedefs
GENERATED_PLATFORMS = %w[linux darwin].freeze
GENERATED_PLATFORMS.each do |platform|
  raise "could not find #{platform} native typedefs" if PLATFORM_TYPEDEFS[platform].empty?
end

def constant_name(method_name)
  "#{method_name.gsub(/[^a-zA-Z0-9_]/, '_').upcase}_FUNCTION"
end

def resolve_native_type(type, typedefs)
  loop do
    return type if SIMPLE_NATIVE_TYPES.key?(type)

    typedef = typedefs[type]
    raise "unknown native type #{type}" unless typedef

    type = typedef
  end
end

def native_carrier(type, typedefs)
  return 'l' if type == 'string'
  return 'v' if type == 'void'

  resolved_type = resolve_native_type(type, typedefs)
  carrier = SIMPLE_NATIVE_TYPES.fetch(resolved_type)
  raise "unsupported native type #{resolved_type}" unless carrier

  carrier
end

def primitive_return_carrier(type, typedefs)
  carrier = native_carrier(type, typedefs)
  if carrier == 'L'
    resolved_type = resolve_native_type(type, typedefs)
    raise "unsupported unsigned long return type #{type} resolved as #{resolved_type}"
  end

  carrier
end

def primitive_name(argument_types, return_type, blocking, typedefs)
  return_carrier = primitive_return_carrier(return_type, typedefs)
  argument_carriers = argument_types.map do |type|
    carrier = native_carrier(type, typedefs)
    # unsigned byte and short can be handled as signed, as we accept any int value anyway
    %w[B S].include?(carrier) ? carrier.downcase : carrier
  end

  name = "posix_invoke_#{return_carrier}"
  name << "_#{argument_carriers.join}" unless argument_carriers.empty?
  name << '_blocking' if blocking
  name
end

def primitive_name_for_platform(function, platform)
  names = PLATFORM_TYPEDEFS.fetch(platform).map do |typedefs|
    primitive_name(function.argument_types, function.return_type, function.blocking, typedefs)
  end.uniq

  raise "multiple primitive names for #{function.method_name} on #{platform}: #{names.join(', ')}" if names.size != 1

  name = names.first
  raise "unsupported primitive #{name} for #{function.method_name}" unless SUPPORTED_PRIMITIVES.include?(name)

  name
end

def errno_wrapper_name(function)
  "tpe_#{function.method_name}"
end

def c_type(type)
  case type
  when 'string'
    'char *'
  when 'pointer'
    'void *'
  when 'bool'
    'bool'
  when 'uchar', 'uint8', 'uint8_t'
    'unsigned char'
  when 'schar', 'int8', 'int8_t'
    'signed char'
  when 'ushort', 'uint16', 'uint16_t'
    'unsigned short'
  when 'int16', 'int16_t'
    'short'
  when 'uint', 'uint32', 'uint32_t'
    'unsigned int'
  when 'int32', 'int32_t'
    'int'
  when 'ulong'
    'unsigned long'
  when 'long_long'
    'long long'
  when 'ulong_long'
    'unsigned long long'
  else
    type
  end
end

def c_argument_type(type)
  c_type(type)
end

def c_declaration(type, name)
  type = c_type(type).sub(/ \*$/, '*')
  "#{type} #{name}"
end

def c_function_arguments(function)
  method_arguments(function).zip(function.argument_types).map do |name, type|
    c_declaration(c_argument_type(type), name)
  end
end

def c_guard(function)
  C_NATIVE_GUARDS[function.native_name]
end

def c_function_call(function)
  "#{function.native_name}(#{method_arguments(function).join(', ')})"
end

def method_arguments(function)
  ('a'..'h').take(function.argument_types.size)
end

def generated_method(function, primitive_name, indent, platform_comment: nil)
  constant = constant_name(function.method_name)
  arguments = method_arguments(function)
  method_head = if arguments.empty?
                  "def self.#{function.method_name}"
                else
                  "def self.#{function.method_name}(#{arguments.join(', ')})"
                end
  method_head = "#{method_head} # #{platform_comment}" if platform_comment

  body = []
  body << "#{indent}#{method_head}"

  string_arguments = []
  native_arguments = arguments.dup
  arguments.zip(function.argument_types).each_with_index do |(argument, type), index|
    case type
    when 'string'
      buffer_name = "#{argument}_posix_string"
      address_name = "#{argument}_address"
      string_arguments << [argument, buffer_name, address_name]
      native_arguments[index] = address_name
    when 'pointer'
      address_name = "#{argument}_address"
      native_arguments[index] = address_name
    end
  end

  # Pointer arguments are expected to stay live on the caller side until the native call completes, typically because
  # they are read or freed after the call or owned by an enclosing block/ensure. String arguments use temporary thread
  # buffers with explicit frees, so they do not need reachability fences either.

  has_string_arguments = !string_arguments.empty?
  call_indent = has_string_arguments ? "#{indent}    " : "#{indent}  "

  case string_arguments.size
  when 0
    # No allocation needed.
  when 1
    argument, buffer_name, = string_arguments.first
    body << "#{indent}  #{buffer_name} = Primitive.io_thread_buffer_allocate(#{argument}.bytesize + 1)"
    body << "#{indent}  begin"
  else
    string_buffer_names = ['posix_string_buffer', *string_arguments.map { |_, buffer_name, _| buffer_name }]
    string_sizes = string_arguments.map { |argument, _, _| "#{argument}.bytesize + 1" }
    body << "#{indent}  #{string_buffer_names.join(', ')} = Truffle::FFI::Pool.stack_alloc(#{string_sizes.join(', ')})"
    body << "#{indent}  begin"
  end

  arguments.zip(function.argument_types).each_with_index do |(argument, type), index|
    case type
    when 'string'
      _, buffer_name, address_name = string_arguments.find { |string_argument, _, _| string_argument == argument }
      body << "#{call_indent}#{address_name} = #{buffer_name}.address"
      body << "#{call_indent}Primitive.pointer_write_string(#{address_name}, #{argument})"
    when 'pointer'
      body << "#{call_indent}#{native_arguments[index]} = #{argument}.address"
    end
  end

  call_arguments = ([constant] + native_arguments).join(', ')
  call = "Primitive.#{primitive_name}(#{call_arguments})"

  if !function.retry_eintr && !has_string_arguments
    case function.return_type
    when 'pointer'
      body << "#{call_indent}Truffle::FFI::Pointer.new(#{call})"
      body << "#{indent}end"
      return body.join("\n")
    when 'string'
      body << "#{call_indent}result = #{call}"
    else
      body << "#{call_indent}#{call}"
      body << "#{indent}end"
      return body.join("\n")
    end
  elsif function.retry_eintr
    body << "#{call_indent}begin"
    body << "#{call_indent}  result = #{call}"
    body << "#{call_indent}end while result == -1 and Errno.errno == Truffle::POSIX::EINTR"
  else
    body << "#{call_indent}result = #{call}"
  end

  if has_string_arguments
    body << "#{indent}  ensure"
    if string_arguments.size == 1
      _, buffer_name, = string_arguments.first
      body << "#{indent}    Primitive.io_thread_buffer_free(#{buffer_name})"
    else
      body << "#{indent}    Truffle::FFI::Pool.stack_free(posix_string_buffer)"
    end
    body << "#{indent}  end"
  end

  case function.return_type
  when 'pointer'
    body << "#{indent}  Truffle::FFI::Pointer.new(result)"
  when 'string'
    body << "#{indent}  result == 0 ? nil : Primitive.pointer_read_string_to_null(result, nil)"
  else
    body << "#{indent}  result"
  end

  body << "#{indent}end"
  body.join("\n")
end

def generated_function(function, platform, indent)
  platform_comment = { 'linux' => 'Linux', 'darwin' => 'macOS' }.fetch(platform)
  generated_method(function, primitive_name_for_platform(function, platform), indent,
                   platform_comment: platform_comment)
end

def shared_function?(function)
  return false if function.guard == :not_darwin

  GENERATED_PLATFORMS.map { |platform| primitive_name_for_platform(function, platform) }.uniq.size == 1
end

def generated_shared_function(function, indent)
  generated_method(function, primitive_name_for_platform(function, GENERATED_PLATFORMS.first), indent)
end

def emit_platform_methods(code, platform)
  functions = FUNCTIONS.reject do |function|
    shared_function?(function) || platform == 'darwin' && function.guard == :not_darwin
  end

  functions.each do |function|
    code << generated_function(function, platform, '    ')
    code << "\n\n"
  end
end

def emit_shared_methods(code)
  FUNCTIONS.select { |function| shared_function?(function) }.each do |function|
    code << generated_shared_function(function, '  ')
    code << "\n\n"
  end
end

def generated_resolve_function_call(function, indent)
  arguments = [constant_name(function.method_name).to_sym,
               function.method_name.to_sym,
               errno_wrapper_name(function).to_sym]
  "#{indent}generated_posix_resolve_function(#{arguments.map(&:inspect).join(', ')})"
end

def generated_c_wrapper(function)
  arguments = ['int* errno_ptr', *c_function_arguments(function)]
  lines = []
  if guard = c_guard(function)
    lines << "#ifdef #{guard}"
  end
  lines << "#{c_declaration(function.return_type, errno_wrapper_name(function))}(#{arguments.join(', ')}) {"
  if function.return_type == 'void'
    lines << "  #{c_function_call(function)};"
    lines << "  *errno_ptr = errno;"
  else
    lines << "  #{c_declaration(function.return_type, 'result')} = #{c_function_call(function)};"
    lines << "  *errno_ptr = errno;"
    lines << "  return result;"
  end
  lines << '}'
  lines << "#endif" if guard
  lines.join("\n")
end

def emit_function_calls(code, indent)
  FUNCTIONS.each do |function|
    case function.guard
    when :not_darwin
      code << "#{indent}unless Truffle::Platform.darwin?\n"
      code << yield(function, "#{indent}  ")
      code << "\n"
      code << "#{indent}end\n"
    else
      code << yield(function, indent)
      code << "\n"
    end
  end
end

code = <<RUBY
# frozen_string_literal: true

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

# GENERATED BY #{__FILE__}

module Truffle::POSIX
  def self.generated_posix_resolve_function(constant_name, method_name, native_name)
    function = Primitive.posix_resolve native_name if NATIVE
    if function
      const_set constant_name, function
    else
      const_set constant_name, nil
      define_singleton_method(method_name) do |*|
        raise NotImplementedError, "\#{native_name} is not available"
      end
      Primitive.method_unimplement method(method_name)
    end
  end

RUBY

emit_shared_methods(code)

code << <<RUBY
  if Truffle::Platform.linux?

RUBY
emit_platform_methods(code, 'linux')
code << <<RUBY
  elsif Truffle::Platform.darwin?

RUBY
emit_platform_methods(code, 'darwin')
code << <<RUBY
  else
    raise 'unsupported POSIX platform'
  end

RUBY

code << <<RUBY
  Truffle::Boot.delay do
RUBY
emit_function_calls(code, '    ') { |function, indent| generated_resolve_function_call(function, indent) }
code << <<RUBY
  end

end
RUBY

File.write(ruby_file, code)

c_code = <<C
/* Copyright (c) 2026 TruffleRuby contributors
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

// GENERATED BY #{__FILE__}

// tpe stands for Truffle POSIX errno. These wrappers call the native function and write errno to errno_ptr.

// #include-d by src/truffleposix.c
// Outside the src/ dir to work around mx complaining about unknown files.

C

FUNCTIONS.each do |function|
  c_code << generated_c_wrapper(function)
  c_code << "\n\n"
end

File.write(c_file, c_code)
