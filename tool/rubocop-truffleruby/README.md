# Rubocop::TruffleRuby

There are some TruffleRuby specific Rubocop copes (a Ruby linter policies) in a `./cop` directory.

They are taken from a `rubocop-truffleruby` gem (https://github.com/truffleruby/rubocop-truffleruby) and should be synchronized every time the gem is updated.

Use `tool/import-rubocop-truffleruby.sh` Shell script to update them automatically:

```shell
tool/import-rubocop-truffleruby.sh
```

The script expects `../rubocop-truffleruby` directory to contain a cloned `rubocop-truffleruby` repository. Otherwise it clones the Git repository itself.