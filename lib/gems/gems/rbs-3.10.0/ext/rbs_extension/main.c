#include "rbs_extension.h"
#include "rbs/util/rbs_assert.h"
#include "rbs/util/rbs_allocator.h"
#include "rbs/util/rbs_constant_pool.h"
#include "ast_translation.h"
#include "legacy_location.h"
#include "rbs_string_bridging.h"

#include "ruby/vm.h"

/**
 * Raises `RBS::ParsingError` or `RuntimeError` on `tok` with message constructed with given `fmt`.
 *
 * ```
 * foo.rbs:11:21...11:25: Syntax error: {message}, token=`{tok source}` ({tok type})
 * ```
 * */
static NORETURN(void) raise_error(rbs_error_t *error, VALUE buffer) {
    RBS_ASSERT(error != NULL, "raise_error() called with NULL error");

    if (!error->syntax_error) {
        rb_raise(rb_eRuntimeError, "Unexpected error");
    }

    VALUE location = rbs_new_location(buffer, error->token.range);
    VALUE type = rb_str_new_cstr(rbs_token_type_str(error->token.type));

    VALUE rb_error = rb_funcall(
        RBS_ParsingError,
        rb_intern("new"),
        3,
        location,
        rb_str_new_cstr(error->message),
        type
    );

    rb_exc_raise(rb_error);
}

void raise_error_if_any(rbs_parser_t *parser, VALUE buffer) {
    if (parser->error != NULL) {
        raise_error(parser->error, buffer);
    }
}

/**
 * Inserts the given array of type variables names into the parser's type variable table.
 * @param parser
 * @param variables A Ruby Array of Symbols, or nil.
 */
static void declare_type_variables(rbs_parser_t *parser, VALUE variables, VALUE buffer) {
    if (NIL_P(variables)) return; // Nothing to do.

    if (!RB_TYPE_P(variables, T_ARRAY)) {
        rbs_parser_free(parser);
        rb_raise(rb_eTypeError, "wrong argument type %" PRIsVALUE " (must be an Array of Symbols or nil)", rb_obj_class(variables));
    }

    rbs_parser_push_typevar_table(parser, true);

    for (long i = 0; i < rb_array_len(variables); i++) {
        VALUE symbol = rb_ary_entry(variables, i);

        if (!RB_TYPE_P(symbol, T_SYMBOL)) {
            rbs_parser_free(parser);
            rb_raise(rb_eTypeError, "Type variables Array contains invalid value %" PRIsVALUE " of type %" PRIsVALUE " (must be an Array of Symbols or nil)", rb_inspect(symbol), rb_obj_class(symbol));
        }

        VALUE name_str = rb_sym2str(symbol);

        rbs_constant_id_t id = rbs_constant_pool_insert_shared(
            &parser->constant_pool,
            (const uint8_t *) RSTRING_PTR(name_str),
            RSTRING_LEN(name_str)
        );

        if (!rbs_parser_insert_typevar(parser, id)) {
            raise_error(parser->error, buffer);
        }
    }
}

struct parse_type_arg {
    VALUE buffer;
    rb_encoding *encoding;
    rbs_parser_t *parser;
    VALUE require_eof;
};

static VALUE ensure_free_parser(VALUE parser) {
    rbs_parser_free((rbs_parser_t *) parser);
    return Qnil;
}

static VALUE parse_type_try(VALUE a) {
    struct parse_type_arg *arg = (struct parse_type_arg *) a;
    rbs_parser_t *parser = arg->parser;

    if (parser->next_token.type == pEOF) {
        return Qnil;
    }

    rbs_node_t *type;
    rbs_parse_type(parser, &type);

    raise_error_if_any(parser, arg->buffer);

    if (RB_TEST(arg->require_eof)) {
        rbs_parser_advance(parser);
        if (parser->current_token.type != pEOF) {
            rbs_parser_set_error(parser, parser->current_token, true, "expected a token `%s`", rbs_token_type_str(pEOF));
            raise_error(parser->error, arg->buffer);
        }
    }

    rbs_translation_context_t ctx = rbs_translation_context_create(
        &parser->constant_pool,
        arg->buffer,
        arg->encoding
    );

    return rbs_struct_to_ruby_value(ctx, type);
}

