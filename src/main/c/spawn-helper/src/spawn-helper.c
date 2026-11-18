/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

/* For seteuid() with -std=c99 on glibc */
#define _DEFAULT_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>

/* The steps which can fail, reported to the parent process. Keep in sync with process_operations.rb. */
enum step { STEP_SETSID, STEP_OPEN_TTY, STEP_TIOCSCTTY, STEP_SETEUID, STEP_CHDIR, STEP_EXEC };

/* Reports the failed step and errno to the parent process, which raises the corresponding exception. */
static void fail(int status_fd, enum step step) {
    int message[2] = { step, errno };
    ssize_t written = write(status_fd, message, sizeof(message));
    (void) written;
    _exit(EXIT_FAILURE);
}

/* Performs the setup that posix_spawn() cannot do between fork() and exec(), and then execs the given command.
 * status_fd is the write end of a pipe to report a failure to the parent process,
 * it is closed by a successful exec so the parent process knows the command was executed.
 * tty is either the path of a terminal, to start a new session with it as controlling terminal (for PTY.spawn),
 * or "-". */
int main(int argc, char* argv[]) {
    if (argc < 6) {
        fprintf(stderr, "Usage: %s status_fd (tty|-) working_directory executable argv0 args...\n", argv[0]);
        return EXIT_FAILURE;
    }

    int status_fd = atoi(argv[1]);
    char* tty = argv[2];
    char* working_directory = argv[3];
    char* executable = argv[4];
    argv += 5;

    if (fcntl(status_fd, F_SETFD, FD_CLOEXEC) == -1) {
        perror("fcntl(status_fd, F_SETFD, FD_CLOEXEC)");
        return EXIT_FAILURE;
    }

    if (strcmp(tty, "-") != 0) {
        if (setsid() == -1) {
            fail(status_fd, STEP_SETSID);
        }
        int tty_fd = open(tty, O_RDWR | O_NOCTTY);
        if (tty_fd == -1) {
            fail(status_fd, STEP_OPEN_TTY);
        }
        if (ioctl(tty_fd, TIOCSCTTY, 0) == -1) {
            fail(status_fd, STEP_TIOCSCTTY);
        }
        close(tty_fd);
        /* Like CRuby, drop privileges once the terminal is acquired */
        if (seteuid(getuid()) == -1) {
            fail(status_fd, STEP_SETEUID);
        }
    }

    if (chdir(working_directory) == -1) {
        fail(status_fd, STEP_CHDIR);
    }

    execvp(executable, argv);

    /* execvp only returns on failure */
    fail(status_fd, STEP_EXEC);
}
