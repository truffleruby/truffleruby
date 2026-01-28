#!/usr/bin/env bash

set -x
set -e

PRISM=../../prism

function create_generated_files() {
  pushd $PRISM
  bundle
  bundle exec rake clobber
  bundle exec rake templates
  popd
}

# Copy C files and Makefile
function copy_c_files_and_makefile() {
  to="$1"
  rm -rf "$to"
  mkdir "$to"
  cp -R $PRISM/{include,src} "$to"
  cp $PRISM/{LICENSE.md,Makefile} "$to"
}

# 1. Copy Prism files for the TruffleRuby parser

export PRISM_SERIALIZE_ONLY_SEMANTICS_FIELDS=1
create_generated_files
copy_c_files_and_makefile src/main/c/yarp

# Copy .java files
rm -rf src/yarp/java
cp -R $PRISM/java src/yarp/java

# 2. Copy Prism files for the default gem

unset PRISM_SERIALIZE_ONLY_SEMANTICS_FIELDS
create_generated_files
copy_c_files_and_makefile src/main/c/prism-gem

# Copy .rb files
cp -R $PRISM/lib/* lib/mri

# Copy Prism tests (and override the ones from the MRI import)
rm -rf test/mri/tests/prism
cp -R $PRISM/test/prism test/mri/tests/
rm -rf test/mri/snapshots
cp -R $PRISM/snapshots test/mri/

# Create and copy default gem gemspec
pushd $PRISM
gem build prism.gemspec -o prism.gem
gem spec prism.gem --ruby > prism.generated.gemspec
VERSION=$(ruby -e 'puts File.read("prism.generated.gemspec")[/stub: prism ([\d\.]+)/, 1]')
popd

rm lib/gems/specifications/default/prism-*.gemspec # remove a gemspec of the previous version

cp $PRISM/prism.generated.gemspec "lib/gems/specifications/default/prism-$VERSION.gemspec"
rm $PRISM/prism.generated.gemspec
rm $PRISM/prism.gem

cat <<'EOF'

MANUAL STEPS:
* Update versions.json
* Run `jt test gems default-bundled-gems`
* Commit the result e.g. `git commit -am 'Update Prism version in tests'`
EOF
