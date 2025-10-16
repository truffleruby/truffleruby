Q$(MX_VERBOSE) = @
MKMF_MAKEFILE_SUBPROCESS_FLAGS = $(MX_VERBOSE:y=V=1)

OS := $(shell uname)
ifeq ($(OS),Darwin)
SOEXT := dylib
DLEXT := bundle
LIBSSL_RPATH =
else
SOEXT := so
DLEXT := so
# This quoting is a bit wild, we need to escape $ in this and the nested Makefile with $$ twice, and avoid expansion twice from the shell,
# so we need nested single quotes like: 'This is '\''nested'\'' single quotes'
LIBSSL_RPATH = '-Wl,-rpath,'\''$$$$ORIGIN/../lib'\'''
endif

ifndef MX_HOME
$(error This Makefile should be called from mx)
endif

ROOT := $(realpath ../../..)
RUBY := $(TRUFFLERUBY_BOOTSTRAP_LAUNCHER)

TRAMPOLINE := cext-trampoline/libtrufflerubytrampoline.$(SOEXT)

RUBY_HEADERS := $(shell find $(ROOT)/lib/cext/include -name '*.h')
CEXT_C_FILES := $(shell find cext -name '*.c')
RBCONFIG := $(ROOT)/lib/truffle/rbconfig.rb
MKMF := $(ROOT)/lib/mri/mkmf.rb
LIBTRUFFLERUBY = cext/libtruffleruby.$(SOEXT)
BASIC_DEPS := Makefile $(RUBY_HEADERS)
BASIC_EXTCONF_DEPS := $(BASIC_DEPS) $(RBCONFIG) $(MKMF)
# C extensions link against libtruffleruby (and might do have_func() checks against it), so it needs to be there before.
# However, if libtruffleruby is recompiled, there is no need to rebuild C extensions, so it's a order-only-prerequisite.
EXTCONF_DEPS := $(BASIC_EXTCONF_DEPS) | $(LIBTRUFFLERUBY)

DEBUG_GEM_EXT := $(ROOT)/lib/gems/gems/debug-1.9.2/ext/debug
RBS_GEM_EXT := $(ROOT)/lib/gems/gems/rbs-3.4.0/ext/rbs_extension

IF_EXTCONF_FAIL := ( echo "`pwd`/extconf.rb failed:" 1>&2 && cat mkmf.log && false )

all: $(TRAMPOLINE) $(LIBTRUFFLERUBY) openssl/openssl.$(DLEXT) zlib/zlib.$(DLEXT) \
			psych/psych.$(DLEXT) ripper/ripper.$(DLEXT) syslog/syslog.$(DLEXT) nkf/nkf.$(DLEXT) \
			bigdecimal/bigdecimal.$(DLEXT) date/date_core.$(DLEXT) io-console/console.$(DLEXT) etc/etc.$(DLEXT) \
			rbconfig-sizeof/sizeof.$(DLEXT) debug/debug.$(DLEXT) rbs/rbs_extension.$(DLEXT)

clean: clean_cexts clean_trampoline

