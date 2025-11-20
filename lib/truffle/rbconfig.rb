# frozen-string-literal: false
# truffleruby_primitives: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
#
# Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
# 1. Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
# OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.
#
#  RbConfig.expand method imported from MRI sources
#

module RbConfig

  ruby_home = Truffle::Boot.ruby_home
  TOPDIR = ruby_home

  sulong = Truffle::Boot.get_option('cexts-sulong')

  host_os = Truffle::System.host_os
  host_cpu = Truffle::System.host_cpu
  host_vendor = 'unknown'
  # Some config entries report linux-gnu rather than simply linux.
  host_os_full = host_os == 'linux' ? 'linux-gnu' : host_os
  # Host should match the target triplet for clang or GCC, otherwise some C extension builds will fail.
  host = "#{host_cpu}-#{host_vendor}-#{host_os_full}"

  ruby_install_name = 'truffleruby'
  ruby_base_name = 'ruby'

  ruby_abi_version = Truffle::GemUtil::ABI_VERSION

  arch = "#{host_cpu}-#{host_os}"
  libs = ''
  rmdir = Truffle::Platform.linux? ? 'rmdir --ignore-fail-on-non-empty' : 'rmdir'

  prefix = ruby_home
  rubyhdrdir = "#{prefix}/lib/cext/include"
  includedir = "#{prefix}/lib/cext" # the parent dir of rubyhdrdir
  cflags_pre = ''

  if sulong
    ar = Truffle::Boot.toolchain_executable(:AR)
    cc = Truffle::Boot.toolchain_executable(:CC)
    cxx = Truffle::Boot.toolchain_executable(:CXX)
    ranlib = Truffle::Boot.toolchain_executable(:RANLIB)
    strip = Truffle::Boot.toolchain_executable(:STRIP)

    strip = "#{strip} --keep-section=.llvmbc" unless Truffle::Platform.darwin?
  else
    if Truffle::Platform.linux?
      ar = 'gcc-ar'
      cc = 'gcc' # -std=gnu99 ?
      cxx = 'g++'
      ranlib = 'gcc-ranlib'
      strip = 'strip -S -x'
    elsif Truffle::Platform.darwin?
      ar = 'ar'
      cc = 'clang'
      cxx = 'clang++'
      ranlib = 'ranlib'
      # We add -x here, `strip -A -n` always fails, like `error: symbols referenced by indirect symbol table entries that can't be stripped` even on CRuby.
      # This is notably necessary for grpc where the current logic does not append -x for TruffleRuby:
      # https://github.com/grpc/grpc/blob/54f65e0dbd2151a3ba2ad364327c0c31b200a5ae/src/ruby/ext/grpc/extconf.rb#L125-L126
      strip = 'strip -A -n -x'
      cflags_pre = '-fdeclspec '
    else
      raise 'Unknown platform'
    end
  end

  # Determine the various flags for native compilation
  optflags = sulong ? '' : '-O3 -fno-fast-math'
  debugflags = sulong ? '' : '-ggdb3'
  warnflags = [
    '-Werror=implicit-function-declaration', # https://bugs.ruby-lang.org/issues/18615
    '-Wno-int-conversion',             # MRI has VALUE defined as long while we have it as void*
    '-Wno-int-to-pointer-cast',        # Same as above
    '-Wno-incompatible-pointer-types', # Fix byebug 8.2.1 compile (st_data_t error)
    '-Wno-format-extra-args',          # Our PRIsVALUE generates this because compilers ignore printf extensions
  ]

  defs = ''
  cppflags = ''
  ldflags = ''
  dldflags = Truffle::Platform.darwin? ? '-Wl,-undefined,dynamic_lookup' : '-Wl,-z,lazy'

  cext_dir = "#{prefix}/lib/cext"
  soext = Truffle::Platform::SOEXT

  # Make C extensions use the same libssl as the one used for the openssl C extension
  configure_args = ''

  openssl_prefix = "#{prefix}/src/main/c/libssl"
  configure_args << " '--with-openssl-dir=#{openssl_prefix}'"

  # The below should not be needed as it's redundant but is still necessary
  # until grpc's extconf.rb is changed, as that does not use dir_config("openssl").
  # See https://github.com/truffleruby/truffleruby/issues/3170#issuecomment-1649471551
  # We change the same variables as MRI's --with-opt-dir configure option would.
  cppflags << " -I#{openssl_prefix}/include"
  ldflags << " -L#{openssl_prefix}/lib"
  dldflags << " -L#{openssl_prefix}/lib"

  configure_args << " '--with-libyaml-dir=#{prefix}/src/main/c/libyaml'"

  # Set extra flags needed for --building-core-cexts
  if Truffle::Boot.get_option 'building-core-cexts'
    repo = Truffle::System.get_java_property 'truffleruby.repository'
    libtruffleruby = "#{repo}/src/main/c/cext/libtruffleruby.#{soext}"
    libtrufflerubytrampoline = "#{repo}/src/main/c/cext-trampoline/libtrufflerubytrampoline.#{soext}"

    relative_debug_paths = " -fdebug-prefix-map=#{repo}=."
    cppflags << relative_debug_paths

    warnflags << '-Wundef' # Warn for undefined preprocessor macros for core C extensions
    warnflags << '-Werror' # Make sure there are no warnings in core C extensions
    # If there are deprecations in core C extensions, do not error for them.
    # This would be problematic for extconf.rb checks as they would think such deprecated functions do not exist.
    warnflags << '-Wno-error=deprecated-declarations'
  else
    libtruffleruby = "#{cext_dir}/libtruffleruby.#{soext}"
    libtrufflerubytrampoline = "#{cext_dir}/libtrufflerubytrampoline.#{soext}"
  end

  # We do not link to libtruffleruby here to workaround GR-29448
  librubyarg = ''

  warnflags = warnflags.join(' ')

  major, minor, teeny = RUBY_VERSION.split('.')

  # org.graalvm.version is currently incorrectly set by the launcher in case of a bootstrap GraalVM with a different version than Truffle.
  # So we use java.vendor.version when it's set instead.
  if java_vendor_version = Truffle::System.get_java_property('java.vendor.version')
    # java.vendor.version seems the most reliable property as it looks like "Oracle GraalVM 25.0.1+8.1" or "GraalVM CE 25+37.1".
    graalvm_version = java_vendor_version[/\d+(\.\d+)*/]
    graalvm_version = "#{graalvm_version}.0.0" if graalvm_version.match?(/\A\d+\z/)
  else
    graalvm_version = Truffle::System.get_java_property('org.graalvm.version')
  end

  # Not quite correct, but there is no public API to get the Truffle version currently.
  # Also this is not available when embedded.
  truffle_version = Truffle::System.get_java_property('org.graalvm.version')

  # Sorted alphabetically using sort(1)
  CONFIG = {
    'AR'                => ar,
    'arch'              => arch,
    'ARCH_FLAG'         => '',
    'build'             => host,
    'build_os'          => host_os_full,
    'configure_args'    => configure_args,
    'CC'                => cc,
    'CCDLFLAGS'         => '-fPIC',
    'CP'                => 'cp',
    'CXX'               => cxx,
    'COUTFLAG'          => '-o ',
    'cppflags'          => cppflags,
    'CPPOUTFILE'        => '-o conftest.i',
    'debugflags'        => debugflags,
    'DEFS'              => defs,
    'DLDFLAGS'          => dldflags,
    'DLDLIBS'           => '',
    'DLEXT'             => Truffle::Platform::DLEXT.dup,
    'ENABLE_SHARED'     => 'yes', # We use a dynamic library for libruby
    'EXECUTABLE_EXTS'   => '',
    'exeext'            => '',
    'EXEEXT'            => '',
    'EXTOUT'            => '.ext',
    'graalvm_version'   => graalvm_version,
    'host_alias'        => '',
    'host_cpu'          => host_cpu,
    'host'              => host,
    'host_os'           => host_os_full,
    'includedir'        => includedir,
    'INSTALL'           => '/usr/bin/install -c',
    'java_specification_version' => Truffle::System.get_java_property('java.specification.version'),
    'java_version'      => Truffle::System.get_java_property('java.version'),
    'LDFLAGS'           => ldflags,
    'libdirname'        => 'libdir',
    'LIBEXT'            => 'a',
    'LIBPATHENV'        => 'LD_LIBRARY_PATH',
    'LIBPATHFLAG'       => ' -L%1$-s',
    'LIBRUBY'           => "cext/libtruffleruby.#{soext}",
    'LIBRUBY_A'         => '',
    'LIBRUBYARG'        => librubyarg,
    'LIBRUBYARG_SHARED' => librubyarg,
    'LIBRUBYARG_STATIC' => '',
    'LIBRUBY_SO'        => "cext/libtruffleruby.#{soext}",
    'LIBS'              => libs,
    'libtruffleruby'    => libtruffleruby,
    'libtrufflerubytrampoline' => libtrufflerubytrampoline,
    'MAKEDIRS'          => 'mkdir -p',
    'MAJOR'             => major,
    'MKDIR_P'           => 'mkdir -p',
    'MINOR'             => minor,
    'NULLCMD'           => ':',
    'OBJEXT'            => 'o',
    'optflags'          => optflags,
    'OUTFLAG'           => '-o ',
    'PATCHLEVEL'        => "#{RUBY_PATCHLEVEL}",
    'PATH_SEPARATOR'    => File::PATH_SEPARATOR.dup,
    'PKG_CONFIG'        => 'pkg-config',
    'prefix'            => prefix,
    'RANLIB'            => ranlib,
    'RM'                => 'rm -f',
    'RMALL'             => 'rm -fr',
    'RMDIR'             => rmdir,
    'RMDIRS'            => "#{rmdir} -p",
    'RPATHFLAG'         => ' -Wl,-rpath,%1$-s',
    'RUBY_API_VERSION'  => ruby_abi_version.dup,
    'RUBY_BASE_NAME'    => ruby_base_name,
    'ruby_install_name' => ruby_install_name,
    'RUBY_INSTALL_NAME' => ruby_install_name,
    'RUBY_PROGRAM_VERSION' => RUBY_VERSION.dup,
    'RUBYW_INSTALL_NAME'=> '',
    'ruby_version'      => ruby_abi_version.dup,
    'rubyarchhdrdir'    => rubyhdrdir.dup,
    'rubyhdrdir'        => rubyhdrdir,
    'SOEXT'             => soext.dup,
    'STRIP'             => strip,
    'sysconfdir'        => "#{prefix}/etc", # doesn't exist, as in MRI
    'target_cpu'        => host_cpu,
    'target_os'         => host_os,
    'TEENY'             => teeny,
    'truffle_version'   => truffle_version,
    'UNICODE_VERSION'   => Primitive.encoding_unicode_version,
    'UNICODE_EMOJI_VERSION' => Primitive.encoding_unicode_emoji_version,
    'warnflags'         => warnflags,
    'WERRORFLAG'        => '-Werror',
  }

  MAKEFILE_CONFIG = CONFIG.dup

  expanded = CONFIG
  mkconfig = MAKEFILE_CONFIG

  expanded['RUBY_SO_NAME'] = ruby_base_name
  mkconfig['RUBY_SO_NAME'] = '$(RUBY_BASE_NAME)'

  exec_prefix = \
  expanded['exec_prefix'] = prefix
  mkconfig['exec_prefix'] = '$(prefix)'
  bindir = \
  expanded['bindir'] = "#{exec_prefix}/bin"
  mkconfig['bindir'] = '$(exec_prefix)/bin'
  libdir = \
  expanded['libdir'] = "#{exec_prefix}/lib"
  mkconfig['libdir'] = '$(exec_prefix)/lib'
  rubylibprefix = \
  expanded['rubylibprefix'] = "#{libdir}/#{ruby_base_name}"
  mkconfig['rubylibprefix'] = '$(libdir)/$(RUBY_BASE_NAME)'
  rubylibdir = \
  expanded['rubylibdir'] = "#{libdir}/mri"
  mkconfig['rubylibdir'] = '$(libdir)/mri'
  rubyarchdir = \
  expanded['rubyarchdir'] = rubylibdir
  mkconfig['rubyarchdir'] = '$(rubylibdir)'
  archdir = \
  expanded['archdir'] = rubyarchdir
  mkconfig['archdir'] = '$(rubyarchdir)'
  expanded['archincludedir'] = "#{includedir}/#{arch}"
  mkconfig['archincludedir'] = '$(includedir)/$(arch)'
  sitearch = \
  expanded['sitearch'] = arch
  mkconfig['sitearch'] = '$(arch)'
  sitedir = \
  expanded['sitedir'] = "#{rubylibprefix}/site_ruby"
  mkconfig['sitedir'] = '$(rubylibprefix)/site_ruby'
  expanded['sitehdrdir'] = "#{rubyhdrdir}/site_ruby"
  mkconfig['sitehdrdir'] = '$(rubyhdrdir)/site_ruby'
  # Must be kept in sync with post.rb
  sitelibdir = \
  expanded['sitelibdir'] = "#{sitedir}/#{ruby_abi_version}"
  mkconfig['sitelibdir'] = '$(sitedir)/$(ruby_version)'
  expanded['sitearchdir'] = "#{sitelibdir}/#{sitearch}"
  mkconfig['sitearchdir'] = '$(sitelibdir)/$(sitearch)'
  expanded['topdir'] = archdir
  mkconfig['topdir'] = '$(archdir)'
  datarootdir = \
  expanded['datarootdir'] = "#{prefix}/share"
  mkconfig['datarootdir'] = '$(prefix)/share'
  expanded['datadir'] = datarootdir
  mkconfig['datadir'] = '$(datarootdir)'
  expanded['mandir'] = "#{datarootdir}/man"
  mkconfig['mandir'] = '$(datarootdir)/man'
  expanded['ridir'] = "#{datarootdir}/ri"
  mkconfig['ridir'] = '$(datarootdir)/ri'
  expanded['vendordir'] = "#{rubylibprefix}/vendor_ruby"
  mkconfig['vendordir'] = '$(rubylibprefix)/vendor_ruby'

  expanded['CPP'] = "#{cc} -E"
  mkconfig['CPP'] = '$(CC) -E'

  ldshared_flags = Truffle::Platform.darwin? ? '-dynamic -bundle' : '-shared'
  expanded['LDSHARED'] = "#{cc} #{ldshared_flags}"
  mkconfig['LDSHARED'] = "$(CC) #{ldshared_flags}"
  expanded['LDSHAREDXX'] = "#{cxx} #{ldshared_flags}"
  mkconfig['LDSHAREDXX'] = "$(CXX) #{ldshared_flags}"

  cflags = \
  expanded['cflags'] = "#{cflags_pre}#{optflags} #{debugflags} #{warnflags}"
  mkconfig['cflags'] = "#{cflags_pre}$(optflags) $(debugflags) $(warnflags)"
  expanded['CFLAGS'] = cflags
  mkconfig['CFLAGS'] = '$(cflags)'

  cxxflags = \
  expanded['cxxflags'] = "#{cflags_pre}#{optflags} #{debugflags}"
  mkconfig['cxxflags'] = "#{cflags_pre}$(optflags) $(debugflags)"
  expanded['CXXFLAGS'] = cxxflags
  mkconfig['CXXFLAGS'] = '$(cxxflags)'
  cppflags_hardcoded = Truffle::Platform.darwin? ? ' -D_DARWIN_C_SOURCE' : ''
  expanded['CPPFLAGS'] = "#{cppflags_hardcoded} #{defs} #{cppflags}"
  mkconfig['CPPFLAGS'] = "#{cppflags_hardcoded} $(DEFS) $(cppflags)"

  launcher = Truffle::Boot.get_option 'launcher'
  launcher = "#{bindir}/#{ruby_install_name}" if launcher.empty?
  RUBY = launcher

  def self.ruby
    RUBY
  end

  def RbConfig.expand(val, config = CONFIG)
    newval = val.gsub(/\$\$|\$\(([^()]+)\)|\$\{([^{}]+)\}/) do
      var = $&
      if !(v = $1 || $2)
        '$'
      elsif key = config[v = v[/\A[^:]+(?=(?::(.*?)=(.*))?\z)/]]
        pat, sub = $1, $2
        config[v] = false
        config[v] = RbConfig.expand(key, config)
        key = key.gsub(/#{Regexp.quote(pat)}(?=\s|\z)/n) { sub } if pat
        key
      else
        var
      end
    end
    val.replace(newval) unless newval == val
    val
  end

end

CROSS_COMPILING = nil
