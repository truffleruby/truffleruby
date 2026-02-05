# Prism

## Update Prism

* Clone `prism` as a sibling of `truffleruby-ws`.
* Run `tool/import-prism.sh` in the truffleruby repo.
* Commit the result with message `Import ruby/prism@COMMIT`

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
