#include "rbs/parser.h"

#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>

#include "rbs/defines.h"
#include "rbs/lexer.h"
#include "rbs/string.h"
#include "rbs/util/rbs_unescape.h"
#include "rbs/util/rbs_buffer.h"
#include "rbs/util/rbs_assert.h"

#define INTERN(str)                    \
    rbs_constant_pool_insert_constant( \
        RBS_GLOBAL_CONSTANT_POOL,      \
        (const uint8_t *) str,         \
        strlen(str)                    \
    )

#define INTERN_TOKEN(parser, tok)                             \
    rbs_constant_pool_insert_shared_with_encoding(            \
        &parser->constant_pool,                               \
        (const uint8_t *) rbs_peek_token(parser->lexer, tok), \
        rbs_token_bytes(tok),                                 \
        parser->lexer->encoding                               \
    )

#define KEYWORD_CASES   \
    case kBOOL:         \
    case kBOT:          \
    case kCLASS:        \
    case kFALSE:        \
    case kINSTANCE:     \
    case kINTERFACE:    \
    case kNIL:          \
    case kSELF:         \
    case kSINGLETON:    \
    case kTOP:          \
    case kTRUE:         \
    case kVOID:         \
    case kTYPE:         \
    case kUNCHECKED:    \
    case kIN:           \
    case kOUT:          \
    case kEND:          \
    case kDEF:          \
    case kINCLUDE:      \
    case kEXTEND:       \
    case kPREPEND:      \
    case kALIAS:        \
    case kMODULE:       \
    case kATTRREADER:   \
    case kATTRWRITER:   \
    case kATTRACCESSOR: \
    case kPUBLIC:       \
    case kPRIVATE:      \
    case kUNTYPED:      \
    case kUSE:          \
    case kAS:           \
    case k__TODO__:     \
        /* nop */

#define CHECK_PARSE(call) \
    if (!call) {          \
        return false;     \
    }

#define ASSERT_TOKEN(parser, expected_type)                                                                                    \
    if (parser->current_token.type != expected_type) {                                                                         \
        rbs_parser_set_error(parser, parser->current_token, true, "expected a token `%s`", rbs_token_type_str(expected_type)); \
        return false;                                                                                                          \
    }

#define ADVANCE_ASSERT(parser, expected_type) \
    do {                                      \
        rbs_parser_advance(parser);           \
        ASSERT_TOKEN(parser, expected_type)   \
    } while (0);

#define RESET_TABLE_P(table) (table->size == 0)

#define ALLOCATOR() parser->allocator

typedef struct {
    rbs_node_list_t *required_positionals;
    rbs_node_list_t *optional_positionals;
    rbs_node_t *rest_positionals;
    rbs_node_list_t *trailing_positionals;
    rbs_hash_t *required_keywords;
    rbs_hash_t *optional_keywords;
    rbs_node_t *rest_keywords;
} method_params;

/**
 * id_table represents a set of RBS constant IDs.
 * This is used to manage the set of bound variables.
 * */
typedef struct id_table {
    size_t size;
    size_t count;
    rbs_constant_id_t *ids;
    struct id_table *next;
} id_table;

static bool rbs_is_untyped_params(method_params *params) {
    return params->required_positionals == NULL;
}

/**
 * Returns RBS::Location object of `current_token` of a parser parser.
 *
 * @param parser
 * @return New RBS::Location object.
 * */
static rbs_location_t *rbs_location_current_token(rbs_parser_t *parser) {
    return rbs_location_new(ALLOCATOR(), parser->current_token.range);
}

static bool parse_optional(rbs_parser_t *parser, rbs_node_t **optional);
static bool parse_simple(rbs_parser_t *parser, rbs_node_t **type);

/**
 * @returns A borrowed copy of the current token, which does *not* need to be freed.
 */
static rbs_string_t rbs_parser_peek_current_token(rbs_parser_t *parser) {
    rbs_range_t rg = parser->current_token.range;

    const char *start = parser->lexer->string.start + rg.start.byte_pos;
    size_t length = rg.end.byte_pos - rg.start.byte_pos;

    return rbs_string_new(start, start + length);
}

static rbs_constant_id_t rbs_constant_pool_insert_string(rbs_constant_pool_t *self, rbs_string_t string) {
    return rbs_constant_pool_insert_shared(self, (const uint8_t *) string.start, rbs_string_len(string));
}

typedef enum {
    CLASS_NAME = 1,
    INTERFACE_NAME = 2,
    ALIAS_NAME = 4
} TypeNameKind;

static void parser_advance_no_gap(rbs_parser_t *parser) {
    if (parser->current_token.range.end.byte_pos == parser->next_token.range.start.byte_pos) {
        rbs_parser_advance(parser);
    } else {
        rbs_parser_set_error(parser, parser->next_token, true, "unexpected token");
    }
}

/*
  type_name ::= {`::`} (tUIDENT `::`)* <tXIDENT>
              | {(tUIDENT `::`)*} <tXIDENT>
              | {<tXIDENT>}
*/
NODISCARD
static bool parse_type_name(rbs_parser_t *parser, TypeNameKind kind, rbs_range_t *rg, rbs_type_name_t **type_name) {
    bool absolute = false;

    if (rg) {
        rg->start = parser->current_token.range.start;
    }

    if (parser->current_token.type == pCOLON2) {
        absolute = true;
        parser_advance_no_gap(parser);
    }

    rbs_node_list_t *path = rbs_node_list_new(ALLOCATOR());

    while (
        parser->current_token.type == tUIDENT && parser->next_token.type == pCOLON2 && parser->current_token.range.end.byte_pos == parser->next_token.range.start.byte_pos && parser->next_token.range.end.byte_pos == parser->next_token2.range.start.byte_pos
    ) {
        rbs_constant_id_t symbol_value = INTERN_TOKEN(parser, parser->current_token);
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), parser->next_token.range);
        rbs_ast_symbol_t *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, symbol_value);
        rbs_node_list_append(path, (rbs_node_t *) symbol);

        rbs_parser_advance(parser);
        rbs_parser_advance(parser);
    }

    rbs_range_t namespace_range = {
        .start = rg->start,
        .end = parser->current_token.range.end
    };
    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), namespace_range);
    rbs_namespace_t *ns = rbs_namespace_new(ALLOCATOR(), loc, path, absolute);

    switch (parser->current_token.type) {
    case tLIDENT:
        if (kind & ALIAS_NAME) goto success;
        goto error_handling;
    case tULIDENT:
        if (kind & INTERFACE_NAME) goto success;
        goto error_handling;
    case tUIDENT:
        if (kind & CLASS_NAME) goto success;
        goto error_handling;
    default:
        goto error_handling;
    }

success: {
    if (rg) {
        rg->end = parser->current_token.range.end;
    }

    rbs_location_t *symbolLoc = rbs_location_current_token(parser);
    rbs_constant_id_t name = INTERN_TOKEN(parser, parser->current_token);
    rbs_ast_symbol_t *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, name);
    *type_name = rbs_type_name_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), *rg), ns, symbol);
    return true;
}

error_handling: {
    const char *ids = NULL;
    if (kind & ALIAS_NAME) {
        ids = "alias name";
    }
    if (kind & INTERFACE_NAME) {
        ids = "interface name";
    }
    if (kind & CLASS_NAME) {
        ids = "class/module/constant name";
    }

    RBS_ASSERT(ids != NULL, "Unknown kind of type: %i", kind);

    rbs_parser_set_error(parser, parser->current_token, true, "expected one of %s", ids);
    return false;
}
}

/*
  type_list ::= {} type `,` ... <`,`> eol
              | {} type `,` ... `,` <type> eol
*/
NODISCARD
static bool parse_type_list(rbs_parser_t *parser, enum RBSTokenType eol, rbs_node_list_t *types) {
    while (true) {
        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));
        rbs_node_list_append(types, type);

        if (parser->next_token.type == pCOMMA) {
            rbs_parser_advance(parser);

            if (parser->next_token.type == eol) {
                break;
            }
        } else {
            if (parser->next_token.type == eol) {
                break;
            } else {
                rbs_parser_set_error(parser, parser->next_token, true, "comma delimited type list is expected");
                return false;
            }
        }
    }

    return true;
}

static bool is_keyword_token(enum RBSTokenType type) {
    switch (type) {
    case tLIDENT:
    case tUIDENT:
    case tULIDENT:
    case tULLIDENT:
    case tQIDENT:
    case tBANGIDENT:
        KEYWORD_CASES
        return true;
    default:
        return false;
    }
}

/*
  function_param ::= {} <type>
                   | {} type <param>
*/
NODISCARD
static bool parse_function_param(rbs_parser_t *parser, rbs_types_function_param_t **function_param) {
    rbs_range_t type_range;
    type_range.start = parser->next_token.range.start;
    rbs_node_t *type;
    CHECK_PARSE(rbs_parse_type(parser, &type));
    type_range.end = parser->current_token.range.end;

    if (parser->next_token.type == pCOMMA || parser->next_token.type == pRPAREN) {
        rbs_range_t param_range = type_range;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), param_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 1);
        rbs_loc_add_optional_child(loc, INTERN("name"), NULL_RANGE);

        *function_param = rbs_types_function_param_new(ALLOCATOR(), loc, type, NULL);
        return true;
    } else {
        rbs_range_t name_range = parser->next_token.range;

        rbs_parser_advance(parser);

        rbs_range_t param_range = {
            .start = type_range.start,
            .end = name_range.end,
        };

        if (!is_keyword_token(parser->current_token.type)) {
            rbs_parser_set_error(parser, parser->current_token, true, "unexpected token for function parameter name");
            return false;
        }

        rbs_string_t unquoted_str = rbs_unquote_string(ALLOCATOR(), rbs_parser_peek_current_token(parser), parser->lexer->encoding);
        rbs_location_t *symbolLoc = rbs_location_current_token(parser);
        rbs_constant_id_t constant_id = rbs_constant_pool_insert_string(&parser->constant_pool, unquoted_str);
        rbs_ast_symbol_t *name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, constant_id);

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), param_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 1);
        rbs_loc_add_optional_child(loc, INTERN("name"), name_range);

        *function_param = rbs_types_function_param_new(ALLOCATOR(), loc, type, name);
        return true;
    }
}

static rbs_constant_id_t intern_token_start_end(rbs_parser_t *parser, rbs_token_t start_token, rbs_token_t end_token) {
    return rbs_constant_pool_insert_shared_with_encoding(
        &parser->constant_pool,
        (const uint8_t *) rbs_peek_token(parser->lexer, start_token),
        end_token.range.end.byte_pos - start_token.range.start.byte_pos,
        parser->lexer->encoding
    );
}

/*
  keyword_key ::= {} <keyword> `:`
                | {} keyword <`?`> `:`
*/
NODISCARD
static bool parse_keyword_key(rbs_parser_t *parser, rbs_ast_symbol_t **key) {
    rbs_parser_advance(parser);

    rbs_location_t *symbolLoc = rbs_location_current_token(parser);

    if (parser->next_token.type == pQUESTION) {
        *key = rbs_ast_symbol_new(
            ALLOCATOR(),
            symbolLoc,
            &parser->constant_pool,
            intern_token_start_end(parser, parser->current_token, parser->next_token)
        );
        rbs_parser_advance(parser);
    } else {
        *key = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
    }

    return true;
}

/*
  keyword ::= {} keyword `:` <function_param>
*/
NODISCARD
static bool parse_keyword(rbs_parser_t *parser, rbs_hash_t *keywords, rbs_hash_t *memo) {
    rbs_ast_symbol_t *key = NULL;
    CHECK_PARSE(parse_keyword_key(parser, &key));

    if (rbs_hash_find(memo, (rbs_node_t *) key)) {
        rbs_parser_set_error(parser, parser->current_token, true, "duplicated keyword argument");
        return false;
    } else {
        rbs_location_t *loc = rbs_location_current_token(parser);
        rbs_hash_set(memo, (rbs_node_t *) key, (rbs_node_t *) rbs_ast_bool_new(ALLOCATOR(), loc, true));
    }

    ADVANCE_ASSERT(parser, pCOLON);
    rbs_types_function_param_t *param = NULL;
    CHECK_PARSE(parse_function_param(parser, &param));

    rbs_hash_set(keywords, (rbs_node_t *) key, (rbs_node_t *) param);

    return true;
}

/*
Returns true if keyword is given.

  is_keyword === {} KEYWORD `:`
*/
static bool is_keyword(rbs_parser_t *parser) {
    if (is_keyword_token(parser->next_token.type)) {
        if (parser->next_token2.type == pCOLON && parser->next_token.range.end.byte_pos == parser->next_token2.range.start.byte_pos) {
            return true;
        }

        if (parser->next_token2.type == pQUESTION && parser->next_token3.type == pCOLON && parser->next_token.range.end.byte_pos == parser->next_token2.range.start.byte_pos && parser->next_token2.range.end.byte_pos == parser->next_token3.range.start.byte_pos) {
            return true;
        }
    }

    return false;
}

/**
 * Advance token if _next_ token is `type`.
 * Ensures one token advance and `parser->current_token.type == type`, or current token not changed.
 *
 * @returns true if token advances, false otherwise.
 **/
static bool parser_advance_if(rbs_parser_t *parser, enum RBSTokenType type) {
    if (parser->next_token.type == type) {
        rbs_parser_advance(parser);
        return true;
    } else {
        return false;
    }
}

