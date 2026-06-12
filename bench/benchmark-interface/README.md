# Benchmark-Interface

## Introduction

Benchmark-Interface is one Ruby benchmarking interface to rule them all. It
allows you to run benchmarks written for one Ruby benchmarking system using some
other Ruby benchmarking system.

For example, Benchmark-Interface lets you take a benchmark from MRI's suite and
run it using `benchmark-ips`, or take a benchmark written using Bench9000 and
run it using `bmbm`.

Benchmark-Interface also provides a new format for writing benchmarks, which is
designed to be the simplest of all, making it easy to write lots of new
benchmarks and run them any way you like.

```
$ gem install benchmark-interface
```

Benchmark-Interface doesn't install any backends (other benchmark tools) by
default, so you probably also want to do:

```
$ gem install benchmark-ips
```

## Examples

### Running an MRI benchmark using `benchmark-ips`

```
$ benchmark ruby/benchmark/bm_vm1_length.rb

These are long benchmarks - we're increasing warmup and sample time
Warming up --------------------------------------
       bm_vm1_length     1.000  i/100ms
       bm_vm1_length     1.000  i/100ms
       bm_vm1_length     1.000  i/100ms
Calculating -------------------------------------
       bm_vm1_length      0.955  (± 0.0%) i/s -     10.000  in  10.472341s
       bm_vm1_length      0.960  (± 0.0%) i/s -     10.000  in  10.433246s
       bm_vm1_length      0.975  (± 0.0%) i/s -     10.000  in  10.260680s
```

### Running a `bench9000` benchmark using `bmbm`

```
$ benchmark bench9000/benchmarks/classic/mandelbrot.rb --bmbm

                 user     system      total        real
mandelbrot   1.760000   0.030000   1.790000 (  1.804423)
```

## The Benchmark-Interface Interface

Benchmark-Interface provides its own new format for writing benchmarks. It's
very simple.

```ruby
benchmark { 14 * 14 * 14 }
benchmark { 14 ** 3 }
```

You can give benchmarks names, and you usually should, but they will be named
with the file name and a counter if you don't.

```ruby
benchmark('mul') { 14 * 14 * 14 }
benchmark('pow') { 14 ** 3 }
```

You just write that in the file. You don't need to require anything. We talk
about all the benchmarks in a file being the 'benchmark set'.

If `benchmark` is for some reason overloaded, you can also use
`BenchmarkInterface.benchmark`.

## Frontends

If you already have benchmarks written for a different system you can run those
with Benchmark-Interface.

Supported frontends are:

* Benchmark-Interface
* MRI's benchmarks
* The `benchmark` standard library (`measure`, `bm`, `bmbm` and so on)
* RBench
* Perfer
* `benchmark-ips`
* `bench9000`

### Notes on specific frontends

#### Benchmark-Interface

As well as normal usage, you can also `require 'benchmark-interface'` at the top
of your file of benchmarks, and then run the file as a normal Ruby script. This
will have the same effect as `benchmark file.rb` (and so will run
`benchmark-ips` as the backend).

#### MRI's benchmarks

To run MRI's benchmarks you need two extra gems.

```
$ gem install parser unparser
```

For MRI's benchmarks we detect the last statement, which is usually a `while`
loop and wrap that in a Benchmark-Interface block. If we see a variable being
initialised and then used in the last statement, we copy that into the block.
This doesn't work in all cases, but it does in most. The `--show-rewrite` option
shows you what we are doing. Please file a bug if we're getting it wrong for any
benchmarks in the MRI repository.

Running MRI's benchmarks requires rewriting their source code, which means
running the fairly complex `parser` and `unparser` gems. This is a bit much to
ask of new Ruby implementations, so you can do the rewriting using another
implementation and get it to cache the result for the real implementation. The
file is written to `mri-rewrite-cache.rb` in the current directory.

```
$ rbenv shell 2.3.1
$ benchmark ruby/benchmark/bm_vm1_length.rb --cache
$ rbenv shell topaz-dev
$ benchmark ruby/benchmark/bm_vm1_length.rb --use-cache
```

#### `benchmark-ips` and Perfer

