# A simple blog

This is a simple blog application that demonstrates running Rails, with the
option of using several different database backends.

## System dependencies

You need to install system packages for SQLite, MySQL and PostgreSQL (whether
you are using MRI, TruffleRuby or Rubinius.)

On macOS:

```shell script
brew install sqlite mysql postgresql
```

## Initial generation

The base of the application was generated with:

```shell script
rails new blog --database=sqlite3 --skip-bootsnap --skip-listen
```

These skip options are needed for TruffleRuby at the moment but we're working
on removing them.

## Setup

These commands are the same for any implementation of Ruby. To use TruffleRuby
in a source repository, you need to put `bin` on your `$PATH`, as Rails starts
sub-rubies using `system`.

This is the standard command to install Bundler.

```shell script
gem install bundler
```

Then the standard command to install dependencies and setup the database.

```shell script
bin/setup
```

And the standard command to start Rails.

```shell script
bundle exec rails server
```

You can now visit http://localhost:3000.

## Using different DB backends

Rails applications by default have three environments: `development`, `test`,
`production`. Those are configured in the blog application to use SQLite as
the database.

This application has additional environments to run with a different database
driver. `development-postgresql`, `test-postgresql` and
`production-postgresql` use PostgreSQL. `development-mysql`, `test-mysql`, and
`production-mysql` use MySQL.

To switch to a different database driver, set the `RAILS_ENV` environment
variable, then run `bin/setup` again.

```shell script
export RAILS_ENV=development-mysql
bin/setup
bundle exec rails server
```

Except for SQLite, you will need to actually run the database service for the
application separately.

```shell script
brew services start mysql
brew services stop mysql
```

## Comparison to JRuby

JRuby does not support C extensions, so the gems `sqlite3`, `mysql2` and `pg`
need to be replaced. The JRuby wiki recommends using
`activerecord-jdbcsqlite3-adapter`, `activerecord-jdbcmysql-adapter `, and
`activerecord-jdbcpostgresql-adapter` instead.

You could add a new `:jruby` platform to the Gemfile to allow this to work on
JRuby but still keep working on MRI, TruffleRuby and Rubinius, or you could
just replace the gem names if you only wanted to run on JRuby.

If you don't replace the C extensions you will see a failure as MySQL looks
for `rb_absint_size`.

With the gem changes the app should work as on MRI.

## Comparison to Rubinius

The `mysql2` C extension does not compile on Rubinius, as `ST_CONTINUE` is not
supported https://github.com/rubinius/rubinius/issues/3795. MySQL can be
disabled using `config`.

```shell script
bundle config --local without mysql
```

PostgreSQL also does not compile on Rubinius, as `ENCODING_SET_INLINED` is not
supported https://owo.codes/owo/ruby-pg/commit/fab02db59c5158e350702869809fd9deb9009641.
PostgreSQL can be disabled as well using `config`.

```shell script
bundle config --local without postgresql mysql
```

`bin/setup` then fails on Rubinus 3.107 while preparing the database, either
with a `SIGSEGV` in the garbage collector while parsing
https://github.com/rubinius/rubinius/issues/3807, or with `racc` not being
supported https://github.com/rubinius/rubinius/issues/2632.

You can add `gem "racc"` to your `Gemfile` to work around this.

You will then see
`Circular dependency detected while autoloading constant PostsController`
while attempting to view a page.