/*
  params ::= {} `)`
           | {} `?` `)`               -- Untyped function params (assign params.required = nil)
           | <required_params> `)`
           | <required_params> `,` `)`

  required_params ::= {} function_param `,` <required_params>
                    | {} <function_param>
                    | {} <optional_params>

  optional_params ::= {} `?` function_param `,` <optional_params>
                    | {} `?` <function_param>
                    | {} <rest_params>

  rest_params ::= {} `*` function_param `,` <trailing_params>
                | {} `*` <function_param>
                | {} <trailing_params>

  trailing_params ::= {} function_param `,` <trailing_params>
                    | {} <function_param>
                    | {} <keywords>

  keywords ::= {} required_keyword `,` <keywords>
             | {} `?` optional_keyword `,` <keywords>
             | {} `**` function_param `,` <keywords>
             | {} <required_keyword>
             | {} `?` <optional_keyword>
             | {} `**` <function_param>
*/
NODISCARD
static bool parse_params(rbs_parser_t *parser, method_params *params) {
    if (parser->next_token.type == pQUESTION && parser->next_token2.type == pRPAREN) {
        params->required_positionals = NULL;
        rbs_parser_advance(parser);
        return true;
    }
    if (parser->next_token.type == pRPAREN) {
        return true;
    }

    rbs_hash_t *memo = rbs_hash_new(ALLOCATOR());

    while (true) {
        switch (parser->next_token.type) {
        case pQUESTION:
            goto PARSE_OPTIONAL_PARAMS;
        case pSTAR:
            goto PARSE_REST_PARAM;
        case pSTAR2:
            goto PARSE_KEYWORDS;
        case pRPAREN:
            goto EOP;

        default:
            if (is_keyword(parser)) {
                goto PARSE_KEYWORDS;
            }

            rbs_types_function_param_t *param = NULL;
            CHECK_PARSE(parse_function_param(parser, &param));
            rbs_node_list_append(params->required_positionals, (rbs_node_t *) param);

            break;
        }

        if (!parser_advance_if(parser, pCOMMA)) {
            goto EOP;
        }
    }

PARSE_OPTIONAL_PARAMS:
    while (true) {
        switch (parser->next_token.type) {
        case pQUESTION:
            rbs_parser_advance(parser);

            if (is_keyword(parser)) {
                CHECK_PARSE(parse_keyword(parser, params->optional_keywords, memo));
                parser_advance_if(parser, pCOMMA);
                goto PARSE_KEYWORDS;
            }

            rbs_types_function_param_t *param = NULL;
            CHECK_PARSE(parse_function_param(parser, &param));
            rbs_node_list_append(params->optional_positionals, (rbs_node_t *) param);

            break;
        default:
            goto PARSE_REST_PARAM;
        }

        if (!parser_advance_if(parser, pCOMMA)) {
            goto EOP;
        }
    }

PARSE_REST_PARAM:
    if (parser->next_token.type == pSTAR) {
        rbs_parser_advance(parser);
        rbs_types_function_param_t *param = NULL;
        CHECK_PARSE(parse_function_param(parser, &param));
        params->rest_positionals = (rbs_node_t *) param;

        if (!parser_advance_if(parser, pCOMMA)) {
            goto EOP;
        }
    }
    goto PARSE_TRAILING_PARAMS;

PARSE_TRAILING_PARAMS:
    while (true) {
        switch (parser->next_token.type) {
        case pQUESTION:
            goto PARSE_KEYWORDS;
        case pSTAR:
            goto EOP;
        case pSTAR2:
            goto PARSE_KEYWORDS;
        case pRPAREN:
            goto EOP;

        default:
            if (is_keyword(parser)) {
                goto PARSE_KEYWORDS;
            }

            rbs_types_function_param_t *param = NULL;
            CHECK_PARSE(parse_function_param(parser, &param));
            rbs_node_list_append(params->trailing_positionals, (rbs_node_t *) param);

            break;
        }

        if (!parser_advance_if(parser, pCOMMA)) {
            goto EOP;
        }
    }

PARSE_KEYWORDS:
    while (true) {
        switch (parser->next_token.type) {
        case pQUESTION:
            rbs_parser_advance(parser);
            if (is_keyword(parser)) {
                CHECK_PARSE(parse_keyword(parser, params->optional_keywords, memo));
            } else {
                rbs_parser_set_error(parser, parser->next_token, true, "optional keyword argument type is expected");
                return false;
            }
            break;

        case pSTAR2:
            rbs_parser_advance(parser);
            rbs_types_function_param_t *param = NULL;
            CHECK_PARSE(parse_function_param(parser, &param));
            params->rest_keywords = (rbs_node_t *) param;
            break;

        case tUIDENT:
        case tLIDENT:
        case tQIDENT:
        case tULIDENT:
        case tULLIDENT:
        case tBANGIDENT:
            KEYWORD_CASES
            if (is_keyword(parser)) {
                CHECK_PARSE(parse_keyword(parser, params->required_keywords, memo));
            } else {
                rbs_parser_set_error(parser, parser->next_token, true, "required keyword argument type is expected");
                return false;
            }
            break;

        default:
            goto EOP;
        }

        if (!parser_advance_if(parser, pCOMMA)) {
            goto EOP;
        }
    }

EOP:
    if (parser->next_token.type != pRPAREN) {
        rbs_parser_set_error(parser, parser->next_token, true, "unexpected token for method type parameters");
        return false;
    }

    return true;
}

/*
  optional ::= {} <simple_type>
             | {} simple_type <`?`>
*/
NODISCARD
static bool parse_optional(rbs_parser_t *parser, rbs_node_t **optional) {
    rbs_range_t rg;
    rg.start = parser->next_token.range.start;

    rbs_node_t *type = NULL;
    CHECK_PARSE(parse_simple(parser, &type));

    if (parser->next_token.type == pQUESTION) {
        rbs_parser_advance(parser);
        rg.end = parser->current_token.range.end;
        rbs_location_t *location = rbs_location_new(ALLOCATOR(), rg);
        *optional = (rbs_node_t *) rbs_types_optional_new(ALLOCATOR(), location, type);
    } else {
        *optional = type;
    }

    return true;
}

static void initialize_method_params(method_params *params, rbs_allocator_t *allocator) {
    *params = (method_params) {
        .required_positionals = rbs_node_list_new(allocator),
        .optional_positionals = rbs_node_list_new(allocator),
        .rest_positionals = NULL,
        .trailing_positionals = rbs_node_list_new(allocator),
        .required_keywords = rbs_hash_new(allocator),
        .optional_keywords = rbs_hash_new(allocator),
        .rest_keywords = NULL,
    };
}

/*
  self_type_binding ::= {} <>
                      | {} `[` `self` `:` type <`]`>
*/
NODISCARD
static bool parse_self_type_binding(rbs_parser_t *parser, rbs_node_t **self_type) {
    if (parser->next_token.type == pLBRACKET) {
        rbs_parser_advance(parser);
        ADVANCE_ASSERT(parser, kSELF);
        ADVANCE_ASSERT(parser, pCOLON);
        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));
        ADVANCE_ASSERT(parser, pRBRACKET);
        *self_type = type;
    }

    return true;
}

typedef struct {
    rbs_node_t *function;
    rbs_types_block_t *block;
    rbs_node_t *function_self_type;
} parse_function_result;

/*
  function ::= {} `(` params `)` self_type_binding? `{` `(` params `)` self_type_binding? `->` optional `}` `->` <optional>
             | {} `(` params `)` self_type_binding? `->` <optional>
             | {} self_type_binding? `{` `(` params `)` self_type_binding? `->` optional `}` `->` <optional>
             | {} self_type_binding? `{` self_type_binding `->` optional `}` `->` <optional>
             | {} self_type_binding? `->` <optional>
*/
NODISCARD
static bool parse_function(rbs_parser_t *parser, bool accept_type_binding, parse_function_result **result) {
    rbs_node_t *function = NULL;
    rbs_types_block_t *block = NULL;
    rbs_node_t *function_self_type = NULL;
    rbs_range_t function_range;
    function_range.start = parser->current_token.range.start;

    method_params params;
    initialize_method_params(&params, ALLOCATOR());

    if (parser->next_token.type == pLPAREN) {
        rbs_parser_advance(parser);
        CHECK_PARSE(parse_params(parser, &params));
        ADVANCE_ASSERT(parser, pRPAREN);
    }

    // Passing NULL to function_self_type means the function itself doesn't accept self type binding. (== method type)
    if (accept_type_binding) {
        CHECK_PARSE(parse_self_type_binding(parser, &function_self_type));
    } else {
        if (rbs_is_untyped_params(&params)) {
            if (parser->next_token.type != pARROW) {
                rbs_parser_set_error(parser, parser->next_token2, true, "A method type with untyped method parameter cannot have block");
                return false;
            }
        }
    }

    bool required = true;
    rbs_range_t block_range;

    if (parser->next_token.type == pQUESTION && parser->next_token2.type == pLBRACE) {
        // Optional block
        block_range.start = parser->next_token.range.start;
        required = false;
        rbs_parser_advance(parser);
    } else if (parser->next_token.type == pLBRACE) {
        block_range.start = parser->next_token.range.start;
    }

    if (parser->next_token.type == pLBRACE) {
        rbs_parser_advance(parser);

        method_params block_params;
        initialize_method_params(&block_params, ALLOCATOR());

        if (parser->next_token.type == pLPAREN) {
            rbs_parser_advance(parser);
            CHECK_PARSE(parse_params(parser, &block_params));
            ADVANCE_ASSERT(parser, pRPAREN);
        }

        rbs_node_t *self_type = NULL;
        CHECK_PARSE(parse_self_type_binding(parser, &self_type));

        ADVANCE_ASSERT(parser, pARROW);
        rbs_node_t *block_return_type = NULL;
        CHECK_PARSE(parse_optional(parser, &block_return_type));

        ADVANCE_ASSERT(parser, pRBRACE);

        block_range.end = parser->current_token.range.end;
        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), block_range);

        rbs_node_t *block_function = NULL;
        if (rbs_is_untyped_params(&block_params)) {
            block_function = (rbs_node_t *) rbs_types_untyped_function_new(ALLOCATOR(), loc, block_return_type);
        } else {
            block_function = (rbs_node_t *) rbs_types_function_new(
                ALLOCATOR(),
                loc,
                block_params.required_positionals,
                block_params.optional_positionals,
                block_params.rest_positionals,
                block_params.trailing_positionals,
                block_params.required_keywords,
                block_params.optional_keywords,
                block_params.rest_keywords,
                block_return_type
            );
        }

        block = rbs_types_block_new(ALLOCATOR(), loc, block_function, required, self_type);
    }

    ADVANCE_ASSERT(parser, pARROW);
    rbs_node_t *type = NULL;
    CHECK_PARSE(parse_optional(parser, &type));

    function_range.end = parser->current_token.range.end;
    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), function_range);
    if (rbs_is_untyped_params(&params)) {
        function = (rbs_node_t *) rbs_types_untyped_function_new(ALLOCATOR(), loc, type);
    } else {
        function = (rbs_node_t *) rbs_types_function_new(
            ALLOCATOR(),
            loc,
            params.required_positionals,
            params.optional_positionals,
            params.rest_positionals,
            params.trailing_positionals,
            params.required_keywords,
            params.optional_keywords,
            params.rest_keywords,
            type
        );
    }

    (*result)->function = function;
    (*result)->block = block;
    (*result)->function_self_type = function_self_type;
    return true;
}

/*
  proc_type ::= {`^`} <function>
*/
NODISCARD
static bool parse_proc_type(rbs_parser_t *parser, rbs_types_proc_t **proc) {
    rbs_position_t start = parser->current_token.range.start;
    parse_function_result *result = rbs_allocator_alloc(ALLOCATOR(), parse_function_result);
    CHECK_PARSE(parse_function(parser, true, &result));

    rbs_position_t end = parser->current_token.range.end;
    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), (rbs_range_t) { .start = start, .end = end });
    *proc = rbs_types_proc_new(ALLOCATOR(), loc, result->function, result->block, result->function_self_type);
    return true;
}

static void check_key_duplication(rbs_parser_t *parser, rbs_hash_t *fields, rbs_node_t *key) {
    if (rbs_hash_find(fields, ((rbs_node_t *) key))) {
        rbs_parser_set_error(parser, parser->current_token, true, "duplicated record key");
    }
}

/**
 * ... `{` ... `}` ...
 *        >   >
 * */
/*
  record_attributes ::= {`{`} record_attribute... <record_attribute> `}`

  record_attribute ::= {} keyword_token `:` <type>
                     | {} literal_type `=>` <type>
*/
NODISCARD
static bool parse_record_attributes(rbs_parser_t *parser, rbs_hash_t **fields) {
    *fields = rbs_hash_new(ALLOCATOR());

    if (parser->next_token.type == pRBRACE) return true;

    while (true) {
        rbs_ast_symbol_t *key = NULL;
        bool required = true;

        if (parser->next_token.type == pQUESTION) {
            // { ?foo: type } syntax
            required = false;
            rbs_parser_advance(parser);
        }

        if (is_keyword(parser)) {
            // { foo: type } syntax
            CHECK_PARSE(parse_keyword_key(parser, &key));

            check_key_duplication(parser, *fields, (rbs_node_t *) key);
            ADVANCE_ASSERT(parser, pCOLON);
        } else {
            // { key => type } syntax
            switch (parser->next_token.type) {
            case tSYMBOL:
            case tSQSYMBOL:
            case tDQSYMBOL:
            case tSQSTRING:
            case tDQSTRING:
            case tINTEGER:
            case kTRUE:
            case kFALSE: {
                rbs_node_t *type = NULL;
                CHECK_PARSE(parse_simple(parser, &type));

                key = (rbs_ast_symbol_t *) ((rbs_types_literal_t *) type)->literal;
                break;
            }
            default:
                rbs_parser_set_error(parser, parser->next_token, true, "unexpected record key token");
                return false;
            }
            check_key_duplication(parser, *fields, (rbs_node_t *) key);
            ADVANCE_ASSERT(parser, pFATARROW);
        }

        rbs_range_t field_range;
        field_range.start = parser->current_token.range.end;

        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));

        field_range.end = parser->current_token.range.end;
        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), field_range);
        rbs_hash_set(*fields, (rbs_node_t *) key, (rbs_node_t *) rbs_types_record_field_type_new(ALLOCATOR(), loc, type, required));

        if (parser_advance_if(parser, pCOMMA)) {
            if (parser->next_token.type == pRBRACE) {
                break;
            }
        } else {
            break;
        }
    }
    return true;
}

/*
  symbol ::= {<tSYMBOL>}
*/
NODISCARD
static bool parse_symbol(rbs_parser_t *parser, rbs_location_t *location, rbs_types_literal_t **symbol) {
    size_t offset_bytes = parser->lexer->encoding->char_width((const uint8_t *) ":", (size_t) 1);
    size_t bytes = rbs_token_bytes(parser->current_token) - offset_bytes;

    rbs_ast_symbol_t *literal;

    switch (parser->current_token.type) {
    case tSYMBOL: {
        rbs_location_t *symbolLoc = rbs_location_current_token(parser);

        char *buffer = rbs_peek_token(parser->lexer, parser->current_token);
        rbs_constant_id_t constant_id = rbs_constant_pool_insert_shared(
            &parser->constant_pool,
            (const uint8_t *) buffer + offset_bytes,
            bytes
        );
        literal = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, constant_id);
        break;
    }
    case tDQSYMBOL:
    case tSQSYMBOL: {
        rbs_location_t *symbolLoc = rbs_location_current_token(parser);
        rbs_string_t current_token = rbs_parser_peek_current_token(parser);

        rbs_string_t symbol = rbs_string_new(current_token.start + offset_bytes, current_token.end);

        rbs_string_t unquoted_symbol = rbs_unquote_string(ALLOCATOR(), symbol, parser->lexer->encoding);

        rbs_constant_id_t constant_id = rbs_constant_pool_insert_string(&parser->constant_pool, unquoted_symbol);

        literal = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, constant_id);
        break;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }

    *symbol = rbs_types_literal_new(ALLOCATOR(), location, (rbs_node_t *) literal);
    return true;
}

