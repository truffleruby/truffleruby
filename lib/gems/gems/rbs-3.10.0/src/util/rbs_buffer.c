#include "rbs/util/rbs_buffer.h"
#include "rbs/util/rbs_assert.h"

bool rbs_buffer_init(rbs_allocator_t *allocator, rbs_buffer_t *buffer) {
    size_t capacity = RBS_BUFFER_DEFAULT_CAPACITY;

    buffer->length = 0;
    buffer->capacity = capacity;

    buffer->value = rbs_allocator_calloc(allocator, capacity, char);
    return buffer->value != NULL;
}

char *rbs_buffer_value(const rbs_buffer_t *buffer) {
    return buffer->value;
}

size_t rbs_buffer_length(const rbs_buffer_t *buffer) {
    return buffer->length;
}

void rbs_buffer_append_string(rbs_allocator_t *allocator, rbs_buffer_t *buffer, const char *source, size_t length) {
    size_t next_length = buffer->length + length;

    if (next_length > buffer->capacity) {
        size_t old_capacity = buffer->capacity;

        RBS_ASSERT(old_capacity != 0, "Precondition: capacity must be at least 1. Got %zu", old_capacity);

        size_t new_capacity = buffer->capacity * 2;

        while (next_length > new_capacity) {
            new_capacity *= 2;
        }

        char *new_value = rbs_allocator_realloc(allocator, buffer->value, old_capacity, new_capacity, char);
        RBS_ASSERT(new_value != NULL, "Failed to append to buffer. Old capacity: %zu, new capacity: %zu", old_capacity, new_capacity);

        buffer->value = new_value;
        buffer->capacity = new_capacity;
    }

    size_t cursor = buffer->length;
    buffer->length = next_length;
    memcpy(buffer->value + cursor, source, length);
}

void rbs_buffer_append_cstr(rbs_allocator_t *allocator, rbs_buffer_t *buffer, const char *value) {
    rbs_buffer_append_string(allocator, buffer, value, strlen(value));
}

rbs_string_t rbs_buffer_to_string(rbs_buffer_t *buffer) {
    return rbs_string_new(buffer->value, buffer->value + buffer->length);
}
