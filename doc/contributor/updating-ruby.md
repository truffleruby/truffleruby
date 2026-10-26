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

To update a specific default gem to a newer version than in the MRI release,
you need to have `git@github.com:eregon/ruby.git` as a remote named `eregon` of `../ruby`.

Then run:
```
cd truffleruby
cd ../ruby
git checkout -b truffleruby-updates-$VERSION vn.n.n
git cherry-pick 5ca7fd9d2bfc028f41fe563be39e44e6d1e31129
pushd ../..
git clone git@github.com:ruby/$GEM.git
cd $GEM
git checkout v<gem-version>
popd
ruby tool/sync_default_gems.rb $GEM

git push -u eregon HEAD
```
to update the default gem in MRI.
Then follow the instructions below to reimport MRI files.

Also update `versions.json`.

Then update the gemspec using, e.g.:
```
rm -f lib/gems/specifications/default/strscan-*.gemspec
gem fetch strscan:3.1.8
gem spec --ruby strscan-3.1.8.gem > lib/gems/specifications/default/strscan-3.1.8.gemspec
```

Then run `jt test gems default-bundled-gems` and commit the result.

## Updating a specific bundled gem

Set new version of the bundled gem in `gems/bundled_gems` in a Ruby source code directory.

Build Ruby (currently supported version) with the following commands:

```
./autogen.sh
mkdir ~/.rubies
./configure --prefix="${HOME}/.rubies/ruby-$VERSION-updated-gems" --disable-install-doc
make -j8 install-nodoc
```

Then reimport MRI files (see below) with `RUBY_INSTALL_DIR=~/.rubies/ruby-$VERSION-updated-gems`,
so the new bundled gem version is copied from that installation.

## Setup

For both the current version of Ruby you're using and the new version:

Set the environment variable `$VERSION` to the target version:
```
export VERSION=n.n.n
```

Re-install the target MRI version using the commands, to have a clean set of gems:
```
rm -rf ~/.rubies/ruby-$VERSION
ruby-build -d ruby-$VERSION ~/.rubies
```

`tool/import-mri-files.sh` copies the default and bundled gems from this installation
(`~/.rubies/ruby-$VERSION` by default, it must be the same version as the MRI source directory;
set `RUBY_INSTALL_DIR` to use another installation), so it must not have extra gems installed.

## Create reference branches

For both the current version of Ruby you're using and the new version create
reference branches (in the TruffleRuby repository) that include unmodified MRI sources.

Check out the version of Ruby you want to create the branch for in `../ruby`, e.g.:

```
git clone --branch v4.0.2 https://github.com/ruby/ruby.git ../ruby
```

Then create the reference branch in the TruffleRuby repository