/*
 instance_type ::= {type_name} <type_args>

 type_args ::= {} <> /empty/
             | {} `[` type_list <`]`>
 */
NODISCARD
static bool parse_instance_type(rbs_parser_t *parser, bool parse_alias, rbs_node_t **type) {
    TypeNameKind expected_kind = (TypeNameKind) (INTERFACE_NAME | CLASS_NAME);
    if (parse_alias) {
        expected_kind = (TypeNameKind) (expected_kind | ALIAS_NAME);
    }

    rbs_range_t name_range;
    rbs_type_name_t *type_name = NULL;
    CHECK_PARSE(parse_type_name(parser, expected_kind, &name_range, &type_name));

    rbs_node_list_t *types = rbs_node_list_new(ALLOCATOR());

    TypeNameKind kind;
    switch (parser->current_token.type) {
    case tUIDENT: {
        kind = CLASS_NAME;
        break;
    }
    case tULIDENT: {
        kind = INTERFACE_NAME;
        break;
    }
    case tLIDENT: {
        kind = ALIAS_NAME;
        break;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "unexpected token for type name");
        return false;
    }

    rbs_range_t args_range;
    if (parser->next_token.type == pLBRACKET) {
        rbs_parser_advance(parser);
        args_range.start = parser->current_token.range.start;
        CHECK_PARSE(parse_type_list(parser, pRBRACKET, types));
        ADVANCE_ASSERT(parser, pRBRACKET);
        args_range.end = parser->current_token.range.end;
    } else {
        args_range = NULL_RANGE;
    }

    rbs_range_t type_range = {
        .start = name_range.start,
        .end = rbs_nonnull_pos_or(args_range.end, name_range.end),
    };

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), type_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_optional_child(loc, INTERN("args"), args_range);

    if (kind == CLASS_NAME) {
        *type = (rbs_node_t *) rbs_types_class_instance_new(ALLOCATOR(), loc, type_name, types);
    } else if (kind == INTERFACE_NAME) {
        *type = (rbs_node_t *) rbs_types_interface_new(ALLOCATOR(), loc, type_name, types);
    } else if (kind == ALIAS_NAME) {
        *type = (rbs_node_t *) rbs_types_alias_new(ALLOCATOR(), loc, type_name, types);
    }

    return true;
}

/*
  singleton_type ::= {`singleton`} `(` type_name <`)`>
*/
NODISCARD
static bool parse_singleton_type(rbs_parser_t *parser, rbs_types_class_singleton_t **singleton) {
    ASSERT_TOKEN(parser, kSINGLETON);

    rbs_range_t type_range;
    type_range.start = parser->current_token.range.start;
    ADVANCE_ASSERT(parser, pLPAREN);
    rbs_parser_advance(parser);

    rbs_range_t name_range;
    rbs_type_name_t *type_name = NULL;
    CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &name_range, &type_name));

    ADVANCE_ASSERT(parser, pRPAREN);
    type_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), type_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 1);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);

    *singleton = rbs_types_class_singleton_new(ALLOCATOR(), loc, type_name);
    return true;
}

/**
 * Returns true if given type variable is recorded in the table.
 * If not found, it goes one table up, if it's not a reset table.
 * Or returns false, if it's a reset table.
 * */
static bool parser_typevar_member(rbs_parser_t *parser, rbs_constant_id_t id) {
    id_table *table = parser->vars;

    while (table && !RESET_TABLE_P(table)) {
        for (size_t i = 0; i < table->count; i++) {
            if (table->ids[i] == id) {
                return true;
            }
        }

        table = table->next;
    }

    return false;
}

/*
  simple ::= {} `(` type <`)`>
           | {} <base type>
           | {} <type_name>
           | {} class_instance `[` type_list <`]`>
           | {} `singleton` `(` type_name <`)`>
           | {} `[` type_list <`]`>
           | {} `{` record_attributes <`}`>
           | {} `^` <function>
*/
NODISCARD
static bool parse_simple(rbs_parser_t *parser, rbs_node_t **type) {
    rbs_parser_advance(parser);

    switch (parser->current_token.type) {
    case pLPAREN: {
        rbs_node_t *lparen_type;
        CHECK_PARSE(rbs_parse_type(parser, &lparen_type));
        ADVANCE_ASSERT(parser, pRPAREN);
        *type = lparen_type;
        return true;
    }
    case kBOOL: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_bool_new(ALLOCATOR(), loc);
        return true;
    }
    case kBOT: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_bottom_new(ALLOCATOR(), loc);
        return true;
    }
    case kCLASS: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_class_new(ALLOCATOR(), loc);
        return true;
    }
    case kINSTANCE: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_instance_new(ALLOCATOR(), loc);
        return true;
    }
    case kNIL: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_nil_new(ALLOCATOR(), loc);
        return true;
    }
    case kSELF: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_self_new(ALLOCATOR(), loc);
        return true;
    }
    case kTOP: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_top_new(ALLOCATOR(), loc);
        return true;
    }
    case kVOID: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_void_new(ALLOCATOR(), loc);
        return true;
    }
    case kUNTYPED: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_any_new(ALLOCATOR(), loc, false);
        return true;
    }
    case k__TODO__: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_bases_any_new(ALLOCATOR(), loc, true);
        return true;
    }
    case tINTEGER: {
        rbs_location_t *loc = rbs_location_current_token(parser);

        rbs_string_t string = rbs_parser_peek_current_token(parser);
        rbs_string_t stripped_string = rbs_string_strip_whitespace(&string);

        rbs_node_t *literal = (rbs_node_t *) rbs_ast_integer_new(ALLOCATOR(), loc, stripped_string);
        *type = (rbs_node_t *) rbs_types_literal_new(ALLOCATOR(), loc, literal);
        return true;
    }
    case kTRUE: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_literal_new(ALLOCATOR(), loc, (rbs_node_t *) rbs_ast_bool_new(ALLOCATOR(), loc, true));
        return true;
    }
    case kFALSE: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        *type = (rbs_node_t *) rbs_types_literal_new(ALLOCATOR(), loc, (rbs_node_t *) rbs_ast_bool_new(ALLOCATOR(), loc, false));
        return true;
    }
    case tSQSTRING:
    case tDQSTRING: {
        rbs_location_t *loc = rbs_location_current_token(parser);

        rbs_string_t unquoted_str = rbs_unquote_string(ALLOCATOR(), rbs_parser_peek_current_token(parser), parser->lexer->encoding);
        rbs_node_t *literal = (rbs_node_t *) rbs_ast_string_new(ALLOCATOR(), loc, unquoted_str);
        *type = (rbs_node_t *) rbs_types_literal_new(ALLOCATOR(), loc, literal);
        return true;
    }
    case tSYMBOL:
    case tSQSYMBOL:
    case tDQSYMBOL: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        rbs_types_literal_t *literal = NULL;
        CHECK_PARSE(parse_symbol(parser, loc, &literal));
        *type = (rbs_node_t *) literal;
        return true;
    }
    case tUIDENT: {
        const char *name_str = rbs_peek_token(parser->lexer, parser->current_token);
        size_t name_len = rbs_token_bytes(parser->current_token);

        rbs_constant_id_t name = rbs_constant_pool_find(&parser->constant_pool, (const uint8_t *) name_str, name_len);

        if (parser_typevar_member(parser, name)) {
            rbs_location_t *loc = rbs_location_current_token(parser);
            rbs_ast_symbol_t *symbol = rbs_ast_symbol_new(ALLOCATOR(), loc, &parser->constant_pool, name);
            *type = (rbs_node_t *) rbs_types_variable_new(ALLOCATOR(), loc, symbol);
            return true;
        }

        RBS_FALLTHROUGH // for type name
    }
    case tULIDENT:
    case tLIDENT:
    case pCOLON2: {
        rbs_node_t *instance_type = NULL;
        CHECK_PARSE(parse_instance_type(parser, true, &instance_type));
        *type = instance_type;
        return true;
    }
    case kSINGLETON: {
        rbs_types_class_singleton_t *singleton = NULL;
        CHECK_PARSE(parse_singleton_type(parser, &singleton));
        *type = (rbs_node_t *) singleton;
        return true;
    }
    case pLBRACKET: {
        rbs_range_t rg;
        rg.start = parser->current_token.range.start;
        rbs_node_list_t *types = rbs_node_list_new(ALLOCATOR());
        if (parser->next_token.type != pRBRACKET) {
            CHECK_PARSE(parse_type_list(parser, pRBRACKET, types));
        }
        ADVANCE_ASSERT(parser, pRBRACKET);
        rg.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), rg);
        *type = (rbs_node_t *) rbs_types_tuple_new(ALLOCATOR(), loc, types);
        return true;
    }
    case pAREF_OPR: {
        rbs_location_t *loc = rbs_location_current_token(parser);
        rbs_node_list_t *types = rbs_node_list_new(ALLOCATOR());
        *type = (rbs_node_t *) rbs_types_tuple_new(ALLOCATOR(), loc, types);
        return true;
    }
    case pLBRACE: {
        rbs_position_t start = parser->current_token.range.start;
        rbs_hash_t *fields = NULL;
        CHECK_PARSE(parse_record_attributes(parser, &fields));
        ADVANCE_ASSERT(parser, pRBRACE);
        rbs_position_t end = parser->current_token.range.end;
        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), (rbs_range_t) { .start = start, .end = end });
        *type = (rbs_node_t *) rbs_types_record_new(ALLOCATOR(), loc, fields);
        return true;
    }
    case pHAT: {
        rbs_types_proc_t *value = NULL;
        CHECK_PARSE(parse_proc_type(parser, &value));
        *type = (rbs_node_t *) value;
        return true;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, true, "unexpected token for simple type");
        return false;
    }
}

/*
  intersection ::= {} optional `&` ... '&' <optional>
                 | {} <optional>
*/
NODISCARD
static bool parse_intersection(rbs_parser_t *parser, rbs_node_t **type) {
    rbs_range_t rg;
    rg.start = parser->next_token.range.start;

    rbs_node_t *optional = NULL;
    CHECK_PARSE(parse_optional(parser, &optional));
    *type = optional;

    rbs_node_list_t *intersection_types = rbs_node_list_new(ALLOCATOR());

    rbs_node_list_append(intersection_types, optional);
    while (parser->next_token.type == pAMP) {
        rbs_parser_advance(parser);
        rbs_node_t *type = NULL;
        CHECK_PARSE(parse_optional(parser, &type));
        rbs_node_list_append(intersection_types, type);
    }

    rg.end = parser->current_token.range.end;

    if (intersection_types->length > 1) {
        rbs_location_t *location = rbs_location_new(ALLOCATOR(), rg);
        *type = (rbs_node_t *) rbs_types_intersection_new(ALLOCATOR(), location, intersection_types);
    }

    return true;
}

/*
  union ::= {} intersection '|' ... '|' <intersection>
          | {} <intersection>
*/
bool rbs_parse_type(rbs_parser_t *parser, rbs_node_t **type) {
    rbs_range_t rg;
    rg.start = parser->next_token.range.start;
    rbs_node_list_t *union_types = rbs_node_list_new(ALLOCATOR());

    CHECK_PARSE(parse_intersection(parser, type));

    rbs_node_list_append(union_types, *type);

    while (parser->next_token.type == pBAR) {
        rbs_parser_advance(parser);
        rbs_node_t *intersection = NULL;
        CHECK_PARSE(parse_intersection(parser, &intersection));
        rbs_node_list_append(union_types, intersection);
    }

    rg.end = parser->current_token.range.end;

    if (union_types->length > 1) {
        rbs_location_t *location = rbs_location_new(ALLOCATOR(), rg);
        *type = (rbs_node_t *) rbs_types_union_new(ALLOCATOR(), location, union_types);
    }

    return true;
}

