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

/* Exit statuses, like a shell and env(1) */
#define EXIT_SETUP_FAILED 125
#define EXIT_CANNOT_EXECUTE 126
#define EXIT_NOT_FOUND 127

static void setup_failed(const char* what) {
    perror(what);
    exit(EXIT_SETUP_FAILED);
}

/* Performs the setup that posix_spawn() cannot do between fork() and exec(), and then execs the given command.
 * tty is either the path of a terminal, to start a new session with it as controlling terminal (for PTY.spawn),
 * or "-".
 * posix_spawn() already succeeded when this runs, so a failure here cannot be reported as an exception by spawn:
 * it is printed on stderr and seen as the exit status of the process. */
int main(int argc, char* argv[]) {
    if (argc < 5) {
        fprintf(stderr, "Usage: %s (tty|-) working_directory executable argv0 args...\n", argv[0]);
        return EXIT_SETUP_FAILED;
    }

    char* tty = argv[1];
    char* working_directory = argv[2];
    char* executable = argv[3];
    argv += 4;

    if (strcmp(tty, "-") != 0) {
        if (setsid() == -1) {
            setup_failed("setsid");
        }
        int tty_fd = open(tty, O_RDWR | O_NOCTTY);
        if (tty_fd == -1) {
            setup_failed(tty);
        }
        if (ioctl(tty_fd, TIOCSCTTY, 0) == -1) {
            setup_failed("ioctl(TIOCSCTTY)");
        }
        close(tty_fd);
        /* Like CRuby, drop privileges once the terminal is acquired */
        if (seteuid(getuid()) == -1) {
            setup_failed("seteuid");
        }
    }

    if (chdir(working_directory) == -1) {
        setup_failed(working_directory);
    }

    execvp(executable, argv);

    /* execvp only returns on failure */
    int status = errno == ENOENT ? EXIT_NOT_FOUND : EXIT_CANNOT_EXECUTE;
    perror(executable);
    return status;
}
