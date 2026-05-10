/**
 * @file encoding.h
 *
 * The encoding interface and implementations used by the parser.
 */
#ifndef RBS_RBS_ENCODING_H
#define RBS_RBS_ENCODING_H

#include "rbs/defines.h"

#include <assert.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * This struct defines the functions necessary to implement the encoding
 * interface so we can determine how many bytes the subsequent character takes.
 * Each callback should return the number of bytes, or 0 if the next bytes are
 * invalid for the encoding and type.
 */
typedef struct {
    /**
     * Return the number of bytes that the next character takes if it is valid
     * in the encoding. Does not read more than n bytes. It is assumed that n is
     * at least 1.
     */
    size_t (*char_width)(const uint8_t *b, ptrdiff_t n);

    /**
     * Return the number of bytes that the next character takes if it is valid
     * in the encoding and is alphabetical. Does not read more than n bytes. It
     * is assumed that n is at least 1.
     */
    size_t (*alpha_char)(const uint8_t *b, ptrdiff_t n);

    /**
     * Return the number of bytes that the next character takes if it is valid
     * in the encoding and is alphanumeric. Does not read more than n bytes. It
     * is assumed that n is at least 1.
     */
    size_t (*alnum_char)(const uint8_t *b, ptrdiff_t n);

    /**
     * Return true if the next character is valid in the encoding and is an
     * uppercase character. Does not read more than n bytes. It is assumed that
     * n is at least 1.
     */
    bool (*isupper_char)(const uint8_t *b, ptrdiff_t n);

    /**
     * The name of the encoding. This should correspond to a value that can be
     * passed to Encoding.find in Ruby.
     */
    const char *name;

    /**
     * Return true if the encoding is a multibyte encoding.
     */
    bool multibyte;
} rbs_encoding_t;

/**
 * All of the lookup tables use the first bit of each embedded byte to indicate
 * whether the codepoint is alphabetical.
 */
#define RBS_ENCODING_ALPHABETIC_BIT 1 << 0

/**
 * All of the lookup tables use the second bit of each embedded byte to indicate
 * whether the codepoint is alphanumeric.
 */
#define RBS_ENCODING_ALPHANUMERIC_BIT 1 << 1

/**
 * All of the lookup tables use the third bit of each embedded byte to indicate
 * whether the codepoint is uppercase.
 */
#define RBS_ENCODING_UPPERCASE_BIT 1 << 2

/**
 * Return the size of the next character in the UTF-8 encoding.
 *
 * @param b The bytes to read.
 * @param n The number of bytes that can be read.
 * @returns The number of bytes that the next character takes if it is valid in
 *     the encoding, or 0 if it is not.
 */
size_t rbs_encoding_utf_8_char_width(const uint8_t *b, ptrdiff_t n);

/**
 * Return the size of the next character in the UTF-8 encoding if it is an
 * alphabetical character.
 *
 * @param b The bytes to read.
 * @param n The number of bytes that can be read.
 * @returns The number of bytes that the next character takes if it is valid in
 *     the encoding, or 0 if it is not.
 */
size_t rbs_encoding_utf_8_alpha_char(const uint8_t *b, ptrdiff_t n);

/**
 * Return the size of the next character in the UTF-8 encoding if it is an
 * alphanumeric character.
 *
 * @param b The bytes to read.
 * @param n The number of bytes that can be read.
 * @returns The number of bytes that the next character takes if it is valid in
 *     the encoding, or 0 if it is not.
 */
size_t rbs_encoding_utf_8_alnum_char(const uint8_t *b, ptrdiff_t n);

/**
 * Return true if the next character in the UTF-8 encoding if it is an uppercase
 * character.
 *
 * @param b The bytes to read.
 * @param n The number of bytes that can be read.
 * @returns True if the next character is valid in the encoding and is an
 *     uppercase character, or false if it is not.
 */
bool rbs_encoding_utf_8_isupper_char(const uint8_t *b, ptrdiff_t n);

/**
 * This lookup table is referenced in both the UTF-8 encoding file and the
 * parser directly in order to speed up the default encoding processing. It is
 * used to indicate whether a character is alphabetical, alphanumeric, or
 * uppercase in unicode mappings.
 */
extern const uint8_t rbs_encoding_unicode_table[256];

/**
 * These are all of the encodings that prism supports.
 */