/*
  type_params ::= {} `[` type_param `,` ... <`]`>
                | {<>}

  type_param ::= kUNCHECKED? (kIN|kOUT|) tUIDENT upper_bound? default_type?   (module_type_params == true)

  type_param ::= tUIDENT upper_bound? default_type?                           (module_type_params == false)
*/
NODISCARD
static bool parse_type_params(rbs_parser_t *parser, rbs_range_t *rg, bool module_type_params, rbs_node_list_t **params) {
    *params = rbs_node_list_new(ALLOCATOR());

    bool required_param_allowed = true;

    if (parser->next_token.type == pLBRACKET) {
        rbs_parser_advance(parser);

        rg->start = parser->current_token.range.start;

        while (true) {
            bool unchecked = false;
            rbs_keyword_t *variance = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("invariant"));
            rbs_node_t *upper_bound = NULL;
            rbs_node_t *default_type = NULL;

            rbs_range_t param_range;
            param_range.start = parser->next_token.range.start;

            rbs_range_t unchecked_range = NULL_RANGE;
            rbs_range_t variance_range = NULL_RANGE;
            if (module_type_params) {
                if (parser->next_token.type == kUNCHECKED) {
                    unchecked = true;
                    rbs_parser_advance(parser);
                    unchecked_range = parser->current_token.range;
                }

                if (parser->next_token.type == kIN || parser->next_token.type == kOUT) {
                    switch (parser->next_token.type) {
                    case kIN:
                        variance = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("contravariant"));
                        break;
                    case kOUT:
                        variance = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("covariant"));
                        break;
                    default:
                        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
                        return false;
                    }

                    rbs_parser_advance(parser);
                    variance_range = parser->current_token.range;
                }
            }

            ADVANCE_ASSERT(parser, tUIDENT);
            rbs_range_t name_range = parser->current_token.range;

            rbs_string_t string = rbs_parser_peek_current_token(parser);
            rbs_location_t *nameSymbolLoc = rbs_location_current_token(parser);
            rbs_constant_id_t id = rbs_constant_pool_insert_string(&parser->constant_pool, string);
            rbs_ast_symbol_t *name = rbs_ast_symbol_new(ALLOCATOR(), nameSymbolLoc, &parser->constant_pool, id);

            CHECK_PARSE(rbs_parser_insert_typevar(parser, id));

            rbs_range_t upper_bound_range = NULL_RANGE;
            if (parser->next_token.type == pLT) {
                rbs_parser_advance(parser);
                upper_bound_range.start = parser->current_token.range.start;
                CHECK_PARSE(rbs_parse_type(parser, &upper_bound));
                upper_bound_range.end = parser->current_token.range.end;
            }

            rbs_range_t default_type_range = NULL_RANGE;
            if (module_type_params) {
                if (parser->next_token.type == pEQ) {
                    rbs_parser_advance(parser);

                    default_type_range.start = parser->current_token.range.start;
                    CHECK_PARSE(rbs_parse_type(parser, &default_type));
                    default_type_range.end = parser->current_token.range.end;

                    required_param_allowed = false;
                } else {
                    if (!required_param_allowed) {
                        rbs_parser_set_error(parser, parser->current_token, true, "required type parameter is not allowed after optional type parameter");
                        return false;
                    }
                }
            }

            param_range.end = parser->current_token.range.end;

            rbs_location_t *loc = rbs_location_new(ALLOCATOR(), param_range);
            rbs_loc_alloc_children(ALLOCATOR(), loc, 5);
            rbs_loc_add_required_child(loc, INTERN("name"), name_range);
            rbs_loc_add_optional_child(loc, INTERN("variance"), variance_range);
            rbs_loc_add_optional_child(loc, INTERN("unchecked"), unchecked_range);
            rbs_loc_add_optional_child(loc, INTERN("upper_bound"), upper_bound_range);
            rbs_loc_add_optional_child(loc, INTERN("default"), default_type_range);

            rbs_ast_type_param_t *param = rbs_ast_type_param_new(ALLOCATOR(), loc, name, variance, upper_bound, default_type, unchecked);

            rbs_node_list_append(*params, (rbs_node_t *) param);

            if (parser->next_token.type == pCOMMA) {
                rbs_parser_advance(parser);
            }

            if (parser->next_token.type == pRBRACKET) {
                break;
            }
        }

        ADVANCE_ASSERT(parser, pRBRACKET);
        rg->end = parser->current_token.range.end;
    } else {
        *rg = NULL_RANGE;
    }

    return true;
}

NODISCARD
static bool parser_pop_typevar_table(rbs_parser_t *parser) {
    id_table *table;

    if (parser->vars) {
        table = parser->vars;
        parser->vars = table->next;
    } else {
        rbs_parser_set_error(parser, parser->current_token, false, "Cannot pop empty table");
        return false;
    }

    if (parser->vars && RESET_TABLE_P(parser->vars)) {
        table = parser->vars;
        parser->vars = table->next;
    }

    return true;
}

/*
  method_type ::= {} type_params <function>
  */
// TODO: Should this be NODISCARD?
bool rbs_parse_method_type(rbs_parser_t *parser, rbs_method_type_t **method_type, bool require_eof) {
    rbs_parser_push_typevar_table(parser, false);

    rbs_range_t rg;
    rg.start = parser->next_token.range.start;

    rbs_range_t params_range = NULL_RANGE;
    rbs_node_list_t *type_params;
    CHECK_PARSE(parse_type_params(parser, &params_range, false, &type_params));

    rbs_range_t type_range;
    type_range.start = parser->next_token.range.start;

    parse_function_result *result = rbs_allocator_alloc(ALLOCATOR(), parse_function_result);
    CHECK_PARSE(parse_function(parser, false, &result));

    CHECK_PARSE(parser_pop_typevar_table(parser));

    rg.end = parser->current_token.range.end;
    type_range.end = rg.end;

    if (require_eof) {
        rbs_parser_advance(parser);
        if (parser->current_token.type != pEOF) {
            rbs_parser_set_error(parser, parser->current_token, true, "expected a token `%s`", rbs_token_type_str(pEOF));
            return false;
        }
    }

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), rg);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
    rbs_loc_add_required_child(loc, INTERN("type"), type_range);
    rbs_loc_add_optional_child(loc, INTERN("type_params"), params_range);

    *method_type = rbs_method_type_new(ALLOCATOR(), loc, type_params, result->function, result->block);
    return true;
}

/*
  global_decl ::= {tGIDENT} `:` <type>
*/
NODISCARD
static bool parse_global_decl(rbs_parser_t *parser, rbs_node_list_t *annotations, rbs_ast_declarations_global_t **global) {
    rbs_range_t decl_range;
    decl_range.start = parser->current_token.range.start;

    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, decl_range.start.line);

    rbs_range_t name_range = parser->current_token.range;
    rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), name_range);

    rbs_ast_symbol_t *type_name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));

    ADVANCE_ASSERT(parser, pCOLON);
    rbs_range_t colon_range = parser->current_token.range;

    rbs_node_t *type;
    CHECK_PARSE(rbs_parse_type(parser, &type));
    decl_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);

    *global = rbs_ast_declarations_global_new(ALLOCATOR(), loc, type_name, type, comment, annotations);
    return true;
}

/*
  const_decl ::= {const_name} `:` <type>
*/
NODISCARD
static bool parse_const_decl(rbs_parser_t *parser, rbs_node_list_t *annotations, rbs_ast_declarations_constant_t **constant) {
    rbs_range_t decl_range;

    decl_range.start = parser->current_token.range.start;
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, decl_range.start.line);

    rbs_range_t name_range;
    rbs_type_name_t *type_name = NULL;
    CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &name_range, &type_name));

    ADVANCE_ASSERT(parser, pCOLON);
    rbs_range_t colon_range = parser->current_token.range;

    rbs_node_t *type;
    CHECK_PARSE(rbs_parse_type(parser, &type));

    decl_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);

    *constant = rbs_ast_declarations_constant_new(ALLOCATOR(), loc, type_name, type, comment, annotations);
    return true;
}

/*
  type_decl ::= {kTYPE} alias_name `=` <type>
*/
NODISCARD
static bool parse_type_decl(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_ast_declarations_type_alias_t **typealias) {
    rbs_parser_push_typevar_table(parser, true);

    rbs_range_t decl_range;
    decl_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, decl_range.start);

    rbs_range_t keyword_range = parser->current_token.range;

    rbs_parser_advance(parser);

    rbs_range_t name_range;
    rbs_type_name_t *type_name = NULL;
    CHECK_PARSE(parse_type_name(parser, ALIAS_NAME, &name_range, &type_name));

    rbs_range_t params_range;
    rbs_node_list_t *type_params;
    CHECK_PARSE(parse_type_params(parser, &params_range, true, &type_params));

    ADVANCE_ASSERT(parser, pEQ);
    rbs_range_t eq_range = parser->current_token.range;

    rbs_node_t *type;
    CHECK_PARSE(rbs_parse_type(parser, &type));

    decl_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 4);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_optional_child(loc, INTERN("type_params"), params_range);
    rbs_loc_add_required_child(loc, INTERN("eq"), eq_range);

    CHECK_PARSE(parser_pop_typevar_table(parser));

    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    *typealias = rbs_ast_declarations_type_alias_new(ALLOCATOR(), loc, type_name, type_params, type, annotations, comment);
    return true;
}

/*
  annotation ::= {<tANNOTATION>}
*/
NODISCARD
static bool parse_annotation(rbs_parser_t *parser, rbs_ast_annotation_t **annotation) {
    rbs_range_t rg = parser->current_token.range;

    size_t offset_bytes =
        parser->lexer->encoding->char_width((const uint8_t *) "%", (size_t) 1) +
        parser->lexer->encoding->char_width((const uint8_t *) "a", (size_t) 1);

    rbs_string_t str = rbs_string_new(
        parser->lexer->string.start + rg.start.byte_pos + offset_bytes,
        parser->lexer->string.end
    );

    // Assumes the input is ASCII compatible
    unsigned int open_char = str.start[0];

    unsigned int close_char;

    switch (open_char) {
    case '{':
        close_char = '}';
        break;
    case '(':
        close_char = ')';
        break;
    case '[':
        close_char = ']';
        break;
    case '<':
        close_char = '>';
        break;
    case '|':
        close_char = '|';
        break;
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }

    size_t open_bytes = parser->lexer->encoding->char_width((const uint8_t *) &open_char, (size_t) 1);
    size_t close_bytes = parser->lexer->encoding->char_width((const uint8_t *) &close_char, (size_t) 1);

    rbs_string_t current_token = rbs_parser_peek_current_token(parser);
    size_t total_offset = offset_bytes + open_bytes;

    rbs_string_t annotation_str = rbs_string_new(
        current_token.start + total_offset,
        current_token.end - close_bytes
    );

    rbs_string_t stripped_annotation_str = rbs_string_strip_whitespace(&annotation_str);

    *annotation = rbs_ast_annotation_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), rg), stripped_annotation_str);
    return true;
}

/*
  annotations ::= {} annotation ... <annotation>
                | {<>}
*/
NODISCARD
static bool parse_annotations(rbs_parser_t *parser, rbs_node_list_t *annotations, rbs_position_t *annot_pos) {
    *annot_pos = NullPosition;

    while (true) {
        if (parser->next_token.type == tANNOTATION) {
            rbs_parser_advance(parser);

            if (rbs_null_position_p((*annot_pos))) {
                *annot_pos = parser->current_token.range.start;
            }

            rbs_ast_annotation_t *annotation = NULL;
            CHECK_PARSE(parse_annotation(parser, &annotation));
            rbs_node_list_append(annotations, (rbs_node_t *) annotation);
        } else {
            break;
        }
    }

    return true;
}

/*
  method_name ::= {} <IDENT | keyword>
                | {} (IDENT | keyword)~<`?`>
*/
NODISCARD
static bool parse_method_name(rbs_parser_t *parser, rbs_range_t *range, rbs_ast_symbol_t **symbol) {
    rbs_parser_advance(parser);

    switch (parser->current_token.type) {
    case tUIDENT:
    case tLIDENT:
    case tULIDENT:
    case tULLIDENT:
        KEYWORD_CASES
        if (parser->next_token.type == pQUESTION && parser->current_token.range.end.byte_pos == parser->next_token.range.start.byte_pos) {
            range->start = parser->current_token.range.start;
            range->end = parser->next_token.range.end;
            rbs_parser_advance(parser);

            rbs_constant_id_t constant_id = rbs_constant_pool_insert_shared_with_encoding(
                &parser->constant_pool,
                (const uint8_t *) parser->lexer->string.start + range->start.byte_pos,
                range->end.byte_pos - range->start.byte_pos,
                parser->lexer->encoding
            );

            rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), *range);
            *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, constant_id);
        } else {
            *range = parser->current_token.range;
            rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), *range);
            *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
        }
        return true;

    case tBANGIDENT:
    case tEQIDENT: {
        *range = parser->current_token.range;
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), *range);
        *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
        return true;
    }
    case tQIDENT: {
        rbs_string_t string = rbs_parser_peek_current_token(parser);
        rbs_string_t unquoted_str = rbs_unquote_string(ALLOCATOR(), string, parser->lexer->encoding);
        rbs_constant_id_t constant_id = rbs_constant_pool_insert_string(&parser->constant_pool, unquoted_str);
        rbs_location_t *symbolLoc = rbs_location_current_token(parser);
        *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, constant_id);
        return true;
    }

    case pBAR:
    case pHAT:
    case pAMP:
    case pSTAR:
    case pSTAR2:
    case pLT:
    case pAREF_OPR:
    case tOPERATOR: {
        *range = parser->current_token.range;
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), *range);
        *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
        return true;
    }

    default:
        rbs_parser_set_error(parser, parser->current_token, true, "unexpected token for method name");
        return false;
    }
}

typedef enum {
    INSTANCE_KIND,
    SINGLETON_KIND,
    INSTANCE_SINGLETON_KIND
} InstanceSingletonKind;

/*
  instance_singleton_kind ::= {<>}
                            | {} kSELF <`.`>
                            | {} kSELF~`?` <`.`>

  @param allow_selfq `true` to accept `self?` kind.
*/
static InstanceSingletonKind parse_instance_singleton_kind(rbs_parser_t *parser, bool allow_selfq, rbs_range_t *rg) {
    InstanceSingletonKind kind = INSTANCE_KIND;

    if (parser->next_token.type == kSELF) {
        rbs_range_t self_range = parser->next_token.range;

        if (parser->next_token2.type == pDOT) {
            rbs_parser_advance(parser);
            rbs_parser_advance(parser);
            kind = SINGLETON_KIND;
        } else if (
            parser->next_token2.type == pQUESTION && parser->next_token.range.end.char_pos == parser->next_token2.range.start.char_pos && parser->next_token3.type == pDOT && allow_selfq
        ) {
            rbs_parser_advance(parser);
            rbs_parser_advance(parser);
            rbs_parser_advance(parser);
            kind = INSTANCE_SINGLETON_KIND;
        }

        *rg = (rbs_range_t) {
            .start = self_range.start,
            .end = parser->current_token.range.end,
        };
    } else {
        *rg = NULL_RANGE;
    }

    return kind;
}

/**
 * def_member ::= {kDEF} method_name `:` <method_types>
 *              | {kPRIVATE} kDEF method_name `:` <method_types>
 *              | {kPUBLIC} kDEF method_name `:` <method_types>
 *
 * method_types ::= {} <method_type>
 *                | {} <`...`>
 *                | {} method_type `|` <method_types>
 *
 * @param instance_only `true` to reject singleton method definition.
 * @param accept_overload `true` to accept overloading (...) definition.
 * */
NODISCARD
static bool parse_member_def(rbs_parser_t *parser, bool instance_only, bool accept_overload, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_ast_members_method_definition_t **method_definition) {
    rbs_range_t member_range;
    member_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);

    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    rbs_range_t visibility_range;
    rbs_keyword_t *visibility;
    switch (parser->current_token.type) {
    case kPRIVATE: {
        visibility_range = parser->current_token.range;
        visibility = rbs_keyword_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), visibility_range), INTERN("private"));
        member_range.start = visibility_range.start;
        rbs_parser_advance(parser);
        break;
    }
    case kPUBLIC: {
        visibility_range = parser->current_token.range;
        visibility = rbs_keyword_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), visibility_range), INTERN("public"));
        member_range.start = visibility_range.start;
        rbs_parser_advance(parser);
        break;
    }
    default:
        visibility_range = NULL_RANGE;
        visibility = NULL;
        break;
    }

    rbs_range_t keyword_range = parser->current_token.range;

    rbs_range_t kind_range;
    InstanceSingletonKind kind;
    if (instance_only) {
        kind_range = NULL_RANGE;
        kind = INSTANCE_KIND;
    } else {
        kind = parse_instance_singleton_kind(parser, visibility == NULL, &kind_range);
    }

    rbs_range_t name_range;
    rbs_ast_symbol_t *name = NULL;
    CHECK_PARSE(parse_method_name(parser, &name_range, &name));

