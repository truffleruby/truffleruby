#include "rbs/util/rbs_constant_pool.h"
#include "rbs/util/rbs_assert.h"

/**
 * A relatively simple hash function (djb2) that is used to hash strings. We are
 * optimizing here for simplicity and speed.
 */
static inline uint32_t
rbs_constant_pool_hash(const uint8_t *start, size_t length) {
    // This is a prime number used as the initial value for the hash function.
    uint32_t value = 5381;

    for (size_t index = 0; index < length; index++) {
        value = ((value << 5) + value) + start[index];
    }

    return value;
}

/**
 * https://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
 */
static uint32_t
next_power_of_two(uint32_t v) {
    // Avoid underflow in subtraction on next line.
    if (v == 0) {
        // 1 is the nearest power of 2 to 0 (2^0)
        return 1;
    }
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v++;
    return v;
}

RBS_ATTRIBUTE_UNUSED static bool is_power_of_two(uint32_t size) {
    return (size & (size - 1)) == 0;
}

/**
 * Resize a constant pool to a given capacity.
 */
static inline bool
rbs_constant_pool_resize(rbs_constant_pool_t *pool) {
    RBS_ASSERT(is_power_of_two(pool->capacity), "pool->capacity is not a power of two. Got %i", pool->capacity);

    uint32_t next_capacity = pool->capacity * 2;
    if (next_capacity < pool->capacity) return false;

    const uint32_t mask = next_capacity - 1;
    const size_t element_size = sizeof(rbs_constant_pool_bucket_t) + sizeof(rbs_constant_t);

    void *next = calloc(next_capacity, element_size);
    if (next == NULL) return false;

    rbs_constant_pool_bucket_t *next_buckets = (rbs_constant_pool_bucket_t *) next;
    rbs_constant_t *next_constants = (rbs_constant_t *) (((char *) next) + next_capacity * sizeof(rbs_constant_pool_bucket_t));

    // For each bucket in the current constant pool, find the index in the
    // next constant pool, and insert it.
    for (uint32_t index = 0; index < pool->capacity; index++) {
        rbs_constant_pool_bucket_t *bucket = &pool->buckets[index];

        // If an id is set on this constant, then we know we have content here.
        // In this case we need to insert it into the next constant pool.
        if (bucket->id != RBS_CONSTANT_ID_UNSET) {
            uint32_t next_index = bucket->hash & mask;

            // This implements linear scanning to find the next available slot
            // in case this index is already taken. We don't need to bother
            // comparing the values since we know that the hash is unique.
            while (next_buckets[next_index].id != RBS_CONSTANT_ID_UNSET) {
                next_index = (next_index + 1) & mask;
            }

            // Here we copy over the entire bucket, which includes the id so
            // that they are consistent between resizes.
            next_buckets[next_index] = *bucket;
        }
    }

    // The constants are stable with respect to hash table resizes.
    memcpy(next_constants, pool->constants, pool->size * sizeof(rbs_constant_t));

    // pool->constants and pool->buckets are allocated out of the same chunk
    // of memory, with the buckets coming first.
    free(pool->buckets);
    pool->constants = next_constants;
    pool->buckets = next_buckets;
    pool->capacity = next_capacity;
    return true;
}

// This storage is initialized by `Init_rbs_extension()` in `main.c`.
static rbs_constant_pool_t RBS_GLOBAL_CONSTANT_POOL_STORAGE = { 0 };
rbs_constant_pool_t *RBS_GLOBAL_CONSTANT_POOL = &RBS_GLOBAL_CONSTANT_POOL_STORAGE;

/**
 * Initialize a new constant pool with a given capacity.
 */
bool rbs_constant_pool_init(rbs_constant_pool_t *pool, uint32_t capacity) {
    const uint32_t maximum = (~((uint32_t) 0));
    if (capacity >= ((maximum / 2) + 1)) return false;

    capacity = next_power_of_two(capacity);
    const size_t element_size = sizeof(rbs_constant_pool_bucket_t) + sizeof(rbs_constant_t);
    void *memory = calloc(capacity, element_size);
    if (memory == NULL) return false;

    pool->buckets = (rbs_constant_pool_bucket_t *) memory;
    pool->constants = (rbs_constant_t *) (((char *) memory) + capacity * sizeof(rbs_constant_pool_bucket_t));
    pool->size = 0;
    pool->capacity = capacity;
    return true;
}

/**
 * Return a pointer to the constant indicated by the given constant id.
 */
rbs_constant_t *
rbs_constant_pool_id_to_constant(const rbs_constant_pool_t *pool, rbs_constant_id_t constant_id) {
    RBS_ASSERT(constant_id != RBS_CONSTANT_ID_UNSET && constant_id <= pool->size, "constant_id is not valid. Got %i, pool->size: %i", constant_id, pool->size);
    return &pool->constants[constant_id - 1];
}

/**
 * Find a constant in a constant pool. Returns the id of the constant, or 0 if
 * the constant is not found.
 */
rbs_constant_id_t
rbs_constant_pool_find(const rbs_constant_pool_t *pool, const uint8_t *start, size_t length) {
    RBS_ASSERT(is_power_of_two(pool->capacity), "pool->capacity is not a power of two. Got %i", pool->capacity);
    const uint32_t mask = pool->capacity - 1;

    uint32_t hash = rbs_constant_pool_hash(start, length);
    uint32_t index = hash & mask;
    rbs_constant_pool_bucket_t *bucket;

    while (bucket = &pool->buckets[index], bucket->id != RBS_CONSTANT_ID_UNSET) {
        rbs_constant_t *constant = &pool->constants[bucket->id - 1];
        if ((constant->length == length) && memcmp(constant->start, start, length) == 0) {
            return bucket->id;
        }

        index = (index + 1) & mask;
    }

    return RBS_CONSTANT_ID_UNSET;
}

