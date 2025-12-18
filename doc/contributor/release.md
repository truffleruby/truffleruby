# Releasing TruffleRuby

Always release from a release branch (to avoid ever setting `"release": True,` on master).

The TruffleRuby version to be released is denoted as `$TRUFFLERUBY_VERSION` below.

## Create a release branch

The branch name should be `release/$MAJOR`, e.g. `release/33`.

```bash
git checkout -b release/$MAJOR
```

## Set release flag to true

(only when creating the release branch)

In [mx.truffleruby/suite.py](../../mx.truffleruby/suite.py) switch the `"release"` field from `False` to `True`.

Commit with:
```bash
git commit -am 'Set release flag to true'
```

## Disable the union merge of CHANGELOG.md

(only when creating the release branch)

```bash
git cherry-pick 2a770e3597f7e34b95ef44b07ea79aec401ee35f
```

So any backported changelog entry can be reviewed if it is under the correct release.

## Set the version field

In [mx.truffleruby/suite.py](../../mx.truffleruby/suite.py), set the `"version"` field to the version you want to release and commit.

Commit with:
```bash
git commit -am 'Set release version'
```

## Set the ABI version to the TruffleRuby version

In [lib/cext/include/truffleruby/truffleruby-abi-version.h](../../lib/cext/include/truffleruby/truffleruby-abi-version.h), set `TRUFFLERUBY_ABI_VERSION` to `$TRUFFLERUBY_VERSION.1`.

This avoids any potential reuse of truffleuby-head gems which could otherwise have the same ABI version but with a different meaning.

Commit with:
```bash
git commit -am 'Set ABI version for release branch'
```

## Push the branch

```bash
git push -u origin HEAD
```

## Run the release workflow

Run the [release workflow](https://github.com/truffleruby/truffleruby/actions/workflows/release.yml) **on that release branch**.
Set `version` to `$TRUFFLERUBY_VERSION` so it creates a draft GitHub release.

This workflow will create a draft GitHub release with all assets already uploaded.

The entire workflow should pass, if not it should be fixed.
If it fails, note that you will have to delete the draft release or change its tag before rerunning the workflow,
as the workflow checks there is no draft release with that version/tag (so it can upload files to the correct draft release).

## Manual Testing

Some manual testing is good to do now, e.g. by downloading the standalone archive from the draft release and checking e.g. that `irb` runs fine on your system.

## Edit the Draft GitHub release

Verify the tag name is `graal-$TRUFFLERUBY_VERSION` since Ruby installers expect that.

Verify the target is the expected commit from the release branch.

Copy the section from [CHANGELOG.md](../../CHANGELOG.md) as the release description.

Use the same formatting as the previous release, notably:
* Same introductory text.
* Same `#` level for changelog sections.

## Sign the Maven Bundle

Download the `maven-bundle` artifact from the workflow.
That will download a `.zip`, so extract it once to get the `.tar.gz`.
Then run:

```bash
jt sign_maven_bundle ~/Downloads/maven-bundle.tar.gz
```

It will create a `maven-bundle-signed.tar.gz` as sibling to `maven-bundle.tar.gz`.

## Upload Maven Bundle to Maven Central

Go to https://central.sonatype.com/publishing and use `Publish Component`.
Upload the `maven-bundle-signed.tar.gz` from the previous step.
There is [documentation about that](https://central.sonatype.org/publish/publish-portal-upload/).
Publish it.

## Publish GitHub release

Publish the GitHub release using the GitHub UI.

## Run Docker Stable workflow

Run the [docker-stable workflow](https://github.com/truffleruby/truffleruby/actions/workflows/docker-stable.yml) **on that release branch**.
Pass it the `$TRUFFLERUBY_VERSION`.

## Update Ruby Installers

Follow [the documentation to updating Ruby Installers](updating-ruby-installers.md).

---
---
---

## Backporting changes from master

Either `git cherry-pick -m 1 MERGE_COMMIT` the relevant merge commits,
or merge all changes from `master` with `git merge master`.
Be careful to not add newer releases in [CHANGELOG.md](../../CHANGELOG.md).
