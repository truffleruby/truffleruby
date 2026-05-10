#include "rbs/util/rbs_unescape.h"
#include "rbs/util/rbs_encoding.h"
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

// Define the escape character mappings
// TODO: use a switch instead
static const struct {
    const char *from;
    const char *to;
} TABLE[] = {
    { "\\a", "\a" },
    { "\\b", "\b" },
    { "\\e", "\033" },
    { "\\f", "\f" },
    { "\\n", "\n" },
    { "\\r", "\r" },
    { "\\s", " " },
    { "\\t", "\t" },
    { "\\v", "\v" },
    { "\\\"", "\"" },
    { "\\'", "'" },
    { "\\\\", "\\" },
    { "\\", "" }
};

// Helper function to convert hex string to integer
static int hex_to_int(const char *hex, int length) {
    int result = 0;
    for (int i = 0; i < length; i++) {
        result = result * 16 + (isdigit(hex[i]) ? hex[i] - '0' : tolower(hex[i]) - 'a' + 10);
    }
    return result;
}

// Helper function to convert octal string to integer
static int octal_to_int(const char *octal, int length) {
    int result = 0;
    for (int i = 0; i < length; i++) {
        result = result * 8 + (octal[i] - '0');
    }
    return result;
}

// Fills buf starting at index 'start' with the UTF-8 encoding of 'codepoint'.
// Returns the number of bytes written, or 0 when the output is not changed.
//
size_t rbs_utf8_fill_codepoint(char *buf, size_t start, size_t end, unsigned int codepoint) {
    if (start + 4 > end) {
        return 0;
    }

    if (codepoint <= 0x7F) {
        buf[start] = codepoint & 0x7F;
        return 1;
    } else if (codepoint <= 0x7FF) {
        buf[start + 0] = 0xC0 | ((codepoint >> 6) & 0x1F);
        buf[start + 1] = 0x80 | (codepoint & 0x3F);
        return 2;
    } else if (codepoint <= 0xFFFF) {
        buf[start + 0] = 0xE0 | ((codepoint >> 12) & 0x0F);
        buf[start + 1] = 0x80 | ((codepoint >> 6) & 0x3F);
        buf[start + 2] = 0x80 | (codepoint & 0x3F);
        return 3;
    } else if (codepoint <= 0x10FFFF) {
        buf[start + 0] = 0xF0 | ((codepoint >> 18) & 0x07);
        buf[start + 1] = 0x80 | ((codepoint >> 12) & 0x3F);
        buf[start + 2] = 0x80 | ((codepoint >> 6) & 0x3F);
        buf[start + 3] = 0x80 | (codepoint & 0x3F);
        return 4;
    } else {
        return 0;
    }
}

rbs_string_t unescape_string(rbs_allocator_t *allocator, const rbs_string_t string, bool is_double_quote, bool is_unicode) {
    if (!string.start) return RBS_STRING_NULL;

    size_t len = string.end - string.start;
    const char *input = string.start;

    // The output cannot be longer than the input even after unescaping.
    char *output = rbs_allocator_alloc_many(allocator, len + 1, char);
    if (!output) return RBS_STRING_NULL;

    size_t i = 0, j = 0;
    while (i < len) {
        if (input[i] == '\\' && i + 1 < len) {
            if (is_double_quote) {
                if (isdigit(input[i + 1])) {
                    // Octal escape
                    int octal_len = 1;
                    while (octal_len < 3 && i + 1 + octal_len < len && isdigit(input[i + 1 + octal_len]))
                        octal_len++;
                    int value = octal_to_int(input + i + 1, octal_len);
                    output[j++] = (char) value;
                    i += octal_len + 1;
                } else if (input[i + 1] == 'x' && i + 3 < len) {
                    // Hex escape
                    int hex_len = isxdigit(input[i + 3]) ? 2 : 1;
                    int value = hex_to_int(input + i + 2, hex_len);
                    output[j++] = (char) value;
                    i += hex_len + 2;
                } else if (input[i + 1] == 'u' && i + 5 < len) {
                    // Unicode escape

                    if (is_unicode) {
                        // The UTF-8 representation is at most 4 bytes, shorter than the input length.
                        int value = hex_to_int(input + i + 2, 4);
                        j += rbs_utf8_fill_codepoint(output, j, len + 1, value);
                        i += 6;
                    } else {
                        // Copy the escape sequence as-is
                        output[j++] = input[i++];
                        output[j++] = input[i++];
                        output[j++] = input[i++];
                        output[j++] = input[i++];
                        output[j++] = input[i++];
                        output[j++] = input[i++];
                    }
                } else {
                    // Other escapes
                    int found = 0;
                    for (size_t k = 0; k < sizeof(TABLE) / sizeof(TABLE[0]); k++) {
                        if (strncmp(input + i, TABLE[k].from, strlen(TABLE[k].from)) == 0) {
                            output[j++] = TABLE[k].to[0];
                            i += strlen(TABLE[k].from);
                            found = 1;
                            break;
                        }
                    }
                    if (!found) {
                        output[j++] = input[i++];
                    }
                }
            } else {
                /* Single quote: only escape ' and \ */
                if (input[i + 1] == '\'' || input[i + 1] == '\\') {
                    output[j++] = input[i + 1];
                    i += 2;
                } else {
                    output[j++] = input[i++];
                }
            }
        } else {
            output[j++] = input[i++];
        }
    }
    output[j] = '\0';
    return rbs_string_new(output, output + j);
}

rbs_string_t rbs_unquote_string(rbs_allocator_t *allocator, rbs_string_t input, const rbs_encoding_t *encoding) {
    unsigned int first_char = input.start[0];

    const char *new_start = input.start;
    const char *new_end = input.end;

    if (first_char == '"' || first_char == '\'' || first_char == '`') {
        new_start += 1;
        new_end -= 1;
    }

    rbs_string_t string = rbs_string_new(new_start, new_end);
    return unescape_string(allocator, string, first_char == '"', encoding == RBS_ENCODING_UTF_8_ENTRY);
}
