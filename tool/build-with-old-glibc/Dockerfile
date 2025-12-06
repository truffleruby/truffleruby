# AlmaLinux has 10 years LTS, Debian only 5 years
# We pick the oldest still-supported release, see https://endoflife.date/almalinux
FROM almalinux:8

# Print the glibc version
RUN ldd --version

# See workflow.md#requirements
RUN dnf install -y git wget ruby python3.12 cmake perl-core make gcc gcc-c++ ca-certificates zlib-devel glibc-langpack-en
ENV LANG=en_US.UTF-8

RUN mkdir -p /truffleruby-ws
RUN useradd -ms /bin/bash test
RUN chown test /truffleruby-ws
USER test
WORKDIR /truffleruby-ws

# Use ARG here to re-clone when changing the commit as it might be a new commit just pushed
ARG GIT_COMMIT=required
RUN git clone https://github.com/truffleruby/truffleruby.git
WORKDIR /truffleruby-ws/truffleruby
RUN git checkout $GIT_COMMIT

ENV PATH="/truffleruby-ws/truffleruby/bin:$PATH"
RUN jt sforceimports
RUN jt install jvmci
RUN jt install graalvm

# Workaround mx bug creating an empty ~/.mx/cache/NINJA_SYNTAX_.../ninja-syntax.extracted/ninja_syntax-1.7.2/
# and then not extracting anything into it: https://github.com/graalvm/mx/pull/295
RUN rm -rf ~/.mx/cache/NINJA_SYNTAX*

RUN jt build --env jvm-ee -- --dep TRUFFLERUBY_JVM_STANDALONE_RELEASE_ARCHIVE
RUN jt build --env native-ee -- --dep TRUFFLERUBY_NATIVE_STANDALONE_RELEASE_ARCHIVE

# Run here just to be sure but actually more interesting to run on other Linux distributions
RUN jt -u jvm-ee test fast
RUN jt -u native-ee test fast
