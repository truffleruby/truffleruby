# AlmaLinux has 10 years LTS, Debian only 5 years
# We pick the oldest still-supported release, see https://endoflife.date/almalinux
FROM almalinux:8

# Print the glibc version
RUN ldd --version

# See workflow.md#requirements
RUN dnf install -y git wget ruby python3.12 cmake perl-core make gcc gcc-c++ ca-certificates zlib-devel

RUN useradd -ms /bin/bash test
RUN mkdir -p /truffleruby-ws /release
RUN chown test /truffleruby-ws /release
USER test
WORKDIR /truffleruby-ws

# Use ARG here to re-clone when changing the commit as it might be a new commit just pushed
ARG GIT_COMMIT=required

# Workaround mx bug creating an empty ~/.mx/cache/NINJA_SYNTAX_.../ninja-syntax.extracted/ninja_syntax-1.7.2/
# and then not extracting anything into it: https://github.com/graalvm/mx/pull/295
# Use a single command and remove large directories after to avoid making the image too big
RUN set -eux ;\
    git clone https://github.com/truffleruby/truffleruby.git ;\
    cd /truffleruby-ws/truffleruby ;\
    git checkout $GIT_COMMIT ;\
    export PATH="/truffleruby-ws/truffleruby/bin:$PATH" ;\
    jt mx path --download NINJA_SYNTAX ;\
    time jt sforceimports ;\
    time jt install jvmci ;\
    time jt install graalvm ;\
    time jt build --env jvm-ee -- --dep TRUFFLERUBY_JVM_STANDALONE_RELEASE_ARCHIVE ;\
    time jt build --env native-ee -- --dep TRUFFLERUBY_NATIVE_STANDALONE_RELEASE_ARCHIVE ;\
    mv release/* /release ;\
    cd .. ;\
    rm -rf truffleruby graal mx ~/.mx