clean_trampoline:
	$(Q) rm -f $(TRAMPOLINE) cext-trampoline/trampoline.c cext-trampoline/*.o

clean_cexts:
	$(Q) rm -f cext/Makefile cext/*.o $(LIBTRUFFLERUBY)
	$(Q) rm -rf libssl_src libssl
	$(Q) rm -f openssl/Makefile openssl/*.o openssl/openssl.$(DLEXT) openssl/extconf.h
	$(Q) rm -f zlib/Makefile zlib/*.o zlib/zlib.$(DLEXT)
	$(Q) rm -f psych/Makefile psych/*.o psych/psych.$(DLEXT)
	$(Q) rm -rf libyaml psych/libyaml psych/libyaml.a
	$(Q) rm -f ripper/Makefile ripper/*.o ripper/ripper.$(DLEXT)
	$(Q) rm -f syslog/Makefile syslog/*.o syslog/syslog.$(DLEXT)
	$(Q) rm -f nkf/Makefile nkf/*.o nkf/nkf.$(DLEXT)
	$(Q) rm -f bigdecimal/Makefile bigdecimal/*.o bigdecimal/bigdecimal.$(DLEXT)
	$(Q) rm -f date/Makefile date/*.o date/date_core.$(DLEXT)
	$(Q) rm -f io-console/Makefile io-console/*.o io-console/console.$(DLEXT)
	$(Q) rm -f etc/Makefile etc/*.o etc/etc.$(DLEXT) etc/constdefs.h
	$(Q) rm -f rbconfig-sizeof/Makefile rbconfig-sizeof/*.o rbconfig-sizeof/sizeof.$(DLEXT)
	$(Q) rm -f debug/Makefile debug/*.o debug/debug.$(DLEXT)
	$(Q) rm -f rbs/Makefile rbs/*.o rbs/rbs_extension.$(DLEXT)

# cext-trampoline
# Remove cext/wrappers.c explicitly, as it was a generated file and so `git pull` does not remove it
cext-trampoline/trampoline.c: $(CEXT_C_FILES) $(BASIC_DEPS) $(ROOT)/tool/generate-cext-trampoline.rb
	$(Q) rm -f cext/wrappers.c
	$(Q) cd $(ROOT) && $(RUBY) tool/generate-cext-trampoline.rb

$(TRAMPOLINE): cext-trampoline/Makefile cext-trampoline/trampoline.c cext-trampoline/*.c Makefile
	$(Q) cd cext-trampoline && $(MAKE)

# libtruffleruby, must be compiled with the GraalVM LLVM Toolchain as it needs to run on Sulong
cext/Makefile: cext/extconf.rb $(BASIC_EXTCONF_DEPS) $(TRAMPOLINE)
	$(Q) cd cext && $(RUBY) --experimental-options --cexts-sulong extconf.rb || $(IF_EXTCONF_FAIL)

$(LIBTRUFFLERUBY): cext/Makefile cext/*.c cext/*.h
	$(Q) cd cext && $(MAKE)

# libssl
# logic based on https://github.com/rbenv/ruby-build/blob/master/bin/ruby-build build_package_openssl()
libssl/lib/libssl.$(SOEXT):
	echo $(LIBSSL_RPATH)
	$(Q) rm -rf libssl_src
	$(Q) cp -pR $(LIBSSL_SRC) libssl_src
	cd libssl_src && ./Configure --prefix="$(CURDIR)/libssl" --openssldir="$(CURDIR)/libssl/ssl" --libdir="lib" zlib-dynamic no-ssl3 shared $(LIBSSL_RPATH)
	cd libssl_src && $(MAKE)
	cd libssl_src && $(MAKE) install_sw install_ssldirs

# openssl
# TODO try to get that --with automatically from rbconfig
# NOTE: --with-openssl-dir=TRUFFLERUBY_BOOTSTRAP_HOME/src/main/c/libssl is automatically added from rbconfig.rb but that's empty because of build ordering issues
openssl/Makefile: openssl/extconf.rb $(EXTCONF_DEPS) libssl/lib/libssl.$(SOEXT)
	$(Q) cd openssl && $(RUBY) extconf.rb --with-openssl-dir=$(CURDIR)/libssl || $(IF_EXTCONF_FAIL)

openssl/openssl.$(DLEXT): openssl/Makefile openssl/*.c openssl/*.h
	$(Q) cd openssl && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# zlib
zlib/Makefile: zlib/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd zlib && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

zlib/zlib.$(DLEXT): zlib/Makefile zlib/zlib.c
	$(Q) cd zlib && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# ripper
ripper/Makefile: ripper/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd ripper && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

ripper/ripper.$(DLEXT): ripper/Makefile ripper/*.c ripper/*.h
	$(Q) cd ripper && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# syslog
syslog/Makefile: syslog/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd syslog && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

syslog/syslog.$(DLEXT): syslog/Makefile syslog/syslog.c
	$(Q) cd syslog && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# nkf
nkf/Makefile: nkf/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd nkf && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

nkf/nkf.$(DLEXT): nkf/Makefile nkf/nkf.c nkf/nkf-utf8/*.c nkf/nkf-utf8/*.h
	$(Q) cd nkf && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# bigdecimal
bigdecimal/Makefile: bigdecimal/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd bigdecimal && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

bigdecimal/bigdecimal.$(DLEXT): bigdecimal/Makefile bigdecimal/bigdecimal.c
	$(Q) cd bigdecimal && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# date
date/Makefile: date/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd date && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

date/date_core.$(DLEXT): date/Makefile date/*.c date/*.h
	$(Q) cd date && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# io-console
io-console/Makefile: io-console/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd io-console && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

io-console/console.$(DLEXT): io-console/Makefile io-console/*.c
	$(Q) cd io-console && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# etc
etc/Makefile: etc/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd etc && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

etc/constdefs.h: etc/mkconstants.rb $(EXTCONF_DEPS)
	$(Q) $(RUBY) $< -o $@

etc/etc.c: etc/constdefs.h

etc/etc.$(DLEXT): etc/Makefile etc/etc.c
	$(Q) cd etc && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# rbconfig/sizeof
rbconfig-sizeof/Makefile: rbconfig-sizeof/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd rbconfig-sizeof && $(RUBY) -rmkmf extconf.rb || $(IF_EXTCONF_FAIL)

rbconfig-sizeof/sizeof.$(DLEXT): rbconfig-sizeof/Makefile rbconfig-sizeof/sizes.c
	$(Q) cd rbconfig-sizeof && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# psych
psych/Makefile: psych/extconf.rb $(EXTCONF_DEPS)
	$(Q) rm -rf libyaml
	$(Q) cp -pR $(LIBYAML_SRC) libyaml
	$(Q) cd psych && $(RUBY) extconf.rb --with-libyaml-source-dir=$(CURDIR)/libyaml || $(IF_EXTCONF_FAIL)

psych/psych.$(DLEXT): psych/Makefile psych/*.c psych/*.h
	$(Q) cd psych && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# debug bundled gem
debug/extconf.rb: $(DEBUG_GEM_EXT)/extconf.rb $(DEBUG_GEM_EXT)/*.c
	$(Q) mkdir -p debug
	$(Q) cd debug && cp $^ .

debug/Makefile: debug/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd debug && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

debug/debug.$(DLEXT): debug/Makefile
	$(Q) cd debug && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# rbs bundled gem
rbs/extconf.rb: $(RBS_GEM_EXT)/extconf.rb $(RBS_GEM_EXT)/*.c $(RBS_GEM_EXT)/*.h
	$(Q) mkdir -p rbs
	$(Q) cd rbs && cp $^ .

rbs/Makefile: rbs/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd rbs && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

rbs/rbs_extension.$(DLEXT): rbs/Makefile
	$(Q) cd rbs && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