static rbs_lexer_t *alloc_lexer_from_buffer(rbs_allocator_t *allocator, VALUE string, rb_encoding *encoding, int start_pos, int end_pos) {
    if (start_pos < 0 || end_pos < 0) {
        rb_raise(rb_eArgError, "negative position range: %d...%d", start_pos, end_pos);
    }

    const char *encoding_name = rb_enc_name(encoding);

    return rbs_lexer_new(
        allocator,
        rbs_string_from_ruby_string(string),
        rbs_encoding_find((const uint8_t *) encoding_name, (const uint8_t *) (encoding_name + strlen(encoding_name))),
        start_pos,
        end_pos
    );
}

static rbs_parser_t *alloc_parser_from_buffer(VALUE buffer, int start_pos, int end_pos) {
    if (start_pos < 0 || end_pos < 0) {
        rb_raise(rb_eArgError, "negative position range: %d...%d", start_pos, end_pos);
    }

    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);

    rb_encoding *encoding = rb_enc_get(string);
    const char *encoding_name = rb_enc_name(encoding);

    return rbs_parser_new(
        rbs_string_from_ruby_string(string),
        rbs_encoding_find((const uint8_t *) encoding_name, (const uint8_t *) (encoding_name + strlen(encoding_name))),
        start_pos,
        end_pos
    );
}

static VALUE rbsparser_parse_type(VALUE self, VALUE buffer, VALUE start_pos, VALUE end_pos, VALUE variables, VALUE require_eof) {
    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);
    rb_encoding *encoding = rb_enc_get(string);

    rbs_parser_t *parser = alloc_parser_from_buffer(buffer, FIX2INT(start_pos), FIX2INT(end_pos));
    declare_type_variables(parser, variables, buffer);
    struct parse_type_arg arg = {
        .buffer = buffer,
        .encoding = encoding,
        .parser = parser,
        .require_eof = require_eof
    };

    VALUE result = rb_ensure(parse_type_try, (VALUE) &arg, ensure_free_parser, (VALUE) parser);

    RB_GC_GUARD(string);

    return result;
}

static VALUE parse_method_type_try(VALUE a) {
    struct parse_type_arg *arg = (struct parse_type_arg *) a;
    rbs_parser_t *parser = arg->parser;

    if (parser->next_token.type == pEOF) {
        return Qnil;
    }

    rbs_method_type_t *method_type = NULL;
    rbs_parse_method_type(parser, &method_type, RB_TEST(arg->require_eof));

    raise_error_if_any(parser, arg->buffer);

    rbs_translation_context_t ctx = rbs_translation_context_create(
        &parser->constant_pool,
        arg->buffer,
        arg->encoding
    );

    return rbs_struct_to_ruby_value(ctx, (rbs_node_t *) method_type);
}

static VALUE rbsparser_parse_method_type(VALUE self, VALUE buffer, VALUE start_pos, VALUE end_pos, VALUE variables, VALUE require_eof) {
    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);
    rb_encoding *encoding = rb_enc_get(string);

    rbs_parser_t *parser = alloc_parser_from_buffer(buffer, FIX2INT(start_pos), FIX2INT(end_pos));
    declare_type_variables(parser, variables, buffer);
    struct parse_type_arg arg = {
        .buffer = buffer,
        .encoding = encoding,
        .parser = parser,
        .require_eof = require_eof
    };

    VALUE result = rb_ensure(parse_method_type_try, (VALUE) &arg, ensure_free_parser, (VALUE) parser);

    RB_GC_GUARD(string);

    return result;
}

static VALUE parse_signature_try(VALUE a) {
    struct parse_type_arg *arg = (struct parse_type_arg *) a;
    rbs_parser_t *parser = arg->parser;

    rbs_signature_t *signature = NULL;
    rbs_parse_signature(parser, &signature);

    raise_error_if_any(parser, arg->buffer);

    rbs_translation_context_t ctx = rbs_translation_context_create(
        &parser->constant_pool,
        arg->buffer,
        arg->encoding
    );

    return rbs_struct_to_ruby_value(ctx, (rbs_node_t *) signature);
}

static VALUE rbsparser_parse_signature(VALUE self, VALUE buffer, VALUE start_pos, VALUE end_pos) {
    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);
    rb_encoding *encoding = rb_enc_get(string);

    rbs_parser_t *parser = alloc_parser_from_buffer(buffer, FIX2INT(start_pos), FIX2INT(end_pos));
    struct parse_type_arg arg = {
        .buffer = buffer,
        .encoding = encoding,
        .parser = parser,
        .require_eof = false
    };

    VALUE result = rb_ensure(parse_signature_try, (VALUE) &arg, ensure_free_parser, (VALUE) parser);

    RB_GC_GUARD(string);

    return result;
}

