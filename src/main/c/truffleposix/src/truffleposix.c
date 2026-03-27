/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

/*
Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
*/

/* For clock_gettime(), lstat() and strdup() on Linux */
#define _XOPEN_SOURCE 600
/* For minor()/major() on Linux */
#define _BSD_SOURCE
#define _DEFAULT_SOURCE
/* For flock() on Darwin */
#define _DARWIN_C_SOURCE 1

#include "ruby/config.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pwd.h>
#include <spawn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <utime.h>

#include <sys/file.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>

/* For minor()/major() on Linux */
#ifdef __linux__
#include <sys/sysmacros.h>
#endif

struct truffleposix_stat {
  uint64_t atime;
  uint64_t mtime;
  uint64_t ctime;
  uint64_t nlink;
  uint64_t rdev;
  uint64_t blksize;
  uint64_t blocks;
  uint64_t dev;
  uint64_t ino;
  uint64_t size;
  uint64_t mode;
  uint64_t gid;
  uint64_t uid;
  uint32_t atime_nsec;
  uint32_t mtime_nsec;
  uint32_t ctime_nsec;
};

static void copy_stat(struct stat *stat, struct truffleposix_stat* buffer);

int truffleposix_poll_single_fd(int fd, int events, int timeout_ms) {
  struct pollfd fds;

  fds.fd = fd;
  fds.events = events;

  return poll(&fds, 1, timeout_ms) >= 0 ? fds.revents : -1;
}

int truffleposix_lutimes(const char *filename, long atime_sec, int atime_nsec,
                        long mtime_sec, int mtime_nsec) {
  struct timespec timespecs[2];

  timespecs[0].tv_sec = atime_sec;
  timespecs[0].tv_nsec = atime_nsec;
  timespecs[1].tv_sec = mtime_sec;
  timespecs[1].tv_nsec = mtime_nsec;

  return utimensat(AT_FDCWD, filename, timespecs, AT_SYMLINK_NOFOLLOW);
}

int truffleposix_utimes(const char *filename, long atime_sec, int atime_nsec,
                        long mtime_sec, int mtime_nsec) {
  struct timespec timespecs[2];
  timespecs[0].tv_sec = atime_sec;
  timespecs[0].tv_nsec = atime_nsec;
  timespecs[1].tv_sec = mtime_sec;
  timespecs[1].tv_nsec = mtime_nsec;
  return utimensat(AT_FDCWD, filename, timespecs, 0);
}

#define timeval2double(timeval) \
  (((double) (timeval).tv_sec) + ((double) (timeval).tv_usec) / 1e6)

int truffleposix_getrusage(double times[4]) {
   struct rusage self, children;
   int r;
   r = getrusage(RUSAGE_SELF, &self);
   if (r == -1) {
     return -1;
   }
   r = getrusage(RUSAGE_CHILDREN, &children);
   if (r == -1) {
     return -1;
   }
   times[0] = timeval2double(self.ru_utime);
   times[1] = timeval2double(self.ru_stime);
   times[2] = timeval2double(children.ru_utime);
   times[3] = timeval2double(children.ru_stime);
   return 0;
}

// Keep in sync with truffleposix_get_user_home() below
char* truffleposix_get_current_user_home(void) {
  uid_t uid = getuid();

  struct passwd entry;
  struct passwd *result = NULL;
  int ret;

  size_t buffer_size = sysconf(_SC_GETPW_R_SIZE_MAX);
  if (buffer_size <= 0) {
    buffer_size = 16384;
  }

  char *buffer = malloc(buffer_size);
  if (buffer == NULL) {
    return NULL;
  }

retry:
  ret = getpwuid_r(uid, &entry, buffer, buffer_size, &result);
  if (result != NULL) {
    char *home = strdup(entry.pw_dir);
    free(buffer);
    return home;
  } else if (ret == ERANGE) {
    buffer_size *= 2;
    free(buffer);
    buffer = malloc(buffer_size);
    if (buffer == NULL) {
      return NULL;
    }
    goto retry;
  } else if (ret == EINTR) {
    goto retry;
  } else if (ret == EIO || ret == EMFILE || ret == ENFILE) {
    free(buffer);
    errno = ret;
    return NULL;
  } else { // result == NULL, which means not found
    // ret should be 0 in that case according to the man page, but it doesn't seem to always hold
    free(buffer);
    return strdup("");
  }
}

