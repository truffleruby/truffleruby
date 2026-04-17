# RBS File Encoding

## Best Practice

**Use UTF-8** for both file encoding and your system locale.

## Supported Encodings

RBS parser supports ASCII-compatible encodings (similar to Ruby's script encoding support).

**Examples**: UTF-8, US-ASCII, Shift JIS, EUC-JP, ...

## Unicode Codepoint Symbols

String literal types in RBS can contain Unicode codepoint escape sequences (`\uXXXX`).

When the file encoding is UTF-8, the parser translates Unicode codepoint symbols:

```rbs
# In UTF-8 encoded files

type t = "\u0123"  # Translated to the actual Unicode character ģ
type s = "\u3042"  # Translated to the actual Unicode character あ
```

When the file encoding is not UTF-8, Unicode escape sequences are interpreted literally as the string `\uXXXX`:

```rbs
# In non-UTF-8 encoded files

type t = "\u0123"  # Remains as the literal string "\u0123"
```

## Implementation

RBS gem currently doesn't do anything for file encoding. It relies on Ruby's encoding handling, specifically `Encoding.default_external` and `Encoding.default_internal`.

`Encoding.default_external` is the encoding Ruby assumes when it reads external resources like files. The Ruby interpreter sets it based on the locale. `Encoding.default_internal` is the encoding Ruby converts the external resources to. The default is `nil` (no conversion.)

When your locale is set to use `UTF-8` encoding, `default_external` is `Encoding::UTF_8`. So the RBS file content read from the disk will have UTF-8 encoding.

### Parsing non UTF-8 RBS source text

If you want to work with another encoding, ensure the source string has ASCII compatible encoding.

```ruby
source = '"日本語"'
RBS::Parser.parse_type(source.encode(Encoding::EUC_JP))  # => Parses successfully
RBS::Parser.parse_type(source.encode(Encoding::UTF_32))  # => Returns `nil` since UTF-32 is not ASCII compatible
```

### Specifying file encoding

Currently, RBS doesn't support specifying file encoding directly.

You can use `Encoding.default_external` while the gem loads RBS files from the storage.