/**
 * Insert a constant into a constant pool and return its index in the pool.
 */
static inline rbs_constant_id_t
rbs_constant_pool_insert(rbs_constant_pool_t *pool, const uint8_t *start, size_t length, rbs_constant_pool_bucket_type_t type) {
    if (pool->size >= (pool->capacity / 4 * 3)) {
        if (!rbs_constant_pool_resize(pool)) return RBS_CONSTANT_ID_UNSET;
    }

    RBS_ASSERT(is_power_of_two(pool->capacity), "pool->capacity is not a power of two. Got %i", pool->capacity);
    const uint32_t mask = pool->capacity - 1;

    uint32_t hash = rbs_constant_pool_hash(start, length);
    uint32_t index = hash & mask;
    rbs_constant_pool_bucket_t *bucket;

    while (bucket = &pool->buckets[index], bucket->id != RBS_CONSTANT_ID_UNSET) {
        // If there is a collision, then we need to check if the content is the
        // same as the content we are trying to insert. If it is, then we can
        // return the id of the existing constant.
        rbs_constant_t *constant = &pool->constants[bucket->id - 1];

        if ((constant->length == length) && memcmp(constant->start, start, length) == 0) {
            // Since we have found a match, we need to check if this is
            // attempting to insert a shared or an owned constant. We want to
            // prefer shared constants since they don't require allocations.
            if (type == RBS_CONSTANT_POOL_BUCKET_OWNED) {
                // If we're attempting to insert an owned constant and we have
                // an existing constant, then either way we don't want the given
                // memory. Either it's duplicated with the existing constant or
                // it's not necessary because we have a shared version.
                free((void *) start);
            } else if (bucket->type == RBS_CONSTANT_POOL_BUCKET_OWNED) {
                // If we're attempting to insert a shared constant and the
                // existing constant is owned, then we can free the owned
                // constant and replace it with the shared constant.
                free((void *) constant->start);
                constant->start = start;
                bucket->type = (unsigned int) (RBS_CONSTANT_POOL_BUCKET_DEFAULT & 0x3);
            }

            return bucket->id;
        }

        index = (index + 1) & mask;
    }

    // IDs are allocated starting at 1, since the value 0 denotes a non-existent
    // constant.
    uint32_t id = ++pool->size;
    RBS_ASSERT(pool->size < ((uint32_t) (1 << 30)), "pool->size is too large. Got %i", pool->size);

    *bucket = (rbs_constant_pool_bucket_t) {
        .id = (unsigned int) (id & 0x3fffffff),
        .type = (unsigned int) (type & 0x3),
        .hash = hash
    };

    pool->constants[id - 1] = (rbs_constant_t) {
        .start = start,
        .length = length,
    };

    return id;
}

/**
 * Insert a constant into a constant pool. Returns the id of the constant, or
 * RBS_CONSTANT_ID_UNSET if any potential calls to resize fail.
 */
rbs_constant_id_t
rbs_constant_pool_insert_shared(rbs_constant_pool_t *pool, const uint8_t *start, size_t length) {
    return rbs_constant_pool_insert(pool, start, length, RBS_CONSTANT_POOL_BUCKET_DEFAULT);
}

rbs_constant_id_t
rbs_constant_pool_insert_shared_with_encoding(rbs_constant_pool_t *pool, const uint8_t *start, size_t length, const rbs_encoding_t *encoding) {
    return rbs_constant_pool_insert_shared(pool, start, length);
}

/**
 * Insert a constant into a constant pool from memory that is now owned by the
 * constant pool. Returns the id of the constant, or RBS_CONSTANT_ID_UNSET if any
 * potential calls to resize fail.
 */
rbs_constant_id_t
rbs_constant_pool_insert_owned(rbs_constant_pool_t *pool, uint8_t *start, size_t length) {
    return rbs_constant_pool_insert(pool, start, length, RBS_CONSTANT_POOL_BUCKET_OWNED);
}

/**
 * Insert a constant into a constant pool from memory that is constant. Returns
 * the id of the constant, or RBS_CONSTANT_ID_UNSET if any potential calls to
 * resize fail.
 */
rbs_constant_id_t
rbs_constant_pool_insert_constant(rbs_constant_pool_t *pool, const uint8_t *start, size_t length) {
    return rbs_constant_pool_insert(pool, start, length, RBS_CONSTANT_POOL_BUCKET_CONSTANT);
}

/**
 * Free the memory associated with a constant pool.
 */
void rbs_constant_pool_free(rbs_constant_pool_t *pool) {
    // For each constant in the current constant pool, free the contents if the
    // contents are owned.
    for (uint32_t index = 0; index < pool->capacity; index++) {
        rbs_constant_pool_bucket_t *bucket = &pool->buckets[index];

        // If an id is set on this constant, then we know we have content here.
        if (bucket->id != RBS_CONSTANT_ID_UNSET && bucket->type == RBS_CONSTANT_POOL_BUCKET_OWNED) {
            rbs_constant_t *constant = &pool->constants[bucket->id - 1];
            free((void *) constant->start);
        }
    }

    free(pool->buckets);
}