// Keep in sync with truffleposix_get_current_user_home() above
char* truffleposix_get_user_home(const char *name) {
  struct passwd entry;
  struct passwd *result = NULL;
  int ret;

  size_t buffer_size = sysconf(_SC_GETPW_R_SIZE_MAX);
  if (buffer_size <= 0) {
    buffer_size = 16384;
  }

  char *buffer = malloc(buffer_size);
  if (buffer == NULL) {
    return NULL;
  }

retry:
  ret = getpwnam_r(name, &entry, buffer, buffer_size, &result);
  if (result != NULL) {
    char *home = strdup(entry.pw_dir);
    free(buffer);
    return home;
  } else if (ret == ERANGE) {
    buffer_size *= 2;
    free(buffer);
    buffer = malloc(buffer_size);
    if (buffer == NULL) {
      return NULL;
    }
    goto retry;
  } else if (ret == EINTR) {
    goto retry;
  } else if (ret == EIO || ret == EMFILE || ret == ENFILE) {
    free(buffer);
    errno = ret;
    return NULL;
  } else { // result == NULL, which means not found
    // ret should be 0 in that case according to the man page, but it doesn't seem to always hold
    free(buffer);
    return strdup("");
  }
}

void truffleposix_free(void *pointer) {
  free(pointer);
}

static unsigned char dirent_type(DIR *dirp, const struct dirent *entry, int resolve_type) {
  if (resolve_type && entry->d_type == DT_UNKNOWN) {
    struct stat native_stat;
    int result = fstatat(dirfd(dirp), entry->d_name, &native_stat, AT_SYMLINK_NOFOLLOW);
    if (result == 0) {
      if (S_ISREG(native_stat.st_mode)) {
        return DT_REG;
      } else if(S_ISDIR(native_stat.st_mode)) {
        return DT_DIR;
      } else if (S_ISCHR(native_stat.st_mode)) {
        return DT_CHR;;
      } else if (S_ISBLK(native_stat.st_mode)) {
        return DT_BLK;
      } else if (S_ISFIFO(native_stat.st_mode)) {
        return DT_FIFO;
      } else if (S_ISLNK(native_stat.st_mode)) {
        return DT_LNK;
      } else if (S_ISSOCK(native_stat.st_mode)) {
        return DT_SOCK;
      }
    }
  }
  return entry->d_type;
}

int truffleposix_readdir_multiple(DIR *dirp, int buffer_size, int resolve_type, int exclude_self_and_parent, char *buffer) {
  errno = 0;
  int offset = sizeof(int); /* The start of the buffer will contain the total number of entries, so leave space for that. */
  int entcount = 0;
  while (1) {
    long pos = telldir(dirp);
    struct dirent *entry = readdir(dirp);
    if (entry) {
      if (exclude_self_and_parent) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
          continue;
        }
      }
      int name_len = strlen(entry->d_name) + 1;
      int ent_len = name_len + sizeof(int) + 1; /* Name length + null byte + int for length + int */
      int alignment = ent_len % sizeof(int);
      if (alignment > 0) {
        alignment = sizeof(int) - alignment;
      }
      ent_len += alignment;
      if ((offset + ent_len) > buffer_size) {
        /* We can't fit this directory entry into the buffer, so we
           need to seek back to our previous position, record the
           number of entries that we've written to the buffer, and
           tell the caller there are more entries to be read. */
        seekdir(dirp, pos);
        *((int *)(buffer)) = entcount;
        return 1;
      }
      entcount++;
      *((int *)(buffer + offset)) = name_len;
      offset += sizeof(int);
      memcpy(buffer + offset, entry->d_name, name_len);
      offset += name_len;
      *(buffer + offset++) = dirent_type(dirp, entry, resolve_type);
      offset += alignment;
    } else {
      /* End of stream, or an error occurred.
         Record the number of entries read, and return a zero so the
         caller knows there are no more to be read. If an error
         occurred then this will have been recorded in errno, and the
         caller will check and handle that.*/
      *((int *)(buffer)) = entcount;
      return 0;
    }
  }
}

char *truffleposix_readdir_name(DIR *dirp) {
  errno = 0;
  struct dirent *entry = readdir(dirp);
  if (entry) {
    return entry->d_name;
  } else if (errno == 0) {
    return "";
  } else {
    return NULL;
  }
}

void truffleposix_rewinddir(DIR *dirp) {
  rewinddir(dirp);
}

