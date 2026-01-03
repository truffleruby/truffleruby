# Abbrev

Given a set of strings, calculate the set of unambiguous abbreviations for
those strings, and return a hash where the keys are all the possible
abbreviations and the values are the full strings.

Thus, given +words+ is "car" and "cone", the keys pointing to "car" would
be "ca" and "car", while those pointing to "cone" would be "co", "con", and
"cone".

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'abbrev'
```

And then execute:

    $ bundle install

Or install it yourself as:

    $ gem install abbrev

## Usage

```ruby
require 'abbrev'

Abbrev.abbrev(%w{ car cone })
#=> {"ca"=>"car", "con"=>"cone", "co"=>"cone", "car"=>"car", "cone"=>"cone"}
```

## Development

After checking out the repo, run `bin/setup` to install dependencies. Then, run `rake test` to run the tests. You can also run `bin/console` for an interactive prompt that will allow you to experiment.

To install this gem onto your local machine, run `bundle exec rake install`. To release a new version, update the version number in `version.rb`, and then run `bundle exec rake release`, which will create a git tag for the version, push git commits and tags, and push the `.gem` file to [rubygems.org](https://rubygems.org).

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/ruby/abbrev.

