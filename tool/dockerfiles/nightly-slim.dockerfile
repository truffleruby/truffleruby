FROM debian:stable-slim

ENV LANG=C.UTF-8
# don't create ".bundle" in all our apps
ENV GEM_HOME=/usr/local/bundle
ENV BUNDLE_SILENCE_ROOT_WARNING=1 \
    BUNDLE_APP_CONFIG="$GEM_HOME"
ENV PATH=$GEM_HOME/bin:$PATH

RUN set -eux ;\
    apt-get update ;\
    apt-get install -y --no-install-recommends \
            make gcc g++ \
            ca-certificates \
            libz-dev \
            tar \
            wget \
    ; \
    rm -rf /var/lib/apt/lists/* ;\
    case "$(uname -m)" in \
      x86_64) arch="x64" ;; \
      aarch64) arch="arm64" ;; \
    esac; \
    wget -q https://github.com/ruby/truffleruby-dev-builder/releases/latest/download/truffleruby-head-ubuntu-22.04-$arch.tar.gz ;\
    tar -xzf truffleruby-head-ubuntu-22.04-$arch.tar.gz -C /usr/local --strip-components=1 ;\
    rm truffleruby-head-ubuntu-22.04-$arch.tar.gz ;\
    /usr/local/lib/truffle/post_install_hook.sh ;\
    ruby --version ;\
    gem --version ;\
    bundle --version ;\
    mkdir -p "$GEM_HOME" && chmod 777 "$GEM_HOME"

CMD [ "irb" ]