Both `benchmark-ips` and Perfer allow you to write benchmarked blocks which
take a number of iterations. This is done to reduce the overhead of calling
the block by putting the iterations loop (presumably a `while` loop, so not
itself involving another block) inside the benchmarked code. However this isn't
supported by other backends. To make this work, we look at how many iterations
are needed to make the benchmark run for about a second and always run that
many iterations. The number of iterations is the same for all benchmarks, and
we take the smallest number, as the larger number can be unbound and make
some benchmarks take a very long time.

## Backends

When you have a file of benchmarks, you can run it using different Ruby
benchmarking systems. By default, it will run your benchmarks with
`benchmark/ips`, which is usually the best choice.

Supported backends are:

* Simple looping, via `--simple`
* `bm`, via `--bm`
* `bmbm`, via `--bmbm`
* `benchmark-ips`, via `--bips`
* Bench9000, via `--bench9000`

### Notes on specific backends

#### Simple looping

Simple looping does the simplest thing possible and runs your benchmark in a
loop printing the iterations per second as it goes. By default, it runs for
10s, printing the time every second, but you can use options like `--time 60`
to run for 60 seconds, and `--freq 0.1` to print ten times a second.

You can also print the actual number of iterations with the `--iterations` flag
and the elapsed time since the benchmark started with the `--elapsed` flag.

#### `bm` and `bmbm`

`bm` and `bmbm` only run your benchmark once when timing, which isn't what
benchmarks written for `benchmark-ips` expect. Therefore, if a benchmark takes
less than a tenth of a second it is run several times so that it takes about a
second. The number of iterations is the same for all benchmarks in the same set
and is printed as the set starts.

You can turn this off with `--no-scale`

#### `benchmark-ips`

We run with `x.iterations = 3` by default.

If a benchmark takes more than a tenth of a second, the warmup and sampling
periods of `benchmark-ips` will be increased, as by default `benchmark-ips` is
set up for quicker benchmarks that complete many iterations in a second.

You can turn this off with `--no-scale`.

You'll need to manually install the `benchmark-ips` gem.

#### Bench9000

Using Bench9000 as a backend is a little more complex. You need to define a
configuration file which runs `benchmark` with the benchmark file, the
`--bench9000` flag and the name of the benchmark, and then you separately run
the `bench9000` command.

```
benchmark 'clamp_a', 'benchmark examples/benchmark-interface.rb --bench9000 clamp_a'
benchmark 'clamp_b', 'benchmark examples/benchmark-interface.rb --bench9000 clamp_b'
```

```
$ bench9000 detail --config bench9000.config 2.3.0 clamp_a clamp_b --value-per-line
```

The Bench9000 has the same scaling functionality as `bm`, but here it switches
to the micro-harness. Turn it off with `--no-scale`.

You'll need to manually install the Bench9000 gem.

```
$ gem install bench9000
```

## Other Commands

The default command is implicit and is `run`. Other commands are available.

### List benchmarks

```
$ benchmark list examples/bips.rb
bips:1
clamp_a1
clamp_b1
clamp_a2
clamp_a3
```

## Supported Ruby Implementations

Tested versions are in brackets.

* MRI (1.8.7-p371, 1.9.3-p547, 2.0.0-p648, 2.1.10, 2.2.5, 2.3.1, head)
* JRuby (1.7.25, 9.0.5.0, 9.1.0.0, head)
* TruffleRuby
* Rubinius (2.71828182, 3.29)
* Topaz

Benchmark-Interface is designed to be gentle on new implementations of Ruby and
should hopefully be relatively easy to get working if you are writing one.

One major limitation is that the `unparser` gem doesn't work with Ruby versions
below 1.9. You can get around this by caching the translation result with a
supported version of Ruby (the `--cache` option) and then using that cached
translation in the version of Ruby you are benchmarking (the `--use-cache`
option).

You also need to install these gems manually if you want to translate MRI
benchmarks.

## Notes on specific implementations

### JRuby

JRuby does not currently have on-stack-replacement, or back-jump counters. This
can cause it to become stuck in a single activation of a long-running method
like mandelbrot. For benchmarks such as this you may want to consider setting
`-X+C` to ahead-of-time compile the benchmark. This would happen anyway if
the benchmark was the main script, instead of the `benchmark` executable, so it
is not an unusual option to set.

## Caveats

* This tool does nothing to address the risk of inputs being constant folded or
results discarded.
* Don't compare results from two different benchmarking backends.
* Some automated things we do like scaling and setting iterations can
complicate benchmarking.
* Use your own scientific judgement!
