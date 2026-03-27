/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>
#include <fcntl.h>
#include <errno.h>
#include <ruby/thread.h>
#include <time.h>

// For howmany()
#ifdef HAVE_SYS_PARAM_H
#include <sys/param.h>
#endif

// File descriptors, rb_fd_*

// Assumption for rb_fd_ implementations below
#if !(defined(NFDBITS) && defined(HAVE_RB_FD_INIT))
#error "expected NFDBITS and HAVE_RB_FD_INIT to be defined"
#endif

void rb_fd_init(rb_fdset_t *fds) {
  fds->maxfd = 0;
  // TruffleRuby: use calloc() instead of ALLOC+FD_ZERO, because FD_ZERO is problematic on some platforms (GR-42420)
  fds->fdset = RB_ZALLOC(fd_set);
}

void rb_fd_term(rb_fdset_t *fds) {
  if (fds->fdset) {
    xfree(fds->fdset);
  }
  fds->maxfd = 0;
  fds->fdset = NULL;
}

void rb_fd_zero(rb_fdset_t *fds) {
  if (fds->fdset) {
    MEMZERO(fds->fdset, fd_mask, howmany(fds->maxfd, NFDBITS));
  }
}

static void rb_fd_resize(int n, rb_fdset_t *fds) {
  size_t m = howmany(n + 1, NFDBITS) * sizeof(fd_mask);
  size_t o = howmany(fds->maxfd, NFDBITS) * sizeof(fd_mask);

  if (m < sizeof(fd_set)) {
    m = sizeof(fd_set);
  }
  if (o < sizeof(fd_set)) {
    o = sizeof(fd_set);
  }

  if (m > o) {
    fds->fdset = xrealloc(fds->fdset, m);
    memset((char *)fds->fdset + o, 0, m - o);
  }
  if (n >= fds->maxfd) {
    fds->maxfd = n + 1;
  }
}

void rb_fd_set(int n, rb_fdset_t *fds) {
  rb_fd_resize(n, fds);
  FD_SET(n, fds->fdset);
}

void rb_fd_clr(int n, rb_fdset_t *fds) {
  if (n >= fds->maxfd) {
    return;
  }
  FD_CLR(n, fds->fdset);
}

int rb_fd_isset(int n, const rb_fdset_t *fds) {
  if (n >= fds->maxfd) {
    return 0;
  }
  return FD_ISSET(n, fds->fdset) != 0;
}

void rb_fd_copy(rb_fdset_t *dst, const fd_set *src, int max) {
  size_t size = howmany(max, NFDBITS) * sizeof(fd_mask);

  if (size < sizeof(fd_set)) {
    size = sizeof(fd_set);
  }
  dst->maxfd = max;
  dst->fdset = xrealloc(dst->fdset, size);
  memcpy(dst->fdset, src, size);
}

void rb_fd_dup(rb_fdset_t *dst, const rb_fdset_t *src) {
  size_t size = howmany(rb_fd_max(src), NFDBITS) * sizeof(fd_mask);

  if (size < sizeof(fd_set)) {
    size = sizeof(fd_set);
  }
  dst->maxfd = src->maxfd;
  dst->fdset = xrealloc(dst->fdset, size);
  memcpy(dst->fdset, src->fdset, size);
}

void rb_fd_init_copy(rb_fdset_t *dst, rb_fdset_t *src) {
  size_t size = howmany(rb_fd_max(src), NFDBITS) * sizeof(fd_mask);

  if (size < sizeof(fd_set)) {
    size = sizeof(fd_set);
  }
  dst->maxfd = src->maxfd;
  dst->fdset = xmalloc(size);
  memcpy(dst->fdset, src->fdset, size);
}

// These functions are based on https://www.gnu.org/software/libc/manual/html_node/Calculating-Elapsed-Time.html
// See also https://stackoverflow.com/questions/15846762/timeval-subtract-explanation
static bool timespec_subtract(struct timespec *result, struct timespec x, struct timespec y) {
  /* Perform the carry for the later subtraction by updating y. */
  if (x.tv_nsec < y.tv_nsec) {
    long nsec = (y.tv_nsec - x.tv_nsec) / 1000000000 + 1;
    y.tv_nsec -= 1000000000 * nsec;
    y.tv_sec += nsec;
  }
  if (x.tv_nsec - y.tv_nsec > 1000000000) {
    long nsec = (x.tv_nsec - y.tv_nsec) / 1000000000;
    y.tv_nsec += 1000000000 * nsec;
    y.tv_sec -= nsec;
  }

  /* Compute the time remaining to wait.
     tv_nsec is certainly positive. */
  result->tv_sec = x.tv_sec - y.tv_sec;
  result->tv_nsec = x.tv_nsec - y.tv_nsec;

  /* Return 1 if result is negative. */
  return x.tv_sec < y.tv_sec;
}

