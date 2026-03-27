/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include "org_truffleruby_signal_LibRubySignal.h"
#include <errno.h>
#include <locale.h>
#include <pthread.h>
#include <signal.h>
#include <unistd.h>
#include <sys/syscall.h>

_Static_assert(sizeof(pthread_t) == sizeof(jlong), "Expected sizeof(pthread_t) == sizeof(jlong)");

static void write_to_stderr(const char *msg, ssize_t len) {
  while (len > 0) {
    ssize_t n = write(STDERR_FILENO, msg, len);
    if (n > 0) {
      msg += n;
      len -= n;
    } else if (n == -1 && errno == EINTR) {
      continue;
    } else {
      break;
    }
  }
}

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_warnStackOverflowError(JNIEnv *env, jclass clazz) {
  static const char message[] = "[ruby] WARNING StackOverflowError\n";
  write_to_stderr(message, sizeof(message) - 1);
}

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_warnOutOfMemoryError(JNIEnv *env, jclass clazz) {
  static const char message[] = "[ruby] WARNING OutOfMemoryError\n";
  write_to_stderr(message, sizeof(message) - 1);
}

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_setupLocale(JNIEnv *env, jclass clazz) {
  setlocale(LC_ALL, "C");
  setlocale(LC_CTYPE, "");
}

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_setupLocaleOnlyCTYPE(JNIEnv *env, jclass clazz) {
  setlocale(LC_CTYPE, "");
}

static void empty_handler(int sig) {
}

JNIEXPORT jint JNICALL Java_org_truffleruby_signal_LibRubySignal_setupSIGVTALRMEmptySignalHandler(JNIEnv *env, jclass clazz) {
  struct sigaction action = {
    .sa_flags = 0, /* flags = 0 is intended as we want no SA_RESTART so we can interrupt blocking syscalls */
    .sa_handler = empty_handler,
  };
  return sigaction(SIGVTALRM, &action, NULL);
}

JNIEXPORT jlong JNICALL Java_org_truffleruby_signal_LibRubySignal_threadID(JNIEnv *env, jclass clazz) {
  pthread_t pthread_id = pthread_self();
  return (jlong) pthread_id;
}

JNIEXPORT jint JNICALL Java_org_truffleruby_signal_LibRubySignal_sendSIGVTALRMToThread(JNIEnv *env, jclass clazz, jlong threadID) {
  pthread_t pthread_id = (pthread_t) threadID;
  return pthread_kill(pthread_id, SIGVTALRM);
}

JNIEXPORT jlong JNICALL Java_org_truffleruby_signal_LibRubySignal_getNativeThreadID(JNIEnv *env, jclass clazz) {
#ifdef __APPLE__
  uint64_t native_id;
  pthread_threadid_np(NULL, &native_id);
#elif defined(__linux__)
  pid_t native_id = (pid_t) syscall(SYS_gettid);
#endif
  return (jlong) native_id;
}

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_restoreSystemHandlerAndRaise(JNIEnv *env, jclass clazz, jint signo) {
  signal(signo, SIG_DFL);
  raise(signo);
}

// Declaration copied from lib/cext/include/ruby/internal/intern/thread.h
typedef void rb_unblock_function_t(void *);

JNIEXPORT void JNICALL Java_org_truffleruby_signal_LibRubySignal_executeUnblockFunction(JNIEnv *env, jclass clazz, jlong function, jlong argument) {
  rb_unblock_function_t* unblock_function = (rb_unblock_function_t*) function;
  void* arg = (void*) argument;
  unblock_function(arg);
}
