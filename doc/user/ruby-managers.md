# Ruby Managers and Installers

TruffleRuby is supported by all major Ruby installers.

## `rvm`

Upgrade `rvm` to let `rvm` know about the latest TruffleRuby release:

```bash
rvm get head
```

Install the latest TruffleRuby Native Standalone release with:

```bash
rvm install truffleruby
```

You can also install the latest Native Standalone dev build of TruffleRuby with:

```bash
rvm install truffleruby-head
```

## `ruby-build` and `rbenv`

We assume you already have [`ruby-build`](https://github.com/rbenv/ruby-build) installed as a plugin for [`rbenv`](https://github.com/rbenv/rbenv).

First, you need to upgrade `ruby-build` to get the latest TruffleRuby definition.
See [`ruby-build`'s instructions for upgrading](https://github.com/rbenv/ruby-build#upgrading).

On macOS, if `ruby-build` is installed via Homebrew and you do not see the [latest TruffleRuby release](https://github.com/truffleruby/truffleruby/releases/latest), you might need to install the HEAD version of `ruby-build` with:

```bash
brew reinstall --HEAD ruby-build
```

Check the latest available version of TruffleRuby with:

```bash
rbenv install --list
```

Then install the latest TruffleRuby Native Standalone release with:

```bash
rbenv install truffleruby-[LATEST_VERSION]
```

You can also install the latest Native Standalone dev build of TruffleRuby with:

```bash
rbenv install truffleruby-dev
```

You can also install the TruffleRuby JVM Standalone with:

```bash
rbenv install truffleruby+graalvm-[LATEST_VERSION] OR truffleruby+graalvm-dev
```

## `asdf` (with `asdf-ruby` plugin)

See https://github.com/asdf-vm/asdf-ruby for installing and updating `asdf-ruby`.

You can install a TruffleRuby Native Standalone release or dev build with:

```bash
asdf install ruby truffleruby-VERSION OR truffleruby-dev
```

You can also install the TruffleRuby JVM Standalone with:

```bash
asdf install ruby truffleruby+graalvm-VERSION OR truffleruby+graalvm-dev
```

## `mise`

Mise includes a Ruby plugin. See https://mise.jdx.dev/lang/ruby.html for details.

You can install a TruffleRuby Native Standalone release or dev build with:

```bash
mise install ruby@truffleruby-VERSION
mise install ruby@truffleruby-dev    # latest dev version
```

You can also install the TruffleRuby JVM Standalone with:

```
mise install ruby@truffleruby+graalvm-VERSION
mise install ruby@truffleruby+graalvm-dev    # latest dev version
```

## `ruby-install` and `chruby`

See https://github.com/postmodern/ruby-install#install for installing and updating `ruby-install`.

First, ensure you have the latest `ruby-install` release.
Check your version with:

```bash
ruby-install --version
```

And compare to the [latest tag](https://github.com/postmodern/ruby-install/tags).
If it is older, you should update to the latest `ruby-install` (e.g. 0.8.4 is necessary for aarch64 support).
Follow the [installation instructions](https://github.com/postmodern/ruby-install#install), since the steps for upgrading `ruby-install` are the same as the steps for installing it.

Then install the latest TruffleRuby Native Standalone release with:

```bash
ruby-install --update
ruby-install truffleruby
```

You can also install the TruffleRuby JVM Standalone with:

```bash
ruby-install truffleruby-graalvm
```

`ruby-install` does not support installing dev builds.
Please use `ruby-build` (which also works with `chruby`) if you want to install dev builds:

```bash
ruby-build -d truffleruby-dev ~/.rubies
OR
ruby-build -d truffleruby+graalvm-dev ~/.rubies
```

There are also instructions on the [chruby wiki](https://github.com/postmodern/chruby/wiki/TruffleRuby) if you prefer to install TruffleRuby manually.

## Using TruffleRuby without a Ruby Manager

If you are using a Ruby manager like `rvm`, `rbenv`, or `chruby` and wish not to add TruffleRuby to one of them, make sure that the manager does not set environment variables `GEM_HOME` and `GEM_PATH`.
The variables are picked up by TruffleRuby (as any other Ruby implementation would do) causing TruffleRuby to pickup the wrong gem home instead of its own.

It can be fixed for the current terminal by clearing the environment with one of the following commands:

```bash
rbenv system
chruby system
rvm use system
# Or manually:
unset GEM_HOME GEM_PATH
```
