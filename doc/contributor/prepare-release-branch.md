# Preparing the release branch

There are a couple things to do when starting the release branch.

## Disable the union merge of CHANGELOG.md

```bash
git cherry-pick 2a770e3597f7e34b95ef44b07ea79aec401ee35f
```

So any backported changelog change can be reviewed if it is under the correct release.

## Use the GraalVM release version as ABI version

In `lib/cext/include/truffleruby/truffleruby-abi-version.h`, similar to https://github.com/truffleruby/truffleruby/commit/0e920e6959166ea9a2d93f6130cd18ef9638806f.

This avoids any potential reuse of truffleuby-head gems which could otherwise have the same ABI version but with a different meaning.
