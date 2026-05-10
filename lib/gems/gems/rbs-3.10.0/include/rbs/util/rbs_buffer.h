#ifndef RBS__RBS_BUFFER_H
#define RBS__RBS_BUFFER_H

#include "rbs/util/rbs_allocator.h"
#include "rbs/string.h"

#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

/**
 * The default capacity of a rbs_buffer_t.
 * If the buffer needs to grow beyond this capacity, it will be doubled.
 */
#define RBS_BUFFER_DEFAULT_CAPACITY 128

/**
 * A rbs_buffer_t is a simple memory buffer that stores data in a contiguous block of memory.
 */
typedef struct {
    /** The length of the buffer in bytes. */
    size_t length;

    /** The capacity of the buffer in bytes that has been allocated. */
    size_t capacity;

    /** A pointer to the start of the buffer. */
    char *value;
} rbs_buffer_t;

/**
 * Initialize a rbs_buffer_t with its default values.
 *
 * @param allocator The allocator to use.
 * @param buffer The buffer to initialize.
 * @returns True if the buffer was initialized successfully, false otherwise.
 */
bool rbs_buffer_init(rbs_allocator_t *, rbs_buffer_t *buffer);

/**
 * Return the value of the buffer.
 *
 * @param buffer The buffer to get the value of.
 * @returns The value of the buffer.
 */
char *rbs_buffer_value(const rbs_buffer_t *buffer);

/**
 * Return the length of the buffer.
 *
 * @param buffer The buffer to get the length of.
 * @returns The length of the buffer.
 */
size_t rbs_buffer_length(const rbs_buffer_t *buffer);

/**
 * Append a C string to the buffer.
 *
 * @param allocator The allocator to use.
 * @param buffer The buffer to append to.
 * @param value The C string to append.
 */
void rbs_buffer_append_cstr(rbs_allocator_t *, rbs_buffer_t *buffer, const char *value);

/**
 * Append a string to the buffer.
 *
 * @param allocator The allocator to use.
 * @param buffer The buffer to append to.
 * @param value The string to append.
 * @param length The length of the string to append.
 */
void rbs_buffer_append_string(rbs_allocator_t *, rbs_buffer_t *buffer, const char *value, size_t length);

/**
 * Convert the buffer to a rbs_string_t.
 *
 * @param buffer The buffer to convert.
 * @returns The converted rbs_string_t.
 */
rbs_string_t rbs_buffer_to_string(rbs_buffer_t *buffer);

#endif
