# Legal Documentation

This document applies to TruffleRuby as built and distributed as the Native Standalone and JVM Standalone distributions,
which are the only supported ways to use TruffleRuby.

## TruffleRuby

TruffleRuby is copyright (c) 2013-2025 Oracle and/or its
affiliates, and is made available to you under the terms of any one of the
following three licenses:

* Eclipse Public License version 2.0, or
* GNU General Public License version 2, or
* GNU Lesser General Public License version 2.1.

See `epl-2.0.txt`, `gpl-2.txt`, `lgpl-2.1.txt`.

## MRI

The standard implementation of Ruby is MRI. TruffleRuby contains code from MRI
version 3.4.7, including:

* the standard library in `lib/mri`, 
* Ruby C extension API in `lib/cext/include` and `src/main/c/cext`, 
* C extensions in `src/main/c/{bigdecimal,date,debug,etc,io-console,json,nkf,openssl,psych,rbconfig-sizeof,rbs,ripper,syslog,zlib}`

MRI is copyright Yukihiro Matsumoto. It is made available under the terms of the
2-clause BSD licence `ruby-bsdl.txt`, or a custom licence `ruby-licence.txt`.

### Fourth-party code

MRI itself includes some third-party code that we have then included. This
includes, but isn't limited to:

The general-purpose hash table library `src/main/c/cext/st.c` and
`lib/cext/include/ruby/st.h` was written originally be Peter Moore and is
public domain.

`lib/cext/include/ccan/{build_assert,check_type,container_of,str}` are all
utilities from CCAN and are public domain or available under the terms of the
CC0 public domain dedication, see `ccan-cc0.txt`.

`lib/cext/include/ccan/list` is a utility from CCAN and is available under the
terms of 'BSD-MIT', see `ccan-bsd-mit.txt`. Despite the filename 'BSD-MIT' this
is the conventional MIT licence.

RDoc Darkfish theme fonts under `lib/mri/rdoc/generator/template/darkfish/` are
available under the terms of the SIL Open Font License 1.1, see `ofl.txt`.

The header file `lib/cext/include/ruby/onigmo.h` is part of Onigmo, available
under the same 2-clause BSD licence as Ruby.

RubyGems, in `lib/mri/rubygems` is available under the same custom licence as
MRI, see `ruby-licence.txt`, or the MIT licence, see `mit.txt`.

## JRuby

TruffleRuby contains code from JRuby 9.4.4.0, including Java implementation
code, build system, shell script launchers, standard library modified from MRI,
and so on.

Where original files had JRuby licence headers we have copied them over. In
general this code is available under any of these licences:

* Eclipse Public License version 2.0, or
* GNU General Public License version 2, or
* GNU Lesser General Public License version 2.1.

See `epl-2.0.txt`, `gpl-2.txt`, `lgpl-2.1.txt`.

Some libraries that were spun out of JRuby, such as ByteList, have been
incorporated into our source code. These were under the same copyright and
licence as JRuby in the first place, so we have considered them part of JRuby.

For historical information from JRuby, see `jruby-copying.txt`, but this will
now be out of date.

## Rubinius

TruffleRuby contains code from Rubinius 2.11, including core library
implementation in `src/main/ruby/truffleruby/core`. This is in some cases
copyright 2011 Evan Phoenix, and in other cases copyright 2007-2015 Evan Phoenix
and contributors, and released under the 3-clause BSD license. We have included
licence headers in these files which weren't in the original ones.

Some parts of the TruffleRuby Java code (such as the implementation of Rubinius
primitives) are derived from Rubinius C++ code which is copyright 2007-2015,
Evan Phoenix and contributors, and released under the 3-clause BSD license.

Some parts of the Ruby implementations of the standard library in `lib/truffle`
are copyright 2013 Brian Shirai and are licensed under the 3-clause BSD license.
In some cases this code is just code from MRI, and covered by their licence. In
some cases we have modified this code.

## OpenSSL

