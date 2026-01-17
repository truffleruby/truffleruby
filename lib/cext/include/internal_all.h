#ifndef RUBY_INTERNAL_ALL_H                                  /*-*-C-*-vi:se ft=c:*/
#define RUBY_INTERNAL_ALL_H 1

#include <internal.h>

// ls lib/cext/include/internal | ruby -e 'puts STDIN.readlines.map { |l| "#include <internal/#{l.chomp}>" }'
// Keep in sync with the list in tool/import-mri-files.sh
#include <internal/bits.h>
#include <internal/compilers.h>
#include <internal/st.h>
#include <internal/static_assert.h>

// ls lib/cext/include/stubs/internal | ruby -e 'puts STDIN.readlines.map { |l| "#include <internal/#{l.chomp}>" }'
#include <internal/hash.h>
#include <internal/string.h>

#endif /* RUBY_INTERNAL_H */
