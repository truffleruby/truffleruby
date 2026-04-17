#ifndef RBS_ALLOCATOR_H
#define RBS_ALLOCATOR_H

#include <stddef.h>

/* Include stdalign.h for C11 and later for alignof support */
#if defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
#include <stdalign.h>
#endif

/*
 * Define a portable alignment macro that works across all supported environments
 */
#if defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
/* C11 or later - use _Alignof directly (always available in C11+) */
#define rbs_alignof(type) _Alignof(type)
#elif defined(__cplusplus) && __cplusplus >= 201103L
/* C++11 or later has alignof keyword */
#define rbs_alignof(type) alignof(type)
#elif defined(__GNUC__) || defined(__clang__)
/* GCC and Clang provide __alignof__ */
#define rbs_alignof(type) __alignof__(type)
#elif defined(_MSC_VER)
/* MSVC provides __alignof */
#define rbs_alignof(type) __alignof(type)
#else
/* Fallback using offset trick for other compilers */
#define rbs_alignof(type) offsetof( \
    struct { char c; type member; },                     \
    member                          \
)
#endif

typedef struct rbs_allocator {
    // The head of a linked list of pages, starting with the most recently allocated page.
    struct rbs_allocator_page *page;

    size_t default_page_payload_size;
} rbs_allocator_t;

rbs_allocator_t *rbs_allocator_init(void);
void rbs_allocator_free(rbs_allocator_t *);
void *rbs_allocator_malloc_impl(rbs_allocator_t *, /*    1    */ size_t size, size_t alignment);
void *rbs_allocator_malloc_many_impl(rbs_allocator_t *, size_t count, size_t size, size_t alignment);
void *rbs_allocator_calloc_impl(rbs_allocator_t *, size_t count, size_t size, size_t alignment);

void *rbs_allocator_realloc_impl(rbs_allocator_t *, void *ptr, size_t old_size, size_t new_size, size_t alignment);

// Use this when allocating memory for a single instance of a type.
#define rbs_allocator_alloc(allocator, type) ((type *) rbs_allocator_malloc_impl((allocator), sizeof(type), rbs_alignof(type)))
// Use this when allocating memory that will be immediately written to in full.
// Such as allocating strings
#define rbs_allocator_alloc_many(allocator, count, type) ((type *) rbs_allocator_malloc_many_impl((allocator), (count), sizeof(type), rbs_alignof(type)))
// Use this when allocating memory that will NOT be immediately written to in full.
// Such as allocating buffers
#define rbs_allocator_calloc(allocator, count, type) ((type *) rbs_allocator_calloc_impl((allocator), (count), sizeof(type), rbs_alignof(type)))
#define rbs_allocator_realloc(allocator, ptr, old_size, new_size, type) ((type *) rbs_allocator_realloc_impl((allocator), (ptr), (old_size), (new_size), rbs_alignof(type)))

#endif
