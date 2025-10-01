# Testing TruffleRuby in CI

## Testing TruffleRuby in GitHub Actions

In GitHub Actions, you can easily setup TruffleRuby with [ruby/setup-ruby](https://github.com/ruby/setup-ruby):

```yaml
name: My workflow
on: [push]
jobs:
  test:
    strategy:
      fail-fast: false
      matrix:
        ruby: [ '3.2', truffleruby, truffleruby-head ]
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: ruby/setup-ruby@v1
      with:
        ruby-version: ${{ matrix.ruby }}
    - run: ruby -v
```

See the [README](https://github.com/marketplace/actions/setup-ruby-jruby-and-truffleruby) of that action for more documentation.

## Testing TruffleRuby in TravisCI

TruffleRuby is now integrated in TravisCI.
Just add `truffleruby` in the build matrix, such as:

```yaml
language: ruby
rvm:
  - 2.6.1
  - truffleruby
  - truffleruby-head
```

See [the TravisCI documentation](https://docs.travis-ci.com/user/languages/ruby#truffleruby) for details.
Please [report](https://github.com/truffleruby/truffleruby/issues) any issue you might find.

## Testing TruffleRuby in CI

If you use another continuous integration system, simply follow these instructions to run TruffleRuby in CI:
download and extract the archive, add it to `PATH`, and run the post-install script.

## Latest Release

Set `TRUFFLERUBY_VERSION` to the latest TruffleRuby version from [GitHub releases](https://github.com/truffleruby/truffleruby/releases/latest).

```bash
export TRUFFLERUBY_VERSION=<desired_version>
curl -L https://github.com/truffleruby/truffleruby/releases/download/graal-$TRUFFLERUBY_VERSION/truffleruby-$TRUFFLERUBY_VERSION-linux-amd64.tar.gz | tar xz
export PATH="$PWD/truffleruby-$TRUFFLERUBY_VERSION-linux-amd64/bin:$PATH"
$PWD/truffleruby-$TRUFFLERUBY_VERSION-linux-amd64/lib/truffle/post_install_hook.sh
ruby -v
```

## Latest Dev Build

Here are the instructions for manually installing the latest dev build:

```bash
curl -L https://github.com/ruby/truffleruby-dev-builder/releases/latest/download/truffleruby-head-ubuntu-18.04.tar.gz | tar xz
export PATH="$PWD/truffleruby-head/bin:$PATH"
$PWD/truffleruby-head/lib/truffle/post_install_hook.sh
ruby -v
```

## RubyGems Configuration

Note that you also need to ensure `GEM_HOME` and `GEM_PATH` are not set, so TruffleRuby uses the correct `GEM_HOME` and `GEM_PATH`.
See [Using TruffleRuby without a Ruby manager](ruby-managers.md#using-truffleruby-without-a-ruby-manager) for details.
