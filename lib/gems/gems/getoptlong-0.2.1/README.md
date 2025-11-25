# Getoptlong

The GetoptLong class allows you to parse command line options similarly to
the GNU getopt_long() C library call. Note, however, that GetoptLong is a
pure Ruby implementation.

GetoptLong allows for POSIX-style options like <tt>--file</tt> as well
as single letter options like <tt>-f</tt>

The empty option <tt>--</tt> (two minus symbols) is used to end option
processing. This can be particularly important if options have optional
arguments.

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'getoptlong'
```

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install getoptlong

## Usage

```ruby
require 'getoptlong'

opts = GetoptLong.new(
  [ '--help', '-h', GetoptLong::NO_ARGUMENT ],
  [ '--repeat', '-n', GetoptLong::REQUIRED_ARGUMENT ],
  [ '--name', GetoptLong::OPTIONAL_ARGUMENT ]
)

dir = nil
name = nil
repetitions = 1
opts.each do |opt, arg|
  case opt
    when '--help'
      puts <<-EOF
hello [OPTION] ... DIR
-h, --help:
   show help
--repeat x, -n x:
   repeat x times
--name [name]:
   greet user by name, if name not supplied default is John
DIR: The directory in which to issue the greeting.
      EOF
    when '--repeat'
      repetitions = arg.to_i
    when '--name'
      if arg == ''
        name = 'John'
      else
        name = arg
      end
  end
end

if ARGV.length != 1
  puts "Missing dir argument (try --help)"
  exit 0
end

dir = ARGV.shift

Dir.chdir(dir)
for i in (1..repetitions)
  print "Hello"
  if name
    print ", #{name}"
  end
  puts
end
```

Example command line:

```
hello -n 6 --name -- /tmp
```

## Development

After checking out the repo, run `bin/setup` to install dependencies. Then, run `rake test` to run the tests. You can also run `bin/console` for an interactive prompt that will allow you to experiment.

To install this gem onto your local machine, run `bundle exec rake install`. To release a new version, update the version number in `version.rb`, and then run `bundle exec rake release`, which will create a git tag for the version, push git commits and tags, and push the `.gem` file to [rubygems.org](https://rubygems.org).

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/ruby/getoptlong.