int truffleposix_getpriority(int which, id_t who) {
  /* getpriority() can return -1 so errno has to be cleared. */
  errno = 0;
  int r = getpriority(which, who);
  if (r == -1 && errno != 0) {
    /* getpriority() is between -20 and 19 on Linux and -20 and 20 on macOS */
    return -100 - errno;
  }
  return r;
}

pid_t truffleposix_waitpid(pid_t pid, int options, int result[4]) {
  int status = 0;
  pid_t r = waitpid(pid, &status, options);
  if (r <= 0) {
    return r;
  }

  int exitcode = -1000, termsig = -1000, stopsig = -1000;
  if (WIFEXITED(status)) {
    exitcode = WEXITSTATUS(status);
  } else if (WIFSIGNALED(status)) {
    termsig = WTERMSIG(status);
  } else if (WIFSTOPPED(status)) {
    stopsig = WSTOPSIG(status);
  }

  result[0] = exitcode;
  result[1] = termsig;
  result[2] = stopsig;
  result[3] = status;
  return r;
}

int truffleposix_stat(const char *path, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = stat(path, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

mode_t truffleposix_stat_mode(const char *path) {
  struct stat native_stat;
  int result = stat(path, &native_stat);
  if (result == 0) {
    return native_stat.st_mode;
  }
  return 0;
}

int64_t truffleposix_stat_size(const char *path) {
  struct stat native_stat;
  int result = stat(path, &native_stat);
  if (result == 0) {
    return native_stat.st_size;
  }
  return result;
}

int truffleposix_fstat(int fd, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = fstat(fd, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

mode_t truffleposix_fstat_mode(int fd) {
  struct stat native_stat;
  int result = fstat(fd, &native_stat);
  if (result == 0) {
    return native_stat.st_mode;
  }
  return 0;
}

int64_t truffleposix_fstat_size(int fd) {
  struct stat native_stat;
  int result = fstat(fd, &native_stat);
  if (result == 0) {
    return native_stat.st_size;
  }
  return result;
}

int truffleposix_fstatat(int dirfd, char *path, struct truffleposix_stat *buffer, int flags) {
  struct stat native_stat;
  int result = fstatat(dirfd, path, &native_stat, flags);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

mode_t truffleposix_fstatat_mode(int dirfd, char *path, int flags) {
  struct stat native_stat;
  int result = fstatat(dirfd, path, &native_stat, flags);
  if (result == 0) {
    return native_stat.st_mode;
  }
  return 0;
}

int64_t truffleposix_fstatat_size(int dirfd, char *path, int flags) {
  struct stat native_stat;
  int result = fstatat(dirfd, path, &native_stat, flags);
  if (result == 0) {
    return native_stat.st_size;
  }
  return result;
}

int truffleposix_lstat(const char *path, struct truffleposix_stat *buffer) {
  struct stat native_stat;
  int result = lstat(path, &native_stat);
  if (result == 0) {
    copy_stat(&native_stat, buffer);
  }
  return result;
}

mode_t truffleposix_lstat_mode(const char *path) {
  struct stat native_stat;
  int result = lstat(path, &native_stat);
  if (result == 0) {
    return native_stat.st_mode;
  }
  return 0;
}

unsigned int truffleposix_major(dev_t dev) {
  return major(dev);
}

unsigned int truffleposix_minor(dev_t dev) {
  return minor(dev);
}

static void copy_stat(struct stat *native_stat, struct truffleposix_stat* buffer) {
  buffer->atime   = native_stat->st_atime;
  buffer->mtime   = native_stat->st_mtime;
  buffer->ctime   = native_stat->st_ctime;
  buffer->nlink   = native_stat->st_nlink;
  buffer->rdev    = native_stat->st_rdev;
  buffer->blksize = native_stat->st_blksize;
  buffer->blocks  = native_stat->st_blocks;
  buffer->dev     = native_stat->st_dev;
  buffer->ino     = native_stat->st_ino;
  buffer->size    = native_stat->st_size;
  buffer->mode    = native_stat->st_mode;
  buffer->gid     = native_stat->st_gid;
  buffer->uid     = native_stat->st_uid;

#if defined(HAVE_STRUCT_STAT_ST_ATIM)
  buffer->atime_nsec = native_stat->st_atim.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_ATIMESPEC)
  buffer->atime_nsec = native_stat->st_atimespec.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_ATIMENSEC)
  buffer->atime_nsec = (long)native_stat->st_atimensec;
#else
  buffer->atime_nsec = 0;
#endif
#if defined(HAVE_STRUCT_STAT_ST_MTIM)
  buffer->mtime_nsec = native_stat->st_mtim.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_MTIMESPEC)
  buffer->mtime_nsec = native_stat->st_mtimespec.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_MTIMENSEC)
  buffer->mtime_nsec = (long)native_stat->st_mtimensec;
#else
  buffer->mtime_nsec = 0;
#endif
#if defined(HAVE_STRUCT_STAT_ST_CTIM)
  buffer->ctime_nsec = native_stat->st_ctim.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_CTIMESPEC)
  buffer->ctime_nsec = native_stat->st_ctimespec.tv_nsec;
#elif defined(HAVE_STRUCT_STAT_ST_CTIMENSEC)
  buffer->ctime_nsec = (long)native_stat->st_ctimensec;
#else
  buffer->ctime_nsec = 0;
#endif
}

