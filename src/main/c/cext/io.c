/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>
#include <ruby/io.h>
#include <fcntl.h>

// IO, rb_io_*

// Ignore deprecated fields on rb_io_t, we still need to set them while the struct fields are exposed.
RBIMPL_WARNING_PUSH()
RBIMPL_WARNING_IGNORED(-Wdeprecated-declarations)

typedef struct {
  struct RFile rfile;
  rb_io_t io_struct;
} RFile_and_rb_io_t;

static inline int rb_tr_io_raw_descriptor(VALUE io) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_io_descriptor", io));
}

int rb_io_descriptor(VALUE io) {
  int fd = rb_tr_io_raw_descriptor(io);
  if (fd < 0) {
    rb_raise(rb_eIOError, "closed stream");
  }
  return fd;
}

int rb_io_mode(VALUE io) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_io_mode", io));
}

VALUE rb_io_path(VALUE io) {
  return RUBY_CEXT_INVOKE("rb_io_path", io);
}

VALUE rb_io_open_descriptor(VALUE klass, int descriptor, int mode, VALUE path, VALUE timeout, struct rb_io_encoding *encoding) {
  VALUE internal_encoding, external_encoding, ecflags, ecopts;

  if (encoding) {
    internal_encoding = rb_enc_from_encoding(encoding->enc);
    external_encoding = rb_enc_from_encoding(encoding->enc2);
    ecflags = INT2FIX(encoding->ecflags);
    ecopts = encoding->ecopts;
  } else {
    internal_encoding = Qnil;
    external_encoding = Qnil;
    ecflags = INT2FIX(0);
    ecopts = rb_hash_new();
  }

  return RUBY_CEXT_INVOKE("rb_io_open_descriptor",
    klass,
    INT2FIX(descriptor),
    INT2FIX(mode),
    path,
    timeout,
    internal_encoding,
    external_encoding,
    ecflags,
    ecopts);
}

VALUE rb_io_closed_p(VALUE io) {
  return RUBY_INVOKE(io, "closed?");
}

static RFile_and_rb_io_t* get_RFile_and_rb_io_t(VALUE io) {
  RFile_and_rb_io_t* pointer = RUBY_CEXT_INVOKE_NO_WRAP("rb_tr_io_pointer", io);
  if (!polyglot_is_null(pointer)) {
    return pointer;
  }

  pointer = polyglot_invoke(RUBY_CEXT, "new_memory_pointer", sizeof(RFile_and_rb_io_t));
  rb_io_t* fptr = &pointer->io_struct;
  fptr->self = io;
  fptr->fd = rb_tr_io_raw_descriptor(io);
  fptr->mode = rb_io_mode(io);
  fptr->pathv = rb_io_path(io);
  VALUE write_io = rb_io_get_write_io(io);
  fptr->tied_io_for_writing = (write_io != io) ? write_io : Qfalse;

  pointer->rfile.fptr = fptr;

  polyglot_invoke(RUBY_CEXT, "rb_tr_io_attach_pointer", rb_tr_unwrap(io), pointer);

  return pointer;
}

rb_io_t* rb_tr_io_get_rb_io_t(VALUE io) {
  return &get_RFile_and_rb_io_t(io)->io_struct;
}

struct RFile* rb_tr_io_get_rfile(VALUE io) {
  return &get_RFile_and_rb_io_t(io)->rfile;
}

static inline int rb_tr_readable(int mode) {
  return mode & FMODE_READABLE;
}

static inline int rb_tr_writable(int mode) {
  return mode & FMODE_WRITABLE;
}

void rb_io_check_writable(rb_io_t *io) {
  if (!rb_tr_writable(io->mode)) {
    rb_raise(rb_eIOError, "not opened for writing");
  }
}

void rb_io_check_readable(rb_io_t *io) {
  if (!rb_tr_readable(io->mode)) {
    rb_raise(rb_eIOError, "not opened for reading");
  }
}

void rb_update_max_fd(int fd) {
}

void rb_fd_fix_cloexec(int fd) {
  fcntl(fd, F_SETFD, fcntl(fd, F_GETFD) | FD_CLOEXEC);
}

int rb_io_wait_readable(int fd) {
  if (fd < 0) {
    rb_raise(rb_eIOError, "closed stream");
  }

  switch (errno) {
    case EAGAIN:
  #if defined(EWOULDBLOCK) && EWOULDBLOCK != EAGAIN
    case EWOULDBLOCK:
  #endif
      rb_thread_wait_fd(fd);
      return true;

    default:
      return false;
  }
}