struct parse_type_params_arg {
    VALUE buffer;
    rb_encoding *encoding;
    rbs_parser_t *parser;
    VALUE module_type_params;
};

static VALUE parse_type_params_try(VALUE a) {
    struct parse_type_params_arg *arg = (struct parse_type_params_arg *) a;
    rbs_parser_t *parser = arg->parser;

    if (parser->next_token.type == pEOF) {
        return Qnil;
    }

    rbs_node_list_t *params = NULL;
    rbs_parse_type_params(parser, arg->module_type_params, &params);

    raise_error_if_any(parser, arg->buffer);

    rbs_translation_context_t ctx = rbs_translation_context_create(
        &parser->constant_pool,
        arg->buffer,
        arg->encoding
    );

    return rbs_node_list_to_ruby_array(ctx, params);
}

static VALUE rbsparser_parse_type_params(VALUE self, VALUE buffer, VALUE start_pos, VALUE end_pos, VALUE module_type_params) {
    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);
    rb_encoding *encoding = rb_enc_get(string);

    rbs_parser_t *parser = alloc_parser_from_buffer(buffer, FIX2INT(start_pos), FIX2INT(end_pos));
    struct parse_type_params_arg arg = {
        .buffer = buffer,
        .encoding = encoding,
        .parser = parser,
        .module_type_params = module_type_params
    };

    VALUE result = rb_ensure(parse_type_params_try, (VALUE) &arg, ensure_free_parser, (VALUE) parser);

    RB_GC_GUARD(string);

    return result;
}

static VALUE rbsparser_lex(VALUE self, VALUE buffer, VALUE end_pos) {
    VALUE string = rb_funcall(buffer, rb_intern("content"), 0);
    StringValue(string);
    rb_encoding *encoding = rb_enc_get(string);

    rbs_allocator_t *allocator = rbs_allocator_init();
    rbs_lexer_t *lexer = alloc_lexer_from_buffer(allocator, string, encoding, 0, FIX2INT(end_pos));

    VALUE results = rb_ary_new();
    rbs_token_t token = NullToken;
    while (token.type != pEOF) {
        token = rbs_lexer_next_token(lexer);
        VALUE type = ID2SYM(rb_intern(rbs_token_type_str(token.type)));
        VALUE location = rbs_new_location(buffer, token.range);
        VALUE pair = rb_ary_new3(2, type, location);
        rb_ary_push(results, pair);
    }

    rbs_allocator_free(allocator);
    RB_GC_GUARD(string);

    return results;
}

void rbs__init_parser(void) {
    RBS_Parser = rb_define_class_under(RBS, "Parser", rb_cObject);
    rb_gc_register_mark_object(RBS_Parser);

    EMPTY_ARRAY = rb_obj_freeze(rb_ary_new());
    rb_gc_register_mark_object(EMPTY_ARRAY);

    EMPTY_HASH = rb_obj_freeze(rb_hash_new());
    rb_gc_register_mark_object(EMPTY_HASH);

    rb_define_singleton_method(RBS_Parser, "_parse_type", rbsparser_parse_type, 5);
    rb_define_singleton_method(RBS_Parser, "_parse_method_type", rbsparser_parse_method_type, 5);
    rb_define_singleton_method(RBS_Parser, "_parse_signature", rbsparser_parse_signature, 3);
    rb_define_singleton_method(RBS_Parser, "_parse_type_params", rbsparser_parse_type_params, 4);
    rb_define_singleton_method(RBS_Parser, "_lex", rbsparser_lex, 2);
}

static void Deinit_rbs_extension(ruby_vm_t *_) {
    rbs_constant_pool_free(RBS_GLOBAL_CONSTANT_POOL);
}

void Init_rbs_extension(void) {
#ifdef HAVE_RB_EXT_RACTOR_SAFE
    rb_ext_ractor_safe(true);
#endif
    rbs__init_constants();
    rbs__init_location();
    rbs__init_parser();

    /* Calculated based on the number of unique strings used with the `INTERN` macro in `parser.c`.
   *
   * ```bash
   * grep -o 'INTERN("\([^"]*\)")' ext/rbs_extension/parser.c \
   *     | sed 's/INTERN("\(.*\)")/\1/' \
   *     | sort -u \
   *     | wc -l
   * ```
   */
    const size_t num_uniquely_interned_strings = 26;
    rbs_constant_pool_init(RBS_GLOBAL_CONSTANT_POOL, num_uniquely_interned_strings);

    ruby_vm_at_exit(Deinit_rbs_extension);
}
