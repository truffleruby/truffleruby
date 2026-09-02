# MRI Tests

MRI tests are located in the `test/mri` directory.

## Running

MRI tests are run using the `jt` tool. By default these commands will not run
tests that are ignored using test exclude files in the `test/mri/excludes`
directory (more exclude details below).

### Run test suite

The `jt test mri` command is used to run all MRI test files that can be run
normally. `--openssl` runs those using `openssl`.

### Run a single test

A single MRI test can be run using the command `jt test mri ruby/test_time.rb`  
or with the full path `jt test mri test/mri/tests/ruby/test_time.rb`.

## Excluding tests 

Whole files are excluded in:
* `test/mri/failing.exclude` - excludes failing test files 

Individual test excludes are created in the `test/mri/excludes`
directory using the test's class name as the file name.

For example the `TestTime` class found in `test/mri/test/ruby/test_time.rb` has
a corresponding exclude file: `test/mri/excludes/TestTime.rb`.

For a nested test class structure like `TestPrime::TestInteger` in
`test_prime.rb`, the exclude directory for `TestInteger` is created in a
`TestPrime` sub-directory.

The lines in exclude file appear like this:

```ruby
exclude :test_asctime, "needs investigation"
exclude :test_at, "needs investigation"
```

Each line starts with the `exclude` method followed by two arguments: first the
test method name to exclude, and then a comment describing the reason for the
exclude.

There is a tool to assist in tagging/untagging tests:

1. Identify the related exclude files and delete them: `rm test/mri/excludes/TEST.rb`.
2. Run `jt test mri TEST | tee output.txt`
3. Run `ruby tool/parse_mri_errors.rb output.txt`
4. Check all tests pass with `jt test mri TEST`
