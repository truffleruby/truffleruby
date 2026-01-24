#ifndef INTERNAL_STRING_H                                /*-*-C-*-vi:se ft=c:*/
#define INTERNAL_STRING_H

#include "ruby.h"
#include <internal/bits.h>
#include <ruby/encoding.h>

// Old String interning API (now: rb_str_to_interned_str()) used by some gems
VALUE rb_fstring(VALUE);

// Useful to implement some public C API functions
static inline const char* search_nonascii(const char *p, const char *e) {
  const char *s, *t;

#if defined(__STDC_VERSION__) && (__STDC_VERSION__ >= 199901L)
# if SIZEOF_UINTPTR_T == 8
#  define NONASCII_MASK UINT64_C(0x8080808080808080)
# elif SIZEOF_UINTPTR_T == 4
#  define NONASCII_MASK UINT32_C(0x80808080)
# else
#  error "don't know what to do."
# endif
#else
# if SIZEOF_UINTPTR_T == 8
#  define NONASCII_MASK ((uintptr_t)0x80808080UL << 32 | (uintptr_t)0x80808080UL)
# elif SIZEOF_UINTPTR_T == 4
#  define NONASCII_MASK 0x80808080UL /* or...? */
# else
#  error "don't know what to do."
# endif
#endif

  if (UNALIGNED_WORD_ACCESS || e - p >= SIZEOF_VOIDP) {
#if !UNALIGNED_WORD_ACCESS
    if ((uintptr_t)p % SIZEOF_VOIDP) {
      int l = SIZEOF_VOIDP - (uintptr_t)p % SIZEOF_VOIDP;
      p += l;
      switch (l) {
        default: UNREACHABLE;
#if SIZEOF_VOIDP > 4
        case 7: if (p[-7]&0x80) return p-7;
        case 6: if (p[-6]&0x80) return p-6;
        case 5: if (p[-5]&0x80) return p-5;
        case 4: if (p[-4]&0x80) return p-4;
#endif
        case 3: if (p[-3]&0x80) return p-3;
        case 2: if (p[-2]&0x80) return p-2;
        case 1: if (p[-1]&0x80) return p-1;
        case 0: break;
      }
    }
#endif
#if defined(HAVE_BUILTIN___BUILTIN_ASSUME_ALIGNED) &&! UNALIGNED_WORD_ACCESS
#define aligned_ptr(value) \
      __builtin_assume_aligned((value), sizeof(uintptr_t))
#else
#define aligned_ptr(value) (value)
#endif
    s = aligned_ptr(p);
    t = (e - (SIZEOF_VOIDP-1));
#undef aligned_ptr
    for (;s < t; s += sizeof(uintptr_t)) {
      uintptr_t word;
      memcpy(&word, s, sizeof(word));
      if (word & NONASCII_MASK) {
#ifdef WORDS_BIGENDIAN
        return (const char *)s + (nlz_intptr(word&NONASCII_MASK)>>3);
#else
        return (const char *)s + (ntz_intptr(word&NONASCII_MASK)>>3);
#endif
      }
    }
    p = (const char *)s;
  }

  switch (e - p) {
    default: UNREACHABLE;
#if SIZEOF_VOIDP > 4
    case 7: if (e[-7]&0x80) return e-7;
    case 6: if (e[-6]&0x80) return e-6;
    case 5: if (e[-5]&0x80) return e-5;
    case 4: if (e[-4]&0x80) return e-4;
#endif
    case 3: if (e[-3]&0x80) return e-3;
    case 2: if (e[-2]&0x80) return e-2;
    case 1: if (e[-1]&0x80) return e-1;
    case 0: return NULL;
  }
}

#endif /* INTERNAL_STRING_H */