int rb_io_wait_writable(int fd) {
  if (fd < 0) {
    rb_raise(rb_eIOError, "closed stream");
  }

  switch (errno) {
    case EAGAIN:
  #if defined(EWOULDBLOCK) && EWOULDBLOCK != EAGAIN
    case EWOULDBLOCK:
  #endif
      rb_thread_fd_writable(fd);
      return true;

    default:
      return false;
  }
}

int rb_thread_wait_fd(int fd) {
  return polyglot_as_i32(polyglot_invoke(RUBY_CEXT, "rb_thread_wait_fd", fd));
}

int rb_wait_for_single_fd(int fd, int events, struct timeval *tv) {
  long tv_sec = -1;
  long tv_usec = -1;
  if (tv != NULL) {
    tv_sec = tv->tv_sec;
    tv_usec = tv->tv_usec;
  }
  return polyglot_as_i32(polyglot_invoke(RUBY_CEXT, "rb_wait_for_single_fd", fd, events, tv_sec, tv_usec));
}

// The only difference between this implementation and CRuby's one
// is that a fiber scheduler isn't supported here
VALUE rb_io_wait(VALUE io, VALUE events, VALUE timeout) {
  rb_io_t * fptr = NULL;
  RB_IO_POINTER(io, fptr);

  struct timeval tv_storage;
  struct timeval *tv = NULL;

  if (timeout != Qnil) {
    tv_storage = rb_time_interval(timeout);
    tv = &tv_storage;
  }

  int ready = rb_wait_for_single_fd(fptr->fd, RB_NUM2INT(events), tv);

  if (ready < 0) {
    rb_sys_fail(0);
  }

  // Not sure if this is necessary:
  rb_io_check_closed(fptr);

  if (ready) {
    return RB_INT2NUM(ready);
  }
  else {
    return Qfalse;
  }
}

VALUE rb_io_maybe_wait(int error, VALUE io, VALUE events, VALUE timeout) {
  // fptr->fd can be set to -1 at any time by another thread when the GVL is
  // released. Many code, e.g. `io_bufread` didn't check this correctly and
  // instead relies on `read(-1) -> -1` which causes this code path. We then
  // check here whether the IO was in fact closed. Probably it's better to
  // check that `fptr->fd != -1` before using it in syscall.
  rb_io_check_closed(RFILE(io)->fptr);

  switch (error) {
  // In old Linux, several special files under /proc and /sys don't handle
  // select properly. Thus we need avoid to call if don't use O_NONBLOCK.
  // Otherwise, we face nasty hang up. Sigh.
  // e.g. http://git.kernel.org/?p=linux/kernel/git/torvalds/linux-2.6.git;a=commit;h=31b07093c44a7a442394d44423e21d783f5523b8
  // http://git.kernel.org/?p=linux/kernel/git/torvalds/linux-2.6.git;a=commit;h=31b07093c44a7a442394d44423e21d783f5523b8
  // In EINTR case, we only need to call RUBY_VM_CHECK_INTS_BLOCKING().
  // Then rb_thread_check_ints() is enough.
  case EINTR:
#if defined(ERESTART)
    case ERESTART:
#endif
      // We might have pending interrupts since the previous syscall was interrupted:
      rb_thread_check_ints();

      // The operation was interrupted, so retry it immediately:
      return events;

  case EAGAIN:
#if EWOULDBLOCK != EAGAIN
  case EWOULDBLOCK:
#endif
    // The operation would block, so wait for the specified events:
    return rb_io_wait(io, events, timeout);

  default:
    // Non-specific error, no event is ready:
    return Qfalse;
  }
}

int rb_io_maybe_wait_readable(int error, VALUE io, VALUE timeout) {
  VALUE result = rb_io_maybe_wait(error, io, RB_INT2NUM(RUBY_IO_READABLE), timeout);

  if (RTEST(result)) {
    return RB_NUM2INT(result);
  }
  else {
    return 0;
  }
}

int rb_io_maybe_wait_writable(int error, VALUE io, VALUE timeout) {
  VALUE result = rb_io_maybe_wait(error, io, RB_INT2NUM(RUBY_IO_WRITABLE), timeout);

  if (RTEST(result)) {
    return RB_NUM2INT(result);
  }
  else {
    return 0;
  }
}

