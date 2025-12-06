#ifndef RIPPER_INIT_H
#define RIPPER_INIT_H

PRINTF_ARGS(void ripper_compile_error(struct parser_params*, const char *fmt, ...), 2, 3);

VALUE rb_str_new_parser_string(rb_parser_string_t *str);

VALUE rb_str_new_mutable_parser_string(rb_parser_string_t *str);

#endif /* RIPPER_INIT_H */
