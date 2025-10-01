/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include "ruby.h"
#include "ruby/thread.h"
#include "rubyspec.h"

#include <errno.h>
#include <time.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

static VALUE thread_spec_rb_thread_call_without_gvl_native_function(VALUE self) {
  // Must be a real native function, see https://github.com/truffleruby/truffleruby/issues/2090
  pid_t ret = (pid_t) (long) rb_thread_call_without_gvl((void *(*)(void *)) getpid, 0, RUBY_UBF_IO, 0);
  return LONG2FIX(ret);
}

static void* call_check_ints(void* arg) {
    rb_thread_check_ints();
    return NULL;
}

static void* block_sleep(void* arg) {
  struct timespec remaining = { .tv_sec = 1, .tv_nsec = 0 };
  while (nanosleep(&remaining, &remaining) == -1 && errno == EINTR) {
    // Similar to how ossl_pkey.c does it
    rb_thread_call_with_gvl(call_check_ints, NULL);
  }
  return (void*) Qtrue;
}

static VALUE thread_spec_rb_thread_call_without_gvl_unblock_signal(VALUE self) {
  return (VALUE) rb_thread_call_without_gvl(block_sleep, NULL, RUBY_UBF_IO, NULL);
}

static void* block(void* arg) {
  int fd = *(int*)arg;
  char buffer = ' ';
  ssize_t r;

  while (true) {
    ssize_t r = read(fd, &buffer, 1);
    if (r == 1) {
      if (buffer == 'D') { // done
        return (void*) Qtrue;
      } else if (buffer == 'U') { // unblock
        // Similar to how ossl_pkey.c does it
        rb_thread_call_with_gvl(call_check_ints, NULL);
        continue;
      } else {
        return (void*) rb_str_new(&buffer, 1);
      }
    } else {
      perror("read() in blocking function returned != 1");
      return (void*) Qfalse;
    }
  }
}

static void unblock(void* arg) {
  int fd = *(int*)arg;
  char buffer = 'U';
  while (write(fd, &buffer, 1) == -1 && errno == EINTR) {
    // retry
  }
}

static VALUE finish(void* arg) {
  int fd = *(int*)arg;

  // Wait 1 second
  struct timespec remaining = { .tv_sec = 1, .tv_nsec = 0 };
  while (nanosleep(&remaining, &remaining) == -1 && errno == EINTR) {
    // Sleep the remaining amount
  }

  char buffer = 'D';
  while (write(fd, &buffer, 1) == -1 && errno == EINTR) {
    // retry
  }
  return Qtrue;
}

static VALUE thread_spec_rb_thread_call_without_gvl_unblock_custom_function(VALUE self) {
  int fds[2];
  if (pipe(fds) == -1) {
    rb_raise(rb_eRuntimeError, "could not create pipe");
  }

  VALUE thread = rb_funcall(rb_block_proc(), rb_intern("call"), 1, INT2FIX(fds[1]));

  rb_thread_call_without_gvl(block, &fds[0], unblock, &fds[1]);

  return rb_funcall(thread, rb_intern("join"), 0);
}

void Init_truffleruby_thread_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubyThreadSpecs", rb_cObject);
  rb_define_method(cls, "rb_thread_call_without_gvl_native_function", thread_spec_rb_thread_call_without_gvl_native_function, 0);
  rb_define_method(cls, "rb_thread_call_without_gvl_unblock_signal", thread_spec_rb_thread_call_without_gvl_unblock_signal, 0);
  rb_define_method(cls, "rb_thread_call_without_gvl_unblock_custom_function", thread_spec_rb_thread_call_without_gvl_unblock_custom_function, 0);
}

#ifdef __cplusplus
}
#endif
