/* Copyright (c) 2026, TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// C does not support forwarding varargs (...).
// So we need to declare varargs functions here and call a `rb_tr_*_va_list` variant which takes a va_list instead.

#include <ruby.h>

#include <internal_all.h>

VALUE rb_tr_ary_new_from_args_va_list(long n, va_list args);

VALUE rb_ary_new_from_args(long n, ...) {
  va_list args;
  va_start(args, n);
  VALUE array = rb_tr_ary_new_from_args_va_list(n, args);
  va_end(args);
  return array;
}

RBIMPL_ATTR_NORETURN()
void rb_tr_fatal_va_list(const char *fmt, va_list args);

void rb_fatal(const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  rb_tr_fatal_va_list(fmt, args);
  va_end(args);
}

RBIMPL_ATTR_NORETURN()
void rb_tr_bug_va_list(const char *fmt, va_list args);

void rb_bug(const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  rb_tr_bug_va_list(fmt, args);
  va_end(args);
}

void rb_tr_warn_va_list(const char *fmt, va_list args);

void rb_warning(const char *fmt, ...) {
  if (RTEST(ruby_verbose)) {
    va_list args;
    va_start(args, fmt);
    rb_tr_warn_va_list(fmt, args);
    va_end(args);
  }
}

void rb_warn(const char *fmt, ...) {
  if (!NIL_P(ruby_verbose)) {
    va_list args;
    va_start(args, fmt);
    rb_tr_warn_va_list(fmt, args);
    va_end(args);
  }
}

bool rb_warning_category_enabled_p(rb_warning_category_t category);
void rb_tr_category_warn_va_list(rb_warning_category_t category, const char *fmt, va_list args);

void rb_category_warn(rb_warning_category_t category, const char *fmt, ...) {
  if (!NIL_P(ruby_verbose) && rb_warning_category_enabled_p(category)) {
    va_list args;
    va_start(args, fmt);
    rb_tr_category_warn_va_list(category, fmt, args);
    va_end(args);
  }
}

VALUE rb_tr_struct_new_va_list(VALUE klass, va_list args);

VALUE rb_struct_new(VALUE klass, ...) {
  va_list args;
  va_start(args, klass);
  VALUE result = rb_tr_struct_new_va_list(klass, args);
  va_end(args);
  return result;
}

VALUE rb_tr_struct_define_va_list(const char *name, va_list args);

VALUE rb_struct_define(const char *name, ...) {
  va_list args;
  va_start(args, name);
  VALUE result = rb_tr_struct_define_va_list(name, args);
  va_end(args);
  return result;
}

VALUE rb_tr_struct_define_under_va_list(VALUE space, const char *name, va_list args);

VALUE rb_struct_define_under(VALUE space, const char *name, ...) {
  va_list args;
  va_start(args, name);
  VALUE result = rb_tr_struct_define_under_va_list(space, name, args);
  va_end(args);
  return result;
}

VALUE rb_tr_data_define_va_list(VALUE super, va_list args);

VALUE rb_data_define(VALUE super, ...) {
  va_list args;
  va_start(args, super);
  VALUE result = rb_tr_data_define_va_list(super == 0 ? Qnil : super, args);
  va_end(args);
  return result;
}

VALUE rb_tr_yield_values_va_list(int n, va_list args);

#undef rb_yield_values
VALUE rb_yield_values(int n, ...) {
  va_list args;
  va_start(args, n);
  VALUE result = rb_tr_yield_values_va_list(n, args);
  va_end(args);
  return result;
}

VALUE rb_tr_rescue2_va_list(VALUE (*b_proc)(VALUE), VALUE data1, VALUE (*r_proc)(VALUE, VALUE), VALUE data2, va_list args);

VALUE rb_rescue2(VALUE (*b_proc)(VALUE), VALUE data1, VALUE (*r_proc)(VALUE, VALUE), VALUE data2, ...) {
    va_list args;
    va_start(args, data2);
    VALUE result = rb_tr_rescue2_va_list(b_proc, data1, r_proc, data2, args);
    va_end(args);
    return result;
}
