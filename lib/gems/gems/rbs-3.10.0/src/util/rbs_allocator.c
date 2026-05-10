/**
 *  @file rbs_allocator.c
 *
 *  A simple arena allocator that can be freed all at once.
 *
*  This allocator maintains a linked list of pages, which come in two flavours:
 *      1. Small allocation pages, which are the same size as the system page size.
 *      2. Large allocation pages, which are the exact size requested, for sizes greater than the small page size.
 *
 *  Small allocations always fit into the unused space at the end of the "head" page. If there isn't enough room, a new
 *  page is allocated, and the small allocation is placed at its start. This approach wastes that unused slack at the
 *  end of the previous page, but it means that allocations are instant and never scan the linked list to find a gap.
 *
 *  This allocator doesn't support freeing individual allocations. Only the whole arena can be freed at once at the end.
 */

#include "rbs/util/rbs_allocator.h"
#include "rbs/util/rbs_assert.h"

#include <stdlib.h>
#include <string.h> // for memset()
#include <stdint.h>
#include <inttypes.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <fcntl.h>
#endif

typedef struct rbs_allocator_page {
    // The previously allocated page, or NULL if this is the first page.
    struct rbs_allocator_page *next;

    // The size of the payload in bytes.
    size_t size;

    // The offset of the next available byte.
    size_t used;
} rbs_allocator_page_t;

static size_t get_system_page_size(void) {
#ifdef _WIN32
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return si.dwPageSize;
#else
    long sz = sysconf(_SC_PAGESIZE);
    if (sz == -1) return 4096; // Fallback to the common 4KB page size
    return (size_t) sz;
#endif
}

static rbs_allocator_page_t *rbs_allocator_page_new(size_t payload_size) {
    const size_t page_header_size = sizeof(rbs_allocator_page_t);

    rbs_allocator_page_t *page = (rbs_allocator_page_t *) malloc(page_header_size + payload_size);
    page->size = payload_size;
    page->used = 0;

    return page;
}

rbs_allocator_t *rbs_allocator_init(void) {
    rbs_allocator_t *allocator = (rbs_allocator_t *) malloc(sizeof(rbs_allocator_t));

    const size_t system_page_size = get_system_page_size();

    allocator->default_page_payload_size = system_page_size - sizeof(rbs_allocator_page_t);

    allocator->page = rbs_allocator_page_new(allocator->default_page_payload_size);
    allocator->page->next = NULL;

    return allocator;
}

void rbs_allocator_free(rbs_allocator_t *allocator) {
    rbs_allocator_page_t *page = allocator->page;
    while (page) {
        rbs_allocator_page_t *next = page->next;
        free(page);
        page = next;
    }
    free(allocator);
}

// Allocates `new_size` bytes from `allocator`, aligned to an `alignment`-byte boundary.
// Copies `old_size` bytes from `ptr` to the new allocation.
// It always reallocates the memory in new space and thus wastes the old space.
void *rbs_allocator_realloc_impl(rbs_allocator_t *allocator, void *ptr, size_t old_size, size_t new_size, size_t alignment) {
    void *p = rbs_allocator_malloc_impl(allocator, new_size, alignment);
    memcpy(p, ptr, old_size);
    return p;
}

// Allocates `size` bytes from `allocator`, aligned to an `alignment`-byte boundary.
void *rbs_allocator_malloc_impl(rbs_allocator_t *allocator, size_t size, size_t alignment) {
    RBS_ASSERT(size % alignment == 0, "size must be a multiple of the alignment. size: %zu, alignment: %zu", size, alignment);

    if (allocator->default_page_payload_size < size) { // Big allocation, give it its own page.
        rbs_allocator_page_t *new_page = rbs_allocator_page_new(size);

        // This simple allocator can only put small allocations into the head page.
        // Naively prepending this large allocation page to the head of the allocator before the previous head page
        // would waste the remaining space in the head page.
        // So instead, we'll splice in the large page *after* the head page.
        //
        // +-------+    +-----------+        +-----------+
        // | arena |    | head page |        | new_page  |
        // |-------|    |-----------+        |-----------+
        // | *page |--->|  size     |   +--->|  size     |   +---> ... previous tail
        // +-------+    |  offset   |   |    |  offset   |   |
        //              | *next ----+---+    | *next ----+---+
        //              |    ...    |        |    ...    |
        //              +-----------+        +-----------+
        //
        new_page->next = allocator->page->next;
        allocator->page->next = new_page;

        uintptr_t pointer = (uintptr_t) new_page + sizeof(rbs_allocator_page_t);
        return (void *) pointer;
    }

    rbs_allocator_page_t *page = allocator->page;
    if (page->used + size > page->size) {
        // Not enough space. Allocate a new small page and prepend it to the allocator's linked list.
        rbs_allocator_page_t *new_page = rbs_allocator_page_new(allocator->default_page_payload_size);
        new_page->next = allocator->page;
        allocator->page = new_page;
        page = new_page;
    }

    uintptr_t pointer = (uintptr_t) page + sizeof(rbs_allocator_page_t) + page->used;
    page->used += size;
    return (void *) pointer;
}

// Note: This will eagerly fill with zeroes, unlike `calloc()` which can map a page in a page to be zeroed lazily.
//       It's assumed that callers to this function will immediately write to the allocated memory, anyway.
void *rbs_allocator_calloc_impl(rbs_allocator_t *allocator, size_t count, size_t size, size_t alignment) {
    void *p = rbs_allocator_malloc_many_impl(allocator, count, size, alignment);
    memset(p, 0, count * size);
    return p;
}

// Similar to `rbs_allocator_malloc_impl()`, but allocates `count` instances of `size` bytes, aligned to an `alignment`-byte boundary.
void *rbs_allocator_malloc_many_impl(rbs_allocator_t *allocator, size_t count, size_t size, size_t alignment) {
    return rbs_allocator_malloc_impl(allocator, count * size, alignment);
}
