# Syslog

A Simple wrapper for the UNIX syslog system calls that might be handy
if you're writing a server in Ruby.  For the details of the syslog(8)
architecture and constants, see the syslog(3) manual page of your
platform.

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'syslog'
```

And then execute:

    $ bundle install

Or install it yourself as:

    $ gem install syslog

## Usage

```ruby
require "syslog"
Syslog.open("webrick", Syslog::LOG_PID,
            Syslog::LOG_DAEMON | Syslog::LOG_LOCAL3)
```

## Development

After checking out the repo, run `bin/setup` to install dependencies. Then, run `rake test` to run the tests. You can also run `bin/console` for an interactive prompt that will allow you to experiment.

To install this gem onto your local machine, run `bundle exec rake install`. To release a new version, update the version number in `version.rb`, and then run `bundle exec rake release`, which will create a git tag for the version, push git commits and tags, and push the `.gem` file to [rubygems.org](https://rubygems.org).

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/ruby/syslog.