VALUE rb_io_addstr(VALUE io, VALUE str) {
  // use write instead of just #<<, it's closer to what MRI does
  // and avoids stack-overflow in zlib where #<< is defined with this method
  rb_io_write(io, str);
  return io;
}

VALUE rb_io_check_io(VALUE io) {
  return rb_check_convert_type(io, T_FILE, "IO", "to_io");
}

void rb_io_check_closed(rb_io_t *fptr) {
  if (fptr->fd < 0) {
    rb_raise(rb_eIOError, "closed stream");
  }
}

VALUE rb_io_taint_check(VALUE io) {
  rb_check_frozen(io);
  return io;
}

VALUE rb_io_close(VALUE io) {
  return RUBY_INVOKE(io, "close");
}

VALUE rb_io_gets(VALUE io) {
  return RUBY_INVOKE(io, "gets");
}

VALUE rb_io_print(int argc, const VALUE *argv, VALUE out) {
  return RUBY_CEXT_INVOKE("rb_io_print", out, rb_ary_new4(argc, argv));
}

VALUE rb_io_printf(int argc, const VALUE *argv, VALUE out) {
  return RUBY_CEXT_INVOKE("rb_io_printf", out, rb_ary_new4(argc, argv));
}

VALUE rb_io_puts(int argc, const VALUE *argv, VALUE out) {
  return RUBY_CEXT_INVOKE("rb_io_puts", out, rb_ary_new4(argc, argv));
}

VALUE rb_io_write(VALUE io, VALUE str) {
  return RUBY_INVOKE(io, "write", str);
}

VALUE rb_io_binmode(VALUE io) {
  return RUBY_INVOKE(io, "binmode");
}

VALUE rb_io_flush(VALUE io) {
  return RUBY_INVOKE(io, "flush");
}

VALUE rb_io_get_write_io(VALUE io) {
  return RUBY_CEXT_INVOKE("rb_io_get_write_io", io);
}

int rb_thread_fd_writable(int fd) {
  return polyglot_as_i32(polyglot_invoke(RUBY_CEXT, "rb_thread_fd_writable", fd));
}

int rb_cloexec_open(const char *pathname, int flags, mode_t mode) {
  int fd = open(pathname, flags, mode);
  if (fd >= 0) {
    rb_fd_fix_cloexec(fd);
  }
  return fd;
}

VALUE rb_file_open(const char *fname, const char *modestr) {
  return RUBY_INVOKE(rb_cFile, "open", rb_str_new_cstr(fname), rb_str_new_cstr(modestr));
}

VALUE rb_file_open_str(VALUE fname, const char *modestr) {
  return RUBY_INVOKE(rb_cFile, "open", fname, rb_str_new_cstr(modestr));
}

VALUE rb_get_path(VALUE object) {
  return RUBY_INVOKE(rb_cFile, "path", object);
}

int rb_io_extract_encoding_option(VALUE opt, rb_encoding **enc_p, rb_encoding **enc2_p, int *fmode_p) {
  // TODO (pitr-ch 12-Jun-2017): review, just approximate implementation
  VALUE encoding = rb_cEncoding;
  VALUE external_encoding = RUBY_INVOKE(encoding, "default_external");
  VALUE internal_encoding = RUBY_INVOKE(encoding, "default_internal");
  if (!NIL_P(external_encoding)) {
    *enc_p = rb_to_encoding(external_encoding);
  }
  if (!NIL_P(internal_encoding)) {
    *enc2_p = rb_to_encoding(internal_encoding);
  }
  return 1;
}

static int rb_fd_set_nonblock(int fd) {
  int oflags = fcntl(fd, F_GETFL);
  if (oflags == -1)
    return -1;
  if (oflags & O_NONBLOCK)
    return 0;
  oflags |= O_NONBLOCK;
  return fcntl(fd, F_SETFL, oflags);
}

void rb_io_set_nonblock(rb_io_t *fptr) {
  if (rb_fd_set_nonblock(fptr->fd) != 0) {
    rb_sys_fail("rb_io_set_nonblock failed");
  }
}

// For 'gem install curb'
FILE *rb_io_stdio_file(rb_io_t *fptr) {
  rb_tr_not_implemented("rb_io_stdio_file");
}

VALUE rb_lastline_get(void) {
  return RUBY_CEXT_INVOKE("rb_lastline_get");
}

void rb_lastline_set(VALUE str) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_lastline_set", str);
}

VALUE rb_io_getbyte(VALUE io) {
  return RUBY_INVOKE(io, "getbyte");
}

RBIMPL_WARNING_POP()
