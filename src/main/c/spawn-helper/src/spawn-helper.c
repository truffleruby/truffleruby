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
#include <unistd.h>

int main(int argc, char* argv[]) {
    if (argc < 4) {
        fprintf(stderr, "Usage: %s working_directory executable argv0 args...\n", argv[0]);
        return EXIT_FAILURE;
    }

    char* working_directory = argv[1];
    char* executable = argv[2];
    argv += 3;

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
