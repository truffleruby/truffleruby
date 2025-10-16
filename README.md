![TruffleRuby logo](logo/png/truffleruby_logo_horizontal_medium_outlined.png)

TruffleRuby is the [GraalVM](http://graalvm.org/) high-performance implementation
of the [Ruby programming language](https://www.ruby-lang.org/en/).

## Getting Started

TruffleRuby comes in two distributions:

* Native Standalone: This only contains TruffleRuby in the [Native configuration](#truffleruby-runtime-configurations).
* JVM Standalone: This only contains TruffleRuby in the [JVM configuration](#truffleruby-runtime-configurations).
  This includes support for other languages such as Java, JavaScript, Python and WebAssembly.

You can install either of those:

* Via your [Ruby manager/installer](doc/user/ruby-managers.md) (RVM, rbenv, chruby, asdf, ruby-build, ruby-install).  
  We recommend trying TruffleRuby dev builds which contain the latest fixes and improvements (replace `VERSION` by `dev`).

Native Standalone:
```bash
RVM:    $ rvm install truffleruby # or truffleruby-head
rbenv:  $ rbenv install truffleruby-VERSION
asdf:   $ asdf install ruby truffleruby-VERSION
chruby: $ ruby-install truffleruby
        $ ruby-build truffleruby-VERSION ~/.rubies/truffleruby-VERSION
```
JVM Standalone:
```bash
rbenv:  $ rbenv install truffleruby+graalvm-VERSION
asdf:   $ asdf install ruby truffleruby+graalvm-VERSION
chruby: $ ruby-install truffleruby-graalvm
        $ ruby-build truffleruby+graalvm-VERSION ~/.rubies/truffleruby+graalvm-VERSION
```

* In CI with GitHub Actions, see [Testing TruffleRuby in CI](doc/user/testing-truffleruby-in-ci.md) for more details and other CIs.

```yaml
- uses: ruby/setup-ruby@v1
  with:
    ruby-version: truffleruby # or truffleruby-head or truffleruby+graalvm or truffleruby+graalvm-head
```

* Via Docker.
  For Native Standalone see [official release images](https://github.com/graalvm/container/blob/master/truffleruby-community/README.md)
  and [nightly images](https://github.com/flavorjones/truffleruby/pkgs/container/truffleruby).
  For JVM Standalone there are no Docker images yet, but you can easily [download it](doc/user/installing-truffleruby.md) and take inspiration from the [Native Standalone Dockerfiles](https://github.com/flavorjones/truffleruby/blob/master/tool/dockerfiles/stable.dockerfile).

* Manually, by following the [documentation](doc/user/installing-truffleruby.md).

You can use `gem` and `bundle` to install gems, as usual.

Please report any issues you might find on [GitHub](https://github.com/truffleruby/truffleruby/issues).

## Aim

TruffleRuby aims to:

* Run idiomatic Ruby code faster.
  * TruffleRuby is the [fastest Ruby implementation](https://eregon.me/blog/2022/01/06/benchmarking-cruby-mjit-yjit-jruby-truffleruby.html) for many CPU-intensive benchmarks.
* Run Ruby code and native extensions in parallel.
  * TruffleRuby does not have a global interpreter lock and runs both Ruby code and [thread-safe native extensions](doc/user/thread-safe-extensions.md) in parallel.
* Support C extensions.
  * Many C extensions work out of the box, including database drivers.
* Add fast and low-overhead interoperability with languages like Java, JavaScript, Python and WebAssembly.
  * See the [Polyglot documentation](doc/user/polyglot.md).
* Provide new tooling, such as debuggers and monitoring, that works across languages.
  * Includes a profiler, debugger, VisualVM, and more. See the [Tools documentation](doc/user/tools.md).
* Provide all of the above while maintaining very high compatibility with the standard implementation of Ruby.

## TruffleRuby Runtime Configurations

There are two main runtime configurations of TruffleRuby, Native and JVM, which have different trade-offs.

| Configuration: | Native (`--native`, default) | JVM (`--jvm`) |
| ------------------ | ------------: | ------------: |
| Time to start TruffleRuby | about as fast as MRI startup | slower |
| Time to reach peak performance | faster | slower |
| Peak performance (also considering GC) | good | best |
| Java host interoperability | needs reflection configuration | just works |

To find out which runtime configuration is being used, run `ruby --version` on the command line,
or check the value of `RUBY_DESCRIPTION` or `TruffleRuby.native?` in Ruby code.
Runtime configurations are further detailed in [Deploying TruffleRuby](doc/user/deploying.md).

## System Compatibility

TruffleRuby is actively tested on the following systems:

* Oracle Linux 7, 8, 9
* Ubuntu 18.04, 20.04, 22.04 (all LTS)
* Fedora 37, 38
* Debian 10, 11, 12
* macOS 11 (Big Sur)

Architectures:

* AMD64 (aka `x86_64`): Supported
* AArch64 (aka `arm64`): Supported on Linux (from 21.2) and on macOS (from 22.2)

You may find that TruffleRuby will not work if you severely restrict the
environment, for example, by unmounting system filesystems such as `/dev/shm`.

## Dependencies

* [make, gcc and g++](doc/user/installing-llvm.md) for building C and C++ extensions
* [CA certificates](doc/user/installing-libssl.md) for the `openssl` C extension
* [zlib](doc/user/installing-zlib.md) for the `zlib` C extension

Without these dependencies, many libraries including RubyGems will not work.
TruffleRuby will try to print a nice error message if a dependency is missing, but this can only be done on a best effort basis.

You also need to set up a [UTF-8 locale](doc/user/utf8-locale.md) if not already done.

See the [contributor workflow](doc/contributor/workflow.md) document if you wish to build TruffleRuby from source.

## Current Status

We recommend that people trying TruffleRuby on their gems and applications [get in touch with us](#contact) for any help they might need.

TruffleRuby runs Rails and is compatible with many gems, including C extensions.
TruffleRuby is not 100% compatible with MRI 3.3 yet. Please [report](https://github.com/truffleruby/truffleruby/issues) any compatibility issues you might find.
TruffleRuby [passes around 97% of ruby/spec](https://eregon.me/rubyspec-stats/),
more than any other alternative Ruby implementation.

Regarding performance, TruffleRuby is [by far](https://eregon.me/blog/2022/01/06/benchmarking-cruby-mjit-yjit-jruby-truffleruby.html)
the fastest Ruby implementation on the [yjit-bench](https://github.com/Shopify/yjit-bench) benchmark suite which includes `railsbench`, etc.
To achieve this performance TruffleRuby needs a fair amount of warmup, as other advanced JIT compilers do.
If you find any performance issue, please see [this guide](doc/user/reporting-performance-problems.md).

## Releases

TruffleRuby is released at the same time as GraalVM.
TruffleRuby continues to use the release numbering based on the calendar year, for example, 21.x, and 22.x., same as GraalVM prior to 2023.
See the [release roadmap](https://www.graalvm.org/release-notes/version-roadmap/) and [the upcoming releases list](https://github.com/truffleruby/truffleruby/milestones?state=open) for the release dates and information about how long releases are supported.
GraalVM Community Edition releases are supported at most one year.
[Longer support](https://docs.oracle.com/en/graalvm/enterprise/22/docs/release-calendar/) is available for Oracle GraalVM.

## Migration from MRI

TruffleRuby should in most cases work as a drop-in replacement for MRI, but you
should read about our [compatibility](doc/user/compatibility.md).

## Migration from JRuby

For many use cases TruffleRuby should work as a drop-in replacement for JRuby.
However, our approach to integration with Java is different to JRuby so you
should read our [migration guide](doc/user/jruby-migration.md).

## Documentation

Extensive user documentation is available in [`doc/user`](doc/user).

See our [source code repository](https://github.com/truffleruby/truffleruby) and
[contributor documentation](CONTRIBUTING.md) to contribute to TruffleRuby.
In particular, see the [contributor workflow](doc/contributor/workflow.md)
document for how to build and run TruffleRuby.

## Contact

The best way to get in touch with us is to join the `#truffleruby` channel on
[GraalVM Slack](https://www.graalvm.org/community/#community-support).
You can also mention or DM [@TruffleRuby](https://bsky.app/profile/truffleruby.bsky.social) on BlueSky.

Please report security vulnerabilities via the process outlined in [SECURITY.md](SECURITY.md).

## Authors

The main authors of TruffleRuby can be seen [on GitHub](https://github.com/truffleruby/truffleruby/graphs/contributors?from=1%2F1%2F2014).

## Security

See [SECURITY.md](SECURITY.md) for how to report security vulnerabilities.
For known vulnerabilities in Ruby, please refer to the [known-cves](doc/user/known-cves.md) file.

## Licence

TruffleRuby is copyright (c) 2013-2025 Oracle and/or its affiliates, and is made
available to you under the terms of any one of the following three licenses:

* Eclipse Public License version 2.0, or
* GNU General Public License version 2, or
* GNU Lesser General Public License version 2.1.

For further licensing information, see [LICENCE](LICENCE.md), [3rd_party_licenses](3rd_party_licenses.txt), and [doc/legal/legal](doc/legal/legal.md).

## Attribution

TruffleRuby includes infrastructure code from [JRuby](https://github.com/jruby/jruby) (e.g. JCodings, Joni), core library code from the [Rubinius](https://github.com/rubinius/rubinius) project, as well as code from the standard implementation of Ruby, [MRI](https://github.com/ruby/ruby).
