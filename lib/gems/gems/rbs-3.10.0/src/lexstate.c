#include "rbs/defines.h"
#include "rbs/lexer.h"
#include "rbs/util/rbs_assert.h"

static const char *RBS_TOKENTYPE_NAMES[] = {
    "NullType",
    "pEOF",
    "ErrorToken",

    "pLPAREN",   /* ( */
    "pRPAREN",   /* ) */
    "pCOLON",    /* : */
    "pCOLON2",   /* :: */
    "pLBRACKET", /* [ */
    "pRBRACKET", /* ] */
    "pLBRACE",   /* { */
    "pRBRACE",   /* } */
    "pHAT",      /* ^ */
    "pARROW",    /* -> */
    "pFATARROW", /* => */
    "pCOMMA",    /* , */
    "pBAR",      /* | */
    "pAMP",      /* & */
    "pSTAR",     /* * */
    "pSTAR2",    /* ** */
    "pDOT",      /* . */
    "pDOT3",     /* ... */
    "pBANG",     /* ! */
    "pQUESTION", /* ? */
    "pLT",       /* < */
    "pEQ",       /* = */

    "kALIAS",        /* alias */
    "kATTRACCESSOR", /* attr_accessor */
    "kATTRREADER",   /* attr_reader */
    "kATTRWRITER",   /* attr_writer */
    "kBOOL",         /* bool */
    "kBOT",          /* bot */
    "kCLASS",        /* class */
    "kDEF",          /* def */
    "kEND",          /* end */
    "kEXTEND",       /* extend */
    "kFALSE",        /* kFALSE */
    "kIN",           /* in */
    "kINCLUDE",      /* include */
    "kINSTANCE",     /* instance */
    "kINTERFACE",    /* interface */
    "kMODULE",       /* module */
    "kNIL",          /* nil */
    "kOUT",          /* out */
    "kPREPEND",      /* prepend */
    "kPRIVATE",      /* private */
    "kPUBLIC",       /* public */
    "kSELF",         /* self */
    "kSINGLETON",    /* singleton */
    "kTOP",          /* top */
    "kTRUE",         /* true */
    "kTYPE",         /* type */
    "kUNCHECKED",    /* unchecked */
    "kUNTYPED",      /* untyped */
    "kVOID",         /* void */
    "kUSE",          /* use */
    "kAS",           /* as */
    "k__TODO__",     /* __todo__ */

    "tLIDENT",  /* Identifiers starting with lower case */
    "tUIDENT",  /* Identifiers starting with upper case */
    "tULIDENT", /* Identifiers starting with `_` */
    "tULLIDENT",
    "tGIDENT",  /* Identifiers starting with `$` */
    "tAIDENT",  /* Identifiers starting with `@` */
    "tA2IDENT", /* Identifiers starting with `@@` */
    "tBANGIDENT",
    "tEQIDENT",
    "tQIDENT",   /* Quoted identifier */
    "pAREF_OPR", /* [] */
    "tOPERATOR", /* Operator identifier */

    "tCOMMENT",
    "tLINECOMMENT",

    "tTRIVIA",

    "tDQSTRING", /* Double quoted string */
    "tSQSTRING", /* Single quoted string */
    "tINTEGER",  /* Integer */
    "tSYMBOL",   /* Symbol */
    "tDQSYMBOL",
    "tSQSYMBOL",
    "tANNOTATION", /* Annotation */
};

const rbs_position_t NullPosition = { -1, -1, -1, -1 };
const rbs_range_t NULL_RANGE = { { -1, -1, -1, -1 }, { -1, -1, -1, -1 } };
const rbs_token_t NullToken = { .type = NullType, .range = { { 0 }, { 0 } } };

const char *rbs_token_type_str(enum RBSTokenType type) {
    return RBS_TOKENTYPE_NAMES[type];
}

int rbs_token_chars(rbs_token_t tok) {
    return tok.range.end.char_pos - tok.range.start.char_pos;
}

int rbs_token_bytes(rbs_token_t tok) {
    return RBS_RANGE_BYTES(tok.range);
}

unsigned int rbs_peek(rbs_lexer_t *lexer) {
    return lexer->current_code_point;
}

bool rbs_next_char(rbs_lexer_t *lexer, unsigned int *codepoint, size_t *byte_len) {
    if (RBS_UNLIKELY(lexer->current.char_pos == lexer->end_pos)) {
        return false;
    }

    const char *start = lexer->string.start + lexer->current.byte_pos;

    // Fast path for ASCII (single-byte) characters
    if ((unsigned int) *start < 128) {
        *codepoint = (unsigned int) *start;
        *byte_len = 1;
        return true;
    }

    *byte_len = lexer->encoding->char_width((const uint8_t *) start, (ptrdiff_t) (lexer->string.end - start));

    if (*byte_len == 1) {
        *codepoint = (unsigned int) *start;
    } else {
        *codepoint = 12523; // Dummy data for "ル" from "ルビー" (Ruby) in Unicode
    }

    return true;
}

void rbs_skip(rbs_lexer_t *lexer) {
    RBS_ASSERT(lexer->current_character_bytes > 0, "rbs_skip called with current_character_bytes == 0");

    if (RBS_UNLIKELY(lexer->current_code_point == '\0')) {
        return;
    }

    unsigned int codepoint;
    size_t byte_len;

    lexer->current.byte_pos += lexer->current_character_bytes;
    lexer->current.char_pos += 1;
    if (lexer->current_code_point == '\n') {
        lexer->current.line += 1;
        lexer->current.column = 0;
        lexer->first_token_of_line = true;
    } else {
        lexer->current.column += 1;
    }

    if (rbs_next_char(lexer, &codepoint, &byte_len)) {
        lexer->current_code_point = codepoint;
        lexer->current_character_bytes = byte_len;
    } else {
        lexer->current_character_bytes = 1;
        lexer->current_code_point = '\0';
    }
}

rbs_token_t rbs_next_token(rbs_lexer_t *lexer, enum RBSTokenType type) {
    rbs_token_t t;

    t.type = type;
    t.range.start = lexer->start;
    t.range.end = lexer->current;
    lexer->start = lexer->current;
    if (type != tTRIVIA) {
        lexer->first_token_of_line = false;
    }

    return t;
}

rbs_token_t rbs_next_eof_token(rbs_lexer_t *lexer) {
    if ((size_t) lexer->current.byte_pos == rbs_string_len(lexer->string) + 1) {
        // End of String
        rbs_token_t t;
        t.type = pEOF;
        t.range.start = lexer->start;
        t.range.end = lexer->start;
        lexer->start = lexer->current;

        return t;
    } else {
        // NULL byte in the middle of the string
        return rbs_next_token(lexer, pEOF);
    }
}

void rbs_skipn(rbs_lexer_t *lexer, size_t size) {
    for (size_t i = 0; i < size; i++) {
        rbs_skip(lexer);
    }
}

char *rbs_peek_token(rbs_lexer_t *lexer, rbs_token_t tok) {
    return (char *) lexer->string.start + tok.range.start.byte_pos;
}
