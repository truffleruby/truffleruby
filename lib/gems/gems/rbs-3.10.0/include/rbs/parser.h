#ifndef RBS__PARSER_H
#define RBS__PARSER_H

#include "rbs/defines.h"
#include "rbs/util/rbs_allocator.h"
#include "rbs/util/rbs_constant_pool.h"
#include "rbs/lexer.h"
#include "rbs/ast.h"

#include <stdbool.h>
#include <stddef.h>

/**
 * comment represents a sequence of comment lines.
 *
 *     # Comment for the method.
 *     #
 *     # ```rb
 *     # object.foo()  # Do something
 *     # ```
 *     #
 *     def foo: () -> void
 *
 * A comment object represents the six lines of comments.
 * */
typedef struct rbs_comment_t {
    rbs_position_t start;
    rbs_position_t end;

    size_t line_tokens_capacity;
    size_t line_tokens_count;
    rbs_token_t *line_tokens;

    struct rbs_comment_t *next_comment;
} rbs_comment_t;

typedef struct rbs_error_t {
    char *message;
    rbs_token_t token;
    bool syntax_error;
} rbs_error_t;

/**
 * An RBS parser is a LL(3) parser.
 * */
typedef struct {
    rbs_lexer_t *lexer;

    rbs_token_t current_token;
    rbs_token_t next_token;  /* The first lookahead token */
    rbs_token_t next_token2; /* The second lookahead token */
    rbs_token_t next_token3; /* The third lookahead token */

    struct id_table *vars;       /* Known type variables */
    rbs_comment_t *last_comment; /* Last read comment */

    rbs_constant_pool_t constant_pool;
    rbs_allocator_t *allocator;
    rbs_error_t *error;
} rbs_parser_t;

/**
 * Insert new table entry.
 * Setting `reset` inserts a _reset_ entry, which stops searching.
 *
 * ```
 * class Foo[A]
 *          ^^^                      <= push new table with reset
 *   def foo: [B] () -> [A, B]
 *            ^^^                    <= push new table without reset
 *
 *   class Baz[C]
 *            ^^^                    <= push new table with reset
 *   end
 * end
 * ```
 * */
void rbs_parser_push_typevar_table(rbs_parser_t *parser, bool reset);

/**
 * Insert new type variable into the latest table.
 * */
NODISCARD bool rbs_parser_insert_typevar(rbs_parser_t *parser, rbs_constant_id_t id);

/**
 * Allocate new rbs_lexer_t object.
 *
 * ```
 * VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
 * rbs_lexer_new(string, 0, 31)    // New rbs_lexer_t with buffer content
 * ```
 * */
rbs_lexer_t *rbs_lexer_new(rbs_allocator_t *, rbs_string_t string, const rbs_encoding_t *encoding, int start_pos, int end_pos);

/**
 * Allocate new rbs_parser_t object.
 *
 * ```
 * rbs_parser_new(buffer, string, encoding, 0, 1);
 * ```
 * */
rbs_parser_t *rbs_parser_new(rbs_string_t string, const rbs_encoding_t *encoding, int start_pos, int end_pos);
void rbs_parser_free(rbs_parser_t *parser);

/**
 * Advance one token.
 * */
void rbs_parser_advance(rbs_parser_t *parser);

void rbs_parser_print(rbs_parser_t *parser);

/**
 * Returns a RBS::Comment object associated with an subject at `subject_line`.
 *
 * ```rbs
 * # Comment1
 * class Foo           # This is the subject line for Comment1
 *
 *   # Comment2
 *   %a{annotation}    # This is the subject line for Comment2
 *   def foo: () -> void
 * end
 * ```
 * */
rbs_ast_comment_t *rbs_parser_get_comment(rbs_parser_t *parser, int subject_line);

void rbs_parser_set_error(rbs_parser_t *parser, rbs_token_t tok, bool syntax_error, const char *fmt, ...) RBS_ATTRIBUTE_FORMAT(4, 5);

bool rbs_parse_type(rbs_parser_t *parser, rbs_node_t **type);
bool rbs_parse_method_type(rbs_parser_t *parser, rbs_method_type_t **method_type, bool require_eof);
bool rbs_parse_signature(rbs_parser_t *parser, rbs_signature_t **signature);

bool rbs_parse_type_params(rbs_parser_t *parser, bool module_type_params, rbs_node_list_t **params);

#endif