#define SELF_ID rbs_constant_pool_insert_constant(&parser->constant_pool, (const unsigned char *) "self?", strlen("self?"))

    if (parser->next_token.type == pDOT && name->constant_id == SELF_ID) {
        rbs_parser_set_error(parser, parser->next_token, true, "`self?` method cannot have visibility");
        return false;
    } else {
        ADVANCE_ASSERT(parser, pCOLON);
    }

    rbs_parser_push_typevar_table(parser, kind != INSTANCE_KIND);

    rbs_node_list_t *overloads = rbs_node_list_new(ALLOCATOR());
    bool overloading = false;
    rbs_range_t overloading_range = NULL_RANGE;
    bool loop = true;
    while (loop) {
        rbs_node_list_t *annotations = rbs_node_list_new(ALLOCATOR());
        rbs_position_t overload_annot_pos = NullPosition;

        rbs_range_t overload_range;
        overload_range.start = parser->current_token.range.start;

        if (parser->next_token.type == tANNOTATION) {
            CHECK_PARSE(parse_annotations(parser, annotations, &overload_annot_pos));
        }

        switch (parser->next_token.type) {
        case pLPAREN:
        case pARROW:
        case pLBRACE:
        case pLBRACKET:
        case pQUESTION: {
            rbs_method_type_t *method_type = NULL;
            CHECK_PARSE(rbs_parse_method_type(parser, &method_type, false));

            overload_range.end = parser->current_token.range.end;
            rbs_location_t *loc = rbs_location_new(ALLOCATOR(), overload_range);
            rbs_node_t *overload = (rbs_node_t *) rbs_ast_members_method_definition_overload_new(ALLOCATOR(), loc, annotations, (rbs_node_t *) method_type);
            rbs_node_list_append(overloads, overload);
            member_range.end = parser->current_token.range.end;
            break;
        }

        case pDOT3:
            if (accept_overload) {
                overloading = true;
                rbs_parser_advance(parser);
                loop = false;
                overloading_range = parser->current_token.range;
                member_range.end = overloading_range.end;
                break;
            } else {
                rbs_parser_set_error(parser, parser->next_token, true, "unexpected overloading method definition");
                return false;
            }

        default:
            rbs_parser_set_error(parser, parser->next_token, true, "unexpected token for method type");
            return false;
        }

        if (parser->next_token.type == pBAR) {
            rbs_parser_advance(parser);
        } else {
            loop = false;
        }
    }

    CHECK_PARSE(parser_pop_typevar_table(parser));

    rbs_keyword_t *k;
    switch (kind) {
    case INSTANCE_KIND: {
        k = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("instance"));
        break;
    }
    case SINGLETON_KIND: {
        k = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("singleton"));
        break;
    }
    case INSTANCE_SINGLETON_KIND: {
        k = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("singleton_instance"));
        break;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 5);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_optional_child(loc, INTERN("kind"), kind_range);
    rbs_loc_add_optional_child(loc, INTERN("overloading"), overloading_range);
    rbs_loc_add_optional_child(loc, INTERN("visibility"), visibility_range);

    *method_definition = rbs_ast_members_method_definition_new(ALLOCATOR(), loc, name, k, overloads, annotations, comment, overloading, visibility);
    return true;
}

/**
 * class_instance_name ::= {} <class_name>
 *                       | {} class_name `[` type args <`]`>
 *
 * @param kind
 * */
NODISCARD
static bool class_instance_name(rbs_parser_t *parser, TypeNameKind kind, rbs_node_list_t *args, rbs_range_t *name_range, rbs_range_t *args_range, rbs_type_name_t **name) {
    rbs_parser_advance(parser);

    rbs_type_name_t *type_name = NULL;
    CHECK_PARSE(parse_type_name(parser, kind, name_range, &type_name));
    *name = type_name;

    if (parser->next_token.type == pLBRACKET) {
        rbs_parser_advance(parser);
        args_range->start = parser->current_token.range.start;
        CHECK_PARSE(parse_type_list(parser, pRBRACKET, args));
        ADVANCE_ASSERT(parser, pRBRACKET);
        args_range->end = parser->current_token.range.end;
    } else {
        *args_range = NULL_RANGE;
    }

    return true;
}

/**
 *  mixin_member ::= {kINCLUDE} <class_instance_name>
 *                 | {kPREPEND} <class_instance_name>
 *                 | {kEXTEND} <class_instance_name>
 *
 * @param from_interface `true` when the member is in an interface.
 * */
NODISCARD
static bool parse_mixin_member(rbs_parser_t *parser, bool from_interface, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_node_t **mixin_member) {
    rbs_range_t member_range;
    member_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);

    enum RBSTokenType type = parser->current_token.type;
    rbs_range_t keyword_range = parser->current_token.range;

    bool reset_typevar_scope;
    switch (type) {
    case kINCLUDE:
        reset_typevar_scope = false;
        break;
    case kEXTEND:
        reset_typevar_scope = true;
        break;
    case kPREPEND:
        reset_typevar_scope = false;
        break;
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }

    if (from_interface) {
        if (parser->current_token.type != kINCLUDE) {
            rbs_parser_set_error(parser, parser->current_token, true, "unexpected mixin in interface declaration");
            return false;
        }
    }

    rbs_parser_push_typevar_table(parser, reset_typevar_scope);

    rbs_node_list_t *args = rbs_node_list_new(ALLOCATOR());
    rbs_range_t name_range;
    rbs_range_t args_range = NULL_RANGE;
    rbs_type_name_t *name = NULL;
    CHECK_PARSE(class_instance_name(
        parser,
        from_interface ? INTERFACE_NAME : (TypeNameKind) (INTERFACE_NAME | CLASS_NAME),
        args,
        &name_range,
        &args_range,
        &name
    ));

    CHECK_PARSE(parser_pop_typevar_table(parser));

    member_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 3);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_optional_child(loc, INTERN("args"), args_range);

    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);
    switch (type) {
    case kINCLUDE:
        *mixin_member = (rbs_node_t *) rbs_ast_members_include_new(ALLOCATOR(), loc, name, args, annotations, comment);
        return true;
    case kEXTEND:
        *mixin_member = (rbs_node_t *) rbs_ast_members_extend_new(ALLOCATOR(), loc, name, args, annotations, comment);
        return true;
    case kPREPEND:
        *mixin_member = (rbs_node_t *) rbs_ast_members_prepend_new(ALLOCATOR(), loc, name, args, annotations, comment);
        return true;
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }
}

/**
 * @code
 * alias_member ::= {kALIAS} method_name <method_name>
 *                | {kALIAS} kSELF `.` method_name kSELF `.` <method_name>
 * @endcode
 *
 * @param[in] instance_only `true` to reject `self.` alias.
 * */
NODISCARD
static bool parse_alias_member(rbs_parser_t *parser, bool instance_only, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_ast_members_alias_t **alias_member) {
    rbs_range_t member_range;
    member_range.start = parser->current_token.range.start;
    rbs_range_t keyword_range = parser->current_token.range;

    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    rbs_keyword_t *kind;
    rbs_ast_symbol_t *new_name, *old_name;
    rbs_range_t new_kind_range, old_kind_range, new_name_range, old_name_range;

    if (!instance_only && parser->next_token.type == kSELF) {
        kind = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("singleton"));

        new_kind_range.start = parser->next_token.range.start;
        new_kind_range.end = parser->next_token2.range.end;
        ADVANCE_ASSERT(parser, kSELF);
        ADVANCE_ASSERT(parser, pDOT);
        CHECK_PARSE(parse_method_name(parser, &new_name_range, &new_name));

        old_kind_range.start = parser->next_token.range.start;
        old_kind_range.end = parser->next_token2.range.end;
        ADVANCE_ASSERT(parser, kSELF);
        ADVANCE_ASSERT(parser, pDOT);
        CHECK_PARSE(parse_method_name(parser, &old_name_range, &old_name));
    } else {
        kind = rbs_keyword_new(ALLOCATOR(), rbs_location_current_token(parser), INTERN("instance"));
        CHECK_PARSE(parse_method_name(parser, &new_name_range, &new_name));
        CHECK_PARSE(parse_method_name(parser, &old_name_range, &old_name));
        new_kind_range = NULL_RANGE;
        old_kind_range = NULL_RANGE;
    }

    member_range.end = parser->current_token.range.end;
    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 5);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("new_name"), new_name_range);
    rbs_loc_add_required_child(loc, INTERN("old_name"), old_name_range);
    rbs_loc_add_optional_child(loc, INTERN("new_kind"), new_kind_range);
    rbs_loc_add_optional_child(loc, INTERN("old_kind"), old_kind_range);

    *alias_member = rbs_ast_members_alias_new(ALLOCATOR(), loc, new_name, old_name, kind, annotations, comment);
    return true;
}

/*
  variable_member ::= {tAIDENT} `:` <type>
                    | {kSELF} `.` tAIDENT `:` <type>
                    | {tA2IDENT} `:` <type>
*/
NODISCARD
static bool parse_variable_member(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_node_t **variable_member) {
    if (annotations->length > 0) {
        rbs_parser_set_error(parser, parser->current_token, true, "annotation cannot be given to variable members");
        return false;
    }

    rbs_range_t member_range;
    member_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    switch (parser->current_token.type) {
    case tAIDENT: {
        rbs_range_t name_range = parser->current_token.range;
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), name_range);
        rbs_ast_symbol_t *name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));

        ADVANCE_ASSERT(parser, pCOLON);
        rbs_range_t colon_range = parser->current_token.range;

        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));
        member_range.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 3);
        rbs_loc_add_required_child(loc, INTERN("name"), name_range);
        rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);
        rbs_loc_add_optional_child(loc, INTERN("kind"), NULL_RANGE);

        *variable_member = (rbs_node_t *) rbs_ast_members_instance_variable_new(ALLOCATOR(), loc, name, type, comment);
        return true;
    }
    case tA2IDENT: {
        rbs_range_t name_range = parser->current_token.range;
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), name_range);
        rbs_ast_symbol_t *name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));

        ADVANCE_ASSERT(parser, pCOLON);
        rbs_range_t colon_range = parser->current_token.range;

        rbs_parser_push_typevar_table(parser, true);

        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));

        CHECK_PARSE(parser_pop_typevar_table(parser));

        member_range.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 3);
        rbs_loc_add_required_child(loc, INTERN("name"), name_range);
        rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);
        rbs_loc_add_optional_child(loc, INTERN("kind"), NULL_RANGE);

        *variable_member = (rbs_node_t *) rbs_ast_members_class_variable_new(ALLOCATOR(), loc, name, type, comment);
        return true;
    }
    case kSELF: {
        rbs_range_t kind_range = {
            .start = parser->current_token.range.start,
            .end = parser->next_token.range.end
        };

        ADVANCE_ASSERT(parser, pDOT);
        if (parser->next_token.type == tAIDENT) {
            rbs_parser_advance(parser);
        } else {
            rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
            return false;
        }

        rbs_range_t name_range = parser->current_token.range;
        rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), name_range);
        rbs_ast_symbol_t *name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));

        ADVANCE_ASSERT(parser, pCOLON);
        rbs_range_t colon_range = parser->current_token.range;

        rbs_parser_push_typevar_table(parser, true);

        rbs_node_t *type;
        CHECK_PARSE(rbs_parse_type(parser, &type));

        CHECK_PARSE(parser_pop_typevar_table(parser));

        member_range.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 3);
        rbs_loc_add_required_child(loc, INTERN("name"), name_range);
        rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);
        rbs_loc_add_optional_child(loc, INTERN("kind"), kind_range);

        *variable_member = (rbs_node_t *) rbs_ast_members_class_instance_variable_new(ALLOCATOR(), loc, name, type, comment);
        return true;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }
}