```bash
git checkout -b ruby-n.n.n
tool/import-mri-files.sh
git commit -am 'Import files from MRI n.n.n'
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
When you commit, uncomment the `Conflicts:` section in the git commit message
so there is a clear record of which files had conflicts.

## Comment out `-test-` requires

Run

```bash
git grep -E -- "^\\s*require '-test-/"
git grep -E -- '^\s*require "-test-/'
```

Or regexp-search for `^\s*require ['"]-test-` in your editor.

And comment any `require` found in files under `test/mri/tests`
but not for files under `test/mri/tests/cext-ruby`.

## Update libraries from third-party repos

Look in `../ruby/ext/json/lib/json/version.rb` to see the version of `ruby/json` being used,
compare to `lib/json/lib/json/version.rb` and if different then reimport json:
```
rm -rf lib/json
mkdir lib/json
cp -R ../../json/lib lib/json
rm -f lib/json/lib/json/ext/.keep

rm -rf src/main/c/json
mkdir src/main/c/json
cp -R ../../json/ext/json/ext/* src/main/c/json
rm -rf src/main/c/json/generator
rm -f src/main/c/json/parser/depend
```

Also reapply our changes to json files, by looking with `git log -p lib/json`.

## Updating default and bundled gems

Default and bundled gems under `lib/gems` are imported by `tool/import-mri-files.sh`
from a clean install of MRI (see [above](#setup)), so they are part of the reference branches
and the TruffleRuby-specific changes to them (e.g., `ext/*/extconf.rb` of the `debug` and `rbs` gems)
are re-applied by the `git revert vOld` step like other files.
For a bundled gem with a C extension whose version changed, this shows as a modify/delete conflict
on the old version's file: re-apply that patch on the new version's file.

The script removes build artifacts and generated files of gems with C extensions,
so only "sources" are imported. Check the reference branch commit to ensure nothing else needs to be removed,
e.g. with
```
git show --stat vNew -- lib/gems | grep -Ev '\.(rb|gemspec|md|c|h|rdoc|txt|ja|png|gif|ttf|rhtml|js|css|yaml|yml|json|rake|rbs|erb)\s|/(COPYING|BSDL|LICENSE|LEGAL|MIT-LICENSE|PSFL|Gemfile|Rakefile|console|setup)\s'
```
and extend the list of files removed in `tool/import-mri-files.sh` if needed.

Update the `ruby/prism` default gem with `tool/import-prism.sh` script.
See the "Update Prism" section in the [Prism](prism.md) document.
That way the Prism `gemspec` is correct.

## Updating exe/ executables

```
rm -rf exe
cp -R ~/.rubies/ruby-$VERSION/bin exe
rm -f exe/ruby exe/typeprof
ruby tool/patch_launchers.rb
```

## Make other changes

Update all of these:

* Check the TruffleRuby patches to the `debug` and `rbs` gems extensions are still applied,
  see `git log --follow -p 'lib/gems/gems/debug-*/ext/debug/extconf.rb'` and
  `git log --follow -p 'lib/gems/gems/rbs-*/ext/rbs_extension/extconf.rb'`.
* Update `.ruby-version`, `TruffleRuby.LANGUAGE_VERSION`
* IMPORTANT: switch to CRuby n.n.n in the current terminal, e.g., `chruby ruby-$(cat .ruby-version)`
* Update the `SyntaxVersion` in `YARPTranslatorDriver`
* Given a Ruby version `A.B.C`, update the TruffleRuby version in `mx.truffleruby/suite.py` to `AB.0.0`.
* Reset `truffleruby-abi-version.h` to `$RUBY_VERSION.1` and `lib/cext/ABI_check.txt` to `1` if `RUBY_VERSION` was updated.
  * use `$TRUFFLERUBY_VERSION.1` in `truffleruby-abi-version.h` instead when on a release branch.
* Update `versions.json`
  * run `RUBY_SOURCE_DIR=../ruby tool/update-gem-versions-list.rb`
  * Undo the change to the `prism` version
* Copy and paste the `-h` and `--help` output from CRuby to `RubyLauncher` (instructions are in the end of the file `src/launcher/java/org/truffleruby/launcher/RubyLauncher.java`)
* This is a good time to get `jt build` working.
* Copy and paste the TruffleRuby `--help` output to `doc/user/options.md` (e.g., with `jt ruby --help | xsel -b`)
* Update `doc/user/compatibility.md` and `README.md`
* Update `doc/legal/legal.md`, notably the `Bundled gems` section
* Update method lists (see `spec/truffle/methods_spec.rb`)
  * run `jt -u ruby test spec/truffle/methods_spec.rb` to add new methods
  * run `jt purge spec/truffle/methods_spec.rb` to remove tags for implemented methods
  * run `jt tag spec/truffle/methods_spec.rb` to add tags for missing methods
* Build TruffleRuby (`jt build`).
* Run `jt test gems default-bundled-gems` and commit generated `Gemfile` and `Gemfile.lock` files
  * Do not include the `BUNDLED WITH` section from the `Gemfile.lock` -- it will cause the linter to fail
* Get `jt test spec/truffle/rubygems/default_gems_list_spec.rb` to pass
* Add a new section with all the subsections for the new TruffleRuby version in `CHANGELOG.md` and add an entry like `* Updated to Ruby $VERSION`.
* Grep for the old Ruby version with `git grep -F x.y.z`
* Grep for the old Ruby version with `git grep -F x.y doc`
* Grep for the old Bundler version with `git grep -F x.y.z`
* If `tool/id.def` has changed, then run `jt build core-symbols` and check for correctness.
* Update `config_*.h` files by running the CI and copying the output.
* Update `StringIO::VERSION` in `lib/truffle/stringio.rb` and tag failing specs.

For a new major version:
* Update `TargetRubyVersion` in `.rubocop.yml`
* Update `spec/truffleruby.next-specs` and remove `/spec/truffleruby.next-specs merge=union` in `.gitattributes`
* Update the docs for next version specs in [workflow.md](workflow.md).
* Update the versions in the `ruby/spec on CRuby` job of `.github/workflows/ci.yml`.
