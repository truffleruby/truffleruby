# AlmaLinux has 10 years LTS, Debian only 5 years
# We pick the oldest still-supported release, see https://endoflife.date/almalinux
FROM almalinux:8

# Print the glibc version
RUN ldd --version

# See workflow.md#requirements
RUN dnf install -y git wget ruby python3.12 cmake perl-core maven make gcc gcc-c++ ca-certificates zlib-devel

RUN useradd -ms /bin/bash test
RUN mkdir -p /truffleruby-ws /release
RUN chown test /truffleruby-ws /release
USER test
WORKDIR /truffleruby-ws

COPY release/* /release

# Use ARG here to re-clone when changing the commit as it might be a new commit just pushed
ARG GIT_COMMIT=required
ARG JT_COMMAND=required

# Use a single command and remove large directories after to avoid making the image too big
# Also all mx commands must be in the same layer to avoid https://github.com/graalvm/mx/pull/295
RUN set -eux ;\
    git clone https://github.com/truffleruby/truffleruby.git ;\
    cd /truffleruby-ws/truffleruby ;\
    git checkout $GIT_COMMIT ;\
    ln -s /release release ;\
    export PATH="/truffleruby-ws/truffleruby/bin:$PATH" ;\
    time jt sforceimports ;\
    time jt install jvmci ;\
    time jt $JT_COMMAND ;\
    cd .. ;\
    rm -rf truffleruby graal mx ~/.mx /release/let_globbing_work
