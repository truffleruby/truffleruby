Q$(MX_VERBOSE) = @
MKMF_MAKEFILE_SUBPROCESS_FLAGS = $(MX_VERBOSE:y=V=1)

OS := $(shell uname)
ifeq ($(OS),Darwin)
SOEXT := dylib
DLEXT := bundle
else
SOEXT := so
DLEXT := so
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

BIGDECIMAL_GEM_EXT := $(ROOT)/lib/gems/gems/bigdecimal-3.1.8/ext/bigdecimal
DEBUG_GEM_EXT := $(ROOT)/lib/gems/gems/debug-1.11.0/ext/debug
NKF_GEM_EXT := $(ROOT)/lib/gems/gems/nkf-0.2.0/ext/nkf
RBS_GEM_EXT := $(ROOT)/lib/gems/gems/rbs-3.8.0/ext/rbs_extension
RBS_GEM_SRC := $(ROOT)/lib/gems/gems/rbs-3.8.0/src
RBS_GEM_INC := $(ROOT)/lib/gems/gems/rbs-3.8.0/include
SYSLOG_GEM_EXT := $(ROOT)/lib/gems/gems/syslog-0.2.0/ext/syslog

IF_EXTCONF_FAIL := ( echo "`pwd`/extconf.rb failed:" 1>&2 && cat mkmf.log && false )

all: $(TRAMPOLINE) $(LIBTRUFFLERUBY) openssl/openssl.$(DLEXT) zlib/zlib.$(DLEXT) \
			psych/psych.$(DLEXT) ripper/ripper.$(DLEXT) syslog/syslog_ext.$(DLEXT) nkf/nkf.$(DLEXT) \
			bigdecimal/bigdecimal.$(DLEXT) date/date_core.$(DLEXT) io-console/console.$(DLEXT) etc/etc.$(DLEXT) \
			rbconfig-sizeof/sizeof.$(DLEXT) json/parser/parser.$(DLEXT) \
			debug/debug.$(DLEXT) rbs/rbs_extension.$(DLEXT)

clean: clean_cexts clean_trampoline

clean_trampoline:
	$(Q) rm -f $(TRAMPOLINE) cext-trampoline/trampoline.c cext-trampoline/*.o

clean_cexts:
	$(Q) rm -f cext/Makefile cext/*.o $(LIBTRUFFLERUBY)
	$(Q) rm -f openssl/Makefile openssl/*.o openssl/openssl.$(DLEXT) openssl/extconf.h
	$(Q) rm -f zlib/Makefile zlib/*.o zlib/zlib.$(DLEXT)
	$(Q) rm -f psych/Makefile psych/*.o psych/psych.$(DLEXT)
	$(Q) rm -f ripper/Makefile ripper/*.o ripper/ripper.$(DLEXT)
	$(Q) rm -f date/Makefile date/*.o date/date_core.$(DLEXT)
	$(Q) rm -f io-console/Makefile io-console/*.o io-console/console.$(DLEXT)
	$(Q) rm -f etc/Makefile etc/*.o etc/etc.$(DLEXT) etc/constdefs.h
	$(Q) rm -f rbconfig-sizeof/Makefile rbconfig-sizeof/*.o rbconfig-sizeof/sizeof.$(DLEXT)
	$(Q) rm -f json/parser/Makefile json/parser/*.o json/parser/parser.$(DLEXT)
	$(Q) echo Cleaning bundled gems
	$(Q) rm -rf bigdecimal
	$(Q) rm -rf debug
	$(Q) rm -rf nkf
	$(Q) rm -rf rbs
	$(Q) rm -rf syslog

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

# openssl
openssl/Makefile: openssl/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd openssl && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

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

# json
json/parser/Makefile: json/parser/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd json/parser && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

json/parser/parser.$(DLEXT): json/parser/Makefile json/parser/*.c json/fbuffer/*.h
	$(Q) cd json/parser && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# psych
psych/Makefile: psych/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd psych && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

psych/psych.$(DLEXT): psych/Makefile psych/*.c psych/*.h
	$(Q) cd psych && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# Bundled gems: the approach is to copy files from the gem into src/main/c/$GEM first, and then proceed like for a default gem. The order is alphabetic.

# bigdecimal bundled gem
bigdecimal/extconf.rb: $(BIGDECIMAL_GEM_EXT)/extconf.rb $(BIGDECIMAL_GEM_EXT)/*.c $(BIGDECIMAL_GEM_EXT)/*.h $(BIGDECIMAL_GEM_EXT)/missing
	$(Q) mkdir -p bigdecimal
	$(Q) cd bigdecimal && cp -R $^ .

bigdecimal/Makefile: bigdecimal/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd bigdecimal && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

bigdecimal/bigdecimal.$(DLEXT): bigdecimal/Makefile
	$(Q) cd bigdecimal && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# debug bundled gem
debug/extconf.rb: $(DEBUG_GEM_EXT)/extconf.rb $(DEBUG_GEM_EXT)/*.c
	$(Q) mkdir -p debug
	$(Q) cd debug && cp $^ .

debug/Makefile: debug/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd debug && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

debug/debug.$(DLEXT): debug/Makefile
	$(Q) cd debug && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# nkf bundled gem
nkf/extconf.rb: $(NKF_GEM_EXT)/extconf.rb $(NKF_GEM_EXT)/*.c $(NKF_GEM_EXT)/nkf-utf8
	$(Q) mkdir -p nkf
	$(Q) cd nkf && cp -R $^ .

nkf/Makefile: nkf/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd nkf && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

nkf/nkf.$(DLEXT): nkf/Makefile
	$(Q) cd nkf && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# rbs bundled gem
rbs/extconf.rb: $(RBS_GEM_EXT)/extconf.rb $(RBS_GEM_EXT)/*.c $(RBS_GEM_EXT)/*.h $(RBS_GEM_SRC)/*.c $(RBS_GEM_INC)
	$(Q) mkdir -p rbs
	$(Q) cd rbs && cp -R $^ .

rbs/Makefile: rbs/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd rbs && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

rbs/rbs_extension.$(DLEXT): rbs/Makefile
	$(Q) cd rbs && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)

# syslog bundled gem
syslog/extconf.rb: $(SYSLOG_GEM_EXT)/extconf.rb $(SYSLOG_GEM_EXT)/*.c
	$(Q) mkdir -p syslog
	$(Q) cd syslog && cp $^ .

syslog/Makefile: syslog/extconf.rb $(EXTCONF_DEPS)
	$(Q) cd syslog && $(RUBY) extconf.rb || $(IF_EXTCONF_FAIL)

syslog/syslog_ext.$(DLEXT): syslog/Makefile
	$(Q) cd syslog && $(MAKE) $(MKMF_MAKEFILE_SUBPROCESS_FLAGS)
