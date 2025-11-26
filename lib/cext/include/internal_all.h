#ifndef RUBY_INTERNAL_ALL_H                                  /*-*-C-*-vi:se ft=c:*/
#define RUBY_INTERNAL_ALL_H 1

#include <internal.h>

// ls lib/cext/include/internal | ruby -e 'puts STDIN.readlines.map { |l| "#include <internal/#{l.chomp}>" }'
#include <internal/bignum.h>
#include <internal/bits.h>
#include <internal/compile.h>
#include <internal/compilers.h>
#include <internal/complex.h>
#include <internal/error.h>
#include <internal/fixnum.h>
#include <internal/imemo.h>
#include <internal/numeric.h>
#include <internal/rational.h>
#include <internal/re.h>
#include <internal/st.h>
#include <internal/static_assert.h>
#include <internal/util.h>

// ls lib/cext/include/stubs/internal | ruby -e 'puts STDIN.readlines.map { |l| "#include <internal/#{l.chomp}>" }'
#include <internal/array.h>
#include <internal/encoding.h>
#include <internal/file.h>
#include <internal/gc.h>
#include <internal/hash.h>
#include <internal/io.h>
#include <internal/sanitizers.h>
#include <internal/string.h>
#include <internal/symbol.h>
#include <internal/thread.h>
#include <internal/time.h>
#include <internal/variable.h>
#include <internal/vm.h>

#endif /* RUBY_INTERNAL_H */