/*
  visibility_member ::= {<`public`>}
                      | {<`private`>}
*/
NODISCARD
static bool parse_visibility_member(rbs_parser_t *parser, rbs_node_list_t *annotations, rbs_node_t **visibility_member) {
    if (annotations->length > 0) {
        rbs_parser_set_error(parser, parser->current_token, true, "annotation cannot be given to visibility members");
        return false;
    }

    rbs_location_t *location = rbs_location_current_token(parser);

    switch (parser->current_token.type) {
    case kPUBLIC: {
        *visibility_member = (rbs_node_t *) rbs_ast_members_public_new(ALLOCATOR(), location);
        return true;
    }
    case kPRIVATE: {
        *visibility_member = (rbs_node_t *) rbs_ast_members_private_new(ALLOCATOR(), location);
        return true;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }
}

/*
  attribute_member ::= {attr_keyword} attr_name attr_var `:` <type>
                     | {visibility} attr_keyword attr_name attr_var `:` <type>
                     | {attr_keyword} `self` `.` attr_name attr_var `:` <type>
                     | {visibility} attr_keyword `self` `.` attr_name attr_var `:` <type>

  attr_keyword ::= `attr_reader` | `attr_writer` | `attr_accessor`

  visibility ::= `public` | `private`

  attr_var ::=                    # empty
             | `(` tAIDENT `)`    # Ivar name
             | `(` `)`            # No variable
*/
NODISCARD
static bool parse_attribute_member(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_node_t **attribute_member) {
    rbs_range_t member_range;

    member_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    rbs_range_t visibility_range;
    rbs_keyword_t *visibility;
    switch (parser->current_token.type) {
    case kPRIVATE: {
        visibility_range = parser->current_token.range;
        visibility = rbs_keyword_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), visibility_range), INTERN("private"));
        rbs_parser_advance(parser);
        break;
    }
    case kPUBLIC: {
        visibility_range = parser->current_token.range;
        visibility = rbs_keyword_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), visibility_range), INTERN("public"));
        rbs_parser_advance(parser);
        break;
    }
    default:
        visibility = NULL;
        visibility_range = NULL_RANGE;
        break;
    }

    enum RBSTokenType attr_type = parser->current_token.type;
    rbs_range_t keyword_range = parser->current_token.range;

    rbs_range_t kind_range;
    InstanceSingletonKind is_kind = parse_instance_singleton_kind(parser, false, &kind_range);

    rbs_keyword_t *kind = rbs_keyword_new(
        ALLOCATOR(),
        rbs_location_new(ALLOCATOR(), keyword_range),
        INTERN(((is_kind == INSTANCE_KIND) ? "instance" : "singleton"))
    );

    rbs_range_t name_range;
    rbs_ast_symbol_t *attr_name;
    CHECK_PARSE(parse_method_name(parser, &name_range, &attr_name));

    rbs_node_t *ivar_name; // rbs_ast_symbol_t, NULL or rbs_ast_bool_new(ALLOCATOR(), false)
    rbs_range_t ivar_range, ivar_name_range;
    if (parser->next_token.type == pLPAREN) {
        ADVANCE_ASSERT(parser, pLPAREN);
        ivar_range.start = parser->current_token.range.start;

        if (parser_advance_if(parser, tAIDENT)) {
            rbs_location_t *symbolLoc = rbs_location_current_token(parser);
            ivar_name = (rbs_node_t *) rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
            ivar_name_range = parser->current_token.range;
        } else {
            rbs_range_t false_range = {
                .start = parser->current_token.range.start,
                .end = parser->current_token.range.end
            };
            ivar_name = (rbs_node_t *) rbs_ast_bool_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), false_range), false);
            ivar_name_range = NULL_RANGE;
        }

        ADVANCE_ASSERT(parser, pRPAREN);
        ivar_range.end = parser->current_token.range.end;
    } else {
        ivar_range = NULL_RANGE;
        ivar_name = NULL;
        ivar_name_range = NULL_RANGE;
    }

    ADVANCE_ASSERT(parser, pCOLON);
    rbs_range_t colon_range = parser->current_token.range;

    rbs_parser_push_typevar_table(parser, is_kind == SINGLETON_KIND);

    rbs_node_t *type;
    CHECK_PARSE(rbs_parse_type(parser, &type));

    CHECK_PARSE(parser_pop_typevar_table(parser));

    member_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 7);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("colon"), colon_range);
    rbs_loc_add_optional_child(loc, INTERN("kind"), kind_range);
    rbs_loc_add_optional_child(loc, INTERN("ivar"), ivar_range);
    rbs_loc_add_optional_child(loc, INTERN("ivar_name"), ivar_name_range);
    rbs_loc_add_optional_child(loc, INTERN("visibility"), visibility_range);

    switch (attr_type) {
    case kATTRREADER:
        *attribute_member = (rbs_node_t *) rbs_ast_members_attr_reader_new(ALLOCATOR(), loc, attr_name, type, ivar_name, kind, annotations, comment, visibility);
        return true;
    case kATTRWRITER:
        *attribute_member = (rbs_node_t *) rbs_ast_members_attr_writer_new(ALLOCATOR(), loc, attr_name, type, ivar_name, kind, annotations, comment, visibility);
        return true;
    case kATTRACCESSOR:
        *attribute_member = (rbs_node_t *) rbs_ast_members_attr_accessor_new(ALLOCATOR(), loc, attr_name, type, ivar_name, kind, annotations, comment, visibility);
        return true;
    default:
        rbs_parser_set_error(parser, parser->current_token, false, "Unexpected error");
        return false;
    }
}

/*
  interface_members ::= {} ...<interface_member> kEND

  interface_member ::= def_member     (instance method only && no overloading)
                     | mixin_member   (interface only)
                     | alias_member   (instance only)
*/
NODISCARD
static bool parse_interface_members(rbs_parser_t *parser, rbs_node_list_t **members) {
    *members = rbs_node_list_new(ALLOCATOR());

    while (parser->next_token.type != kEND) {
        rbs_node_list_t *annotations = rbs_node_list_new(ALLOCATOR());
        rbs_position_t annot_pos = NullPosition;

        CHECK_PARSE(parse_annotations(parser, annotations, &annot_pos));
        rbs_parser_advance(parser);

        rbs_node_t *member;
        switch (parser->current_token.type) {
        case kDEF: {
            rbs_ast_members_method_definition_t *method_definition = NULL;
            CHECK_PARSE(parse_member_def(parser, true, true, annot_pos, annotations, &method_definition));
            member = (rbs_node_t *) method_definition;
            break;
        }

        case kINCLUDE:
        case kEXTEND:
        case kPREPEND: {
            CHECK_PARSE(parse_mixin_member(parser, true, annot_pos, annotations, &member));
            break;
        }

        case kALIAS: {
            rbs_ast_members_alias_t *alias_member = NULL;
            CHECK_PARSE(parse_alias_member(parser, true, annot_pos, annotations, &alias_member));
            member = (rbs_node_t *) alias_member;
            break;
        }

        default:
            rbs_parser_set_error(parser, parser->current_token, true, "unexpected token for interface declaration member");
            return false;
        }

        rbs_node_list_append(*members, member);
    }

    return true;
}

/*
  interface_decl ::= {`interface`} interface_name module_type_params interface_members <kEND>
*/
NODISCARD
static bool parse_interface_decl(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_ast_declarations_interface_t **interface_decl) {
    rbs_parser_push_typevar_table(parser, true);

    rbs_range_t member_range;
    member_range.start = parser->current_token.range.start;
    comment_pos = rbs_nonnull_pos_or(comment_pos, member_range.start);

    rbs_range_t keyword_range = parser->current_token.range;

    rbs_parser_advance(parser);

    rbs_range_t name_range;
    rbs_type_name_t *name = NULL;
    CHECK_PARSE(parse_type_name(parser, INTERFACE_NAME, &name_range, &name));

    rbs_range_t type_params_range;
    rbs_node_list_t *type_params;
    CHECK_PARSE(parse_type_params(parser, &type_params_range, true, &type_params));

    rbs_node_list_t *members = NULL;
    CHECK_PARSE(parse_interface_members(parser, &members));

    ADVANCE_ASSERT(parser, kEND);
    rbs_range_t end_range = parser->current_token.range;
    member_range.end = end_range.end;

    CHECK_PARSE(parser_pop_typevar_table(parser));

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), member_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 4);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("end"), end_range);
    rbs_loc_add_optional_child(loc, INTERN("type_params"), type_params_range);

    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    *interface_decl = rbs_ast_declarations_interface_new(ALLOCATOR(), loc, name, type_params, members, annotations, comment);
    return true;
}

/*
  module_self_types ::= {`:`} module_self_type `,` ... `,` <module_self_type>

  module_self_type ::= <module_name>
                     | module_name `[` type_list <`]`>
*/
NODISCARD
static bool parse_module_self_types(rbs_parser_t *parser, rbs_node_list_t *array) {
    while (true) {
        rbs_parser_advance(parser);

        rbs_range_t self_range;
        self_range.start = parser->current_token.range.start;

        rbs_range_t name_range;
        rbs_type_name_t *module_name = NULL;
        CHECK_PARSE(parse_type_name(parser, (TypeNameKind) (CLASS_NAME | INTERFACE_NAME), &name_range, &module_name));
        self_range.end = name_range.end;

        rbs_node_list_t *args = rbs_node_list_new(ALLOCATOR());
        rbs_range_t args_range = NULL_RANGE;
        if (parser->next_token.type == pLBRACKET) {
            rbs_parser_advance(parser);
            args_range.start = parser->current_token.range.start;
            CHECK_PARSE(parse_type_list(parser, pRBRACKET, args));
            rbs_parser_advance(parser);
            self_range.end = args_range.end = parser->current_token.range.end;
        }

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), self_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
        rbs_loc_add_required_child(loc, INTERN("name"), name_range);
        rbs_loc_add_optional_child(loc, INTERN("args"), args_range);

        rbs_ast_declarations_module_self_t *self_type = rbs_ast_declarations_module_self_new(ALLOCATOR(), loc, module_name, args);
        rbs_node_list_append(array, (rbs_node_t *) self_type);

        if (parser->next_token.type == pCOMMA) {
            rbs_parser_advance(parser);
        } else {
            break;
        }
    }

    return true;
}

NODISCARD
static bool parse_nested_decl(rbs_parser_t *parser, const char *nested_in, rbs_position_t annot_pos, rbs_node_list_t *annotations, rbs_node_t **decl);

/*
  module_members ::= {} ...<module_member> kEND

  module_member ::= def_member
                  | variable_member
                  | mixin_member
                  | alias_member
                  | attribute_member
                  | `public`
                  | `private`
*/
NODISCARD
static bool parse_module_members(rbs_parser_t *parser, rbs_node_list_t **members) {
    *members = rbs_node_list_new(ALLOCATOR());

    while (parser->next_token.type != kEND) {
        rbs_node_list_t *annotations = rbs_node_list_new(ALLOCATOR());
        rbs_position_t annot_pos;
        CHECK_PARSE(parse_annotations(parser, annotations, &annot_pos));

        rbs_parser_advance(parser);

        rbs_node_t *member;
        switch (parser->current_token.type) {
        case kDEF: {
            rbs_ast_members_method_definition_t *method_definition;
            CHECK_PARSE(parse_member_def(parser, false, true, annot_pos, annotations, &method_definition));
            member = (rbs_node_t *) method_definition;
            break;
        }

        case kINCLUDE:
        case kEXTEND:
        case kPREPEND: {
            CHECK_PARSE(parse_mixin_member(parser, false, annot_pos, annotations, &member));
            break;
        }
        case kALIAS: {
            rbs_ast_members_alias_t *alias_member = NULL;
            CHECK_PARSE(parse_alias_member(parser, false, annot_pos, annotations, &alias_member));
            member = (rbs_node_t *) alias_member;
            break;
        }
        case tAIDENT:
        case tA2IDENT:
        case kSELF: {
            CHECK_PARSE(parse_variable_member(parser, annot_pos, annotations, &member));
            break;
        }

        case kATTRREADER:
        case kATTRWRITER:
        case kATTRACCESSOR: {
            CHECK_PARSE(parse_attribute_member(parser, annot_pos, annotations, &member));
            break;
        }

        case kPUBLIC:
        case kPRIVATE:
            if (parser->next_token.range.start.line == parser->current_token.range.start.line) {
                switch (parser->next_token.type) {
                case kDEF: {
                    rbs_ast_members_method_definition_t *method_definition = NULL;
                    CHECK_PARSE(parse_member_def(parser, false, true, annot_pos, annotations, &method_definition));
                    member = (rbs_node_t *) method_definition;
                    break;
                }
                case kATTRREADER:
                case kATTRWRITER:
                case kATTRACCESSOR: {
                    CHECK_PARSE(parse_attribute_member(parser, annot_pos, annotations, &member));
                    break;
                }
                default:
                    rbs_parser_set_error(parser, parser->next_token, true, "method or attribute definition is expected after visibility modifier");
                    return false;
                }
            } else {
                CHECK_PARSE(parse_visibility_member(parser, annotations, &member));
            }
            break;

        default:
            CHECK_PARSE(parse_nested_decl(parser, "module", annot_pos, annotations, &member));
            break;
        }

        rbs_node_list_append(*members, member);
    }

    return true;
}

/*
  module_decl ::= {module_name} module_type_params module_members <kEND>
                | {module_name} module_name module_type_params `:` module_self_types module_members <kEND>
*/
NODISCARD
static bool parse_module_decl0(rbs_parser_t *parser, rbs_range_t keyword_range, rbs_type_name_t *module_name, rbs_range_t name_range, rbs_ast_comment_t *comment, rbs_node_list_t *annotations, rbs_ast_declarations_module_t **module_decl) {
    rbs_parser_push_typevar_table(parser, true);

    rbs_range_t decl_range;
    decl_range.start = keyword_range.start;

    rbs_range_t type_params_range;
    rbs_node_list_t *type_params;
    CHECK_PARSE(parse_type_params(parser, &type_params_range, true, &type_params));

    rbs_node_list_t *self_types = rbs_node_list_new(ALLOCATOR());
    rbs_range_t colon_range;
    rbs_range_t self_types_range;
    if (parser->next_token.type == pCOLON) {
        rbs_parser_advance(parser);
        colon_range = parser->current_token.range;
        self_types_range.start = parser->next_token.range.start;
        CHECK_PARSE(parse_module_self_types(parser, self_types));
        self_types_range.end = parser->current_token.range.end;
    } else {
        colon_range = NULL_RANGE;
        self_types_range = NULL_RANGE;
    }

    rbs_node_list_t *members = NULL;
    CHECK_PARSE(parse_module_members(parser, &members));

    ADVANCE_ASSERT(parser, kEND);
    rbs_range_t end_range = parser->current_token.range;
    decl_range.end = parser->current_token.range.end;

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 6);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("end"), end_range);
    rbs_loc_add_optional_child(loc, INTERN("type_params"), type_params_range);
    rbs_loc_add_optional_child(loc, INTERN("colon"), colon_range);
    rbs_loc_add_optional_child(loc, INTERN("self_types"), self_types_range);

    CHECK_PARSE(parser_pop_typevar_table(parser));

    *module_decl = rbs_ast_declarations_module_new(ALLOCATOR(), loc, module_name, type_params, self_types, members, annotations, comment);
    return true;
}

/*
  module_decl ::= {`module`} module_name `=` old_module_name <kEND>
                | {`module`} module_name module_decl0 <kEND>

*/
NODISCARD
static bool parse_module_decl(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_node_t **module_decl) {
    rbs_range_t keyword_range = parser->current_token.range;

    comment_pos = rbs_nonnull_pos_or(comment_pos, parser->current_token.range.start);
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    rbs_parser_advance(parser);

    rbs_range_t module_name_range;
    rbs_type_name_t *module_name;
    CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &module_name_range, &module_name));

    if (parser->next_token.type == pEQ) {
        rbs_range_t eq_range = parser->next_token.range;
        rbs_parser_advance(parser);
        rbs_parser_advance(parser);

        rbs_range_t old_name_range;
        rbs_type_name_t *old_name = NULL;
        CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &old_name_range, &old_name));

        rbs_range_t decl_range = {
            .start = keyword_range.start,
            .end = old_name_range.end
        };

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 4);
        rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
        rbs_loc_add_required_child(loc, INTERN("new_name"), module_name_range);
        rbs_loc_add_required_child(loc, INTERN("eq"), eq_range);
        rbs_loc_add_optional_child(loc, INTERN("old_name"), old_name_range);

        *module_decl = (rbs_node_t *) rbs_ast_declarations_module_alias_new(ALLOCATOR(), loc, module_name, old_name, comment, annotations);
    } else {
        rbs_ast_declarations_module_t *module_decl0 = NULL;
        CHECK_PARSE(parse_module_decl0(parser, keyword_range, module_name, module_name_range, comment, annotations, &module_decl0));
        *module_decl = (rbs_node_t *) module_decl0;
    }

    return true;
}

