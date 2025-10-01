# Security

Please report security vulnerabilities via the process outlined in the [reporting vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).
Specific guidelines for reporting security issues of the GraalVM project, including TruffleRuby, can be found in the [SECURITY file](https://github.com/truffleruby/truffleruby/blob/master/SECURITY.md).

## MRI Vulnerabilities

Vulnerabilities reported against MRI may apply to the design of Ruby or to code that we share with MRI.
We list reported MRI vulnerabilities here and document how MRI has mitigated the vulnerability, if the mitigation is tested
by anything, and how TruffleRuby has mitigated. We have not investigated all legacy vulnerabilities, as it is often very hard to work out the details from older reports.

Cross-reference with the details on [the MRI website](https://www.ruby-lang.org/en/security/).

Number | Description | Their Mitigation | Test | Our Mitigation
--- | --- | --- | --- | ---
CVE-2024-49761 | ReDoS vulnerability in REXML | [Fix](https://github.com/ruby/rexml/commit/ce59f2eb1aeb371fe1643414f06618dbe031979f) | [Test](https://github.com/ruby/rexml/commit/ce59f2eb1aeb371fe1643414f06618dbe031979f) | Not applicable thanks to TRegex
CVE-2024-39908 | DoS in REXML | [Fix](https://github.com/ruby/rexml/compare/v3.3.1...v3.3.2) | [Test](https://github.com/ruby/rexml/compare/v3.3.1...v3.3.2) | [same mitigation as CRuby](https://www.ruby-lang.org/en/news/2024/07/16/dos-rexml-cve-2024-39908/), users must update rexml to 3.3.2+ if they use it
CVE-2024-35176 | DoS in REXML | [Fix](https://github.com/ruby/rexml/pull/126/commits/ba70cfefadf7e79885fe08228d1df4c0549ad0f8) | [Test](https://github.com/ruby/rexml/pull/126/commits/ba70cfefadf7e79885fe08228d1df4c0549ad0f8) | [same mitigation as CRuby](https://www.ruby-lang.org/en/news/2024/05/16/dos-rexml-cve-2024-35176/), users must update rexml to 3.2.7+ if they use it
CVE-2024-27282 | Arbitrary memory address read vulnerability with Regex search | [Fix](https://github.com/ruby/ruby/commit/989a2355808a63fc45367785c82ffd46d18c900a) | | Not applicable thanks to Java semantics
CVE-2024-27281 | RCE vulnerability with .rdoc_options in RDoc | [Fix](https://github.com/ruby/rdoc/compare/v6.3.3...v6.3.4.1) | | Not applicable because TruffleRuby does not generate rdoc on gem install
CVE-2024-27280 | Buffer overread vulnerability in StringIO | [Fix](https://github.com/ruby/stringio/compare/v3.0.1...v3.0.1.1) | [Test](https://github.com/ruby/stringio/commit/c58c5f54f1eab99665ea6a161d29ff6a7490afc8) | Not applicable, we use the Ruby implementation of StringIO
CVE-2023-36617 | ReDoS vulnerability in URI | [Fix](https://github.com/ruby/uri/compare/v0.12.1...v0.12.2) | [Test](https://github.com/ruby/uri/compare/v0.12.1...v0.12.2) | Not applicable thanks to TRegex
CVE-2023-28756 | ReDoS vulnerability in Time | [Fix](https://github.com/ruby/time/compare/v0.2.1...v0.2.2) | [Test](https://github.com/ruby/time/commit/c9b92a6e21f432528bddf65fbd132db3487d07bf) | Not applicable thanks to TRegex
CVE-2023-28755 | ReDoS vulnerability in URI | [Fix](https://github.com/ruby/uri/compare/v0.12.0...v0.12.1) | [Test](https://github.com/ruby/uri/commit/d829f16559b9b61478dea024d72a2d615f67942f) | Not applicable thanks to TRegex
CVE-2021-33621 | HTTP response splitting in CGI | [Fix](https://github.com/ruby/cgi/compare/v0.3.3...v0.3.5) | [Test](https://github.com/ruby/cgi/compare/v0.3.3...v0.3.5) | Same
CVE-2022-28739 | Buffer overrun in String-to-Float conversion | | | Not applicable thanks to Java semantics
CVE-2022-28738 | Double free in Regexp compilation | | | Not applicable thanks to Java semantics
CVE-2021-41819 | Cookie Prefix Spoofing in CGI::Cookie.parse | [Fix](https://github.com/ruby/ruby/commit/0f31b3f465) | [Test](https://github.com/ruby/ruby/commit/0f31b3f465) | Same
CVE-2021-41817 | Regular expression denial of service vulnerability (ReDoS) in date | [Fix](https://github.com/ruby/date/commit/3959accef8) | [Test](https://github.com/ruby/date/commit/3959accef8) | Not applicable thanks to TRegex
CVE-2021-41816 | Buffer Overrun in CGI.escape_html | [Fix](https://github.com/ruby/ruby/commit/e4b35b158a) | [Test](https://github.com/ruby/ruby/commit/e4b35b158a) | Not applicable, we use the Ruby definition of CGI.escape_html
CVE-2021-31810 | Trusting FTP PASV responses vulnerability in Net::FTP | [Fix](https://github.com/ruby/ruby/commit/3ca1399150ed4eacfd2fe1ee251b966f8d1ee469) | [Test](https://github.com/ruby/ruby/commit/3ca1399150ed4eacfd2fe1ee251b966f8d1ee469) | Same
CVE-2021-32066 | A StartTLS stripping vulnerability in Net::IMAP | [Fix](https://github.com/ruby/ruby/commit/a21a3b7d23704a01d34bd79d09dc37897e00922a) | [Test](https://github.com/ruby/ruby/commit/a21a3b7d23704a01d34bd79d09dc37897e00922a) | Same
CVE-2021-31799 | A command injection vulnerability in RDoc | [Fix](https://github.com/ruby/rdoc/commit/a7f5d6ab88632b3b482fe10611382ff73d14eed7) [Backport](https://github.com/ruby/ruby/commit/483f303d02e768b69e476e0b9be4ab2f26389522) | [Test](https://github.com/ruby/rdoc/commit/a7f5d6ab88632b3b482fe10611382ff73d14eed7) | Same
CVE-2021-28966 | Path traversal in Tempfile on Windows | Sanitization of paths in tmpdir.rb | In `test/mri/tests/test_tmpdir.rb` | Sanitization of paths in tmpdir.rb
CVE-2020-25613 | Potential HTTP Request Smuggling Vulnerability in WEBrick | [Fix](https://github.com/ruby/webrick/commit/076ac636bf48b7a492887ce4de7041de23e6c00d) | | WEBrick no longer shipped in stdlib
CVE-2020-10933 | Heap exposure vulnerability in the socket library | [Fix](https://github.com/ruby/ruby/commit/61b7f86248bd121be2e83768be71ef289e8e5b90) | | Not applicable thanks to Java semantics
CVE-2021-28965 | XML round-trip vulnerability in REXML | Update to REXML 3.2.5 | In ruby/rexml | Update to REXML 3.2.5
CVE-2020-10663 | Unsafe Object Creation Vulnerability in JSON (Additional fix) | [Fix](https://bugs.ruby-lang.org/issues/16698) | [Spec](https://github.com/ruby/spec/pull/764) | The pure Ruby version of JSON we use is safe
CVE-2019-16255 | A code injection vulnerability of Shell#[] and Shell#test | [Fix](https://github.com/ruby/ruby/commit/d6adc68dc9c74a33b3ca012af171e2d59f0dea10) | MRI test | Same
CVE-2019-16254 | HTTP response splitting in WEBrick (Additional fix) | [Fix](https://github.com/ruby/ruby/commit/3ce238b5f9795581eb84114dcfbdf4aa086bfecc) | MRI test | Same
CVE-2019-15845 | A NUL injection vulnerability of File.fnmatch and File.fnmatch? | [Fix](https://github.com/ruby/ruby/commit/a0a2640b398cffd351f87d3f6243103add66575b) | MRI test | Check for NUL bytes
CVE-2019-16201 | Regular Expression Denial of Service vulnerability of WEBrick's Digest access authentication | [Fix](https://github.com/ruby/ruby/commit/36e057e26ef2104bc2349799d6c52d22bb1c7d03) | MRI test | Same
CVE-2012-6708 | Multiple jQuery vulnerabilities in RDoc | Remove jquery.js | N/A | Same
CVE-2015-9251 | Multiple jQuery vulnerabilities in RDoc | Remove jquery.js | N/A | Same
CVE-2019-8320 | Delete directory using symlink when decompressing `tar` | Check the expanded path | Tested in MRI `test/rubygems/test_gem_package.rb` | Applied the same patch
CVE-2019-8321 | Escape sequence injection in `verbose` | Sanitise message | Tested in `ruby/spec` `:security` | Applied the same patch
CVE-2019-8322 | Escape sequence injection in `gem owner` | Sanitise message | Tested in `ruby/spec` `:security` | Applied the same patch
CVE-2019-8323 | Escape sequence injection vulnerability in API response handling | Sanitise message | Tested in `ruby/spec` `:security` | Applied the same patch
CVE-2019-8324 | Installing a malicious gem may lead to arbitrary code execution | Verifying gems before pre-install checks | Tested in MRI `test/rubygems/test_gem_installer.rb` | Applied the same patch
CVE-2019-8325 | Escape sequence injection in errors | Sanitise error messages | Tested in `ruby/spec` `:security` | Applied the same patch
None | Multiple vulnerabilities in RubyGems | | | Applied the same patch
CVE-2018-16395 | `OpenSSL::X509::Name` equality check does not work correctly | | |
CVE-2018-16396 | Tainted flags are not propagated in `Array#pack` and `String#unpack` with some directives | Additional taint operations | Tested in `ruby/spec` `:security` | Additional taint operations
CVE-2018-6914 | Unintentional file and directory creation with directory traversal in `tempfile` and `tmpdir` | Sanitization of paths | Tested in `ruby/spec` `:security` | Sanitization of paths
CVE-2018-8779 | Unintentional socket creation by poisoned NUL byte in `UNIXServer` and `UNIXSocket` | Check for NUL bytes | Tested in `ruby/spec` `:security` | Check for NUL bytes
CVE-2018-8780 | Unintentional directory traversal by poisoned NUL byte in `Dir` | Check for NUL bytes | Tested in `ruby/spec` `:security` | Check for NUL bytes
CVE-2018-8777 | DoS by large request in WEBrick | Logic for header length | Tested in MRI `test/webrick/test_httpserver.rb` | Applied the same mitigation
CVE-2017-17742 | HTTP response splitting in WEBrick | Logic for invalid headers | Tested in `ruby/spec` `:security` | Applied the same mitigation
CVE-2018-8778 | Buffer under-read in String#unpack | A range check | Tested in `ruby/spec` `:security` | A range check
CVE-2017-17405 | Command injection vulnerability in `Net::FTP` | Treat paths in commands explicitly as paths, not general IO commands | Tested in MRI `test/net/ftp/test_ftp.rb` | Applied the same mitigation
CVE-2017-10784 | Escape sequence injection vulnerability in the Basic authentication of WEBrick | Proper escaping of logs | Tested in MRI `test/webrick/test_httpauth.rb` | Applied the same mitigation
CVE-2017-0898 | Buffer underrun vulnerability in `Kernel.sprintf` | | |
CVE-2017-14033 | Buffer underrun vulnerability in OpenSSL ASN1 decode | | |
CVE-2017-14064 | Heap exposure vulnerability in generating JSON | | |
CVE-2017-0902, CVE-2017-0899, CVE-2017-0900, CVE-2017-0901 | Multiple vulnerabilities in RubyGems | | |
CVE-2015-7551 | Unsafe tainted string usage in Fiddle and DL (regression of the mitigation of CVE-2009-5147) | Additional taint checks | Tested in MRI `test/mri/tests/fiddle/test_handle.rb` | Not applicable as we do not support `$SAFE`, and the `DL` module was removed in Ruby 2.2.0
CVE-2015-1855 | Ruby OpenSSL Hostname Verification | | |
CVE-2014-8090 | Another Denial of Service XML Expansion | | |
CVE-2014-8080 | Denial of Service XML Expansion | | Tested in `ruby/spec` `:security` |
None | Changed default settings of ext/openssl | | |
CVE-2014-2734 | Dispute of Vulnerability | | |
CVE-2014-0160 | OpenSSL Severe Vulnerability in TLS Heartbeat Extension | | |
CVE-2014-2525 | Heap Overflow in YAML URI Escape Parsing | | |
CVE-2013-4164 | Heap Overflow in Floating Point Parsing  | | Tested in `ruby/spec` `:security` |
CVE-2013-4073 | Hostname check bypassing vulnerability in SSL client | | |
CVE-2013-2065 | Object taint bypassing in DL and Fiddle in Ruby | Additional taint checks | Tested in MRI `test/mri/tests/fiddle/test_func.rb` | Not applicable as we do not support `$SAFE`, and the `DL` module was removed in Ruby 2.2.0
CVE-2013-1821 | Entity expansion DoS vulnerability in REXML | | |
CVE-2013-0269 | Denial of Service and Unsafe Object Creation Vulnerability in JSON | | |
CVE-2013-0256 | XSS exploit of RDoc documentation generated by `rdoc` | | |
CVE-2012-5371 | Hash-flooding DoS vulnerability for ruby 1.9 | | |
CVE-2012-4522 | Unintentional file creation caused by inserting a illegal NUL character | | |
CVE-2012-4464, CVE-2012-4466  | $SAFE escaping vulnerability about `Exception#to_s` / `NameError#to_s` | | | Not applicable as we do not support `$SAFE`
None | Security Fix for RubyGems: SSL server verification failure for remote repository | | |
CVE-2011-3389 | Security Fix for Ruby OpenSSL module: Allow 0/n splitting as a prevention for the TLS BEAST attack | | |
CVE-2011-4815 | Denial of service attack was found for Ruby's Hash algorithm (cross-reference CVE-2011-4838, CVE-2012-5370, CVE-2012-5372) | Hashes are made non-deterministic by incorporating process start time | Tested in `ruby/spec` `:security` | Hashes are made non-deterministic by incorporating a seed from `/dev/urandom`
None | Exception methods can bypass `$SAFE` || | Not applicable as we do not support `$SAFE`
None | FileUtils is vulnerable to symlink race attacks | | |
CVE-2010-0541 | XSS in WEBrick | | | WEBrick no longer shipped
None | Buffer over-run in `ARGF.inplace_mode=` | | |
None | WEBrick has an Escape Sequence Injection vulnerability | | | WEBrick no longer shipped
CVE-2009-5147 | `DL::dlopen` opens libraries with tainted names | Additional taint checks | The `DL` module does not exist in modern Ruby | Not applicable as we do not support `$SAFE`, and the `DL` module was removed in Ruby 2.2.0
CVE-2009-4124 | Heap overflow in `String` | | |
None | DoS vulnerability in `BigDecimal` | | |
None | DoS vulnerability in `REXML` | | |
CVE-2008-1447 | Multiple vulnerabilities in Ruby | | |
CVE-2008-2662, CVE-2008-2663, CVE-2008-2725, CVE-2008-2726, CVE-2008-2664, CVE-2008-1891 | Arbitrary code execution vulnerabilities | | |
None | File access vulnerability of WEBrick | | | WEBrick no longer shipped
None | `Net::HTTPS` Vulnerability | | |
JVN#84798830 | Another DoS Vulnerability in CGI Library | | |
CVE-2006-5467 | DoS Vulnerability in CGI Library | | |
VU#160012 | Ruby vulnerability in the safe level settings | | | Not applicable as we do not support `$SAFE`

## JRuby Vulnerabilities

TruffleRuby uses code from JRuby, so vulnerabilities reported against JRuby may apply to TruffleRuby.

Number | Description | Their Mitigation | Test | Our Mitigation
--- | --- | --- | --- | ---
CVE-2012-5370 | JRuby computes hash values without properly restricting the ability to trigger hash collisions predictably (cross-reference CVE-2011-4815, CVE-2011-4838, CVE-2012-5372) | Hashes are made non-deterministic by incorporating process start time | Tested in `ruby/spec` `:security` | Hashes are made non-deterministic by incorporating a seed from `/dev/urandom`
CVE-2011-4838 | JRuby before 1.6.5.1 computes hash values without restricting the ability to trigger hash collisions predictably (cross-reference CVE-2011-4815, CVE-2012-5370, CVE-2012-5372) | Hashes are made non-deterministic by incorporating process start time | Tested in `ruby/spec` `:security` | Hashes are made non-deterministic by incorporating a seed from `/dev/urandom`

## Rubinius Vulnerabilities

TruffleRuby uses code from Rubinius, so vulnerabilities reported against Rubinius may apply to TruffleRuby.

Number | Description | Their Mitigation | Test | Our Mitigation
--- | --- | --- | --- | ---
CVE-2012-5372 | Rubinius computes hash values without properly restricting the ability to trigger hash collisions predictably (cross-reference CVE-2011-4815, CVE-2011-4838, CVE-2012-5370) | Hashes are made non-deterministic by incorporating output from `/dev/urandom` | Tested in `ruby/spec` `:security` | Hashes are made non-deterministic by incorporating a seed from `/dev/urandom`

## Java Dependency Vulnerabilities

### JONI

No vulnerabilities are known.

### JCodings

Number | Description | Their Mitigation | Test | Our Mitigation
--- | --- | --- | --- | ---
CVE-2010-1330 | The regular expression engine in JRuby before 1.4.1, when `$KCODE` is set to `'u'`, does not properly handle characters immediately after a UTF-8 character | Check byte sequences for the UTF-8 encoding when perform regexp operations | Tested in `ruby/spec` `:security` | Applied the same mitigation

## Other Dependency Vulnerabilities

### `zlib`

No vulnerabilities are known, but consider potential vulnerabilities in your system `zlib`.

### `libssl`

Consider potential vulnerabilities in your system `libssl`.

### FFI

Number | Description | Their Mitigation | Test | Our Mitigation
--- | --- | --- | --- | ---
CVE-2018-1000201 | A DLL loading issue can be hijacked on Windows when a `Symbol` is used for the library name | Treat Symbols the same as Strings in `ffi_lib` | | Applied the same mitigation, by using a version of FFI which fixed this vulnerability

## Notes on Hashing

TruffleRuby uses `MurmurHash2` hashing with a seed from `/dev/urandom` - it cannot be configured to use any other hashing algorithm.
For hashing strings, TruffleRuby uses Java's hash algorithm (and then `MurmurHash2` on top).
