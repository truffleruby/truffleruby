# CHANGELOG

## 3.8.0 (2024-12-24)

### Signature updates

* `Dir.mktmpdir` ([#2158](https://github.com/ruby/rbs/pull/2158))
* `File.join` ([#2158](https://github.com/ruby/rbs/pull/2158))
* `IO#each_line` ([#2151](https://github.com/ruby/rbs/pull/2151))
* `Kernel#readlines` ([#2151](https://github.com/ruby/rbs/pull/2151))
* `Ractor.store_if_absent` ([#2198](https://github.com/ruby/rbs/pull/2198))
* Update docs as of 2024-12-24, 16:13 JST ([#2193](https://github.com/ruby/rbs/pull/2193))

### Library changes

* Adjust capacity of location children ([#2197](https://github.com/ruby/rbs/pull/2197))
* Should validate self-type on Proc ([#2192](https://github.com/ruby/rbs/pull/2192))

## 3.8.0.pre.1 (2024-12-19)

### Signature updates

* `RubyVM::AbstractSyntaxTree::Location` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `_JsonWrite#flush` ([#2134](https://github.com/ruby/rbs/pull/2134))
* `Array#fetch_values` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Array#to_csv` ([#2099](https://github.com/ruby/rbs/pull/2099))
* `Exception#set_backtrace` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Fiber#raise` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `GC.config` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Hash.new` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Kernel#raise` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `MatchData#bytebegin` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `MatchData#byteend` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Pathname#mkpath` ([#2048](https://github.com/ruby/rbs/pull/2048))
* `Pathname#rmtree` ([#2048](https://github.com/ruby/rbs/pull/2048))
* `Ractor._require` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Ractor.[]` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Ractor.[]=` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Ractor.main?` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Range#step` ([#1958](https://github.com/ruby/rbs/pull/1958))
* `RubyVM::AbstractSyntaxTree::Node#locations` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `String#append_as_bytes` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `String#parse_csv` ([#2099](https://github.com/ruby/rbs/pull/2099))
* `String#scan` ([#2146](https://github.com/ruby/rbs/pull/2146))
* `Tempfile.create` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Thread#raise` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Time#iso8601` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Time#xmlschema` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `Warning.categories` ([#2189](https://github.com/ruby/rbs/pull/2189))
* `ZStream#finish` ([#2136](https://github.com/ruby/rbs/pull/2136))
* Generate docs based on ruby 3.4.0-rc1 ([#2183](https://github.com/ruby/rbs/pull/2183))

### Library changes

* Deprecate `Kernel#Namespace` ([#2123](https://github.com/ruby/rbs/pull/2123))
* Fix `missing braces around initializer` warning ([#2180](https://github.com/ruby/rbs/pull/2180))
* Avoid double name resolution ([#2127](https://github.com/ruby/rbs/pull/2127))
* Remove forward declarations of local variables ([#2132](https://github.com/ruby/rbs/pull/2132))
* Designated initializers ([#2131](https://github.com/ruby/rbs/pull/2131))

### Miscellaneous

* Update docs with rdoc-6.10.0 ([#2182](https://github.com/ruby/rbs/pull/2182))
* Update rdoc ([#2160](https://github.com/ruby/rbs/pull/2160))
* Prepare for ruby 3.4.0 ([#2181](https://github.com/ruby/rbs/pull/2181))
* Fix typo in `instance` usage example ([#2139](https://github.com/ruby/rbs/pull/2139))
* Indent `parser_test` code snippets properly ([#2137](https://github.com/ruby/rbs/pull/2137))
* Bump rbs and steep in /steep ([#2129](https://github.com/ruby/rbs/pull/2129))
* Un-hardcode repo name in test suite ([#2128](https://github.com/ruby/rbs/pull/2128))

## 3.7.0 (2024-12-05)

### Miscellaneous

* bundle update mutex_m ([#2122](https://github.com/ruby/rbs/pull/2122))

## 3.7.0.pre.1 (2024-12-05)

### Signature updates

* `CGI.accept_charset` ([#2045](https://github.com/ruby/rbs/pull/2045))
* `IO#readline`, `IO#readlines` ([#2059](https://github.com/ruby/rbs/pull/2059))
* `Kernel#proc` ([#2036](https://github.com/ruby/rbs/pull/2036))
* `Kernel#system` ([#2075](https://github.com/ruby/rbs/pull/2075))
* `Object#to_yaml` ([#2107](https://github.com/ruby/rbs/pull/2107))
* `OpenSSL::Digest` ([#2108](https://github.com/ruby/rbs/pull/2108))
* `OpenSSL.base64digest` ([#2078](https://github.com/ruby/rbs/pull/2078))
* `Process.detach` ([#2083](https://github.com/ruby/rbs/pull/2083))

### Library changes

* Reduce object allocation for record type ([#2118](https://github.com/ruby/rbs/pull/2118))
* Milestone to graduate stringio from core. ([#2114](https://github.com/ruby/rbs/pull/2114))
* Mark all internal `parse_` methods as `static` ([#2103](https://github.com/ruby/rbs/pull/2103))
* Add templating mechanism to automatically generate `.c`/`.h` files ([#2098](https://github.com/ruby/rbs/pull/2098))
* Remove unused `rbs_unescape_string` declaration ([#2058](https://github.com/ruby/rbs/pull/2058))
* Fix UntypedFunction does not provide #map_type_name ([#2054](https://github.com/ruby/rbs/pull/2054))
* Better `instance_eval`/`instance_exec` detection ([#2052](https://github.com/ruby/rbs/pull/2052))
* Overload annotations ([#2049](https://github.com/ruby/rbs/pull/2049))

#### rbs prototype

* prototype rb: Fix crashed by self::CONST ([#2079](https://github.com/ruby/rbs/pull/2079))

#### rbs collection

* mutex_m support ([#2115](https://github.com/ruby/rbs/pull/2115))

### Miscellaneous

* Use `File.open` with block ([#2119](https://github.com/ruby/rbs/pull/2119))
* Compile without activesupport ([#2105](https://github.com/ruby/rbs/pull/2105))
* docs: Add singleton attribute members to syntax.md ([#2090](https://github.com/ruby/rbs/pull/2090))
* Bundle update rubocop-on-rbs ([#2094](https://github.com/ruby/rbs/pull/2094))
* `bundle exec` on windows ([#2101](https://github.com/ruby/rbs/pull/2101))
* Fix typos ([#2097](https://github.com/ruby/rbs/pull/2097))
* Bundle update json 2.8.2 ([#2095](https://github.com/ruby/rbs/pull/2095))
* Skip `rbs` gem validation ([#2084](https://github.com/ruby/rbs/pull/2084))
* Skip PTY_test with Windows platform ([#2055](https://github.com/ruby/rbs/pull/2055))
* Randomize stdlib test order to uncover hidden assumptions ([#2043](https://github.com/ruby/rbs/pull/2043))
* Suppress `inline` is not at beginning of declaration for melt_array ([#2050](https://github.com/ruby/rbs/pull/2050))
* Skip `mkpath` test with 3.4 ([#2053](https://github.com/ruby/rbs/pull/2053))

## 3.6.1 (2024-10-03)

### Library changes

* Mark as ractor-safe ([#2041](https://github.com/ruby/rbs/pull/2041))

### Miscellaneous

* Omit IOConsoleTest with Windows platform ([#2044](https://github.com/ruby/rbs/pull/2044))
* Recover `$stdout` after test ([#2042](https://github.com/ruby/rbs/pull/2042))

## 3.6.0 (2024-09-30)

Nothing changed since `3.6.0.pre.3`.

## 3.6.0.pre.3 (2024-09-30)

### Signature updates

* `JSON.load_file`, `.load_file!` ([#2031](https://github.com/ruby/rbs/pull/2031))
* `Module#class_eval`, `Module#module_eval` ([#2029](https://github.com/ruby/rbs/pull/2029))
* `Open3` ([#2014](https://github.com/ruby/rbs/pull/2014))
* `RubyVM::InstructionSequence` ([#2027](https://github.com/ruby/rbs/pull/2027))
* Fixup RBS type definitions with rbs-on-rubocop ([#2023](https://github.com/ruby/rbs/pull/2023))
* Avoid using `instance`/`class` types in modules ([#2020](https://github.com/ruby/rbs/pull/2020))

### Language updates

* Optional type param restriction ([#2030](https://github.com/ruby/rbs/pull/2030))
* Prohibit `self` type as a generics default ([#2022](https://github.com/ruby/rbs/pull/2022))
* Allow `self` types in generics default type ([#2021](https://github.com/ruby/rbs/pull/2021))

### Library changes

* Add `TypeParamDefaultReferenceError#type_param` ([#2032](https://github.com/ruby/rbs/pull/2032))

## 3.6.0.pre.2 (2024-09-18)

### Signature updates

* `Kernel#class` ([#2017](https://github.com/ruby/rbs/pull/2017))
* `TracePoint#enable` ([#2015](https://github.com/ruby/rbs/pull/2015))

### Miscellaneous

* Fix type definition and type errors ([#2016](https://github.com/ruby/rbs/pull/2016))

## 3.6.0.pre.1 (2024-09-17)

### Signature updates

* csv ([#2012](https://github.com/ruby/rbs/pull/2012), [#1864](https://github.com/ruby/rbs/pull/1864), [#2002](https://github.com/ruby/rbs/pull/2002), [#2003](https://github.com/ruby/rbs/pull/2003), [#1903](https://github.com/ruby/rbs/pull/1903))
* digest ([#1880](https://github.com/ruby/rbs/pull/1880))
* kconv ([#1857](https://github.com/ruby/rbs/pull/1857))
* zlib ([#1911](https://github.com/ruby/rbs/pull/1911))
* `_Each` ([#2006](https://github.com/ruby/rbs/pull/2006))
* `Array` ([#1921](https://github.com/ruby/rbs/pull/1921))
* `Enumerable` ([#1921](https://github.com/ruby/rbs/pull/1921))
* `Enumerator::_Each` ([#2006](https://github.com/ruby/rbs/pull/2006), [#1915](https://github.com/ruby/rbs/pull/1915))
* `Enumerator` ([#2004](https://github.com/ruby/rbs/pull/2004))
* `Kernel` ([#1945](https://github.com/ruby/rbs/pull/1945))
* `Method` ([#1939](https://github.com/ruby/rbs/pull/1939))
* `Module` ([#1921](https://github.com/ruby/rbs/pull/1921))
* `Net::HTTPHeader` ([#1932](https://github.com/ruby/rbs/pull/1932))
* `Proc` ([#1937](https://github.com/ruby/rbs/pull/1937))
* `Queue` ([#2004](https://github.com/ruby/rbs/pull/2004))
* `Range` ([#1921](https://github.com/ruby/rbs/pull/1921))
* `Refinement` ([#1931](https://github.com/ruby/rbs/pull/1931))
* `Symbol` ([#1930](https://github.com/ruby/rbs/pull/1930))
* `TracePoint` ([#1943](https://github.com/ruby/rbs/pull/1943))
* `#rand` ([#2007](https://github.com/ruby/rbs/pull/2007))
* Use untyped blocks ([#1935](https://github.com/ruby/rbs/pull/1935))
* Update docs for ruby-3.3.4 ([#1923](https://github.com/ruby/rbs/pull/1923))
* Update docs for ruby-3.3.3 ([#1889](https://github.com/ruby/rbs/pull/1889))

### Language updates

* Fix position for default_type ([#1997](https://github.com/ruby/rbs/pull/1997))
* Update `syntax.md` ([#1995](https://github.com/ruby/rbs/pull/1995))
* Improve generics ([#1994](https://github.com/ruby/rbs/pull/1994))
* Allow alias to module-self-type from module ([#1948](https://github.com/ruby/rbs/pull/1948))
* Make it a syntax error when the keys of keyword and record are duplicated. ([#1883](https://github.com/ruby/rbs/pull/1883))

### Library changes

* Omit unnecessary field from location range ([#1788](https://github.com/ruby/rbs/pull/1788))
* Handle ISEQs compiled by Prism ([#1983](https://github.com/ruby/rbs/pull/1983))
* Add AST Visitor ([#1975](https://github.com/ruby/rbs/pull/1975))
* Make the extension `GC.auto_compact` compatible ([#1971](https://github.com/ruby/rbs/pull/1971))
* Make `FileFinder` faster by globbing all possible files ([#1965](https://github.com/ruby/rbs/pull/1965))
* Fix #1905: Fix typo in `UntypedFunction` ([#1906](https://github.com/ruby/rbs/pull/1906))

#### rbs prototype

* [prototype] Remove redundant access modifier ([#1914](https://github.com/ruby/rbs/pull/1914))

### Miscellaneous

* Allow RBS/Style/InitializeReturnType ([#2008](https://github.com/ruby/rbs/pull/2008))
* docs: An interface can include interfaces only and not modules ([#1996](https://github.com/ruby/rbs/pull/1996))
* Fix the portability of an external command ([#2001](https://github.com/ruby/rbs/pull/2001))
* Skip `AbstractSyntaxTree` test with ruby-3.4 ([#2005](https://github.com/ruby/rbs/pull/2005))
* Fix test ([#1972](https://github.com/ruby/rbs/pull/1972))
* Use `File.open` instead of `IO.sysopen` ([#1912](https://github.com/ruby/rbs/pull/1912))

## 3.5.3 (2024-08-23)

### Library changes

* Backport GC fix PR ([#1977](https://github.com/ruby/rbs/pull/1977))

## 3.5.2 (2024-07-10)

### Library changes

* Update docs for ruby-3.3.4 ([#1923](https://github.com/ruby/rbs/pull/1923), Backported in [#1925](https://github.com/ruby/rbs/pull/1925))
* Update docs for ruby-3.3.3 ([#1889](https://github.com/ruby/rbs/pull/1889), Backported in [#1925](https://github.com/ruby/rbs/pull/1925))
* Fix #1905: Fix typo in `UntypedFunction` ([#1906](https://github.com/ruby/rbs/pull/1906), Backported in [#1925](https://github.com/ruby/rbs/pull/1925))

### Miscellaneous

* Use `File.open` instead of `IO.sysopen` ([#1913](https://github.com/ruby/rbs/pull/1913))

## 3.5.1 (2024-06-07)

### Library changes

* Add explicit dependency on the `logger` gem ([#1865](https://github.com/ruby/rbs/pull/1865))
* Make c99, c23 compatible ([#1870](https://github.com/ruby/rbs/pull/1870))

### Miscellaneous

* Don't try to sign git commits when running tests ([#1867](https://github.com/ruby/rbs/pull/1867))

## 3.5.0 (2024-06-06)

### Signature updates

* `net-http` headers ([#1750](https://github.com/ruby/rbs/pull/1750))
* `CSV.foreach` ([#1738](https://github.com/ruby/rbs/pull/1738))
* `Enumerator#initialize` ([#1801](https://github.com/ruby/rbs/pull/1801))
* `Float#divmod`, `Rational#divmod` ([#1868](https://github.com/ruby/rbs/pull/1868))
* `GC` ([#1530](https://github.com/ruby/rbs/pull/1530))
* `Integer#pow` ([#1706](https://github.com/ruby/rbs/pull/1706))
* `Kernel.rand` ([#1783](https://github.com/ruby/rbs/pull/1783))
* `Kernel#extend` ([#1769](https://github.com/ruby/rbs/pull/1769))
* `Module#include`, `Module#prepend` ([#1769](https://github.com/ruby/rbs/pull/1769))
* `Proc#parameters` ([#1819](https://github.com/ruby/rbs/pull/1819))
* `Range#step` ([#1709](https://github.com/ruby/rbs/pull/1709))
* `Regexp.union` ([#1809](https://github.com/ruby/rbs/pull/1809))
* `RubyVM::YJIT.enable` ([#1812](https://github.com/ruby/rbs/pull/1812))
* `RubyVM::YJIT`, `RubyVM::RJIT` ([#1746](https://github.com/ruby/rbs/pull/1746))
* `String#bytesplice` ([#1793](https://github.com/ruby/rbs/pull/1793))
* `String#gsub!` ([#1768](https://github.com/ruby/rbs/pull/1768))
* `Symbol#=~` ([#1704](https://github.com/ruby/rbs/pull/1704))
* `Time#initialize` ([#1771](https://github.com/ruby/rbs/pull/1771))
* `Time#zone` ([#1770](https://github.com/ruby/rbs/pull/1770))
* `Timeout::ExitException`  ([#1803](https://github.com/ruby/rbs/pull/1803))
* `URI::MailTo` ([#1858](https://github.com/ruby/rbs/pull/1858))
* Update docs ([#1811](https://github.com/ruby/rbs/pull/1811), [#1699](https://github.com/ruby/rbs/pull/1699))

### Language updates

* Fix proc type syntax ([#1807](https://github.com/ruby/rbs/pull/1807))
* Function types with untyped parameter ([#1806](https://github.com/ruby/rbs/pull/1806))
* Fix record type ([#1732](https://github.com/ruby/rbs/pull/1732))
* parser: enable record types with optional fields ([#1717](https://github.com/ruby/rbs/pull/1717))

### Library changes

* Drop dependency on `abbrev` ([#1866](https://github.com/ruby/rbs/pull/1866))
* Fix source display ([#1832](https://github.com/ruby/rbs/pull/1832))
* Include trivia tokens to lex result ([#1831](https://github.com/ruby/rbs/pull/1831))
* Implement token list API ([#1829](https://github.com/ruby/rbs/pull/1829))
* Fix memory leak when ParsingError ([#1830](https://github.com/ruby/rbs/pull/1830))
* [rbs diff] Fix error when empty manifest.yaml ([#1762](https://github.com/ruby/rbs/pull/1762))
* Remove deprecated API since RBS v1 ([#1805](https://github.com/ruby/rbs/pull/1805))
* Use array instead of linked list for rbs  location's child ([#1786](https://github.com/ruby/rbs/pull/1786))
* Fix SEGV with parse negative position ([#1790](https://github.com/ruby/rbs/pull/1790))
* Add location for attribute ([#1787](https://github.com/ruby/rbs/pull/1787))
* Always be private ([#1774](https://github.com/ruby/rbs/pull/1774))
* Prevent resource leak with type/method-type parser when reaching EOF. ([#1742](https://github.com/ruby/rbs/pull/1742))
* Allow loading RBS from different version of a gem ([#1731](https://github.com/ruby/rbs/pull/1731))
* Fix method name tag on assertion ([#1705](https://github.com/ruby/rbs/pull/1705))
* Improve interface check by method parameters ([#1698](https://github.com/ruby/rbs/pull/1698))

#### rbs prototype

* Fix prototype for ruby-3.4 ([#1760](https://github.com/ruby/rbs/pull/1760))
* Fix 3.4 `prototype rb` failure ([#1713](https://github.com/ruby/rbs/pull/1713))
* Add Numeric Node's check for parse rbs ([#1711](https://github.com/ruby/rbs/pull/1711))

#### rbs collection

* Fix RBS loading precedence ([#1720](https://github.com/ruby/rbs/pull/1720))
* Refactor Git source by using `git?` method ([#1701](https://github.com/ruby/rbs/pull/1701))

### Miscellaneous

* Introduce RaaP for testing of signature ([#1810](https://github.com/ruby/rbs/pull/1810))
* Revise .gemspec to remove `Gemfile` and `Gemfile.lock` ([#1856](https://github.com/ruby/rbs/pull/1856))
* Add link to gem_rbs_collection to README ([#1827](https://github.com/ruby/rbs/pull/1827))
* Remove `Gemfile.lock` from the gem package ([#1823](https://github.com/ruby/rbs/pull/1823))
* Modify template to use TestHelper ([#1776](https://github.com/ruby/rbs/pull/1776))
* Should focus on `String.new` not `String#initialize`. ([#1789](https://github.com/ruby/rbs/pull/1789))
* Make the test suite compatible with `--enable-frozen-string-literal` ([#1767](https://github.com/ruby/rbs/pull/1767))
* Stop reusing `fd` ([#1752](https://github.com/ruby/rbs/pull/1752))
* Fix test failures on Ruby 3.4 ([#1734](https://github.com/ruby/rbs/pull/1734))
* Stop trace object allocations during test ([#1724](https://github.com/ruby/rbs/pull/1724))
* Fix head test fails ([#1719](https://github.com/ruby/rbs/pull/1719))

## 3.4.4 (2024-02-08)

### Miscellaneous

* Backport [#1752](https://github.com/ruby/rbs/pull/1752) ([#1753](https://github.com/ruby/rbs/pull/1753))

## 3.4.3 (2024-01-25)

### Library changes

#### rbs collection

* Backport [#1731](https://github.com/ruby/rbs/pull/1731) ([#1735](https://github.com/ruby/rbs/pull/1735))

## 3.4.2 (2024-01-19)

### Miscellaneous

* Backport [#1724](https://github.com/ruby/rbs/pull/1724) ([#1726](https://github.com/ruby/rbs/pull/1726))

## 3.4.1 (2023-12-26)

### Signature updates

* Update embedded RDoc based on ruby-3.3.0 (Backport #1699) ([#1700](https://github.com/ruby/rbs/pull/1700))

## 3.4.0 (2023-12-21)

### Signature updates

* minitest's lifecycle hooks ([#1694](https://github.com/ruby/rbs/pull/1694))
* `IO.popen`, `IO#read` ([#1693](https://github.com/ruby/rbs/pull/1693))

### Library changes

* Fix klass name if klass is singleton_class ([#1695](https://github.com/ruby/rbs/pull/1695))

### Miscellaneous

* Suppress warnings on testing ([#1691](https://github.com/ruby/rbs/pull/1691))

## 3.4.0.pre.1 (2023-12-19)

### Signature updates

* Changes in ruby-3.3 ([#1684](https://github.com/ruby/rbs/pull/1684), [#1674](https://github.com/ruby/rbs/pull/1674))
* Embedded docs update ([#1689](https://github.com/ruby/rbs/pull/1689), [#1676](https://github.com/ruby/rbs/pull/1676))
* delegate ([#1677](https://github.com/ruby/rbs/pull/1677))
* `MatchData` ([#1651](https://github.com/ruby/rbs/pull/1651))
* `String` ([#1672](https://github.com/ruby/rbs/pull/1672), [#1641](https://github.com/ruby/rbs/pull/1641))
* `Struct` ([#1621](https://github.com/ruby/rbs/pull/1621))
* `Warning` categories ([#1576](https://github.com/ruby/rbs/pull/1576))
* `ConditionVariable#wait` ([#1670](https://github.com/ruby/rbs/pull/1670))
* `Date.parse` ([#1653](https://github.com/ruby/rbs/pull/1653))
* `IO#gets` ([#1620](https://github.com/ruby/rbs/pull/1620))
* `Kernel.Pathname` ([#1681](https://github.com/ruby/rbs/pull/1681))
* `Resolv::DNS::Resource::Generic.create` ([#1655](https://github.com/ruby/rbs/pull/1655))
* `Thread#raise` ([#1668](https://github.com/ruby/rbs/pull/1668))
* Builtin types ([#1680](https://github.com/ruby/rbs/pull/1680), [#1644](https://github.com/ruby/rbs/pull/1644))

### Library changes

* Add more details error message for `UnresolvedOverloadingError` ([#1688](https://github.com/ruby/rbs/pull/1688))
* Add `with_***` helpers ([#1687](https://github.com/ruby/rbs/pull/1687))
* Fix lexer ([#1685](https://github.com/ruby/rbs/pull/1685))
* Support `Delegator` object ([#1682](https://github.com/ruby/rbs/pull/1682))
* Should raise `ParsingError` instead of NoMethodError ([#1675](https://github.com/ruby/rbs/pull/1675))
* Implement multiple validate ([#1648](https://github.com/ruby/rbs/pull/1648))
* Add `RBS::UnitTest` modules ([#1660](https://github.com/ruby/rbs/pull/1660))
* Not cached as it may be replaced ([#1656](https://github.com/ruby/rbs/pull/1656))
* Should raise `ParsingError` instead of ArgumentError ([#1652](https://github.com/ruby/rbs/pull/1652))
* Added `WithEnum#but` and `with_range` ([#1649](https://github.com/ruby/rbs/pull/1649))
* Bugfix; `.respond_to` is no longer used in `Rbs::Test::Errors#inspect_` ([#1650](https://github.com/ruby/rbs/pull/1650))
* Add `--detail` option to rbs diff ([#1623](https://github.com/ruby/rbs/pull/1623))
* Fix parse error when quoted required keywords ([#1627](https://github.com/ruby/rbs/pull/1627))

#### rbs collection

* Fix order for collection sources ([#1659](https://github.com/ruby/rbs/pull/1659))
* [collection] Ignore directory starting with `_` as version from git source ([#1657](https://github.com/ruby/rbs/pull/1657))

### Miscellaneous

* Fix token names in comments ([#1658](https://github.com/ruby/rbs/pull/1658))
* Small bugfix to have guaranteed print out instance variables ([#1642](https://github.com/ruby/rbs/pull/1642))
* Fix flaky test ([#1616](https://github.com/ruby/rbs/pull/1616))
* Update syntax.md ([#1634](https://github.com/ruby/rbs/pull/1634))
* Add steep typecheck workflow ([#1632](https://github.com/ruby/rbs/pull/1632))
* Add doc for Generating RBS ([#1617](https://github.com/ruby/rbs/pull/1617))
* updated test harness utils ([#1600](https://github.com/ruby/rbs/pull/1600))

## 3.3.2 (2023-11-21)

### Miscellaneous

* Require `bundler` explicitly for ruby/ruby CI ([#1629](https://github.com/ruby/rbs/pull/1629))

## 3.3.1 (2023-11-21)

### Library changes

* Allow to use RBS in symlinked tree ([#1624](https://github.com/ruby/rbs/pull/1624))
* Should escape if param name include not simple-word ([#1618](https://github.com/ruby/rbs/pull/1618))

#### rbs collection

* Load Bundler lazily ([#1612](https://github.com/ruby/rbs/pull/1612))

### Miscellaneous

* Stop using `bundle` command ([#1619](https://github.com/ruby/rbs/pull/1619))

## 3.3.0 (2023-11-09)

### Library changes

* Stop exiting with error when syntax error detected during validation ([#1603](https://github.com/ruby/rbs/pull/1603))
* [rbs diff] Load dependencies from manifest.yaml ([#1602](https://github.com/ruby/rbs/pull/1602))
* [rbs diff] Resolve constants name ([#1601](https://github.com/ruby/rbs/pull/1601))

## 3.3.0.pre.2 (2023-11-02)

### Signature updates

* net/smtp ([#1560](https://github.com/ruby/rbs/pull/1560))
* open-uri ([#1592](https://github.com/ruby/rbs/pull/1592))
* `Set` ([#1595](https://github.com/ruby/rbs/pull/1595))

### Library changes

* Fixup validation ([#1596](https://github.com/ruby/rbs/pull/1596))
* Print type syntax errors instead of raising error ([#1594](https://github.com/ruby/rbs/pull/1594))

#### rbs prototype

* Fix error when MASGN ([#1593](https://github.com/ruby/rbs/pull/1593))
* Support cases where `Module#constants` has been redefined ([#1591](https://github.com/ruby/rbs/pull/1591))
* [prototype runtime] Fix error if direct inherited ([#1590](https://github.com/ruby/rbs/pull/1590))

## 3.3.0.pre.1 (2023-10-31)

### Signature updates

* json ([#1575](https://github.com/ruby/rbs/pull/1575))
* net-protocol ([#1559](https://github.com/ruby/rbs/pull/1559))
* pp ([#1545](https://github.com/ruby/rbs/pull/1545))
* psych ([#1527](https://github.com/ruby/rbs/pull/1527))
* `BasicObject` ([#1506](https://github.com/ruby/rbs/pull/1506))
* `ConditionVariable` ([#1468](https://github.com/ruby/rbs/pull/1468))
* `Encoding` ([#1510](https://github.com/ruby/rbs/pull/1510))
* `Exception` ([#1529](https://github.com/ruby/rbs/pull/1529))
* `FalseClass` ([#1478](https://github.com/ruby/rbs/pull/1478), [#1487](https://github.com/ruby/rbs/pull/1487))
* `Fiber` ([#1463](https://github.com/ruby/rbs/pull/1463))
* `FileTest` ([#1500](https://github.com/ruby/rbs/pull/1500))
* `Kernel` ([#1567](https://github.com/ruby/rbs/pull/1567), [#1518](https://github.com/ruby/rbs/pull/1518), [#1504](https://github.com/ruby/rbs/pull/1504), [#1487](https://github.com/ruby/rbs/pull/1487))
* `Marshal` ([#1524](https://github.com/ruby/rbs/pull/1524))
* `Math` ([#1533](https://github.com/ruby/rbs/pull/1533))
* `Mutex` ([#1468](https://github.com/ruby/rbs/pull/1468))
* `NilClass` ([#1478](https://github.com/ruby/rbs/pull/1478), [#1487](https://github.com/ruby/rbs/pull/1487))
* `Object` ([#1504](https://github.com/ruby/rbs/pull/1504))
* `Queue` ([#1468](https://github.com/ruby/rbs/pull/1468))
* `RbConfig` ([#1534](https://github.com/ruby/rbs/pull/1534))
* `Signal` ([#1512](https://github.com/ruby/rbs/pull/1512))
* `SizedQueue` ([#1468](https://github.com/ruby/rbs/pull/1468))
* `ThreadGroup` ([#1509](https://github.com/ruby/rbs/pull/1509))
* `TrueClass` ([#1478](https://github.com/ruby/rbs/pull/1478), [#1487](https://github.com/ruby/rbs/pull/1487))
* `Warning` ([#1477](https://github.com/ruby/rbs/pull/1477))
* `Enumerator.produce` ([#1516](https://github.com/ruby/rbs/pull/1516))
* `Module.define_method` ([#1557](https://github.com/ruby/rbs/pull/1557))
* `URI.join` ([#1580](https://github.com/ruby/rbs/pull/1580))
* `YAML.unsafe_load` ([#1528](https://github.com/ruby/rbs/pull/1528))
* `builtin.rbs`([#1455](https://github.com/ruby/rbs/pull/1455), [#1469](https://github.com/ruby/rbs/pull/1469))
* `constants.rbs` ([#1531](https://github.com/ruby/rbs/pull/1531))
* `::interned` ([#1499](https://github.com/ruby/rbs/pull/1499), [#1488](https://github.com/ruby/rbs/pull/1488))

### Language updates

* Add `__todo__` type ([#1520](https://github.com/ruby/rbs/pull/1520))

### Library changes

* Colored diff ([#1589](https://github.com/ruby/rbs/pull/1589))
* Fix the issue where ::array[T] would be interpreted as ::array, and type mismatch ([#1491](https://github.com/ruby/rbs/pull/1491))
* Add `Abbrev` to runtime dependency ([#1573](https://github.com/ruby/rbs/pull/1573))
* Avoid an error when parsing files including SyntaxError ([#1574](https://github.com/ruby/rbs/pull/1574))
* Implement rbs diff command ([#1459](https://github.com/ruby/rbs/pull/1459))
* Add another type syntax validation ([#1566](https://github.com/ruby/rbs/pull/1566))
* Mark global variables ([#1551](https://github.com/ruby/rbs/pull/1551))
* Show location of type by method command ([#1537](https://github.com/ruby/rbs/pull/1537))
* Make alias and interface names absolute ([#1502](https://github.com/ruby/rbs/pull/1502))
* Better record type format ([#1501](https://github.com/ruby/rbs/pull/1501))
* Should keep escape of alias and attr family ([#1486](https://github.com/ruby/rbs/pull/1486))
* Fix error when undefined interface or alias ([#1465](https://github.com/ruby/rbs/pull/1465))

#### rbs prototype

* [prototype runtime --todo] Consider accessibility mismatch ([#1539](https://github.com/ruby/rbs/pull/1539))
* Better support for inherited class of `Struct` or `Data` by prototype runtime ([#1571](https://github.com/ruby/rbs/pull/1571))
* prototype rb: Ignore ITER (method call with block) ([#1562](https://github.com/ruby/rbs/pull/1562))
* [prototype runtime] Add --autoload option ([#1561](https://github.com/ruby/rbs/pull/1561))
* Implement todo option ([#1470](https://github.com/ruby/rbs/pull/1470))
* [prototype runtime] Fix error when constant is a BasicObject instance. ([#1485](https://github.com/ruby/rbs/pull/1485))
* [prototype runtime] Const name should be absolute ([#1490](https://github.com/ruby/rbs/pull/1490))
* [prototype runtime] Optimize performance ([#1495](https://github.com/ruby/rbs/pull/1495))
* [prototype runtime] Fix error when output Object constants ([#1472](https://github.com/ruby/rbs/pull/1472))
* [prototype runtime] Support prepend mixin ([#1460](https://github.com/ruby/rbs/pull/1460))
* [prototype runtime] Avoid alias for methods defined in another module ([#1457](https://github.com/ruby/rbs/pull/1457))

#### rbs collection

* [Collection] Simple colorize collection text like Bundler ([#1558](https://github.com/ruby/rbs/pull/1558))
* Delete `sources` section from lockfile ([#1544](https://github.com/ruby/rbs/pull/1544))

### Miscellaneous

* Add in with_XXX methods ([#1492](https://github.com/ruby/rbs/pull/1492))
* Updated binding to use modern kernel tests ([#1507](https://github.com/ruby/rbs/pull/1507))
* Remove all non-required methods from testing types ([#1541](https://github.com/ruby/rbs/pull/1541))
* Add `assert_type` assertion to confirm if the value has a type ([#1532](https://github.com/ruby/rbs/pull/1532))
* Add `#assert_const_type` and guide to use type assertions ([#1514](https://github.com/ruby/rbs/pull/1514))
* Improve skip test message ([#1476](https://github.com/ruby/rbs/pull/1476))
* Type check ([#1467](https://github.com/ruby/rbs/pull/1467))
* Type check RDoc related Ruby code ([#1466](https://github.com/ruby/rbs/pull/1466))
* Fix CI failure ([#1464](https://github.com/ruby/rbs/pull/1464))

## 3.2.2 (2023-09-28)

### Library changes

* Mark global variables ([\#1552](https://github.com/ruby/rbs/pull/1552), Backport [\#1551](https://github.com/ruby/rbs/pull/1551))

## 3.2.1 (2023-08-29)

### Signature updates

* `Fiber` (Backport [#1463](https://github.com/ruby/rbs/pull/1463))

### Library changes

* Fix error when undefined interface or alias (Backport [#1465](https://github.com/ruby/rbs/pull/1465))

### Miscellaneous

* Improve skip test message (Backport [#1476](https://github.com/ruby/rbs/pull/1476))
* Fix CI failure (Backport [#1464](https://github.com/ruby/rbs/pull/1464))

## 3.2.0 (2023-08-22)

### Signature updates

* `Binding` ([#1451](https://github.com/ruby/rbs/pull/1451))
* `Kernel` ([#1445](https://github.com/ruby/rbs/pull/1445), [#1444](https://github.com/ruby/rbs/pull/1444), [#1443](https://github.com/ruby/rbs/pull/1443), [#1441](https://github.com/ruby/rbs/pull/1441), [#1440](https://github.com/ruby/rbs/pull/1440))
* `Errno` ([#1450](https://github.com/ruby/rbs/pull/1450))

### Library changes

#### rbs collection

* Fix LockfileGenerator crashes if failed to get deps for locked source ([#1413](https://github.com/ruby/rbs/pull/1413))

## 3.2.0.pre.1 (2023-08-18)

### Signature updates

* observable ([#1424](https://github.com/ruby/rbs/pull/1424))
* uri ([#1371](https://github.com/ruby/rbs/pull/1371))
* zlib ([#1389](https://github.com/ruby/rbs/pull/1389))
* `IO` ([#1399](https://github.com/ruby/rbs/pull/1399))
* `::DATA` ([#1395](https://github.com/ruby/rbs/pull/1395))
* `$DEBUG` ([#1427](https://github.com/ruby/rbs/pull/1427))
* `ConditionVariable#wait` ([#1397](https://github.com/ruby/rbs/pull/1397))
* `Kernel::exit!` ([#1396](https://github.com/ruby/rbs/pull/1396))
* `Kernel#exec` ([#1368](https://github.com/ruby/rbs/pull/1368))
* `Kernel#p`, `Kernel#pp` ([#1342](https://github.com/ruby/rbs/pull/1342))
* `Logger.new` ([#1423](https://github.com/ruby/rbs/pull/1423))
* `Module` ([#1321](https://github.com/ruby/rbs/pull/1321))
* `Object#define_singleton_method` ([#1431](https://github.com/ruby/rbs/pull/1431))
* `Object#enum_for` ([#1327](https://github.com/ruby/rbs/pull/1327))
* `StringIO#truncate` ([#1425](https://github.com/ruby/rbs/pull/1425))
* `Thread#raise` ([#1426](https://github.com/ruby/rbs/pull/1426))
* `Time#round`, `Time#floor`, `Time#ceil` ([#1337](https://github.com/ruby/rbs/pull/1337))
* Remove `TRUE`, `FALSE`, and `NIL` constants ([#1432](https://github.com/ruby/rbs/pull/1432))
* Updated builtin.rbs's interfaces ([#1429](https://github.com/ruby/rbs/pull/1429))
* Convert `| nil` to `?` in global_variables.rbs ([#1430](https://github.com/ruby/rbs/pull/1430))
* Remove references to the transient heap ([#1374](https://github.com/ruby/rbs/pull/1374))

### Library changes

* subtract: Remove RBS file if the subtracted definition is empty ([#1385](https://github.com/ruby/rbs/pull/1385))
* subtract: Filter redundant access modifiers ([#1384](https://github.com/ruby/rbs/pull/1384))
* Fix module alias normalizations ([#1393](https://github.com/ruby/rbs/pull/1393))
* Reduce memory allocation on TypeName#initialize ([#1363](https://github.com/ruby/rbs/pull/1363))
* Fix a type error in FileFinder ([#1326](https://github.com/ruby/rbs/pull/1326))
* Add signatures about DetailedMessage ([#1323](https://github.com/ruby/rbs/pull/1323))
* Make TypeNameResolver more compatible with Ruby ([#1373](https://github.com/ruby/rbs/pull/1373))
* Drop support of Ruby 2.x ([#1364](https://github.com/ruby/rbs/pull/1364))
* Print detailed_message on parse command ([#1329](https://github.com/ruby/rbs/pull/1329))
* Relax rdoc version requirement ([#1333](https://github.com/ruby/rbs/pull/1333))
* Support keyword argument to #detailed_message ([#1328](https://github.com/ruby/rbs/pull/1328))

#### rbs prototype

* Avoid to generate anonymous or internal module ([#1421](https://github.com/ruby/rbs/pull/1421))
* Fix type name for prototype rbi ([#1386](https://github.com/ruby/rbs/pull/1386))
* Support type params for known signatures ([#1408](https://github.com/ruby/rbs/pull/1408))
* Super class should be absolute paths. ([#1415](https://github.com/ruby/rbs/pull/1415))
* prototype rb: Fix a crash by "include foo" ([#1419](https://github.com/ruby/rbs/pull/1419))
* Add `--outline` option to `prototype runtime` to skip generating method definition ([#1404](https://github.com/ruby/rbs/pull/1404))
* [rbs prototype] Follow up ivar/cvar feature ([#1361](https://github.com/ruby/rbs/pull/1361))
* prototype rb: Extract instance variables and class variables ([#1343](https://github.com/ruby/rbs/pull/1343))

#### rbs collection

* Print warning when specified gem not found ([#1367](https://github.com/ruby/rbs/pull/1367))
* Generate gem specific sources in lockfile ([#1402](https://github.com/ruby/rbs/pull/1402))
* Spec may be missing when `rbs_collection.yaml` declares dependency ([#1378](https://github.com/ruby/rbs/pull/1378))

### Miscellaneous

* Remove ruby-lsp && bundle update --bundler ([#1420](https://github.com/ruby/rbs/pull/1420))
* Delete all remaining set-related descriptions. ([#1324](https://github.com/ruby/rbs/pull/1324))
* Add customized test runner to accept minitest options ([#1392](https://github.com/ruby/rbs/pull/1392))
* Specify `rbs` syntax to code snippets in Markdown files ([#1366](https://github.com/ruby/rbs/pull/1366))
* Fix typo in parser.rbs ([#1352](https://github.com/ruby/rbs/pull/1352))
* Fix broken CI ([#1353](https://github.com/ruby/rbs/pull/1353))
* Handle connection error with Resolv_test.rb ([#1356](https://github.com/ruby/rbs/pull/1356))
* Removed Prime signature and tests ([#1355](https://github.com/ruby/rbs/pull/1355))

## 3.1.3 (2023-07-31)

### Library changes

#### rbs collection

* Generate gem specific sources in lockfile (Backport [#1402](https://github.com/ruby/rbs/pull/1402))

## 3.1.2 (2023-07-27)

⚠️ Note that this patch level release may report additional validation errors that is caused by fix of type name resolution ([#1373](https://github.com/ruby/rbs/pull/1373)).

### Library changes

* Make `TypeNameResolver` more compatible with Ruby (Backport [#1373](https://github.com/ruby/rbs/pull/1373))
* Fix module alias normalizations (Backport [#1393](https://github.com/ruby/rbs/pull/1393))

## 3.1.1 (2023-07-18)

### Signature updates

* Removed `GC.verify_transient_heap_internal_consistency` (Backport [#1374](https://github.com/ruby/rbs/pull/1374))

### Library changes

* Relax rdoc version requirement in RDoc plugin (Backport [#1333](https://github.com/ruby/rbs/pull/1333))

#### rbs collection

* Spec may be missing when rbs_collection.yaml declares dependency (Backport [#1378](https://github.com/ruby/rbs/pull/1378))

### Miscellaneous

* Handle connection error with Resolv_test.rb (Backport [#1356](https://github.com/ruby/rbs/pull/1356))
* Fix broken CI (Backport [#1353](https://github.com/ruby/rbs/pull/1353))

## 3.1.0 (2023-04-26)

### Signature updates

* `Module#attr_*` methods ([#1300](https://github.com/ruby/rbs/pull/1300))
* `Object#define_singleton_method` ([#1299](https://github.com/ruby/rbs/pull/1299))
* `String#scan` ([#1297](https://github.com/ruby/rbs/pull/1297))
* Removed `Gem.datadir` ([#1273](https://github.com/ruby/rbs/pull/1273))

### Language updates

### Library changes

* Use fenced code block instead of indented one in .rbs files ([#1312](https://github.com/ruby/rbs/pull/1312))
* Fix single use clause `:type_name` location ([#1307](https://github.com/ruby/rbs/pull/1307))
* Fix `InvalidOverloadMethodError` on overloading extended method ([#1294](https://github.com/ruby/rbs/pull/1294))
* Fix super class of `NoTypeFoundError` from `BaseError` to `DefinitionError` ([#1290](https://github.com/ruby/rbs/pull/1290))
* Support `#detailed_message` for `NonregularTypeAliasError`, `CyclicTypeParameterBound`, `InconsistentClassModuleAliasError` and `CyclicClassAliasDefinitionError` ([#1289](https://github.com/ruby/rbs/pull/1289))
* Implement `rbs subtract` command ([#1287](https://github.com/ruby/rbs/pull/1287))
* Support `#detailed_message` for `InvalidVarianceAnnotationError`, `RecursiveAliasDefinitionError`, `MixinClassError` and `RecursiveTypeAliasError` ([#1285](https://github.com/ruby/rbs/pull/1285))
* Support `#detailed_message` for `NoTypeError`, `InheritModuleError`, `NoSelfTypeFoundError` and `NoMixinFoundError` ([#1280](https://github.com/ruby/rbs/pull/1280))
* Validate duplicate type aliases ([#1279](https://github.com/ruby/rbs/pull/1279))
* Add `-e`, `--type`, and `--method-type` options to `rbs parse` ([#1252](https://github.com/ruby/rbs/pull/1252))

#### rbs prototype

* Convert `include` in `class << self` to `extend` by `prototype rb` ([#1255](https://github.com/ruby/rbs/pull/1255))

#### rbs collection

* Skip loading `#gemspec` gems via RBS collection ([#1319](https://github.com/ruby/rbs/pull/1319))

### Miscellaneous

* Run `bundle install` automatically in `bin/steep` ([#1308](https://github.com/ruby/rbs/pull/1308))
* Fix warnings ([#1314](https://github.com/ruby/rbs/pull/1314))

## 3.0.4 (2023-03-13)

### Library changes

* Add missing implementation to support class/module alias ([#1271](https://github.com/ruby/rbs/pull/1271))

## 3.0.3 (2023-03-07)

### Library changes

* Fix `DefinitionBuilder` ([\#1268](https://github.com/ruby/rbs/pull/1268))

#### rbs collection

* Skip dependencies not included in `Gemfile.lock` ([\#1266](https://github.com/ruby/rbs/pull/1266))

### Miscellaneous

* Skip RBS validation on Ruby CI ([\#1264](https://github.com/ruby/rbs/pull/1264), [\#1263](https://github.com/ruby/rbs/pull/1263))

## 3.0.2 (2023-03-01)

### Library changes

* Delete `RubyVM::AbstractSyntaxTree` stub type definition ([#1259](https://github.com/ruby/rbs/pull/1259))

## 3.0.1 (2023-02-27)

### Library changes

* Ignore `set` library on loading ([#1251](https://github.com/ruby/rbs/pull/1251))

## 3.0.0 (2023-02-22)

Read [the release note](https://github.com/ruby/rbs/wiki/Release-Note-3.0) for the overview of the changes.

### Signature updates

* Ruby 3.2 core updates ([#1238](https://github.com/ruby/rbs/pull/1238), [#1246](https://github.com/ruby/rbs/pull/1246), [#1242](https://github.com/ruby/rbs/pull/1242))
* Ruby 3.2 stdlib updates ([#1240](https://github.com/ruby/rbs/pull/1240))
* Ruby 3.2 incompatibilities ([#1241](https://github.com/ruby/rbs/pull/1241))
* ripper ([#1193](https://github.com/ruby/rbs/pull/1193))
* `RubyVM::AbstractSyntaxTree` ([#1189](https://github.com/ruby/rbs/pull/1189))
* `Array#zip` ([#1232](https://github.com/ruby/rbs/pull/1232))
* `Enumerable#zip` ([#1232](https://github.com/ruby/rbs/pull/1232))
* `Module#attr_*` ([#1218](https://github.com/ruby/rbs/pull/1218), [#1229](https://github.com/ruby/rbs/pull/1229))
* `Regexp.new` ([#1237](https://github.com/ruby/rbs/pull/1237))
* `Regexp#=~` ([#1195](https://github.com/ruby/rbs/pull/1195))
* `Socket.new` ([#1185](https://github.com/ruby/rbs/pull/1185))
* `Object::name` ([#1204](https://github.com/ruby/rbs/pull/1204))
* `_ToProc#to_proc` ([#1223](https://github.com/ruby/rbs/pull/1223))
* Add `implicitly-returns-nil` annotations ([#1226](https://github.com/ruby/rbs/pull/1226))

### Language updates

#### Class/module alias declaration

* Spec: https://hackmd.io/SAE7AGD6SMSJFnYiR2M5-Q?view
* Pull requests: [#1219](https://github.com/ruby/rbs/pull/1219), [#1220](https://github.com/ruby/rbs/pull/1220)

#### `use` directives

* Spec: https://hackmd.io/pQehT1u1RfSj6lBcdgW8hg
* Pull requests: [#1224](https://github.com/ruby/rbs/pull/1224), [#1234](https://github.com/ruby/rbs/pull/1234), [#1235](https://github.com/ruby/rbs/pull/1235)

#### Per-overload annotations

* Pull requests: [#1174](https://github.com/ruby/rbs/pull/1174)

### Library changes

* `Environment` cleanup ([#1209](https://github.com/ruby/rbs/pull/1209), [#1236](https://github.com/ruby/rbs/pull/1236), [#1247](https://github.com/ruby/rbs/pull/1247))
* Add "Expected one argument." to `rbs ancestors` command ([#1245](https://github.com/ruby/rbs/pull/1245))
* Implement `#detailed_message` for `ParsingError` ([#1166](https://github.com/ruby/rbs/pull/1166))
* Support interface in class/module for `Sorter` ([#1191](https://github.com/ruby/rbs/pull/1191))
* Use `-std=gnu99` instead of `-std=c99` ([#973](https://github.com/ruby/rbs/pull/973))
* Add `rbs_` prefix to `skip` function ([#1186](https://github.com/ruby/rbs/pull/1186))
* Better method definition resolution for overload/mixin combination ([#1199](https://github.com/ruby/rbs/pull/1199))
* Escaping for keywords is no longer necessary ([#1178](https://github.com/ruby/rbs/pull/1178))
* Fix super-class location ([#1172](https://github.com/ruby/rbs/pull/1172))

#### rbs prototype

* Introduce new heuristics for block types ([#1180](https://github.com/ruby/rbs/pull/1180))

#### rbs collection

* Install only *library* dependencies ([#1222](https://github.com/ruby/rbs/pull/1222), [#1228](https://github.com/ruby/rbs/pull/1228))
* Add *local source* ([#854](https://github.com/ruby/rbs/pull/854))
* Refactor collection ([#1149](https://github.com/ruby/rbs/pull/1149))
* Let `#resolved_revision` return the last fetched commit ([#1215](https://github.com/ruby/rbs/pull/1215))

### Miscellaneous

* Update `syntax.md` ([#1243](https://github.com/ruby/rbs/pull/1243))
* Add link to `RBS by Example` to `README.md` [#1225](https://github.com/ruby/rbs/pull/1225)
* Make `rake stdlib_test` run with single process ([#1207](https://github.com/ruby/rbs/pull/1207), [#1211](https://github.com/ruby/rbs/pull/1211), [#1231](https://github.com/ruby/rbs/pull/1231))
* Remove `steep/*` files from `.gem` ([#1214](https://github.com/ruby/rbs/pull/1214), [#1216](https://github.com/ruby/rbs/pull/1216))

## 2.8.4 (2023-01-20)

### Miscellaneous

* Make `rake stdlib_test` run with single process (Backport [#1207](https://github.com/ruby/rbs/pull/1207), [#1211](https://github.com/ruby/rbs/pull/1211))
* Remove `steep/*` files from `.gem` (Backport [#1214](https://github.com/ruby/rbs/pull/1214), [#1216](https://github.com/ruby/rbs/pull/1216))

## 2.8.3 (2023-01-06)

### Library changes

* Use `-std=gnu99` instead of `-std=c99` (Backport [#973](https://github.com/ruby/rbs/pull/973))
* Add `rbs_` prefix to `skip` function (Backport [#1186](https://github.com/ruby/rbs/pull/1186))

## 2.8.2 (2022-12-21)

### Signature updates

* `YAML.load` ([#1170](https://github.com/ruby/rbs/pull/1170))

### Library changes

* Add the methods of the `prepend`ed module itself ([#1182](https://github.com/ruby/rbs/pull/1182))

### Miscellaneous

* Add `TestSkip` module to skip tests that fails in ruby CI ([#1167](https://github.com/ruby/rbs/pull/1167))

## 2.8.1 (2022-11-28)

### Signature updates

* Update documents based on ruby-3.1.3 ([#1160](https://github.com/ruby/rbs/pull/1160))

### Library changes

#### rbs collection

* Delay loading `Gemfile` for unbundled environments ([#1161](https://github.com/ruby/rbs/pull/1161))

### Miscellaneous

* Fix collection tests ([#1159](https://github.com/ruby/rbs/pull/1159), [#1162](https://github.com/ruby/rbs/pull/1162))

## 2.8.0 (2022-11-24)

### Signature updates

* random ([#1144](https://github.com/ruby/rbs/pull/1144))

### Library changes

* Fix SEGV when give invalid object to `variables:` ([#1153](https://github.com/ruby/rbs/pull/1153))

### Miscellaneous

* Fix some typos ([#1155](https://github.com/ruby/rbs/pull/1155))
* Fix a formatting some YAML files ([#1154](https://github.com/ruby/rbs/pull/1154))

## 2.8.0.pre.1 (2022-11-17)

### Signature updates

* `IO` ([#1132](https://github.com/ruby/rbs/pull/1132))
* `IO::Buffer` ([#1137](https://github.com/ruby/rbs/pull/1137))
* `IPAddr` ([#1131](https://github.com/ruby/rbs/pull/1131), [#1151](https://github.com/ruby/rbs/pull/1151))
* `Socket` ([#1133](https://github.com/ruby/rbs/pull/1133), [#1134](https://github.com/ruby/rbs/pull/1134), [#1151](https://github.com/ruby/rbs/pull/1151))
* `Yaml` ([#1141](https://github.com/ruby/rbs/pull/1141))

### Library changes

* Make type/method-type parser more flexible about input position ([#1140](https://github.com/ruby/rbs/pull/1140))
* Raise an error if a class definition inherits a module ([#1152](https://github.com/ruby/rbs/pull/1152))
* Fix SEGV when parse with invalid content ([#1146](https://github.com/ruby/rbs/pull/1146))

#### rbs collection

* Support to call collection command in child dir ([#1025](https://github.com/ruby/rbs/pull/1025), [#1135](https://github.com/ruby/rbs/pull/1135))

### Miscellaneous

* Remove `rubygems` from `rbs`’s manifest ([#1150](https://github.com/ruby/rbs/pull/1150))
* Use `untyped` instead of `any` in `syntax.md` ([#1147](https://github.com/ruby/rbs/pull/1147))
* Fix typo and grammatical mistakes in "Generics" section of `syntax.md` ([#1127](https://github.com/ruby/rbs/pull/1127))
* Add a doc that describes tools related to RBS ([#1125](https://github.com/ruby/rbs/pull/1125))
* Add self-type-bindings to `syntax.md` ([#1123](https://github.com/ruby/rbs/pull/1123))
* Add documentation for `...` in `syntax.md` ([#1120](https://github.com/ruby/rbs/pull/1120))

## 2.7.0 (2022-10-07)

### Signature updates

* `Symbol#start_with?` ([#1118](https://github.com/ruby/rbs/pull/1118))
* `URL::*.build` ([#1118](https://github.com/ruby/rbs/pull/1118))

## 2.7.0.pre.3 (2022-10-06)

### Signature updates

* rubygems ([#1107](https://github.com/ruby/rbs/pull/1107))
* `OptionParser` ([#1114](https://github.com/ruby/rbs/pull/1114))

## 2.7.0.pre.2 (2022-09-20)

### Signature updates

* `Set` ([#1110](https://github.com/ruby/rbs/pull/1110))
* `RDoc::Markup` ([#1109](https://github.com/ruby/rbs/pull/1109))

### Library changes

* Always skip CR explicitly ([#1112](https://github.com/ruby/rbs/pull/1112))

### Miscellaneous

* Use `append_cflags` for other than GCC ([#1106](https://github.com/ruby/rbs/pull/1106))

## 2.7.0.pre.1 (2022-09-02)

See [Release Note 2.7](https://github.com/ruby/rbs/wiki/Release-Note-2.7) for the highlights of this release.

### Signature updates

* fiber ([#1071](https://github.com/ruby/rbs/pull/1071))
* `BigDecimal` ([#1053](https://github.com/ruby/rbs/pull/1053))
* `ERB::Util`, `ERB::DefMethod` ([#1074](https://github.com/ruby/rbs/pull/1074))
* `Float::Infinity` ([#1095](https://github.com/ruby/rbs/pull/1095))
* `Logger` ([#1046](https://github.com/ruby/rbs/pull/1046))
* `IO.pipe`, `IO.foreach` ([#1057](https://github.com/ruby/rbs/pull/1057))
* `Module#refine` ([#1064](https://github.com/ruby/rbs/pull/1064))
* `Regexp.new` ([#1059](https://github.com/ruby/rbs/pull/1059))
* `StringIO#write` ([#1065](https://github.com/ruby/rbs/pull/1065))
* `Warning.#warn`, `Kernel.#warn` ([#1056](https://github.com/ruby/rbs/pull/1056))

### Language updates

* Type of `self` in blocks/procs ([#1077](https://github.com/ruby/rbs/issues/1077), [#1101](https://github.com/ruby/rbs/pull/1101))

### Library changes

* RDoc plugin ([#1048](https://github.com/ruby/rbs/pull/1048))
* Dedupe method comments ([#1103](https://github.com/ruby/rbs/pull/1103))
* Reduce object allocations for GC ([#1102](https://github.com/ruby/rbs/pull/1102))
* Add `frozen_string_literal: true` ([#1100](https://github.com/ruby/rbs/pull/1100))
* Load dependencies on `-r` option also ([#1013](https://github.com/ruby/rbs/pull/1013))
* Fix DefinitionBuilder for methods aliased from module self constraints ([#1099](https://github.com/ruby/rbs/pull/1099))
* Fix RBS type definitions ([#1098](https://github.com/ruby/rbs/pull/1098))
* Give aliases of `.new` methods correct type ([#1097](https://github.com/ruby/rbs/pull/1097))
* `nil` versions are discouraged and will be deprecated in Rubygems 4 ([#1091](https://github.com/ruby/rbs/pull/1091))
* Fixes towards Rubygems 4.0 ([#1090](https://github.com/ruby/rbs/pull/1090))
* Remove redundant `add` for `Repository.default` ([#1062](https://github.com/ruby/rbs/pull/1062))
* Refactor: Use Repository in stdlib source ([#1063](https://github.com/ruby/rbs/pull/1063))
* Move `bin/sort` implementation to under `lib/` ([#1051](https://github.com/ruby/rbs/pull/1051))

#### rbs prototype

* Fix some error on `prototype runtime` ([#1055](https://github.com/ruby/rbs/pull/1055))
* Skip existing RBS files from batch `prototype` ([#1060](https://github.com/ruby/rbs/pull/1060))

### Miscellaneous

* Discard outputs from test code ([#1093](https://github.com/ruby/rbs/pull/1093))
* Skip testing visibility methods with Ruby 3.2 ([#1082](https://github.com/ruby/rbs/pull/1082))

## 2.6.0 (2022-06-22)

RBS 2.6 ships with `rbs prototype` commands improvements and signature updates.
New minitest RBS definitions will help you to type check your tests.

### Signature updates

* etc ([\#1006](https://github.com/ruby/rbs/pull/1006))
* minitest ([\#1009](https://github.com/ruby/rbs/pull/1009))
* date ([\#1033](https://github.com/ruby/rbs/pull/1033))
* `ENV`. `ARGF`, `Random::Base` ([\#1041](https://github.com/ruby/rbs/pull/1041))
* `Hash#dig` ([\#1011](https://github.com/ruby/rbs/pull/1011))
* `Hash#reject` ([\#1016](https://github.com/ruby/rbs/pull/1016))
* `Kernel?.fail` ([\#1026](https://github.com/ruby/rbs/pull/1026), [\#1028](https://github.com/ruby/rbs/pull/1028))
* `Rational#*` ([\#1012](https://github.com/ruby/rbs/pull/1012))
* `Regexp.new` ([\#1040](https://github.com/ruby/rbs/pull/1040))

### Library changes

* Add `visibility` columns in {AttrReader,AttrAccessor,AttrWriter}#to_json ([\#1014](https://github.com/ruby/rbs/pull/1014))
* Add support for RSpec's verifying doubles in test setup helper ([\#1015](https://github.com/ruby/rbs/pull/1015))
* Fix lexer to analyze escape characters ([\#1043](https://github.com/ruby/rbs/pull/1043))

#### rbs prototype

* [rb] Support the case where the return value is `self` ([\#1003](https://github.com/ruby/rbs/pull/1003))
* [rb] Lean to the optional side ([\#1021](https://github.com/ruby/rbs/pull/1021))
* [rb] Fix method commenting ([\#1027](https://github.com/ruby/rbs/pull/1027))
* [runtime] Fix decls of output from `Prototype::Runtime` ([\#1030](https://github.com/ruby/rbs/pull/1030))
* [runtime] Fix class name resolution of prototype runtime ([\#1032](https://github.com/ruby/rbs/pull/1032))
* [rbi] Improve `Prototype::RBI` ([\#1018](https://github.com/ruby/rbs/pull/1018))

### Miscellaneous

* Fix typo in `CONTRIBUTING.md` ([\#1004](https://github.com/ruby/rbs/pull/1004))
* Use manifest.yaml instead of manually for validate task ([\#1010](https://github.com/ruby/rbs/pull/1010))
* Update re2c ([\#1017](https://github.com/ruby/rbs/pull/1017))
* Type check `Prototype::RB` ([\#1019](https://github.com/ruby/rbs/pull/1019))
* Minor cleanup & fix ([\#1020](https://github.com/ruby/rbs/pull/1020))
* Fix type errors ([\#1023](https://github.com/ruby/rbs/pull/1023))
* Clarify GHA step name for rake annotate ([\#1024](https://github.com/ruby/rbs/pull/1024))
* Silence parser warning ([\#1039](https://github.com/ruby/rbs/pull/1039))
* Fix warnings ([\#1035](https://github.com/ruby/rbs/pull/1035))

## 2.5.1 (2022-06-19)

This is a maintenance release mainly to fix errors detected in CI of `ruby/ruby`.
Nothing changed related to the users of RBS gem.

### Miscellaneous

* Skip failing tests on ruby/ruby CI ([\#1036](https://github.com/ruby/rbs/pull/1036))
* Fix warnings ([\#1035](https://github.com/ruby/rbs/pull/1035) except changes on `test/tools/sort_test.rb` that is not included in this release)
* Fix `Regexp.new` argument ([\#1034](https://github.com/ruby/rbs/pull/1034))
* Fix errors with OpenSSL 3 ([\#1029](https://github.com/ruby/rbs/pull/1029))
* Fix `OpenSSL::PKey::PKeyError: EVP_PKEY_keygen: bad ffc parameters` with OpenSSL 3 ([\#1005](https://github.com/ruby/rbs/pull/1005))

## 2.5.0 (2022-05-20)

This is a minor release with tuple type/record type syntax updates and `rbs prototype` improvements.

* You can write empty tuple type as `[]`, instead of `[ ]`
* You can write empty record type as `{}`
* `prototype-rb` command with `--out-dir` option generates multiple RBS files in the directory

### Signature updates

* `Time` ([\#994](https://github.com/ruby/rbs/pull/994))

### Language updates

* Fix empty tuple type syntax and allow empty record type ([\#1001](https://github.com/ruby/rbs/pull/1001))

### Library changes

* Add `name` attribute in `MethodDefinition#to_json` ([\#997](https://github.com/ruby/rbs/pull/997))

#### rbs prototype

* Fix (`ArgumentError`) invalid byte sequence in UTF-8 ([\#995](https://github.com/ruby/rbs/pull/995))
* Fix broken args after parsed `decls` from `RBS::Prototype::RB` ([\#998](https://github.com/ruby/rbs/pull/998))
* Add *batch* mode to `rbs prototype` ([\#1000](https://github.com/ruby/rbs/pull/1000))

## 2.4.0 (2022-05-08)

This is a minor release with some signature updates, `prototype rb` improvement, and test fixes.

### Signature updates

* did_you_mean ([\#980](https://github.com/ruby/rbs/pull/980))
* Pattern-matching exceptions ([\#979](https://github.com/ruby/rbs/pull/979/files))
* `Singleton::SingletonClassMethods` ([\#978](https://github.com/ruby/rbs/pull/978))

#### rbs prototype

* Omit unnecessary param name on keyword arg from `prototype rb` ([\#974](https://github.com/ruby/rbs/pull/974))

### Miscellaneous

* Fix git unsafe repository error on ci test ([\#985](https://github.com/ruby/rbs/pull/985))
* Prefer `IO::Buffer.new` for mutable buffer tests ([\#989](https://github.com/ruby/rbs/pull/989))
* Update steep ([\#990](https://github.com/ruby/rbs/pull/990))

## 2.3.2 (2022-04-06)

### Library changes

* Let modules have constants under `::Object` ([\#972](https://github.com/ruby/rbs/pull/972))

### Miscellaneous

* Delete `-Wold-style-definition` ([\#971](https://github.com/ruby/rbs/pull/971))
* `\e` is not defined in C90 ([\#970](https://github.com/ruby/rbs/pull/970))

## 2.3.1 (2022-04-05)

### Library changes

* Fix mswin build, use `append_cflags`, add Windows 'compile only' CI  ([\#964](https://github.com/ruby/rbs/pull/964))
* Raise `RBS::SyntaxError` from `parse_record_attributes` ([\#966](https://github.com/ruby/rbs/pull/966))
* Toplevel constant must have the lowest precedence ([\#967](https://github.com/ruby/rbs/pull/967))

#### rbs prototype

* Use default value also for `literal_to_type` ([\#962](https://github.com/ruby/rbs/pull/962))

## 2.3.0 (2022-04-01)

### Signature updates

* `Enumerator` ([\#931](https://github.com/ruby/rbs/pull/931))
* `FalseClass` ([\#931](https://github.com/ruby/rbs/pull/931))
* `Integer` ([\#914](https://github.com/ruby/rbs/pull/914))
* `Module` ([\#931](https://github.com/ruby/rbs/pull/931))
* `NilClass` ([\#931](https://github.com/ruby/rbs/pull/931))
* `Object` ([\#931](https://github.com/ruby/rbs/pull/931))
* `Prime` ([\#934](https://github.com/ruby/rbs/pull/934))
* `TracePoint` ([\#941](https://github.com/ruby/rbs/pull/941))
* `TrueClass` ([\#931](https://github.com/ruby/rbs/pull/931))
* `#to_d` ([\#936](https://github.com/ruby/rbs/pull/936))
* `IO#wait_writable` ([\#943](https://github.com/ruby/rbs/pull/943))

### Library changes

* Add `Resolver::ConstantResolver` and `Resolver::TypeNameResolver` ([\#938](https://github.com/ruby/rbs/pull/938))
* Fix RBS::Locator ([\#930](https://github.com/ruby/rbs/pull/930))
* Trying to preserve more locations ([\#915](https://github.com/ruby/rbs/pull/915))
* Add visibility modifier to method definition json ([\#923](https://github.com/ruby/rbs/pull/923), [\#928](https://github.com/ruby/rbs/pull/928))
* Add manifest.yaml for rbs gem ([\#921](https://github.com/ruby/rbs/pull/921))
* Fix Environment type checking ([\#929](https://github.com/ruby/rbs/pull/929))
* Fix memory violation in C extension ([\#952](https://github.com/ruby/rbs/pull/952))
* Fix warnings in C extension ([\#954](https://github.com/ruby/rbs/pull/954))

#### rbs prototype

* Better typing for known `self`-returning method calls ([\#932](https://github.com/ruby/rbs/pull/932))
* Let constants have literal types ([\#937](https://github.com/ruby/rbs/pull/937))

#### rbs collection

* Fix error message to tell gem name when source is not found ([\#927](https://github.com/ruby/rbs/pull/927))

### Miscellaneous

* Fix `bin/sort` for constants ([\#919](https://github.com/ruby/rbs/pull/919))
* Use actions/checkout@v3 ([\#944](https://github.com/ruby/rbs/pull/944))
* Added actions ecosystem for dependabot ([\#946](https://github.com/ruby/rbs/pull/946))
* Confirm RDoc is up to date ([\#945](https://github.com/ruby/rbs/pull/945))

## 2.2.2 (2022-02-22)

### Signature updates

* `cgi` ([\#761](https://github.com/ruby/rbs/pull/761))
* `json` ([\#761](https://github.com/ruby/rbs/pull/761))
* `set` ([\#761](https://github.com/ruby/rbs/pull/761))

## 2.2.1 (2022-02-22)

### Library changes

* Let `validate_type_alias` run without an error on unknown type ([\#912](https://github.com/ruby/rbs/pull/912))

## 2.2.0 (2022-02-22)

RBS 2.2 ships with a new syntax to specify the visibility of a method per definition bases.

```rbs
class Foo
  private def hello: () -> void

  private attr_accessor world: String
end
```

It also changes the semantics of `self?` method.
It defines both _public_ singleton method and _private_ instance method, which is equivalent to `module_function`.

## Signature updates

* `net-http` ([\#892](https://github.com/ruby/rbs/pull/892))
* `uri` ([\#864](https://github.com/ruby/rbs/pull/864))
* `Encoding` ([\#897](https://github.com/ruby/rbs/pull/897), [\#898](https://github.com/ruby/rbs/pull/898))
* `File::Stat` ([\#879](https://github.com/ruby/rbs/pull/879))
* `Kernel#sleep` ([\#893](https://github.com/ruby/rbs/pull/893))

### Language updates

* Add public/private per member modifier ([\#911](https://github.com/ruby/rbs/pull/911))
* Let `self?.` be equivalent to `module_function` ([\#910](https://github.com/ruby/rbs/pull/910))

### Library changes

* Add c99 flag for compiling on Ruby 2 ([\#895](https://github.com/ruby/rbs/pull/895))
* Fix incorrect URI reference in `schema/typeParam.json` ([\#891](https://github.com/ruby/rbs/pull/891))
* Allow scaffolding below namespace ([\#894](https://github.com/ruby/rbs/pull/894))
* Let `Writer` preserve format ([\#900](https://github.com/ruby/rbs/pull/900))
* Reduce memory usage of `RBS::Buffer` ([\#907](https://github.com/ruby/rbs/pull/907))
* Do not call `#class` from `#hash` to improve performance ([\#906](https://github.com/ruby/rbs/pull/906))
* Skip type variable validation if `unchcked` ([\#909](https://github.com/ruby/rbs/pull/909))

### Miscellaneous

* Add `Thread::Backtrace` and `Thread::Backtrace::Location` test ([\#896](https://github.com/ruby/rbs/pull/896))
* Test annotations ([\#902](https://github.com/ruby/rbs/pull/902))
* Remove goodcheck rule for arglists section ([\#901](https://github.com/ruby/rbs/pull/901))
* Remove `CharScanner` due to no longer used ([\#905](https://github.com/ruby/rbs/pull/905))

## 2.1.0 (2022-02-02)

RBS 2.1 is a release to deliver the types and documents of the new and updated methods of Ruby 3.1.

### Signature updates

* Documents imported from Ruby 3.1 ([\#881](https://github.com/ruby/rbs/pull/881))
* Methods/classes updated in Ruby 3.1 ([\#886](https://github.com/ruby/rbs/pull/886))
* io/wait ([\#865](https://github.com/ruby/rbs/pull/865))
* json ([\#863](https://github.com/ruby/rbs/pull/863))
* nkf ([\#873](https://github.com/ruby/rbs/pull/873))
* openssl ([\#866](https://github.com/ruby/rbs/pull/866))
* `Dir.exists?`, `FileTest#exists?` ([\#884](https://github.com/ruby/rbs/pull/884))
* `FileTest` ([\#880](https://github.com/ruby/rbs/pull/880))
* `Gem::Version#<=>` ([\#869](https://github.com/ruby/rbs/pull/869))
* `Process.clock_gettime` ([\#858](https://github.com/ruby/rbs/pull/858))

### Library changes

* Introduce `rbs annotate` command ([\#881](https://github.com/ruby/rbs/pull/881))
* Make prototype rb to be aware of prepend ([\#861](https://github.com/ruby/rbs/pull/861))
* Fixes incorrect "Invalid Variance" error when method type variable shadows outer type variable ([#889](https://github.com/ruby/rbs/pull/889))

## 2.0.0 (2021-12-24)

### Bounded Generics

RBS 2.0 ships with _bounded generics_, which improves the expressiveness of the language by adding a new syntax to define constraints on type parameters.

```rbs
class PrettyPrint[T < _Output]
  interface _Output
    def <<: (String) -> void
  end

  attr_reader output: T

  def initialize: (T output) -> void
end
```

This is the motivating example I found in the [prettyprint library](https://github.com/ruby/prettyprint).
The `#initialize` receives a object of type `T` and it must have `#<<` method.
This is defined with `< _Output` syntax in the example.
It means _`T` has to be compatible with `_Output` interface._
`PrettyPrint[String]` is okay, but `PrettyPrint[Integer]` is a type error.

See [the PR for details](https://github.com/ruby/rbs/pull/844).

### RBS Collection manager

RBS Collection feature is generally available on RBS 2.0. In short, it is Bundler for RBS. You can manage RBSs of standard libraries and third party gems with `rbs collection` subcommand.

```bash
$ rbs collection init
created: rbs_collection.yaml

# The `install` command set up RBS files and creates `rbs_collection.lock.yaml` file
$ rbs collection install
Installing actionpack:6.0 (actionpack@ce6664cec73)
(...snip...)
Using tsort:0 (/path/to/rbs-2.0.0/stdlib/tsort/0)
It's done! 21 gems' RBSs now installed.

# Now you can use `rbs`, `typeprof` and `steep` commands with the dependencies out of the box!
$ rbs validate
$ typeprof app.rb
$ steep check
```

RBS 2.0 also introduces `manifest.yaml` to declare the dependencies from your gems to standard libraries explicitly.
See [the documentation](https://github.com/ruby/rbs/blob/master/docs/collection.md) for more information.

### Breaking changes

This version contains a bug fix, which potentially breaks the compatibility with older versions.
The super class names in class definitions are now resolved in _outer_ context.
It was an incompatibility with Ruby and [this PR](https://github.com/ruby/rbs/pull/856) fixed the problem.

### Signature updates

* uri ([\#846](https://github.com/ruby/rbs/pull/846), [\#852](https://github.com/ruby/rbs/pull/852), [\#851](https://github.com/ruby/rbs/pull/851), [\#850](https://github.com/ruby/rbs/pull/850), [#849](https://github.com/ruby/rbs/pull/849))

### Language updates

* Bounded generics ([\#844](https://github.com/ruby/rbs/pull/844))
* Resolve super type names in outer context ([\#856](https://github.com/ruby/rbs/pull/856))

### Library changes

* Add `manifest.yaml` for collection to specify stdlib dependencies ([\#808](https://github.com/ruby/rbs/pull/808))
* Remove experimental warning of `rbs collection` ([\#855](https://github.com/ruby/rbs/pull/855))
* Add the utility `#map_type` methods ([\#841](https://github.com/ruby/rbs/pull/841))

## 1.8.1 (2021-12-13)

### Library changes

* Validate `extend` arguments ([\#840](https://github.com/ruby/rbs/pull/840))
* Allow a relative path as git remote in collection ([\#839](https://github.com/ruby/rbs/pull/839))

## 1.8.0 (2021-12-02)

RBS 1.8.0 ships with a language feature enhancement, _generic type alias_.
You can define a type alias with type parameters now.

```rbs
type list[T] = [T, list[T]] | nil  # Defines a list of type T

type int_list = list[Integer]      # List of Integer
type string_list = list[String]    # List of String
```

You can find the detail in the [PR](https://github.com/ruby/rbs/pull/823).

### Signature updates

* `Date#+`, `Date#-` ([\#830](https://github.com/ruby/rbs/pull/830))
* `#include?`, `#member?`, `#delete`, `#count` ([\#835](https://github.com/ruby/rbs/pull/835))

### Language updates

* Generic type alias ([\#823](https://github.com/ruby/rbs/pull/823))

## 1.7.1 (2021-11-18)

### Signature updates

* `Thread#value` ([\#824](https://github.com/ruby/rbs/pull/824/files))

### Library changes

* Unquote parameter name ([\#827](https://github.com/ruby/rbs/pull/827))
* Remove `ruby/signature.rb` ([\#825](https://github.com/ruby/rbs/pull/825))

### Miscellaneous

* Use `untyped` as an expectation of return value of `IO#ready?` in test ([\#828](https://github.com/ruby/rbs/pull/828/files))

## 1.7.0 (2021-11-11)

This version replaces `RBS::Parser` implementation from pure Ruby code based on [Racc](https://github.com/ruby/racc) to C extension.
It improves the RBS file parsing performance up to 5 times faster. :rocket:

* There are some incompatibilities to drop obsolete syntax rules: `super` keyword and `any` type are no longer supported.
* [re2c](https://github.com/skvadrik/re2c) is used to generate lexical generator.

When you want to change the parser/lexer, change the files under `ext/rbs_extension` directory and run `rake compile` to compile the extension.

### Signature updates

* io/console ([\#783](https://github.com/ruby/rbs/pull/783))
* `ENV` -- Note that it introduces a dummy `::ENVClass` class, which is not defined in Ruby. ([\#812](https://github.com/ruby/rbs/pull/812))
* `Net::HTTPRequest` ([\#784](https://github.com/ruby/rbs/pull/784))
* `#clone` ([#811](https://github.com/ruby/rbs/pull/811), [\#813](https://github.com/ruby/rbs/pull/813))
* `Kernel#BigDecimal` ([\#817](https://github.com/ruby/rbs/pull/817))
* `Tempfile.new`, `Tempfile.create` ([\#792](https://github.com/ruby/rbs/pull/792), [\#791](https://github.com/ruby/rbs/pull/791))

### Library changes

* Replace `RBS::Parser` ([#788](https://github.com/ruby/rbs/pull/788), [#789](https://github.com/ruby/rbs/pull/789))
* Fix unexpected `CollectionNotAvailable` without `gems` from git source ([\#795](https://github.com/ruby/rbs/pull/795))
* Print deprecation warning ([\#801](https://github.com/ruby/rbs/pull/801))
* Make `Parser::KEYWORDS` a hash ([\#804](https://github.com/ruby/rbs/pull/804))
* Use _partial clone_ for `rbs collection` installer ([#805](https://github.com/ruby/rbs/pull/805))
* Respect logger level for test/setup logger ([\#819](https://github.com/ruby/rbs/pull/819), [\#822](https://github.com/ruby/rbs/pull/822))

## Miscellaneous

* Avoid a mixture of `Array#filter` and `Array#select` ([\#820](https://github.com/ruby/rbs/pull/820))
* Remove leftover documentation about `super` ([\#807](https://github.com/ruby/rbs/pull/807))

## 1.6.2 (2021-09-09)

### Signature updates

* `Enumerator::Lazy#force` ([\#782](https://github.com/ruby/rbs/pull/782))
* `IO.readlines` ([\#780](https://github.com/ruby/rbs/pull/780))

### Miscellaneous

* Set `$XDG_CACHE_HOME` during test ([\#781](https://github.com/ruby/rbs/pull/781))

## 1.6.1 (2021-09-05)

This is a minor release including test fixes.

### Miscellaneous

* Fix stdlib test for `Resolv::Hosts` by removing `/etc/hosts` dependency ([\#779](https://github.com/ruby/rbs/pull/779))
* Fix bundler related test for test-bundled-gems ([\#778](https://github.com/ruby/rbs/pull/778))

## 1.6.0 (2021-09-05)

This release includes a preview of `rbs collection` commands, which is _bundler for RBS_.
The command helps you manage RBS files from gem_rbs_collection or other repositories.

This feature is a preview, and any feedback is welcome!

### Signature updates

* objspace ([\#763](https://github.com/ruby/rbs/pull/763), [\#776](https://github.com/ruby/rbs/pull/776))
* tempfile ([\#767](https://github.com/ruby/rbs/pull/767), [\#775](https://github.com/ruby/rbs/pull/775))
* `IO#set_encoding_by_bom` ([\#106](https://github.com/ruby/rbs/pull/106))
* `OpenSSL::PKey::EC#dh_compute_key` ([\#775](https://github.com/ruby/rbs/pull/775))

### Library changes

* Add `rbs collection` ([\#589](https://github.com/ruby/rbs/pull/589), [\#772](https://github.com/ruby/rbs/pull/772), [\#773](https://github.com/ruby/rbs/pull/773))

### Miscellaneous

* Let `bin/annotate-with-rdoc` process nested constants/classes ([\#766](https://github.com/ruby/rbs/pull/766), [\#768](https://github.com/ruby/rbs/pull/768))
* Stop printing version mismatch message in CI ([\#777](https://github.com/ruby/rbs/pull/777))
* Update Steep and fix type errors ([\#770](https://github.com/ruby/rbs/pull/770), [\#774](https://github.com/ruby/rbs/pull/774))
* Add dependabot configuration ([\#771](https://github.com/ruby/rbs/pull/771))

## 1.5.1 (2021-08-22)

### Miscellaneous

* Fix Net_HTTP_test ([\#759](https://github.com/ruby/rbs/pull/759))

## 1.5.0 (2021-08-22)

This release includes stdlib signature updates.

### Signature updates

* digest ([\#744](https://github.com/ruby/rbs/pull/744), [\#757](https://github.com/ruby/rbs/pull/757))
* io-wait ([\#756](https://github.com/ruby/rbs/pull/756), [\#758](https://github.com/ruby/rbs/pull/758))
* `Ractor` ([\#755](https://github.com/ruby/rbs/pull/755))
* `File::Stat#size?` ([\#754](https://github.com/ruby/rbs/pull/754))
* `$-i`, `$0`, `$PROGRAM_NAME` ([\#753](https://github.com/ruby/rbs/pull/753))

## 1.4.0 (2021-08-19)

This release includes feature enhancements including recursive `type` definition validation, improved compatibility of global variable names, and various method type updates.

### Signature updates

* openssl ([\#743](https://github.com/ruby/rbs/pull/743))
* `Array`, `Enumerator`, `Enumerable`, `Hash`, `FalseClass`, `Float`, `Hash`, `Integer`, `Object`, `Range`, `TrueClass` ([\#728](https://github.com/ruby/rbs/pull/728))
* `Array#[]` ([\#732](https://github.com/ruby/rbs/pull/732))
* `Exception#set_backtrace` ([\#738](https://github.com/ruby/rbs/pull/738))
* `Kernel#Array` ([\#733](https://github.com/ruby/rbs/pull/733))
* `Kernel#spawn` ([\#748](https://github.com/ruby/rbs/pull/748))
* `Kernel#String` ([\#745](https://github.com/ruby/rbs/pull/745), [\#751](https://github.com/ruby/rbs/pull/751))
* `URI::Generic#fragment` ([\#752](https://github.com/ruby/rbs/pull/752))
* `URI::Generic#merge` ([\#746](https://github.com/ruby/rbs/pull/746))

### Language updates

* Add global variables signature ([\#749](https://github.com/ruby/rbs/pull/749))

### Library changes

* Add Recursive type alias definition validation ([\#719](https://github.com/ruby/rbs/pull/719))
* Generate included modules with complete name ([\#731](https://github.com/ruby/rbs/pull/731))
* Fix `rbs-prototype-rb` error when multi assign with const ([\#740](https://github.com/ruby/rbs/pull/740))

## 1.3.3 (2021-07-28)

This release includes a minor update of `resolv` library RBS and a fix of test for `ruby/ruby` CI.

### Signature updates

* resolv ([#726](https://github.com/ruby/rbs/pull/726))

## 1.3.2 (2021-07-23)

This release is to fix a bug introduced by parser update in 1.3.0.

* Fix parser to accept alias names starting with `_` ([#723](https://github.com/ruby/rbs/pull/723))

## 1.3.1 (2021-07-21)

This release is to fix a bug introduced by parser update in 1.3.0.

* Fix parser to accept param names starting with `_` ([#721](https://github.com/ruby/rbs/pull/721))

## 1.3.0 (2021-07-20)

### Summary

RBS 1.3.0 includes bug fixes of the parser and class/module definition validations.

### Signature updates

* dbm ([#718](https://github.com/ruby/rbs/pull/718))
* net-http ([#686](https://github.com/ruby/rbs/pull/686))
* optparse ([#693](https://github.com/ruby/rbs/pull/693))
* resolv ([#697](https://github.com/ruby/rbs/pull/697))
* socket ([#699](https://github.com/ruby/rbs/pull/699))
* `IO` ([#698](https://github.com/ruby/rbs/pull/698))
* `Marshal` ([#684](https://github.com/ruby/rbs/pull/684))
* `Mutex` ([#683](https://github.com/ruby/rbs/pull/683))
* `Array#shift` ([#707](https://github.com/ruby/rbs/pull/707))
* `BasicObject#method_missing` ([#707](https://github.com/ruby/rbs/pull/706), [#710](https://github.com/ruby/rbs/pull/710))
* `Kernel#caller` ([#705](https://github.com/ruby/rbs/pull/705))

### Library changes

* Interface names starting with lower case characters are now syntax error ([#678](https://github.com/ruby/rbs/pull/678), [#720](https://github.com/ruby/rbs/pull/720))
* Mixins of classes are rejected ([#681](https://github.com/ruby/rbs/pull/681))
* Generate prototype of `initialize` method with return type `void` ([#685](https://github.com/ruby/rbs/pull/685))
* Let `prototype runtime` generate class/module declarations in nested syntax ([#700](https://github.com/ruby/rbs/pull/700))
* Fix race condition for multi-thread support ([#702](https://github.com/ruby/rbs/pull/702))

### Miscellaneous

* Add new doc `docs/rbs_by_example.md` ([#694](https://github.com/ruby/rbs/pull/694))

## 1.2.1 (2021-05-27)

This release includes the following minor changes:

* Fix test to run the tests in ruby repository. ([#679](https://github.com/ruby/rbs/pull/679))
* Remove unnecessary files from the gem package. ([#675](https://github.com/ruby/rbs/pull/675))
* Suppress unused variable warning ([#674](https://github.com/ruby/rbs/pull/674))
* Update documents ([#672](https://github.com/ruby/rbs/pull/672))

## 1.2.0 (2021-04-21)

### Summary

RBS 1.2 ships with better support for AST/token locations and `Locator` utility class. The AST objects now keep the locations of tokens. The `Locator` class is to translate the text position (line and column) to semantic object at the location. The class allows to find if a text position is on the class name of a class declaration.

### Signature updates

* Hash ([#631](https://github.com/ruby/rbs/pull/631), [#632](https://github.com/ruby/rbs/pull/632), [\#637](https://github.com/ruby/rbs/pull/637), [\#638](https://github.com/ruby/rbs/pull/638), [\#639](https://github.com/ruby/rbs/pull/639), )
* Module ([\#645](https://github.com/ruby/rbs/pull/645))
* Enumerable ([\#647](https://github.com/ruby/rbs/pull/647))
* Array ([\#648](https://github.com/ruby/rbs/pull/648))
* Proc ([\#649](https://github.com/ruby/rbs/pull/649))
* Struct ([\#650](https://github.com/ruby/rbs/pull/650), [\#668](https://github.com/ruby/rbs/pull/668))
* Thread ([\#651](https://github.com/ruby/rbs/pull/651))
* Random ([\#669](https://github.com/ruby/rbs/pull/669))
* Shellwords ([\#665](https://github.com/ruby/rbs/pull/665))
* IO ([\#659](https://github.com/ruby/rbs/pull/659))

### Language updates

* Module self type syntax update ([\#653](https://github.com/ruby/rbs/pull/653))

### Library changes

* Token locations ([\#666](https://github.com/ruby/rbs/pull/666))
* Add RBS::Locator ([\#667](https://github.com/ruby/rbs/pull/667))
* Fix runtime type checker ([\#644](https://github.com/ruby/rbs/pull/644))

### Miscellaneous

* Update documentation for overloading ([\#658](https://github.com/ruby/rbs/pull/658))
* Update target ruby version ([\#633](https://github.com/ruby/rbs/pull/633))

## 1.1.1 (2021-03-12)

### Signature updates

* rubygem ([#630](https://github.com/ruby/rbs/pull/630))

## 1.1.0 (2021-03-08)

### Summary

Errors are now organized by `RBS::BaseError`, `RBS::ParsingError`, `RBS::LoadingError`, and `RBS::DefinitionError`.
The library users can rescue RBS related errors with `RBS::BaseError`, parsing errors with `RBS::ParsingError`, and other errors with `RBS::LoadingError` and `RBS::DefinitionErrors`.

Updating a part of environments are supported. Library users can remove declarations read from a set of files, adding new declarations, running name resolution related to the new decls, and deleting `DefinitionBuilder` caches related to the changes.
See `RBS::Environment#reject`, `RBS::Environment#resolve_type_names`, `RBS::AncestorGraph`, and `RBS::DefinitionBuilder#update`.

`RBS::DefinitionBuilder#build_singleton` now returns definitions containing `instance` type, which had returned resolved class instance types. This is a breaking change, but we consider it a bug fix because `RBS::DefinitionBuilder#build_instance` has returned `instance` types and `#build_singleton` has returned `class` type.

### Signature updates

* rubygem ([\#605](https://github.com/ruby/rbs/pull/605), [\#610](https://github.com/ruby/rbs/pull/610))
* Array ([\#612](https://github.com/ruby/rbs/pull/612), [\#614](https://github.com/ruby/rbs/pull/614))
* cgi/core ([\#599](https://github.com/ruby/rbs/pull/599))
* Thread ([\#618](https://github.com/ruby/rbs/pull/618))

### Language updates

* Allow trailing comma for Record and Tuple types ([\#606](https://github.com/ruby/rbs/pull/606))

### Library changes

* Allow partial update of RBS declarations ([\#608](https://github.com/ruby/rbs/pull/608), [\#621](https://github.com/ruby/rbs/pull/621))
* Let errors have `TypeName` ([\#611](https://github.com/ruby/rbs/pull/611))
* Add `Parser::LexerError` ([\#615](https://github.com/ruby/rbs/pull/615))
* Performance improvement ([\#617](https://github.com/ruby/rbs/pull/617), [\#620](https://github.com/ruby/rbs/pull/620))
* No substitute `instance` types on `#build_singleton` ([\#619](https://github.com/ruby/rbs/pull/619))

### Miscellaneous

* Make racc name customizable by `RACC` environment variable ([\#602](https://github.com/ruby/rbs/pull/602))
* Suppress warnings ([\#624](https://github.com/ruby/rbs/pull/624))
* Remove needless `Gem::Version` polyfill ([\#622](https://github.com/ruby/rbs/pull/622))

## 1.0.6 (2021-02-17)

* Signature Updates
  * `Enumerable` ([\#595](https://github.com/ruby/rbs/pull/595), [\#596](https://github.com/ruby/rbs/pull/596), [\#601](https://github.com/ruby/rbs/pull/601))
  * `#as_json` ([\#597](https://github.com/ruby/rbs/pull/597))

## 1.0.5 (2021-02-13)

* Signature Updates
  * Enumerable ([\#596](https://github.com/ruby/rbs/pull/596))
  * Set ([\#595](https://github.com/ruby/rbs/pull/595))
  * `#to_json` ([\#592](https://github.com/ruby/rbs/pull/592))
  * `<=>` ([\#593](https://github.com/ruby/rbs/pull/593))
  * Timeout ([\#586](https://github.com/ruby/rbs/pull/586))
  * URI::RFC2396_Parser ([\#587](https://github.com/ruby/rbs/pull/587))
* Rename generic class parameters on re-open ([\#594](https://github.com/ruby/rbs/pull/594))
* Make `refute_send_type` check that method call doesn't match with types in RBS ([\#588](https://github.com/ruby/rbs/pull/588))

## 1.0.4 (2021-01-31)

* Unbundle `rr` to run test in `ruby/ruby` repo ([#585](https://github.com/ruby/rbs/pull/585))

## 1.0.3 (2021-01-28)

* Set up `#ancestors` and `#location` of `RecursiveAncestorError` ([#583](https://github.com/ruby/rbs/pull/583))

## 1.0.2 (2021-01-28)

* Signature Updates
  * Kernel([#582](https://github.com/ruby/rbs/pull/582))

## 1.0.1 (2021-01-27)

* Signature Updates
  * PrettyPrint ([\#573](https://github.com/ruby/rbs/pull/573))
  * FileUtils ([\#576](https://github.com/ruby/rbs/pull/576))
  * UnboundMethod ([\#555](https://github.com/ruby/rbs/pull/555))
  * IO ([\#571](https://github.com/ruby/rbs/pull/571))
  * Kernel ([\#549](https://github.com/ruby/rbs/pull/549), [\#569](https://github.com/ruby/rbs/pull/569))
  * File ([\#552](https://github.com/ruby/rbs/pull/552))
  * Data is removed ([\#570](https://github.com/ruby/rbs/pull/570))
  * Module ([\#568](https://github.com/ruby/rbs/pull/568))
  * Object ([\#557](https://github.com/ruby/rbs/pull/557))
* Renew test sample code ([#581](https://github.com/ruby/rbs/pull/581))
* Add description to `generate:stdlib_test` Rake task ([\#578](https://github.com/ruby/rbs/pull/578))
* Declare supported ruby version >= 2.6 ([\#548](https://github.com/ruby/rbs/pull/548))
* Fix module self-type override ([\#577](https://github.com/ruby/rbs/pull/577))
* Fix parser to support all operator symbols ([\#550](https://github.com/ruby/rbs/pull/550))
* Migrate from Minitest to Test::Unit ([\#556](https://github.com/ruby/rbs/pull/556))
* Fix type alias parsing ([\#565](https://github.com/ruby/rbs/pull/565))
* Support end-less method definition in `prototype rb` ([\#561](https://github.com/ruby/rbs/pull/561))
* Support method argument forwarding in `prototype rb` ([\#560](https://github.com/ruby/rbs/pull/560))

## 1.0.0 (2020-12-24)

* Signature updates for `URI`, `IO`, `File`, `Pathname`, `Module`, and `Time` ([#529](https://github.com/ruby/rbs/pull/529), [#521](https://github.com/ruby/rbs/pull/521), [#520](https://github.com/ruby/rbs/pull/520), [#511](https://github.com/ruby/rbs/pull/511), [#517](https://github.com/ruby/rbs/pull/517), [#542](https://github.com/ruby/rbs/pull/542), [#546](https://github.com/ruby/rbs/pull/546), [#540](https://github.com/ruby/rbs/pull/540), [#538](https://github.com/ruby/rbs/pull/538))
* `rbs prototype runtime` generates `extend`s ([#535](https://github.com/ruby/rbs/pull/535))
* `rbs prototype runtime` stability improvements ([#533](https://github.com/ruby/rbs/pull/533), [#526](https://github.com/ruby/rbs/pull/526))
* `rbs prototype rb` compatibility improvements ([#545](https://github.com/ruby/rbs/pull/545))
* Implement method names escape in `RBS::Writer` ([#537](https://github.com/ruby/rbs/pull/537))
* Improve runtime type checker compatibility ([#532](https://github.com/ruby/rbs/pull/532), [#528](https://github.com/ruby/rbs/pull/528), [#547](https://github.com/ruby/rbs/pull/547))
* Fix `ruby2_keywords` for `Proc` in Ruby <2.7 ([#513](https://github.com/ruby/rbs/pull/513))
* Better compatibility for record type attribute names ([#525](https://github.com/ruby/rbs/pull/525), [#524](https://github.com/ruby/rbs/pull/524))
* Let module self-types be classes ([#523](https://github.com/ruby/rbs/pull/523))
* Delete `extension` syntax ([#543](https://github.com/ruby/rbs/pull/543))
* Method resolution improvements about `alias` and `.new` ([#522](https://github.com/ruby/rbs/pull/522), [#519](https://github.com/ruby/rbs/pull/519), [#516](https://github.com/ruby/rbs/pull/516))
* Better message for `DuplicatedMethodDefinitionError` ([#539](https://github.com/ruby/rbs/pull/539))

## 0.20.1 (2020-12-06)

* Make the order of RBS load reproducible ([#508](https://github.com/ruby/rbs/pull/508))

## 0.20.0 (2020-12-06)

* Signature updates for `TSort`, `DBM`, `Time`, and `Hash` ([#496](https://github.com/ruby/rbs/pull/496), [#497](https://github.com/ruby/rbs/pull/497), [#499](https://github.com/ruby/rbs/pull/499), [#507](https://github.com/ruby/rbs/pull/507))
* Add _singleton attribute_ syntax ([#502](https://github.com/ruby/rbs/pull/502), [#506](https://github.com/ruby/rbs/pull/506), [#505](https://github.com/ruby/rbs/pull/505))
* Proc types with blocks ([#503](https://github.com/ruby/rbs/pull/503))
* Add support for escape sequences in string literal types ([#501](https://github.com/ruby/rbs/pull/501))
* Fix runtime type checking of blocks with keyword args ([#500](https://github.com/ruby/rbs/pull/500))

## 0.19.0 (2020-12-02)

* Signature updates for `Monitor` and File ([#485](https://github.com/ruby/rbs/pull/485), [#495](https://github.com/ruby/rbs/pull/495))

## 0.18.1 (2020-12-01)

* Fix `EnvironmentWalker#each_type_name` ([#494](https://github.com/ruby/rbs/pull/494))

## 0.18.0 (2020-12-01)

* Signature updates for `YAML`, `ObjectSpace`, and `Singleton` ([#408](https://github.com/ruby/rbs/pull/408), [#477](https://github.com/ruby/rbs/pull/477), [#482](https://github.com/ruby/rbs/pull/482))
* `prototype rb` improvements ([#492](https://github.com/ruby/rbs/pull/492), [#487](https://github.com/ruby/rbs/pull/487), [#486](https://github.com/ruby/rbs/pull/486), [#481](https://github.com/ruby/rbs/pull/481))
* Runtime type checker improvements ([#488](https://github.com/ruby/rbs/pull/488), [#489](https://github.com/ruby/rbs/pull/489), [#490](https://github.com/ruby/rbs/pull/490))
* Update `DependencyWalker` API to receive _Node_ objects instead of `TypeName` ([#484](https://github.com/ruby/rbs/pull/484))
* Assume encoding of RBS files to be UTF-8 ([#493](https://github.com/ruby/rbs/pull/493))

## 0.17.0 (2020-11-14)

* Signature updates for `Enumerable`, `Hash`, and `TSort` ([#462](https://github.com/ruby/rbs/pull/462), [#468](https://github.com/ruby/rbs/pull/468), [#471](https://github.com/ruby/rbs/pull/471), [#472](https://github.com/ruby/rbs/pull/472), [#473](https://github.com/ruby/rbs/pull/473), [#474](https://github.com/ruby/rbs/pull/474))
* Parser error handling improvement ([#463](https://github.com/ruby/rbs/pull/463), [#475](https://github.com/ruby/rbs/pull/475))
* Hash spread syntax handling improvement with `prototype rb` ([#465](https://github.com/ruby/rbs/pull/465))

## 0.16.0 (2020-11-05)

* Signature update for `DBM` ([#441](https://github.com/ruby/rbs/pull/441))
* RBS repository ([#405](https://github.com/ruby/rbs/pull/405))
* Support `alias` in `rbs prototype rb` ([#457](https://github.com/ruby/rbs/pull/457))

## 0.15.0 (2020-11-02)

* Signature updates for `Kernel`, `PStore`, `Enumerable`, and `Array` ([#450](https://github.com/ruby/rbs/pull/450), [#443](https://github.com/ruby/rbs/pull/443), [#438](https://github.com/ruby/rbs/pull/438), [#437](https://github.com/ruby/rbs/pull/437), [#433](https://github.com/ruby/rbs/pull/433), [#432](https://github.com/ruby/rbs/pull/432))
* Add helper interfaces ([#434](https://github.com/ruby/rbs/pull/434), [#428](https://github.com/ruby/rbs/pull/428))
* Change `bool` type semantics ([#456](https://github.com/ruby/rbs/pull/456))
* Support alias in `rbs prototype rb` ([#457](https://github.com/ruby/rbs/pull/457))
* Runtime testing improvements ([#455](https://github.com/ruby/rbs/pull/455), [#447](https://github.com/ruby/rbs/pull/447), [#444](https://github.com/ruby/rbs/pull/444), [#431](https://github.com/ruby/rbs/pull/431))
* Fix proc type parsing ([#451](https://github.com/ruby/rbs/pull/451))
* Fix type variable parsing ([#442](https://github.com/ruby/rbs/pull/442))


## 0.14.0 (2020-10-17)

* Allow keyword names ending with `?` and `!` ([#417](https://github.com/ruby/rbs/pull/417))
* Make `Range[T]` covariant ([#418](https://github.com/ruby/rbs/pull/418))

## 0.13.1 (2020-10-09)

* Fix test for CI of ruby/ruby ([#412](https://github.com/ruby/rbs/pull/412))

## 0.13.0 (2020-10-09)

* Signature updates for `URI` classes.
* Fix tests ([#410](https://github.com/ruby/rbs/pull/410))
* Add `--silent` option for `rbs validate` ([#411](https://github.com/ruby/rbs/pull/411))

## 0.12.2 (2020-09-17)

* Minor signature update for `pty`
* Fix `PTY` stdlib test

## 0.12.1 (2020-09-16)

This version is to improve Ruby 3 testing compatibility. Nothing changed for users.

## 0.12.0 (2020-09-15)

* Signature updates for `forwardable`, `struct`, `set`, `URI::Generic`, `URI::File`, and `BigDecimal`.
* Define `.new` methods from `initialize` included from modules [#390](https://github.com/ruby/rbs/pull/390)

## 0.11.0 (2020-08-31)

* Signature update for `date/datetime` [#367](https://github.com/ruby/rbs/pull/367)
* Add test double support for runtime type checker [#380](https://github.com/ruby/rbs/pull/380)
* Add `rbs test` command for runtime type checking [#366](https://github.com/ruby/rbs/pull/366)
* Fix runtime type checking for record types [#365](https://github.com/ruby/rbs/pull/365)
* Improve EnvironmentLoader API [#370](https://github.com/ruby/rbs/pull/370)
* Allow overloading from super methods [#364](https://github.com/ruby/rbs/pull/364)

## 0.10.0 (2020-08-10)

* Signature update for `Zlib`
* Make "no type checker installed" message a debug print [#363](https://github.com/ruby/rbs/pull/363)
* Print `...` for overloading method definitions [#362](https://github.com/ruby/rbs/pull/362)
* Allow missing method implementation in Ruby code [#359](https://github.com/ruby/rbs/pull/359)
* Runtime testing improvements [#356](https://github.com/ruby/rbs/pull/356)

## 0.9.1 (2020-08-04)

* Ensure using Module#name [#354](https://github.com/ruby/rbs/pull/354)
* Fix runtime test setup [#353](https://github.com/ruby/rbs/pull/353)

## 0.9.0 (2020-08-03)

* Fix signature validation [#351](https://github.com/ruby/rbs/pull/351), [#352](https://github.com/ruby/rbs/pull/352)
* Parsing performance improvement [#350](https://github.com/ruby/rbs/pull/350)

## 0.8.0 (2020-08-01)

* Signature updates for `Enumerator` and `PTY`
* Fix prototype rb/rbi error handling [#349](https://github.com/ruby/rbs/pull/349)
* Runtime test improvements [#344](https://github.com/ruby/rbs/pull/344), [#343](https://github.com/ruby/rbs/pull/343)
* Add `...` syntax [#342](https://github.com/ruby/rbs/pull/342)

## 0.7.0 (2020-07-20)

* Add `DefinitionBuilder#one_instance_ancestors` and `DefinitionBuilder#one_singleton_ancestors` [#341](https://github.com/ruby/rbs/pull/341)
* Bug fix in ConstantTable [#340](https://github.com/ruby/rbs/pull/340)
* Make `rbs validate` faster [#338](https://github.com/ruby/rbs/pull/338)
* Dedup methods generated by `rbs prototype rb` [#334](https://github.com/ruby/rbs/pull/334)

## 0.6.0 (2020-07-12)

* Signature update for `Logger`.
* Clean `Environment#inspect`. [#331](https://github.com/ruby/rbs/pull/331)
* Module self type syntax update. [#329](https://github.com/ruby/rbs/pull/329)
* Better validation. [#328](https://github.com/ruby/rbs/pull/328)
* Parser performance improvement. [#327](https://github.com/ruby/rbs/pull/327)
* Runtime type checking performance improvements with sampling [#323](https://github.com/ruby/rbs/pull/323)

## 0.5.0 (2020-07-04)

* Signature updates for `Mutex_m`, `IO`, and `Enumerable`.
* Syntax update. [#307](https://github.com/ruby/rbs/pull/307)
* AST command prints _absolute_ type names with file name filtering. [#312](https://github.com/ruby/rbs/pull/312)
* Improve CLI message. [#309](https://github.com/ruby/rbs/pull/309)

## 0.4.0 (2020-06-15)

* Signature update for `Fiber`, `Encoding`, and `Enumerator`.
* Fix syntax error for underscore and `!` `?` [#304](https://github.com/ruby/rbs/pull/304)
* Improved return type inference in `rbs prototype rb` [#303](https://github.com/ruby/rbs/pull/303)
* Skip anonymous modules/classes in `rbs prototype runtime` [#302](https://github.com/ruby/rbs/pull/302)
* Fix `--require-relative` option in `rbs prototype runtime` [#299](https://github.com/ruby/rbs/pull/299)
* Add JSON schema for `rbs ast` [#295](https://github.com/ruby/rbs/pull/295)

## 0.3.1 (2020-05-22)

* Fix constant resolution again [#289](https://github.com/ruby/rbs/pull/289)

## 0.3.0 (2020-05-20)

* Fix constant resolution [#288](https://github.com/ruby/rbs/pull/288)

## 0.2.0

* The first release of RBS gem.

## 0.1.0

* Version 0.1.0 is the original `rbs` gem and it is different software from RBS.