TruffleRuby ships OpenSSL, version as described in [mx.truffleruby/suite.py](../../mx.truffleruby/suite.py).
OpenSSL is copyright `The OpenSSL Project Authors` and is released under the Apache License 2.0 (see `apache-2.txt`).
OpenSSL is used by the `openssl` C extension.

## LibYAML

TruffleRuby ships LibYAML, version as described in [mx.truffleruby/suite.py](../../mx.truffleruby/suite.py).
LibYAML is copyright `Kirill Simonov` and `Ingy dot Net` and is released under the MIT licence, see `mit.txt`.
LibYAML is used by the `psych` C extension.

## Bundled gems

This list is from [bundled_gems](bundled_gems) and `grep licenses lib/gems/specifications/*.gemspec`.
Versions as used in MRI unless otherwise specified.

<!--
Script to get the data:
Dir["lib/gems/specifications/*.gemspec"].sort.map { |f| [File.basename(f)[/^(.+?)-\d/, 1], eval(File.read(f)[/licenses = (.+)/, 1]) - %w[Ruby]] }.group_by(&:last).to_h { |k,v| [k, v.map(&:first).join(", ")] }
-->

### abbrev, base64, bigdecimal, csv, debug, drb, getoptlong, matrix, mutex_m, net-ftp, net-imap, net-pop, net-smtp, nkf, observer, power_assert, prime, racc, rbs, resolv-replace, rexml, rinda, rss, syslog

These gems are available under the 2-clause BSD licence (see `ruby-bsdl.txt`).

### minitest, rake, repl_type_completor

These gems are available under an MIT licence (see `mit.txt`).

### test-unit

test-unit is copyright Sutou Kouhei, Ryan Davis, and Nathaniel Talbott
and is available under the 2-clause BSD licence (see `ruby-bsdl.txt`).

## Other gems

### json

The json gem is available under the same licence as MRI (see `ruby-bsdl.txt`).

### RDoc

It's part of the standard library, not a bundled gem. RDoc is copyright
Dave Thomas and Eric Hodel and is available under the terms of the GPL 2 (see
`gpl-2.txt`), or the same custom licence as MRI (see `ruby-licence.txt`). Some
other files in RDoc have different, but compatible, licences detailed in the
files.

### FFI

TruffleRuby includes parts of the FFI gem (version as described in [lib/truffle/ffi/version.rb](../../lib/truffle/ffi/version.rb)).
The FFI gem is copyright  2008-2016, Ruby FFI project contributors, and covered by the three-clause BSD licence (see `ffi.txt`).

### Prism

TruffleRuby uses the [Prism](https://github.com/ruby/prism) Ruby parser
(version as described in [src/main/c/yarp/include/prism/version.h](../../src/main/c/yarp/include/prism/version.h)),
copyright Shopify Inc. and is available under an MIT licence (see `src/main/c/yarp/LICENSE.md`).

## Java dependencies

TruffleRuby has Java dependencies on these modules, which are then included in
the distribution:

### JOni

TruffleRuby uses JOni, version as described in [mx.truffleruby/suite.py](../../mx.truffleruby/suite.py).
JOni is copyright its authors and is released under an MIT licence (see `mit.txt`).

### JCodings

TruffleRuby uses JCodings, version as described in [Truffle's suite.py](https://github.com/oracle/graal/blob/master/truffle/mx.truffle/suite.py) (select the correct branch/tag for finding the version used in a release).
JCodings is copyright its authors and is released under an MIT licence (see `mit.txt`).

## Patches

`lib/patches` contains patches to gems that are automatically applied when the
gems are loaded, and contain third party code from those gems, with permissive
licenses. We've added the licenses to the individual files.

`lib/patches/stdlib` patches code in the standard library.

## Ruby Specs

We do not distribute MSpec or the Ruby Specs, but they are both copyright 2008
Engine Yard and are released under an MIT licence (see `mit.txt`).

## FFI Specs

We do not distribute the FFI Specs, but they are copyright 2008-2014
Ruby-FFI contributors and are released under an MIT licence (see `mit.txt`).