/*
  class_decl_super ::= {} `<` <class_instance_name>
                     | {<>}
*/
NODISCARD
static bool parse_class_decl_super(rbs_parser_t *parser, rbs_range_t *lt_range, rbs_ast_declarations_class_super_t **super) {
    if (parser_advance_if(parser, pLT)) {
        *lt_range = parser->current_token.range;

        rbs_range_t super_range;
        super_range.start = parser->next_token.range.start;

        rbs_node_list_t *args = rbs_node_list_new(ALLOCATOR());
        rbs_type_name_t *name = NULL;
        rbs_range_t name_range, args_range;
        CHECK_PARSE(class_instance_name(parser, CLASS_NAME, args, &name_range, &args_range, &name));

        super_range.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), super_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
        rbs_loc_add_required_child(loc, INTERN("name"), name_range);
        rbs_loc_add_optional_child(loc, INTERN("args"), args_range);

        *super = rbs_ast_declarations_class_super_new(ALLOCATOR(), loc, name, args);
    } else {
        *lt_range = NULL_RANGE;
    }

    return true;
}

/*
  class_decl ::= {class_name} type_params class_decl_super class_members <`end`>
*/
NODISCARD
static bool parse_class_decl0(rbs_parser_t *parser, rbs_range_t keyword_range, rbs_type_name_t *name, rbs_range_t name_range, rbs_ast_comment_t *comment, rbs_node_list_t *annotations, rbs_ast_declarations_class_t **class_decl) {
    rbs_parser_push_typevar_table(parser, true);

    rbs_range_t decl_range;
    decl_range.start = keyword_range.start;

    rbs_range_t type_params_range;
    rbs_node_list_t *type_params;
    CHECK_PARSE(parse_type_params(parser, &type_params_range, true, &type_params));

    rbs_range_t lt_range;
    rbs_ast_declarations_class_super_t *super = NULL;
    CHECK_PARSE(parse_class_decl_super(parser, &lt_range, &super));

    rbs_node_list_t *members = NULL;
    CHECK_PARSE(parse_module_members(parser, &members));

    ADVANCE_ASSERT(parser, kEND);

    rbs_range_t end_range = parser->current_token.range;

    decl_range.end = end_range.end;

    CHECK_PARSE(parser_pop_typevar_table(parser));

    rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
    rbs_loc_alloc_children(ALLOCATOR(), loc, 5);
    rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
    rbs_loc_add_required_child(loc, INTERN("name"), name_range);
    rbs_loc_add_required_child(loc, INTERN("end"), end_range);
    rbs_loc_add_optional_child(loc, INTERN("type_params"), type_params_range);
    rbs_loc_add_optional_child(loc, INTERN("lt"), lt_range);

    *class_decl = rbs_ast_declarations_class_new(ALLOCATOR(), loc, name, type_params, super, members, annotations, comment);
    return true;
}

/*
  class_decl ::= {`class`} class_name `=` <class_name>
               | {`class`} class_name <class_decl0>
*/
NODISCARD
static bool parse_class_decl(rbs_parser_t *parser, rbs_position_t comment_pos, rbs_node_list_t *annotations, rbs_node_t **class_decl) {
    rbs_range_t keyword_range = parser->current_token.range;

    comment_pos = rbs_nonnull_pos_or(comment_pos, parser->current_token.range.start);
    rbs_ast_comment_t *comment = rbs_parser_get_comment(parser, comment_pos.line);

    rbs_parser_advance(parser);
    rbs_range_t class_name_range;
    rbs_type_name_t *class_name = NULL;
    CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &class_name_range, &class_name));

    if (parser->next_token.type == pEQ) {
        rbs_range_t eq_range = parser->next_token.range;
        rbs_parser_advance(parser);
        rbs_parser_advance(parser);

        rbs_range_t old_name_range;
        rbs_type_name_t *old_name = NULL;
        CHECK_PARSE(parse_type_name(parser, CLASS_NAME, &old_name_range, &old_name));

        rbs_range_t decl_range = {
            .start = keyword_range.start,
            .end = old_name_range.end,
        };

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), decl_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 4);
        rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);
        rbs_loc_add_required_child(loc, INTERN("new_name"), class_name_range);
        rbs_loc_add_required_child(loc, INTERN("eq"), eq_range);
        rbs_loc_add_optional_child(loc, INTERN("old_name"), old_name_range);

        *class_decl = (rbs_node_t *) rbs_ast_declarations_class_alias_new(ALLOCATOR(), loc, class_name, old_name, comment, annotations);
    } else {
        rbs_ast_declarations_class_t *class_decl0 = NULL;
        CHECK_PARSE(parse_class_decl0(parser, keyword_range, class_name, class_name_range, comment, annotations, &class_decl0));
        *class_decl = (rbs_node_t *) class_decl0;
    }

    return true;
}

/*
  nested_decl ::= {<const_decl>}
                | {<class_decl>}
                | {<interface_decl>}
                | {<module_decl>}
                | {<class_decl>}
*/
NODISCARD
static bool parse_nested_decl(rbs_parser_t *parser, const char *nested_in, rbs_position_t annot_pos, rbs_node_list_t *annotations, rbs_node_t **decl) {
    rbs_parser_push_typevar_table(parser, true);

    switch (parser->current_token.type) {
    case tUIDENT:
    case pCOLON2: {
        rbs_ast_declarations_constant_t *constant = NULL;
        CHECK_PARSE(parse_const_decl(parser, annotations, &constant));
        *decl = (rbs_node_t *) constant;
        break;
    }
    case tGIDENT: {
        rbs_ast_declarations_global_t *global = NULL;
        CHECK_PARSE(parse_global_decl(parser, annotations, &global));
        *decl = (rbs_node_t *) global;
        break;
    }
    case kTYPE: {
        rbs_ast_declarations_type_alias_t *typealias = NULL;
        CHECK_PARSE(parse_type_decl(parser, annot_pos, annotations, &typealias));
        *decl = (rbs_node_t *) typealias;
        break;
    }
    case kINTERFACE: {
        rbs_ast_declarations_interface_t *interface_decl = NULL;
        CHECK_PARSE(parse_interface_decl(parser, annot_pos, annotations, &interface_decl));
        *decl = (rbs_node_t *) interface_decl;
        break;
    }
    case kMODULE: {
        rbs_node_t *module_decl = NULL;
        CHECK_PARSE(parse_module_decl(parser, annot_pos, annotations, &module_decl));
        *decl = module_decl;
        break;
    }
    case kCLASS: {
        rbs_node_t *class_decl = NULL;
        CHECK_PARSE(parse_class_decl(parser, annot_pos, annotations, &class_decl));
        *decl = class_decl;
        break;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, true, "unexpected token for class/module declaration member");
        return false;
    }

    CHECK_PARSE(parser_pop_typevar_table(parser));

    return true;
}

NODISCARD
static bool parse_decl(rbs_parser_t *parser, rbs_node_t **decl) {
    rbs_node_list_t *annotations = rbs_node_list_new(ALLOCATOR());
    rbs_position_t annot_pos = NullPosition;

    CHECK_PARSE(parse_annotations(parser, annotations, &annot_pos));
    rbs_parser_advance(parser);

    switch (parser->current_token.type) {
    case tUIDENT:
    case pCOLON2: {
        rbs_ast_declarations_constant_t *constant = NULL;
        CHECK_PARSE(parse_const_decl(parser, annotations, &constant));
        *decl = (rbs_node_t *) constant;
        return true;
    }
    case tGIDENT: {
        rbs_ast_declarations_global_t *global = NULL;
        CHECK_PARSE(parse_global_decl(parser, annotations, &global));
        *decl = (rbs_node_t *) global;
        return true;
    }
    case kTYPE: {
        rbs_ast_declarations_type_alias_t *typealias = NULL;
        CHECK_PARSE(parse_type_decl(parser, annot_pos, annotations, &typealias));
        *decl = (rbs_node_t *) typealias;
        return true;
    }
    case kINTERFACE: {
        rbs_ast_declarations_interface_t *interface_decl = NULL;
        CHECK_PARSE(parse_interface_decl(parser, annot_pos, annotations, &interface_decl));
        *decl = (rbs_node_t *) interface_decl;
        return true;
    }
    case kMODULE: {
        rbs_node_t *module_decl = NULL;
        CHECK_PARSE(parse_module_decl(parser, annot_pos, annotations, &module_decl));
        *decl = module_decl;
        return true;
    }
    case kCLASS: {
        rbs_node_t *class_decl = NULL;
        CHECK_PARSE(parse_class_decl(parser, annot_pos, annotations, &class_decl));
        *decl = class_decl;
        return true;
    }
    default:
        rbs_parser_set_error(parser, parser->current_token, true, "cannot start a declaration");
        return false;
    }
}

/*
  namespace ::= {} (`::`)? (`tUIDENT` `::`)* `tUIDENT` <`::`>
              | {} <>                                            (empty -- returns empty namespace)
*/
NODISCARD
static bool parse_namespace(rbs_parser_t *parser, rbs_range_t *rg, rbs_namespace_t **out_ns) {
    bool is_absolute = false;

    if (parser->next_token.type == pCOLON2) {
        *rg = (rbs_range_t) {
            .start = parser->next_token.range.start,
            .end = parser->next_token.range.end,
        };
        is_absolute = true;

        rbs_parser_advance(parser);
    }

    rbs_node_list_t *path = rbs_node_list_new(ALLOCATOR());

    while (true) {
        if (parser->next_token.type == tUIDENT && parser->next_token2.type == pCOLON2) {
            rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), parser->next_token.range);
            rbs_ast_symbol_t *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->next_token));
            rbs_node_list_append(path, (rbs_node_t *) symbol);
            if (rbs_null_position_p(rg->start)) {
                rg->start = parser->next_token.range.start;
            }
            rg->end = parser->next_token2.range.end;
            rbs_parser_advance(parser);
            rbs_parser_advance(parser);
        } else {
            break;
        }
    }

    *out_ns = rbs_namespace_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), *rg), path, is_absolute);
    return true;
}

/*
  use_clauses ::= {} use_clause `,` ... `,` <use_clause>

  use_clause ::= {} namespace <tUIDENT>
               | {} namespace tUIDENT `as` <tUIDENT>
               | {} namespace <tSTAR>
*/
NODISCARD
static bool parse_use_clauses(rbs_parser_t *parser, rbs_node_list_t *clauses) {
    while (true) {
        rbs_range_t namespace_range = NULL_RANGE;
        rbs_namespace_t *ns = NULL;
        CHECK_PARSE(parse_namespace(parser, &namespace_range, &ns));

        switch (parser->next_token.type) {
        case tLIDENT:
        case tULIDENT:
        case tUIDENT: {
            rbs_parser_advance(parser);

            enum RBSTokenType ident_type = parser->current_token.type;

            rbs_range_t type_name_range = rbs_null_range_p(namespace_range) ? parser->current_token.range : (rbs_range_t) { .start = namespace_range.start, .end = parser->current_token.range.end };

            rbs_location_t *symbolLoc = rbs_location_current_token(parser);
            rbs_ast_symbol_t *symbol = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
            rbs_type_name_t *type_name = rbs_type_name_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), type_name_range), ns, symbol);

            rbs_range_t keyword_range = NULL_RANGE;
            rbs_range_t new_name_range = NULL_RANGE;
            rbs_ast_symbol_t *new_name = NULL;
            rbs_range_t clause_range = type_name_range;
            if (parser->next_token.type == kAS) {
                rbs_parser_advance(parser);
                keyword_range = parser->current_token.range;

                if (ident_type == tUIDENT) ADVANCE_ASSERT(parser, tUIDENT);
                if (ident_type == tLIDENT) ADVANCE_ASSERT(parser, tLIDENT);
                if (ident_type == tULIDENT) ADVANCE_ASSERT(parser, tULIDENT);

                rbs_location_t *symbolLoc = rbs_location_new(ALLOCATOR(), new_name_range);
                new_name = rbs_ast_symbol_new(ALLOCATOR(), symbolLoc, &parser->constant_pool, INTERN_TOKEN(parser, parser->current_token));
                new_name_range = parser->current_token.range;
                clause_range.end = new_name_range.end;
            }

            rbs_location_t *loc = rbs_location_new(ALLOCATOR(), clause_range);
            rbs_loc_alloc_children(ALLOCATOR(), loc, 3);
            rbs_loc_add_required_child(loc, INTERN("type_name"), type_name_range);
            rbs_loc_add_optional_child(loc, INTERN("keyword"), keyword_range);
            rbs_loc_add_optional_child(loc, INTERN("new_name"), new_name_range);

            rbs_ast_directives_use_single_clause_t *clause = rbs_ast_directives_use_single_clause_new(ALLOCATOR(), loc, type_name, new_name);
            rbs_node_list_append(clauses, (rbs_node_t *) clause);

            break;
        }
        case pSTAR: {
            rbs_range_t clause_range = namespace_range;
            rbs_parser_advance(parser);

            rbs_range_t star_range = parser->current_token.range;
            clause_range.end = star_range.end;

            rbs_location_t *loc = rbs_location_new(ALLOCATOR(), clause_range);
            rbs_loc_alloc_children(ALLOCATOR(), loc, 2);
            rbs_loc_add_required_child(loc, INTERN("namespace"), namespace_range);
            rbs_loc_add_required_child(loc, INTERN("star"), star_range);

            rbs_ast_directives_use_wildcard_clause_t *clause = rbs_ast_directives_use_wildcard_clause_new(ALLOCATOR(), loc, ns);
            rbs_node_list_append(clauses, (rbs_node_t *) clause);

            break;
        }
        default:
            rbs_parser_set_error(parser, parser->next_token, true, "use clause is expected");
            return false;
        }

        if (parser->next_token.type == pCOMMA) {
            rbs_parser_advance(parser);
        } else {
            break;
        }
    }

    return true;
}

/*
  use_directive ::= {} `use` <clauses>
 */
