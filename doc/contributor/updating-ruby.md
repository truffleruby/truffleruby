# Updating our version of Ruby

TruffleRuby contains some MRI source code files e.g. default gems source code,
some C headers that define public API, etc. Some of them are modified to provide
TruffleRuby-specific implementation or disable some functionality. During
importing new MRI version source code files these patches should be also preserved.

The workflow below will allow you to see and reapply the modifications that we
have to MRI source code while updating.

The approach is the following:
- create a TruffleRuby branch with currently supported MRI version imported files
  (and without any TruffleRuby-specific patches, so it actually undoes the patches)
- create a TruffleRuby branch with the target MRI version imported files
- apply the patches (the difference between the first branch and TruffleRuby master)
  to the second branch

You can re-run these instructions at any time to compare against unmodified
MRI files.

## Updating a specific default gem

To update a specific default gem to a newer version than in the MRI release, run:
```
cd ruby
git checkout -b truffleruby-updates-$VERSION vn_n_n
cd ..
git clone git@github.com:ruby/<gem>.git
cd <gem>
git checkout v<gem-version>
cd ../ruby
ruby tool/sync_default_gems.rb $GEM

git push -u eregon HEAD
```
to update the default gem in MRI.
Then follow the instructions below to reimport MRI files and to update default gems.

Another way to update a gem to a specific version is to pass a commits range:

```
ruby tool/sync_default_gems.rb <gem-name> <sha1-of-currently-imported-version>..<sha1-of-a-new-version>
```

Run `ruby tool/sync_default_gems.rb --help` to get other ways to run the script.

## Updating a specific bundled gem

Set new version of the bundled gem in `gems/bundled_gems` in a Ruby source code directory.

Build Ruby (currently supported version) with the following commands:

```
./autogen.sh
mkdir ~/.rubies
./configure --prefix="${HOME}/.rubies/ruby-$VERSION-updated-gems" --disable-install-doc
make -j8 install-nodoc
```

Use `~/.rubies/ruby-$VERSION-updated-gems` directory as a source for copying a new bundled gem version.

Follow instructions how to update default and bundled gems.

## Setup

Set the environment variable `$VERSION` to the target version:
```
export VERSION=n.n.n
```

Re-install the target MRI version using the commands, to have a clean set of gems:
```
rm -rf ~/.rubies/ruby-$VERSION
ruby-install ruby $VERSION
# OR
rm -rf ~/.rubies/ruby-$VERSION
ruby-build $VERSION ~/.rubies/ruby-$VERSION
ruby-install --no-install-deps -r ~/tmp ruby $VERSION
rm -rf ~/tmp/ruby-$VERSION
```

Ensure that currently supported MRI version is installed this way too.

`ruby-build` does not keep the build directory
(required as `RUBY_BUILD_DIR` for `tool/import-mri-files.sh`),
so one needs the extra `ruby-install` command when using `ruby-build`.