typedef enum {
    RBS_ENCODING_UTF_8 = 0,
    RBS_ENCODING_US_ASCII,
    RBS_ENCODING_ASCII_8BIT,
    RBS_ENCODING_EUC_JP,
    RBS_ENCODING_WINDOWS_31J,

// We optionally support excluding the full set of encodings to only support the
// minimum necessary to process Ruby code without encoding comments.
#ifndef RBS_ENCODING_EXCLUDE_FULL
    RBS_ENCODING_BIG5,
    RBS_ENCODING_BIG5_HKSCS,
    RBS_ENCODING_BIG5_UAO,
    RBS_ENCODING_CESU_8,
    RBS_ENCODING_CP51932,
    RBS_ENCODING_CP850,
    RBS_ENCODING_CP852,
    RBS_ENCODING_CP855,
    RBS_ENCODING_CP949,
    RBS_ENCODING_CP950,
    RBS_ENCODING_CP951,
    RBS_ENCODING_EMACS_MULE,
    RBS_ENCODING_EUC_JP_MS,
    RBS_ENCODING_EUC_JIS_2004,
    RBS_ENCODING_EUC_KR,
    RBS_ENCODING_EUC_TW,
    RBS_ENCODING_GB12345,
    RBS_ENCODING_GB18030,
    RBS_ENCODING_GB1988,
    RBS_ENCODING_GB2312,
    RBS_ENCODING_GBK,
    RBS_ENCODING_IBM437,
    RBS_ENCODING_IBM720,
    RBS_ENCODING_IBM737,
    RBS_ENCODING_IBM775,
    RBS_ENCODING_IBM852,
    RBS_ENCODING_IBM855,
    RBS_ENCODING_IBM857,
    RBS_ENCODING_IBM860,
    RBS_ENCODING_IBM861,
    RBS_ENCODING_IBM862,
    RBS_ENCODING_IBM863,
    RBS_ENCODING_IBM864,
    RBS_ENCODING_IBM865,
    RBS_ENCODING_IBM866,
    RBS_ENCODING_IBM869,
    RBS_ENCODING_ISO_8859_1,
    RBS_ENCODING_ISO_8859_2,
    RBS_ENCODING_ISO_8859_3,
    RBS_ENCODING_ISO_8859_4,
    RBS_ENCODING_ISO_8859_5,
    RBS_ENCODING_ISO_8859_6,
    RBS_ENCODING_ISO_8859_7,
    RBS_ENCODING_ISO_8859_8,
    RBS_ENCODING_ISO_8859_9,
    RBS_ENCODING_ISO_8859_10,
    RBS_ENCODING_ISO_8859_11,
    RBS_ENCODING_ISO_8859_13,
    RBS_ENCODING_ISO_8859_14,
    RBS_ENCODING_ISO_8859_15,
    RBS_ENCODING_ISO_8859_16,
    RBS_ENCODING_KOI8_R,
    RBS_ENCODING_KOI8_U,
    RBS_ENCODING_MAC_CENT_EURO,
    RBS_ENCODING_MAC_CROATIAN,
    RBS_ENCODING_MAC_CYRILLIC,
    RBS_ENCODING_MAC_GREEK,
    RBS_ENCODING_MAC_ICELAND,
    RBS_ENCODING_MAC_JAPANESE,
    RBS_ENCODING_MAC_ROMAN,
    RBS_ENCODING_MAC_ROMANIA,
    RBS_ENCODING_MAC_THAI,
    RBS_ENCODING_MAC_TURKISH,
    RBS_ENCODING_MAC_UKRAINE,
    RBS_ENCODING_SHIFT_JIS,
    RBS_ENCODING_SJIS_DOCOMO,
    RBS_ENCODING_SJIS_KDDI,
    RBS_ENCODING_SJIS_SOFTBANK,
    RBS_ENCODING_STATELESS_ISO_2022_JP,
    RBS_ENCODING_STATELESS_ISO_2022_JP_KDDI,
    RBS_ENCODING_TIS_620,
    RBS_ENCODING_UTF8_MAC,
    RBS_ENCODING_UTF8_DOCOMO,
    RBS_ENCODING_UTF8_KDDI,
    RBS_ENCODING_UTF8_SOFTBANK,
    RBS_ENCODING_WINDOWS_1250,
    RBS_ENCODING_WINDOWS_1251,
    RBS_ENCODING_WINDOWS_1252,
    RBS_ENCODING_WINDOWS_1253,
    RBS_ENCODING_WINDOWS_1254,
    RBS_ENCODING_WINDOWS_1255,
    RBS_ENCODING_WINDOWS_1256,
    RBS_ENCODING_WINDOWS_1257,
    RBS_ENCODING_WINDOWS_1258,
    RBS_ENCODING_WINDOWS_874,
#endif

    RBS_ENCODING_MAXIMUM
} rbs_encoding_type_t;

/**
 * This is the table of all of the encodings that prism supports.
 */
extern const rbs_encoding_t rbs_encodings[RBS_ENCODING_MAXIMUM];

/**
 * This is the default UTF-8 encoding. We need a reference to it to quickly
 * create parsers.
 */
#define RBS_ENCODING_UTF_8_ENTRY (&rbs_encodings[RBS_ENCODING_UTF_8])

/**
 * This is the US-ASCII encoding. We need a reference to it to be able to
 * compare against it when a string is being created because it could possibly
 * need to fall back to ASCII-8BIT.
 */
#define RBS_ENCODING_US_ASCII_ENTRY (&rbs_encodings[RBS_ENCODING_US_ASCII])

/**
 * This is the ASCII-8BIT encoding. We need a reference to it so that rbs_strpbrk
 * can compare against it because invalid multibyte characters are not a thing
 * in this encoding. It is also needed for handling Regexp encoding flags.
 */
#define RBS_ENCODING_ASCII_8BIT_ENTRY (&rbs_encodings[RBS_ENCODING_ASCII_8BIT])

/**
 * This is the EUC-JP encoding. We need a reference to it to quickly process
 * regular expression modifiers.
 */
#define RBS_ENCODING_EUC_JP_ENTRY (&rbs_encodings[RBS_ENCODING_EUC_JP])

/**
 * This is the Windows-31J encoding. We need a reference to it to quickly
 * process regular expression modifiers.
 */
#define RBS_ENCODING_WINDOWS_31J_ENTRY (&rbs_encodings[RBS_ENCODING_WINDOWS_31J])

/**
 * Parse the given name of an encoding and return a pointer to the corresponding
 * encoding struct if one can be found, otherwise return NULL.
 *
 * @param start A pointer to the first byte of the name.
 * @param end A pointer to the last byte of the name.
 * @returns A pointer to the encoding struct if one is found, otherwise NULL.
 */
const rbs_encoding_t *rbs_encoding_find(const uint8_t *start, const uint8_t *end);

#endif