NODISCARD
static bool parse_use_directive(rbs_parser_t *parser, rbs_ast_directives_use_t **use_directive) {
    if (parser->next_token.type == kUSE) {
        rbs_parser_advance(parser);

        rbs_range_t keyword_range = parser->current_token.range;

        rbs_node_list_t *clauses = rbs_node_list_new(ALLOCATOR());
        CHECK_PARSE(parse_use_clauses(parser, clauses));

        rbs_range_t directive_range = keyword_range;
        directive_range.end = parser->current_token.range.end;

        rbs_location_t *loc = rbs_location_new(ALLOCATOR(), directive_range);
        rbs_loc_alloc_children(ALLOCATOR(), loc, 1);
        rbs_loc_add_required_child(loc, INTERN("keyword"), keyword_range);

        *use_directive = rbs_ast_directives_use_new(ALLOCATOR(), loc, clauses);
    }

    return true;
}

static rbs_ast_comment_t *parse_comment_lines(rbs_parser_t *parser, rbs_comment_t *com) {
    size_t hash_bytes = parser->lexer->encoding->char_width((const uint8_t *) "#", (size_t) 1);
    size_t space_bytes = parser->lexer->encoding->char_width((const uint8_t *) " ", (size_t) 1);

    rbs_buffer_t rbs_buffer;
    rbs_buffer_init(ALLOCATOR(), &rbs_buffer);

    for (size_t i = 0; i < com->line_tokens_count; i++) {
        rbs_token_t tok = com->line_tokens[i];

        const char *comment_start = parser->lexer->string.start + tok.range.start.byte_pos + hash_bytes;
        size_t comment_bytes = RBS_RANGE_BYTES(tok.range) - hash_bytes;

        rbs_string_t str = rbs_string_new(
            comment_start,
            parser->lexer->string.end
        );

        // Assumes the input is ASCII compatible
        unsigned char c = str.start[0];

        if (c == ' ') {
            comment_start += space_bytes;
            comment_bytes -= space_bytes;
        }

        rbs_buffer_append_string(ALLOCATOR(), &rbs_buffer, comment_start, comment_bytes);
        rbs_buffer_append_cstr(ALLOCATOR(), &rbs_buffer, "\n");
    }

    return rbs_ast_comment_new(
        ALLOCATOR(),
        rbs_location_new(ALLOCATOR(), (rbs_range_t) { .start = com->start, .end = com->end }),
        rbs_buffer_to_string(&rbs_buffer)
    );
}

static rbs_comment_t *comment_get_comment(rbs_comment_t *com, int line) {
    if (com == NULL) {
        return NULL;
    }

    if (com->end.line < line) {
        return NULL;
    }

    if (com->end.line == line) {
        return com;
    }

    return comment_get_comment(com->next_comment, line);
}

static void comment_insert_new_line(rbs_allocator_t *allocator, rbs_comment_t *com, rbs_token_t comment_token) {
    if (com->line_tokens_count == com->line_tokens_capacity) {
        size_t old_size = com->line_tokens_capacity;
        size_t new_size = old_size * 2;
        com->line_tokens_capacity = new_size;

        com->line_tokens = rbs_allocator_realloc(
            allocator,
            com->line_tokens,
            sizeof(rbs_token_t) * old_size,
            sizeof(rbs_token_t) * new_size,
            rbs_token_t
        );
    }

    com->line_tokens[com->line_tokens_count++] = comment_token;
    com->end = comment_token.range.end;
}

static rbs_comment_t *alloc_comment(rbs_allocator_t *allocator, rbs_token_t comment_token, rbs_comment_t *last_comment) {
    rbs_comment_t *new_comment = rbs_allocator_alloc(allocator, rbs_comment_t);

    size_t initial_line_capacity = 10;

    rbs_token_t *tokens = rbs_allocator_calloc(allocator, initial_line_capacity, rbs_token_t);
    tokens[0] = comment_token;

    *new_comment = (rbs_comment_t) {
        .start = comment_token.range.start,
        .end = comment_token.range.end,

        .line_tokens_capacity = initial_line_capacity,
        .line_tokens_count = 1,
        .line_tokens = tokens,

        .next_comment = last_comment,
    };

    return new_comment;
}

/**
 * Insert new comment line token.
 * */
static void insert_comment_line(rbs_parser_t *parser, rbs_token_t tok) {
    int prev_line = tok.range.start.line - 1;

    rbs_comment_t *com = comment_get_comment(parser->last_comment, prev_line);

    if (com) {
        comment_insert_new_line(ALLOCATOR(), com, tok);
    } else {
        parser->last_comment = alloc_comment(ALLOCATOR(), tok, parser->last_comment);
    }
}

bool rbs_parse_signature(rbs_parser_t *parser, rbs_signature_t **signature) {
    rbs_range_t signature_range;
    signature_range.start = parser->current_token.range.start;

    rbs_node_list_t *dirs = rbs_node_list_new(ALLOCATOR());
    rbs_node_list_t *decls = rbs_node_list_new(ALLOCATOR());

    while (parser->next_token.type == kUSE) {
        rbs_ast_directives_use_t *use_node;
        CHECK_PARSE(parse_use_directive(parser, &use_node));

        if (use_node == NULL) {
            rbs_node_list_append(dirs, NULL);
        } else {
            rbs_node_list_append(dirs, (rbs_node_t *) use_node);
        }
    }

    while (parser->next_token.type != pEOF) {
        rbs_node_t *decl = NULL;
        CHECK_PARSE(parse_decl(parser, &decl));
        rbs_node_list_append(decls, decl);
    }

    signature_range.end = parser->current_token.range.end;
    *signature = rbs_signature_new(ALLOCATOR(), rbs_location_new(ALLOCATOR(), signature_range), dirs, decls);
    return true;
}

bool rbs_parse_type_params(rbs_parser_t *parser, bool module_type_params, rbs_node_list_t **params) {
    if (parser->next_token.type != pLBRACKET) {
        rbs_parser_set_error(parser, parser->next_token, true, "expected a token `pLBRACKET`");
        return false;
    }

    rbs_range_t rg = NULL_RANGE;
    rbs_parser_push_typevar_table(parser, true);
    bool res = parse_type_params(parser, &rg, module_type_params, params);
    rbs_parser_push_typevar_table(parser, false);

    rbs_parser_advance(parser);
    if (parser->current_token.type != pEOF) {
        rbs_parser_set_error(parser, parser->current_token, true, "expected a token `%s`", rbs_token_type_str(pEOF));
        return false;
    }

    return res;
}

id_table *alloc_empty_table(rbs_allocator_t *allocator) {
    id_table *table = rbs_allocator_alloc(allocator, id_table);

    *table = (id_table) {
        .size = 10,
        .count = 0,
        .ids = rbs_allocator_calloc(allocator, 10, rbs_constant_id_t),
        .next = NULL,
    };

    return table;
}

id_table *alloc_reset_table(rbs_allocator_t *allocator) {
    id_table *table = rbs_allocator_alloc(allocator, id_table);

    *table = (id_table) {
        .size = 0,
        .count = 0,
        .ids = NULL,
        .next = NULL,
    };

    return table;
}

void rbs_parser_push_typevar_table(rbs_parser_t *parser, bool reset) {
    if (reset) {
        id_table *table = alloc_reset_table(ALLOCATOR());
        table->next = parser->vars;
        parser->vars = table;
    }

    id_table *table = alloc_empty_table(ALLOCATOR());
    table->next = parser->vars;
    parser->vars = table;
}

NODISCARD
bool rbs_parser_insert_typevar(rbs_parser_t *parser, rbs_constant_id_t id) {
    id_table *table = parser->vars;

    if (RESET_TABLE_P(table)) {
        rbs_parser_set_error(parser, parser->current_token, false, "Cannot insert to reset table");
        return false;
    }

    if (table->size == table->count) {
        // expand
        rbs_constant_id_t *ptr = table->ids;
        table->size += 10;
        table->ids = rbs_allocator_calloc(ALLOCATOR(), table->size, rbs_constant_id_t);
        memcpy(table->ids, ptr, sizeof(rbs_constant_id_t) * table->count);
    }

    table->ids[table->count++] = id;

    return true;
}

void rbs_parser_print(rbs_parser_t *parser) {
    printf("  current_token = %s (%d...%d)\n", rbs_token_type_str(parser->current_token.type), parser->current_token.range.start.char_pos, parser->current_token.range.end.char_pos);
    printf("     next_token = %s (%d...%d)\n", rbs_token_type_str(parser->next_token.type), parser->next_token.range.start.char_pos, parser->next_token.range.end.char_pos);
    printf("    next_token2 = %s (%d...%d)\n", rbs_token_type_str(parser->next_token2.type), parser->next_token2.range.start.char_pos, parser->next_token2.range.end.char_pos);
    printf("    next_token3 = %s (%d...%d)\n", rbs_token_type_str(parser->next_token3.type), parser->next_token3.range.start.char_pos, parser->next_token3.range.end.char_pos);
}

void rbs_parser_advance(rbs_parser_t *parser) {
    parser->current_token = parser->next_token;
    parser->next_token = parser->next_token2;
    parser->next_token2 = parser->next_token3;

    while (true) {
        if (parser->next_token3.type == pEOF) {
            break;
        }

        parser->next_token3 = rbs_lexer_next_token(parser->lexer);

        if (parser->next_token3.type == tCOMMENT) {
            // skip
        } else if (parser->next_token3.type == tLINECOMMENT) {
            insert_comment_line(parser, parser->next_token3);
        } else if (parser->next_token3.type == tTRIVIA) {
            //skip
        } else {
            break;
        }
    }
}

void rbs_print_token(rbs_token_t tok) {
    printf(
        "%s char=%d...%d\n",
        rbs_token_type_str(tok.type),
        tok.range.start.char_pos,
        tok.range.end.char_pos
    );
}

void rbs_print_lexer(rbs_lexer_t *lexer) {
    printf("Lexer: (range = %d...%d, encoding = %s\n", lexer->start_pos, lexer->end_pos, lexer->encoding->name);
    printf("  start = { char_pos = %d, byte_pos = %d }\n", lexer->start.char_pos, lexer->start.byte_pos);
    printf("  current = { char_pos = %d, byte_pos = %d }\n", lexer->current.char_pos, lexer->current.byte_pos);
    printf("  character = { code_point = %d (%c), bytes = %zu }\n", lexer->current_code_point, lexer->current_code_point < 256 ? lexer->current_code_point : '?', lexer->current_character_bytes);
    printf("  first_token_of_line = %s\n", lexer->first_token_of_line ? "true" : "false");
}

rbs_ast_comment_t *rbs_parser_get_comment(rbs_parser_t *parser, int subject_line) {
    int comment_line = subject_line - 1;

    rbs_comment_t *com = comment_get_comment(parser->last_comment, comment_line);

    if (com) {
        return parse_comment_lines(parser, com);
    } else {
        return NULL;
    }
}

rbs_lexer_t *rbs_lexer_new(rbs_allocator_t *allocator, rbs_string_t string, const rbs_encoding_t *encoding, int start_pos, int end_pos) {
    rbs_lexer_t *lexer = rbs_allocator_alloc(allocator, rbs_lexer_t);

    rbs_position_t start_position = (rbs_position_t) {
        .byte_pos = 0,
        .char_pos = 0,
        .line = 1,
        .column = 0,
    };

    *lexer = (rbs_lexer_t) {
        .string = string,
        .start_pos = start_pos,
        .end_pos = end_pos,
        .current = start_position,
        .start = { 0 },
        .first_token_of_line = true,
        .current_character_bytes = 0,
        .current_code_point = '\0',
        .encoding = encoding,
    };

    unsigned int codepoint;
    size_t bytes;

    if (rbs_next_char(lexer, &codepoint, &bytes)) {
        lexer->current_code_point = codepoint;
        lexer->current_character_bytes = bytes;
    } else {
        lexer->current_code_point = '\0';
        lexer->current_character_bytes = 1;
    }

    if (start_pos > 0) {
        rbs_skipn(lexer, start_pos);
    }

    lexer->start = lexer->current;

    return lexer;
}

rbs_parser_t *rbs_parser_new(rbs_string_t string, const rbs_encoding_t *encoding, int start_pos, int end_pos) {
    rbs_allocator_t *allocator = rbs_allocator_init();

    rbs_lexer_t *lexer = rbs_lexer_new(allocator, string, encoding, start_pos, end_pos);
    rbs_parser_t *parser = rbs_allocator_alloc(allocator, rbs_parser_t);

    *parser = (rbs_parser_t) {
        .lexer = lexer,

        .current_token = NullToken,
        .next_token = NullToken,
        .next_token2 = NullToken,
        .next_token3 = NullToken,

        .vars = NULL,
        .last_comment = NULL,

        .constant_pool = { 0 },
        .allocator = allocator,
        .error = NULL,
    };

    // The parser's constant pool is mainly used for storing the names of type variables, which usually aren't many.
    // Below are some statistics gathered from the current test suite. We can see that 56% of parsers never add to their
    // constant pool at all. The initial capacity needs to be a power of 2. Picking 2 means that we won't need to realloc
    // in 85% of cases.
    //
    // TODO: recalculate these statistics based on a real world codebase, rather than the test suite.
    //
    // | Size | Count | Cumulative | % Coverage |
    // |------|-------|------------|------------|
    // |   0  | 7,862 |      7,862 |     56%    |
    // |   1  | 3,196 |     11,058 |     79%    |
    // |   2  |   778 |     12,719 |     85%    |
    // |   3  |   883 |     11,941 |     91%    |
    // |   4  |   478 |     13,197 |     95%    |
    // |   5  |   316 |     13,513 |     97%    |
    // |   6  |   288 |     13,801 |     99%    |
    // |   7  |   144 |     13,945 |    100%    |
    const size_t initial_pool_capacity = 2;
    rbs_constant_pool_init(&parser->constant_pool, initial_pool_capacity);

    rbs_parser_advance(parser);
    rbs_parser_advance(parser);
    rbs_parser_advance(parser);

    return parser;
}

void rbs_parser_free(rbs_parser_t *parser) {
    rbs_constant_pool_free(&parser->constant_pool);
    rbs_allocator_free(ALLOCATOR());
}

void rbs_parser_set_error(rbs_parser_t *parser, rbs_token_t tok, bool syntax_error, const char *fmt, ...) {
    if (parser->error) {
        return;
    }

    va_list args;

    va_start(args, fmt);
    int length = vsnprintf(NULL, 0, fmt, args);
    va_end(args);

    char *message = rbs_allocator_alloc_many(ALLOCATOR(), length + 1, char);

    va_start(args, fmt);
    vsnprintf(message, length + 1, fmt, args);
    va_end(args);

    parser->error = rbs_allocator_alloc(ALLOCATOR(), rbs_error_t);
    parser->error->token = tok;
    parser->error->message = message;
    parser->error->syntax_error = syntax_error;
}
