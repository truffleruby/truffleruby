# Observer

The Observer pattern (also known as publish/subscribe) provides a simple
mechanism for one object to inform a set of interested third-party objects
when its state changes.

## Mechanism

The notifying class mixes in the +Observable+
module, which provides the methods for managing the associated observer
objects.

The observable object must:

* assert that it has +#changed+
* call +#notify_observers+

An observer subscribes to updates using Observable#add_observer, which also
specifies the method called via #notify_observers. The default method for
notify_observers is #update.

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'observer'
```

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install observer

## Usage

The following example demonstrates this nicely.  A +Ticker+, when run,
continually receives the stock +Price+ for its <tt>@symbol</tt>.  A +Warner+
is a general observer of the price, and two warners are demonstrated, a
+WarnLow+ and a +WarnHigh+, which print a warning if the price is below or
above their set limits, respectively.

The +update+ callback allows the warners to run without being explicitly
called.  The system is set up with the +Ticker+ and several observers, and the
observers do their duty without the top-level code having to interfere.

Note that the contract between publisher and subscriber (observable and
observer) is not declared or enforced.  The +Ticker+ publishes a time and a
price, and the warners receive that.  But if you don't ensure that your
contracts are correct, nothing else can warn you.

```ruby
require "observer"

class Ticker          ### Periodically fetch a stock price.

  include Observable

  def initialize(symbol)
    @symbol = symbol
  end

  def run
    last_price = nil
    loop do
      price = Price.fetch(@symbol)
      print "Current price: #{price}\n"
      if price != last_price
        changed                 # notify observers
        last_price = price
        notify_observers(Time.now, price)
      end
      sleep 1
    end
  end
end

class Price           ### A mock class to fetch a stock price (60 - 140).
  def self.fetch(symbol)
    60 + rand(80)
  end
end

class Warner          ### An abstract observer of Ticker objects.
  def initialize(ticker, limit)
    @limit = limit
    ticker.add_observer(self)
  end
end

class WarnLow < Warner
  def update(time, price)       # callback for observer
    if price < @limit
      print "--- #{time.to_s}: Price below #@limit: #{price}\n"
    end
  end
end

class WarnHigh < Warner
  def update(time, price)       # callback for observer
    if price > @limit
      print "+++ #{time.to_s}: Price above #@limit: #{price}\n"
    end
  end
end

ticker = Ticker.new("MSFT")
WarnLow.new(ticker, 80)
WarnHigh.new(ticker, 120)
ticker.run
```

Produces:

```
Current price: 83
Current price: 75
--- Sun Jun 09 00:10:25 CDT 2002: Price below 80: 75
Current price: 90
Current price: 134
+++ Sun Jun 09 00:10:25 CDT 2002: Price above 120: 134
Current price: 134
Current price: 112
Current price: 79
--- Sun Jun 09 00:10:25 CDT 2002: Price below 80: 79
```

## Development

After checking out the repo, run `bin/setup` to install dependencies. Then, run `rake test` to run the tests. You can also run `bin/console` for an interactive prompt that will allow you to experiment.

To install this gem onto your local machine, run `bundle exec rake install`. To release a new version, update the version number in `version.rb`, and then run `bundle exec rake release`, which will create a git tag for the version, push git commits and tags, and push the `.gem` file to [rubygems.org](https://rubygems.org).

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/ruby/observer.
