#ifndef RBS__LEXER_H
#define RBS__LEXER_H

#include "string.h"
#include "util/rbs_encoding.h"

enum RBSTokenType {
    NullType,   /* (Nothing) */
    pEOF,       /* EOF */
    ErrorToken, /* Error */

    pLPAREN,   /* ( */
    pRPAREN,   /* ) */
    pCOLON,    /* : */
    pCOLON2,   /* :: */
    pLBRACKET, /* [ */
    pRBRACKET, /* ] */
    pLBRACE,   /* { */
    pRBRACE,   /* } */
    pHAT,      /* ^ */
    pARROW,    /* -> */
    pFATARROW, /* => */
    pCOMMA,    /* , */
    pBAR,      /* | */
    pAMP,      /* & */
    pSTAR,     /* * */
    pSTAR2,    /* ** */
    pDOT,      /* . */
    pDOT3,     /* ... */
    pBANG,     /* ! */
    pQUESTION, /* ? */
    pLT,       /* < */
    pEQ,       /* = */

    kALIAS,        /* alias */
    kATTRACCESSOR, /* attr_accessor */
    kATTRREADER,   /* attr_reader */
    kATTRWRITER,   /* attr_writer */
    kBOOL,         /* bool */
    kBOT,          /* bot */
    kCLASS,        /* class */
    kDEF,          /* def */
    kEND,          /* end */
    kEXTEND,       /* extend */
    kFALSE,        /* false */
    kIN,           /* in */
    kINCLUDE,      /* include */
    kINSTANCE,     /* instance */
    kINTERFACE,    /* interface */
    kMODULE,       /* module */
    kNIL,          /* nil */
    kOUT,          /* out */
    kPREPEND,      /* prepend */
    kPRIVATE,      /* private */
    kPUBLIC,       /* public */
    kSELF,         /* self */
    kSINGLETON,    /* singleton */
    kTOP,          /* top */
    kTRUE,         /* true */
    kTYPE,         /* type */
    kUNCHECKED,    /* unchecked */
    kUNTYPED,      /* untyped */
    kVOID,         /* void */
    kUSE,          /* use */
    kAS,           /* as */
    k__TODO__,     /* __todo__ */

    tLIDENT,    /* Identifiers starting with lower case */
    tUIDENT,    /* Identifiers starting with upper case */
    tULIDENT,   /* Identifiers starting with `_` followed by upper case */
    tULLIDENT,  /* Identifiers starting with `_` followed by lower case */
    tGIDENT,    /* Identifiers starting with `$` */
    tAIDENT,    /* Identifiers starting with `@` */
    tA2IDENT,   /* Identifiers starting with `@@` */
    tBANGIDENT, /* Identifiers ending with `!` */
    tEQIDENT,   /* Identifiers ending with `=` */
    tQIDENT,    /* Quoted identifier */
    pAREF_OPR,  /* [] */
    tOPERATOR,  /* Operator identifier */

    tCOMMENT,     /* Comment */
    tLINECOMMENT, /* Comment of all line */

    tTRIVIA, /* Trivia tokens -- space and new line */

    tDQSTRING,   /* Double quoted string */
    tSQSTRING,   /* Single quoted string */
    tINTEGER,    /* Integer */
    tSYMBOL,     /* Symbol */
    tDQSYMBOL,   /* Double quoted symbol */
    tSQSYMBOL,   /* Single quoted symbol */
    tANNOTATION, /* Annotation */
};

/**
 * The `byte_pos` (or `char_pos`) is the primary data.
 * The rest are cache.
 *
 * They can be computed from `byte_pos` (or `char_pos`), but it needs full scan from the beginning of the string (depending on the encoding).
 * */
typedef struct {
    int byte_pos;
    int char_pos;
    int line;
    int column;
} rbs_position_t;

typedef struct {
    rbs_position_t start;
    rbs_position_t end;
} rbs_range_t;

typedef struct {
    enum RBSTokenType type;
    rbs_range_t range;
} rbs_token_t;

/**
 * The lexer state is the curren token.
 *
 * ```
 #.   0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6
 * ... " a   s t r i n g   t o k e n "
 *    ^                                   start position (0)
 *                ^                       current position (6)
 *                 ^                      current character ('i', bytes = 1)
 *     ~~~~~~~~~~~                        Token => "a str
 * ```
 * */
typedef struct {
    rbs_string_t string;
    int start_pos;          /* The character position that defines the start of the input */
    int end_pos;            /* The character position that defines the end of the input */
    rbs_position_t current; /* The current position: just before the current_character */
    rbs_position_t start;   /* The start position of the current token */

    unsigned int current_code_point; /* Current character code point */
    size_t current_character_bytes;  /* Current character byte length (0 or 1~4) */

    bool first_token_of_line; /* This flag is used for tLINECOMMENT */

    const rbs_encoding_t *encoding;
} rbs_lexer_t;

extern const rbs_token_t NullToken;
extern const rbs_position_t NullPosition;
extern const rbs_range_t NULL_RANGE;

char *rbs_peek_token(rbs_lexer_t *lexer, rbs_token_t tok);
int rbs_token_chars(rbs_token_t tok);
int rbs_token_bytes(rbs_token_t tok);

#define rbs_null_position_p(pos) (pos.byte_pos == -1)
#define rbs_null_range_p(range) (range.start.byte_pos == -1)
#define rbs_nonnull_pos_or(pos1, pos2) (rbs_null_position_p(pos1) ? pos2 : pos1)
#define RBS_RANGE_BYTES(range) (range.end.byte_pos - range.start.byte_pos)

const char *rbs_token_type_str(enum RBSTokenType type);

/**
 * Returns the next character.
 * */
unsigned int rbs_peek(rbs_lexer_t *lexer);

/**
 * Advances the current position by one character.
 * */
void rbs_skip(rbs_lexer_t *lexer);

/**
 * Read next character and store the codepoint and byte length to the given pointers.
 * 
 * This doesn't update the lexer state.
 * Returns `true` if succeeded, or `false` if reached to EOF.
 * */
bool rbs_next_char(rbs_lexer_t *lexer, unsigned int *codepoint, size_t *bytes);

/**
 * Skip n characters.
 * */
void rbs_skipn(rbs_lexer_t *lexer, size_t size);

/**
 * Return new rbs_token_t with given type.
 * */
rbs_token_t rbs_next_token(rbs_lexer_t *lexer, enum RBSTokenType type);

/**
 * Return new rbs_token_t with EOF type.
 * */
rbs_token_t rbs_next_eof_token(rbs_lexer_t *lexer);

rbs_token_t rbs_lexer_next_token(rbs_lexer_t *lexer);

void rbs_print_token(rbs_token_t tok);

void rbs_print_lexer(rbs_lexer_t *lexer);

#endif