int64_t truffleposix_clock_gettime(int clock) {
  struct timespec timespec;
  int ret = clock_gettime((clockid_t) clock, &timespec);
  if (ret != 0) {
    return 0;
  }
  return ((int64_t) timespec.tv_sec * 1000000000) + (int64_t) timespec.tv_nsec;
}

int64_t truffleposix_clock_getres(int clock) {
  struct timespec timespec;
  int ret = clock_getres((clockid_t) clock, &timespec);
  if (ret != 0) {
    return 0;
  }
  return ((int64_t) timespec.tv_sec * 1000000000) + (int64_t) timespec.tv_nsec;
}

#define CHECK(call, label) if ((error = call) != 0) { perror(#call); goto label; }

pid_t truffleposix_posix_spawnp(const char *command, char *const argv[], char *const envp[],
                                int nredirects, int* redirects, int pgroup, int nfds_to_close, int* fds_to_close) {
  int ret = -1;
  pid_t pid = -1;
  int error = 0;
  int called_posix_spawn = 0;

  /* We want to use NULL for actions and attrs if there are no special options,
   * as that is more efficient on Linux as it uses vfork() (see the man page).
   * It also avoids the extra _init/_destroy calls in such a case. */
  posix_spawn_file_actions_t *file_actions_ptr = NULL;
  posix_spawn_file_actions_t file_actions;

  if (nredirects > 0) {
    if (file_actions_ptr == NULL) {
      CHECK(posix_spawn_file_actions_init(&file_actions), end);
      file_actions_ptr = &file_actions;
    }

    for (int i = 0; i < nredirects; i += 2) {
      int from = redirects[i];
      int to = redirects[i+1];
      CHECK(posix_spawn_file_actions_adddup2(file_actions_ptr, to, from), cleanup_actions);
    }
  }

  if (nfds_to_close > 0) {
    if (file_actions_ptr == NULL) {
      CHECK(posix_spawn_file_actions_init(&file_actions), end);
      file_actions_ptr = &file_actions;
    }

    for (int i = 0; i < nfds_to_close; i++) {
      int fd = fds_to_close[i];
      CHECK(posix_spawn_file_actions_addclose(&file_actions, fd), cleanup_actions);
    }
  }

  posix_spawnattr_t *attrs_ptr = NULL;
  posix_spawnattr_t attrs;
  if (pgroup >= 0) {
    CHECK(posix_spawnattr_init(&attrs), cleanup_actions);
    attrs_ptr = &attrs;
    CHECK(posix_spawnattr_setflags(&attrs, POSIX_SPAWN_SETPGROUP), cleanup_attrs);
    CHECK(posix_spawnattr_setpgroup(&attrs, pgroup), cleanup_attrs);
  }

  ret = posix_spawnp(&pid, command, file_actions_ptr, attrs_ptr, argv, envp);
  called_posix_spawn = 1;

cleanup_attrs:
  if (attrs_ptr) {
    posix_spawnattr_destroy(attrs_ptr);
    attrs_ptr = NULL;
  }
cleanup_actions:
  if (file_actions_ptr) {
    posix_spawn_file_actions_destroy(file_actions_ptr);
    file_actions_ptr = NULL;
  }
end:
  if (!called_posix_spawn) {
    return -error;
  }
  if (ret == 0) {
    return pid;
  } else {
    return -ret;
  }
}
