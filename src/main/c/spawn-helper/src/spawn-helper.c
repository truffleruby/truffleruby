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

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>

/* Performs the setup that posix_spawn() cannot do between fork() and exec(), and then execs the given command.
 * The mode is either "ctty", to start a new session with stdin as its controlling terminal (for PTY.spawn), or "-". */
int main(int argc, char* argv[]) {
    if (argc < 5) {
        fprintf(stderr, "Usage: %s (ctty|-) working_directory executable argv0 args...\n", argv[0]);
        return EXIT_FAILURE;
    }

    char* mode = argv[1];
    char* working_directory = argv[2];
    char* executable = argv[3];
    argv += 4;

    if (strcmp(mode, "ctty") == 0) {
        if (setsid() == -1) {
            perror("setsid");
            return EXIT_FAILURE;
        }
        if (ioctl(STDIN_FILENO, TIOCSCTTY, 0) == -1) {
            perror("ioctl(TIOCSCTTY)");
            return EXIT_FAILURE;
        }
    }

    int r = chdir(working_directory);
    if (r != 0) {
        perror("chdir");
        fprintf(stderr, "working_directory=%s\n", working_directory);
        return EXIT_FAILURE;
    }

    execvp(executable, argv);

    /* execvp only returns on failure */
    perror("execvp");
    fprintf(stderr, "executable=%s\n", executable);
    return EXIT_FAILURE;
}