See [these docs](../../doc/user/ruby-managers.md#ruby-install-and-chruby) for details about `ruby-install`.

## Create reference branches

For both the current version of Ruby you're using and the new version create
reference branches (in the TruffleRuby repository) that include unmodified MRI sources.

Check out the version of Ruby you want to create the branch for in `../ruby`, e.g.:

```
git clone --branch v3_1_2 https://github.com/ruby/ruby.git ../ruby
```

Then create the reference branch in the TruffleRuby repository

```bash
git checkout -b vNN
tool/import-mri-files.sh
git commit -am 'vNN'
```

You can then compare between these two branches and yours. For example to see
what changes you made on top of the old version, what's changed between the
old version and the new version, and so on. Keep them around while you do the
update.

## Update MRI with modifications

In your working branch you can cherry-pick the new reference branch,
and then re-apply old patches using the old reference branch.

```bash
# Commit message: Import files from MRI n.n.n
git cherry-pick vNew
# Commit message: Re-apply changes on top of n.n.n files
git revert vOld
```

You'll usually get some conflicts to work out.

## Comment out `-test-` requires

Run

```bash
git grep -E -- "^\\s*require '-test-/"
git grep -E -- '^\s*require "-test-/'
```

And comment any `require` found in files under `test/mri/tests`
but not for files under `test/mri/tests/cext-ruby`.

## Update libraries from third-party repos

Look in `../ruby/ext/json/lib/json/version.rb` to see the version of `flori/json` being used,
compare to `lib/json/lib/json/version.rb` and if different then
copy `flori/json`'s `lib` directory into `lib/json`:
```
rm -rf lib/json/lib
cp -R ../../json/lib lib/json
```

Also reapply our changes to json files, by looking with `git log -p lib/json`.

## Updating default and bundled gems

You need a clean install (e.g., no extra gems installed) of MRI for this
(see `ruby-install` above).

```
export TRUFFLERUBY=$(pwd)
rm -rf lib/gems/gems
rm -rf lib/gems/specifications

cd ~/.rubies/ruby-$VERSION
cp -R lib/ruby/gems/*.0/gems $TRUFFLERUBY/lib/gems
cp -R lib/ruby/gems/*.0/specifications $TRUFFLERUBY/lib/gems

cd $TRUFFLERUBY
rm -f lib/gems/gems/**/*.{o,a,so,bundle} lib/gems/gems/**/{Makefile,extconf.h,mkmf.log} lib/gems/gems/**/*.mk
rm -rf lib/gems/gems/typeprof-* lib/gems/specifications/typeprof-*.gemspec
rm lib/gems/gems/rbs-*/Gemfile.lock
ruby tool/patch-default-gemspecs.rb
```

Then review bundled gems with extensions like `lib/gems/gems/debug-*` and `lib/gems/gems/rbs-*`
to ensure no build artifacts/generated files are committed, only "sources".

Update the `ruby/prism` default gem with `tool/import-prism.sh` script. See the "Update Prism" section
in the [Prism](prism.md) document.

## Updating exe/ executables

```
rm -rf exe
cp -R ~/.rubies/ruby-$VERSION/bin exe
rm -f exe/ruby exe/typeprof
ruby tool/patch_launchers.rb
```

Also update the list of `provided_executables` in `mx_truffleruby.py` if some launchers were added or removed.

## Make other changes

Update all of these:

* Update `.ruby-version`, `TruffleRuby.LANGUAGE_VERSION`
* Reset `truffleruby-abi-version.h` to `$RUBY_VERSION.1` and `lib/cext/ABI_check.txt` to `1` if `RUBY_VERSION` was updated.
  * use `$RUBY_VERSION.$GRAALVM_VERSION.1` for releases in `truffleruby-abi-version.h`
* Update `versions.json`
  * with bundled gem versions provided by `cat ../ruby/gems/bundled_gems | sort` (ensure the target Ruby version is checked out in the `../ruby` directory),
  * default gem versions provided by `ls -l lib/gems/specifications/default`
  * and `gem` gem version provided by `grep 'VERSION =' lib/mri/rubygems.rb`
* Also update version numbers for `debug`, `racc`, and `rbs` in `src/main/c/Makefile`, `mx.truffleruby/suite.py` and `lib/gems/gems/debug-*/ext/debug/extconf.rb`.
* Copy and paste `-h` and `--help` output to `RubyLauncher` (instructions are in the end of the file `src/launcher/java/org/truffleruby/launcher/RubyLauncher.java`)
* This is a good time to get `jt build` working.
* Copy and paste the TruffleRuby `--help` output to `doc/user/options.md` (e.g., with `jt ruby --help | xsel -b`)
* Update `doc/user/compatibility.md` and `README.md`
* Update `doc/legal/legal.md`, notably the `Bundled gems` section
* Update method lists (see `spec/truffle/methods_spec.rb`)
  * run `jt -u ruby test spec/truffle/methods_spec.rb` to add new methods
  * run `jt purge spec/truffle/methods_spec.rb` to remove tags for implemented methods
* Build TruffleRuby (`jt build`).
* Run `jt test gems default-bundled-gems` and commit generated `Gemfile` and `Gemfile.lock` files
* Get `jt test spec/truffle/rubygems/default_gems_list_spec.rb` to pass
* Grep for the old Ruby version with `git grep -F x.y.z`
* Grep for the old Bundler version with `git grep -F x.y.z`
* If `tool/id.def` or `lib/cext/include/truffleruby/internal/id.h` has changed, then run `jt build core-symbols` and check for correctness.
* Upload the [CRuby source archive](https://www.ruby-lang.org/en/downloads/) of that version to the CI for `tool/generate-config-header.sh` (ask Benoit).
* Update `config_*.h` files by running the gate and copying the output, or trigger the `ruby-generate-native-config-*` CI jobs.

For a new major version:
* Update `TargetRubyVersion` in `.rubocop.yml`
* Update `spec/truffleruby.next-specs` and remove `/spec/truffleruby.next-specs merge=union` in `.gitattributes`
* Update the docs for next version specs in [workflow.md](workflow.md).
* Update the versions in the `ruby/spec on CRuby` job of `.github/workflows/ci.yml`.

Use "Update versions" for a commit message.

## Last step

* Request the new MRI version on Jira, then update `ci/common.jsonnet` to use the corresponding MRI version for benchmarking.
