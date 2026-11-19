# Prism

## Update Prism

* Clone `prism` as a sibling of `truffleruby-ws` and checkout the commit to import.
* Run `tool/import-prism.sh` in the truffleruby repo and follow the printed `MANUAL STEPS`.

## `Prism` and `Ruby::Prism`

There are 3 copies of Prism in TruffleRuby:
* `src/main/c/yarp` and `src/yarp/java`: the parser used by TruffleRuby itself, which only serializes the fields needed to execute code.
* The `prism` default gem (`Prism`): `lib/mri/prism.rb`, `lib/mri/prism/` and `src/main/c/prism-gem` (`libprism.so`, used via FFI).
  A newer version of the `prism` gem can be installed and activated instead, like for any default gem.
* `Ruby::Prism` (`require "ruby/prism"`, autoloaded): `lib/mri/ruby/prism.rb` and `lib/mri/ruby/prism/`.
  This is always the Prism built into TruffleRuby, no matter which version of the `prism` gem is activated, see [Bug #22379](https://bugs.ruby-lang.org/issues/22379).
  `#syntax_tree` returns `Ruby::Prism` nodes.
  These files are generated from the default gem files by `tool/generate-ruby-prism.rb`, which renames the namespace, and they use the same `libprism.so`.

## Print Detailed Prism AST

### Expore the AST interactively

See the [Ruby AST Tree Viewer](https://ko1.github.io/astv/).

### From the Prism repository

```bash
$ cd prism
$ bundle exec rake compile
$ bin/parse -e '1&.itself'
@ ProgramNode (location: (1,0)-(1,9))
├── locals: []
└── statements:
    @ StatementsNode (location: (1,0)-(1,9))
    └── body: (length: 1)
        └── @ CallNode (location: (1,0)-(1,9))
            ├── flags: safe_navigation
            ├── receiver:
            │   @ IntegerNode (location: (1,0)-(1,1))
            │   └── flags: decimal
            ├── call_operator_loc: (1,1)-(1,3) = "&."
            ├── name: :itself
            ├── message_loc: (1,3)-(1,9) = "itself"
            ├── opening_loc: ∅
            ├── arguments: ∅
            ├── closing_loc: ∅
            └── block: ∅
```

### From TruffleRuby

We can also see what the AST as Java nodes and without extra location fields looks like on TruffleRuby with:
```bash
$ cd truffleruby
$ jt -q ruby tool/parse_ast.rb '1&.itself'
Source:
1&.itself

AST:
ProgramNode
  locals:
  statements: StatementsNode
    body:
      CallNode[Li]
        flags: 1
        receiver: IntegerNode
          flags: 2
        name: "itself"
        arguments: null
        block: null
```

```bash
$ jt -q ruby tool/parse_ast.rb some_file.rb
```

You can also compare to JRuby's AST with:
```bash
$ jruby tool/parse_ast.rb '1&.itself'
Source:
1&.itself

AST:
RootNode line: 0
  CallNode*[lazy]:itself line: 0
    FixnumNode line: 0, long: 1
, null, null
```

## Print the Truffle AST

```bash
$ cd truffleruby
$ jt -q ruby tool/truffle_ast.rb '1&.itself'
```
