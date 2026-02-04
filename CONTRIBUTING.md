Thank you for thinking about contributing something to TruffleRuby!

## Contributor Documentation

See the [workflow docs](doc/contributor/workflow.md) for how to build TruffleRuby from source, run tests, etc.

We have some more technical contributor documentation under [doc/contributor](doc/contributor).

You can find the most common issues described in a [How-To Guide](doc/contributor/how-to-guide.md).

## Slack

You are welcome to join the channel `#truffleruby` of the
[GraalVM Slack](https://www.graalvm.org/slack-invitation/)
for any help related to contributing to TruffleRuby.

## Style

We use various lint tools to keep the style consistent.
The most important checks can be run locally with `jt lint fast`.
You can use `tool/hooks/lint-check.sh` as a git hook to run `jt lint fast` automatically, see instructions in that file.

## ChangeLog

When opening a Pull Request, if the change is visible or meaningful to users (they are the intended readers of the ChangeLog),
please add a ChangeLog entry with this format:

```
* Description (#GitHub issue number if any, @author).
```

See the [the ChangeLog](CHANGELOG.md) for examples.

This is the meaning of the sections in the ChangeLog:
* New features: a big new feature or a new method provided by TruffleRuby which does not exist in CRuby (e.g. a new interop method).
* Compatibility: any change which helps compatibility with CRuby, whether it is a new method or behavior closer to CRuby.
* Bug fixes: only for fixes where the bug "silently" caused incorrect behavior.
  For example, if it raised an exception before, the wrong behavior was pretty clear, so it should be under `Compatibility` not `Bug fixes`.
  On the other hand, if e.g. `1 + 2` returned `4` that should be under `Bug fixes`.
* Performance: something which improves performance (whether interpreter, warmup or peak).
* Memory Footprint: something which improves memory footprint.
* Incompatible Changes: this means incompatible changes that users may need to adapt to.

Always keep an empty line around the various sections, like it is done for entries in older releases.
The idea is only add lines, never remove lines (important since this file uses union merge).

GitHub might show on the Pull Request (such as [here](https://github.com/truffleruby/truffleruby/pull/4105#issuecomment-3770098818)):
```
This branch has conflicts that must be resolved
CHANGELOG.md
```
This is a GitHub bug, there is never a conflict in `CHANGELOG.md` because `CHANGELOG.md` uses union merge.
Please do not use the `Resolve conflicts` button as that will create a redundant merge commit.
Instead such PRs can be merged by using:

```bash
git checkout master
git pull

git fetch $REMOTE
git merge --no-ff $REMOTE/$BRANCH
# Copy the PR title + PR number as commit message and add `()` around the PR number to look like regular merges with the GitHub UI.

git push
```

## Code of Conduct

We have a [code of conduct for contributors](https://www.contributor-covenant.org/version/1/4/code-of-conduct/).
