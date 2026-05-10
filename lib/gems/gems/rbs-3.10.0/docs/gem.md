# Releasing a gem with RBS

You can release the RBS type definition of your gem included in the gem package. Just add your RBS files inside `/sig` directory, put them in your rubygem package, and release a new version. RBS gem will load the RBS files from your gem package automatically.

## `/sig` directory

RBS gem tries to load a type definition of a gem from gem package first. It checks if there is `/sig` directory in the gem package and loads `*.rbs` files from the directory. So, everything you have to do to make your type definition available are:

1. Add `/sig` directory in your gem package
2. Put your RBS files inside the directory
3. Make sure the RBS files are included in the gem package

### Hidden RBS files

If you have RBS files you don't want to export to the gem users, you can put the files under a directory that starts with `_``.

Assume you have three RBS files in your gem package:

* `/sig/foo.rbs`
* `/sig/bar/baz.rbs`
* `/sig/_private/internal.rbs`

`foo.rbs` and `baz.rbs` will be loaded from the gem package, but the `internal.rbs` will be skipped. This is only when you load RBS files of a *library*, for example through `-r` option given to `rbs` command. If you load RBS files as *source code*, for example through `-I` option given to `rbs` command, the hidden RBS files will be loaded too.

* `rbs -r your-gem` => Loading a library
* `rbs -I sig` => Loading RBS files as source code

### Adding `manifest.yaml`

`manifest.yaml` lets you declare dependencies to standard libraries. Here is an example, from [RBS gem](https://github.com/ruby/rbs/blob/6b3d0f976a50b3974d0bff26ea8fa9931053f38b/sig/manifest.yaml).

```yaml
dependencies:
  - name: json
  - name: logger
  - name: optparse
  - name: rdoc
  - name: tsort
```

Note that you don't have to write the dependencies that are included in your `.gemspec`. RBS will detect the dependencies between gems, declared in `.gemspec`. `manifest.yaml` is a material for undeclared dependencies, which usually is for standard libraries.

## Testing your type definition

If you develop your gem using a static type checker, like [Steep](https://github.com/soutaro/steep), your type definition will be (mostly) correct and reliable. If not, we strongly recommend adding extra tests focusing on the RBS type definitions.

`RBS::UnitTest` is a library to do that. `assert_send_type` is the most important assertion.

```rb
assert_send_type '(Regexp) { (String) -> String } -> String',
                 'hello', :gsub, /hello/, &proc { "foo" }
```

It calls `String#gsub` method and confirms if given arguments and the return value has correct types.

You can find examples under `test/stdlib` directory of [RBS repository](https://github.com/ruby/rbs/blob/6b3d0f976a50b3974d0bff26ea8fa9931053f38b/test/stdlib/String_test.rb).