static bool timeval_subtract(struct timeval *result, struct timeval x, struct timeval y) {
  /* Perform the carry for the later subtraction by updating y. */
  if (x.tv_usec < y.tv_usec) {
    long usec = (y.tv_usec - x.tv_usec) / 1000000 + 1;
    y.tv_usec -= 1000000 * usec;
    y.tv_sec += usec;
  }
  if (x.tv_usec - y.tv_usec > 1000000) {
    long usec = (x.tv_usec - y.tv_usec) / 1000000;
    y.tv_usec += 1000000 * usec;
    y.tv_sec -= usec;
  }

  /* Compute the time remaining to wait.
     tv_usec is certainly positive. */
  result->tv_sec = x.tv_sec - y.tv_sec;
  result->tv_usec = x.tv_usec - y.tv_usec;

  /* Return 1 if result is negative. */
  return x.tv_sec < y.tv_sec;
}

static int should_retry(int result) {
  return (result < 0) && (errno == EINTR);
}

int rb_fd_select(int n, rb_fdset_t *readfds, rb_fdset_t *writefds, rb_fdset_t *exceptfds, struct timeval *timeout) {
  fd_set *r = NULL, *w = NULL, *e = NULL;
  if (readfds) {
    rb_fd_resize(n - 1, readfds);
    r = rb_fd_ptr(readfds);
  }
  if (writefds) {
    rb_fd_resize(n - 1, writefds);
    w = rb_fd_ptr(writefds);
  }
  if (exceptfds) {
    rb_fd_resize(n - 1, exceptfds);
    e = rb_fd_ptr(exceptfds);
  }
  return select(n, r, w, e, timeout);
}


// NOTE: MRI's version has more fields
struct select_set {
  int max;
  rb_fdset_t *rset;
  rb_fdset_t *wset;
  rb_fdset_t *eset;
  rb_fdset_t orig_rset;
  rb_fdset_t orig_wset;
  rb_fdset_t orig_eset;
  struct timeval *timeout;
  struct timeval *orig_timeout;
};

static inline void restore_fds(rb_fdset_t *dst, rb_fdset_t *src) {
  if (dst) {
    rb_fd_dup(dst, src);
  }
}

static bool update_timeout(struct timeval *timeout, struct timeval *orig_timeout, struct timespec *starttime) {
  struct timespec currenttime;
  struct timespec difftime;
  struct timeval difftimeout;
  bool timeleft = true;
  if (timeout) {
    clock_gettime(CLOCK_MONOTONIC, &currenttime);
    timespec_subtract(&difftime, currenttime, *starttime);
    difftimeout.tv_sec = difftime.tv_sec;
    difftimeout.tv_usec = difftime.tv_nsec / 1000;
    timeleft = !timeval_subtract(timeout, *orig_timeout, difftimeout);
  }

  return timeleft;
}

static void* rb_thread_fd_select_blocking(void *data) {
  struct select_set *set = (struct select_set*)data;
  struct timespec starttime;

  if (set->timeout) {
    clock_gettime(CLOCK_MONOTONIC, &starttime);
  }

  int result = 0;
  bool timeleft = true;
  do {
    restore_fds(set->rset, &set->orig_rset);
    restore_fds(set->wset, &set->orig_wset);
    restore_fds(set->eset, &set->orig_eset);
    timeleft = update_timeout(set->timeout, set->orig_timeout, &starttime);
    if (!timeleft) {
      break;
    }
    result = rb_fd_select(set->max, set->rset, set->wset, set->eset, set->timeout);
  } while (should_retry(result));
  return (void*)(long)result;
}

static void* rb_thread_fd_select_internal(void *sets) {
  return rb_thread_call_without_gvl(rb_thread_fd_select_blocking, sets, RUBY_UBF_IO, 0);
}

static void rb_thread_fd_select_set_free(struct select_set *sets) {
  if (sets->rset) {
    rb_fd_term(&sets->orig_rset);
  }
  if (sets->wset) {
    rb_fd_term(&sets->orig_wset);
  }
  if (sets->eset) {
    rb_fd_term(&sets->orig_eset);
  }
}

static void fd_init_copy(rb_fdset_t *dst, int max, rb_fdset_t *src) {
  if (src) {
    rb_fd_resize(max - 1, src);
    rb_fd_init_copy(dst, src);
  } else {
    dst->fdset = NULL;
    dst->maxfd = 0;
  }
}

int rb_thread_fd_select(int max, rb_fdset_t *read, rb_fdset_t *write, rb_fdset_t *except, struct timeval *timeout) {
  // NOTE: MRI has more logic in here
  struct select_set set;
  struct timeval orig_timeval;

  set.max = max;
  set.rset = read;
  set.wset = write;
  set.eset = except;
  set.timeout = timeout;

  fd_init_copy(&set.orig_rset, max, set.rset);
  fd_init_copy(&set.orig_wset, max, set.wset);
  fd_init_copy(&set.orig_eset, max, set.eset);

  if (timeout) {
    orig_timeval = *timeout;
    set.orig_timeout = &orig_timeval;
  } else {
    set.orig_timeout = NULL;
  }

  void* result = rb_ensure(rb_thread_fd_select_internal, (VALUE)&set, rb_thread_fd_select_set_free, (VALUE)&set);
  return (int)(long)result;
}
