Q$(MX_VERBOSE) = @

OS := $(shell uname)
ifeq ($(OS),Darwin)
SOEXT := dylib
else
SOEXT := so
endif

# Similar values as in RbConfig
ifeq ($(OS),Darwin)
CC := clang
CFLAGS := -fdeclspec -Wall -Werror -fPIC -std=c99 -g -O3
else
CC := gcc
CFLAGS := -Wall -Werror -fPIC -std=c99 -g -O3
endif

LDFLAGS :=

ifeq ($(OS),Darwin)
# Set the install_name of libtrufflerubytrampoline on macOS, so mkmf executables linking to it
# will know they need to look at the rpath to find it.
LIBS := -Wl,-install_name,@rpath/libtrufflerubytrampoline.$(SOEXT)
else
LIBS :=
endif

ROOT := $(realpath ../../../..)
RUBY_HDR_DIR := $(ROOT)/lib/cext/include

OBJECT_FILES := trampoline.o st.o strlcpy.o cext_constants.o varargs.o struct_by_value.o

libtrufflerubytrampoline.$(SOEXT): $(OBJECT_FILES) Makefile
	$(Q) $(CC) -shared $(LDFLAGS) -o $@ $(OBJECT_FILES) $(LIBS)

%.o: %.c Makefile
	$(Q) $(CC) -o $@ -c $(CFLAGS) $(LDFLAGS) -I$(RUBY_HDR_DIR) -I$(ROOT)/lib/cext/include/stubs -I$(TRUFFLE_NFI_NATIVE_INCLUDE) $<
