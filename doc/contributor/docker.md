# Testing TruffleRuby Using Docker

The `jt docker` tool can be used to generate `Dockerfiles` for testing
TruffleRuby on multiple Linux distributions in multiple configurations.

We don't use Docker in a very Docker-like way - we could be using something like
Vagrant instead.

You need to have Docker or `podman` installed.
When using `podman`, you need to set `DOCKER=podman`.

Note that running a test will fail the `build` command, so building the image
is itself a test. You can then also log into the image after it's successfully
built to use TruffleRuby.

## Tests Run on Release Candidates or Previews

This is what we run on release builds before the release to ensure the built artifacts work on the various Linux distributions.

First, it is useful to create a branch with the potential changes needed to [tool/docker-configs.yaml](../../tool/docker-configs.yaml), `jt docker`, etc:
```bash
cd truffleruby
git checkout master
git checkout -b bd/docker-tests-$VERSION
```
Note that branch is based on `master`, not the release branch, because it should be merged to `master` (so those changes will be used for the next release).

Then download the standalone's `.tar.gz` for linux-amd64 (looks like `ruby-standalone-svm-java*-linux-amd64-*.tar.gz`).
The jdk version to use for the standalone is the one in `graal/vm/ce-release-artifacts.json`.
The TruffleRuby commit in that build should correspond to the last commit of the release branch (`release/graal-vm/$VERSION`).

We run all Docker tests on the standalone distributions.
This can be done with:
```bash
jt docker test --standalone $PATH_TO_STANDALONE_TAR_GZ --test release/graal-vm/$VERSION
# A concrete example
jt docker test --standalone $PWD/ruby-native-standalone-svm-svmee-java22-linux-amd64-*.tar.gz --test release/graal-vm/23.0
```

We typically run them in parallel instead to make it faster, running each of these commands in a different terminal:
```bash
jt docker test --filter ol --standalone $PATH_TO_STANDALONE_TAR_GZ --test release/graal-vm/$VERSION
jt docker test --filter fedora --standalone $PATH_TO_STANDALONE_TAR_GZ --test release/graal-vm/$VERSION
jt docker test --filter ubuntu --standalone $PATH_TO_STANDALONE_TAR_GZ --test release/graal-vm/$VERSION
jt docker test --filter debian --standalone $PATH_TO_STANDALONE_TAR_GZ --test release/graal-vm/$VERSION
```

## Example Usages

For example, to run a full set of tests on a set of new release candidate tarballs:

```bash
jt docker test --standalone truffleruby-linux-amd64.tar.gz --test release_branch
```

To run tests on a specific distribution:
```bash
DOCKER=podman jt docker build --ubuntu2004 --standalone ~/Downloads/truffleruby-21.2.0-linux-amd64.tar.gz --test release/graal-vm/21.2
```

## Distributions

Pick one of the distributions in [docker-configs.yaml](../../tool/docker-configs.yaml).

## Methods of installing

From a TruffleRuby standalone distribution you have locally, `--standalone truffleruby-1.0.0-linux-amd64.tar.gz`

## What to do

Pick any of:

* Nothing (default)
* Run a basic test of installing and using a few gems (needs network access), `--basic-test`
* Run a full set of tests (needs network access, and a branch of the repo to test against), `--test master`

## Other options

* Print the Dockerfile rather than building it, `print` instead of `build`
* Run a full set of Docker tests we care about, `test` instead of `build`
* Do not rebuild `openssl`, to test error messages, `--no-rebuild-openssl`

When using `test` you need to specify the method of installing, and what to do.

## Docker cache

You may find that the Docker cache interacts badly with these Dockerfiles (such
as repository URLs being cached that become unavailable). Therefore we recommend
regularly clearing your Docker cache.

```bash
docker system prune -a -f
```

## Version incompatibilities

Some of our Docker configurations are a bit fragile, and trying to run Docker
to install old binaries, or using old versions of branches in the source
repository, may not work.
