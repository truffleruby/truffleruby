# Reporting Performance Problems

We are interested in hearing from you if you experience lower performance with TruffleRuby than with other implementations of Ruby.
Please report any performance issues you might find on [GitHub](https://github.com/truffleruby/truffleruby/issues).

The [Compatibility](compatibility.md) guide lists some features which we know are slow and are unlikely to get faster.

## Common Problems and Why

TruffleRuby uses extremely sophisticated techniques to optimize your Ruby program.
These optimizations take time to apply, so TruffleRuby is often a lot slower than other implementations *until it has had time to warm up.*

Also, TruffleRuby tries to find a "stable state" of your program and then automatically remove the dynamism of Ruby where it is not needed, but this then means that if the stable state is disturbed, performance lowers again until TruffleRuby can adapt to the new stable state.

Another problem is that TruffleRuby is very good at removing unnecessary work, such as calculations that
are not needed or loops that contain no work.

All of these issues make it hard to benchmark TruffleRuby. This is not a problem that is unique to us - it applies to many sophisticated virtual machines - but most Ruby implementations are not yet performing optimizations powerful enough to show these problems, so they may be new to some people in the Ruby community.

## Using Oracle GraalVM

To experiment with how fast TruffleRuby can be, use [Oracle GraalVM](installing-truffleruby.md#oracle-graalvm-and-graalvm-community-edition).

## Using the JVM Configuration

For the best peak performance, use the JVM configuration, using `--jvm`.
The default native configuration starts faster but does not quite reach the same peak performance.
However, you *must* then use a good benchmarking tool, like `benchmark-ips` described below, to run the benchmark, or the slower
warmup time will mean that you do not see TruffleRuby's true performance in the benchmark.
If you want to write simpler benchmarks that just run a while loop with a simple timer (which we would not recommend anyway), then use the default native mode so that startup and warmup time is shorter.

## How to Check for Basic Performance Problems

If you are examining the performance of TruffleRuby, we would recommend that you always run with the `--engine.TraceCompilation` flag.
If you see compilation failures or repeated compilation of the same methods, this is an indicator that something is not working as intended and you may need to examine why, or ask us to help you do so.
If you do not run with this flag, TruffleRuby will try to work around errors and you will not see that there is a problem.

## How to Write a Performance Benchmark

The TruffleRuby team recommends that you use [`benchmark-ips`](https://github.com/evanphx/benchmark-ips) to check the performance of TruffleRuby.
It makes things easier for us if you report any potential performance problems using a report from `benchmark-ips`.

A benchmark could look like this:

```ruby
require 'benchmark/ips'

Benchmark.ips do |x|
  x.iterations = 2

  x.report("adding") do
    14 + 2
  end
end
```

We use the `x.iterations =` extension in `benchmark-ips` to run the warmup and measurement cycles of `benchmark-ips` two times, to ensure the results are stable and that enough warmup was provided (which can be tweaked with `x.warmup = 5`).

You should see something like this:

```bash
Warming up --------------------------------------
              adding    20.933k i/100ms
              adding     1.764M i/100ms
Calculating -------------------------------------
              adding      2.037B (±12.7%) i/s -      9.590B in   4.965741s
              adding      2.062B (±11.5%) i/s -     10.123B in   4.989398s
```

We want to look at the last line, which says that TruffleRuby runs 2.062 billion iterations of this block per second, with a margin of error of ±11.5%.

Compare that to an implementation like Rubinius:

```bash
Warming up --------------------------------------
              adding    71.697k i/100ms
              adding    74.983k i/100ms
Calculating -------------------------------------
              adding      2.111M (±12.2%) i/s -     10.302M
              adding      2.126M (±10.6%) i/s -     10.452M
```

Here, TruffleRuby's performance can be described as a thousand times faster than Rubinius.
That seems like a lot - and what is actually happening here is that TruffleRuby is optimizing
away your benchmark.
The effect is less pronounced with complex code that cannot be optimized away.

### Final Technical Note: Blackholes and Value Profiling

Some benchmarking tools for other languages have features called "blackholes."
These surround a value and make it appear to be a variable at runtime, even if it is in fact a constant, so that the optimizer does not remove it and actually performs any computations that use it.
However, TruffleRuby uses extensive value profiling (caching of values and turning them into constants), so that even if you make a value appear to be a variable at its source, it is likely to be value-profiled at an intermediate stage.
In general, more complex benchmarks that naturally defeat value profiling are preferable, rather than manually adding annotations to turn off important features.
