# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

from __future__ import division

import os
import json
import shlex
import signal
import subprocess
import sys
import time
from os.path import join

import mx
import mx_benchmark

# TODO IMPORTANT: you need RUBY_BENCHMARKS=true for this file to be imported by mx_truffleruby.py

# Utilities

_suite = mx.suite('truffleruby')
rubyDir = _suite.dir

def jt(args, nonZeroIsFatal=True, out=None, err=None, timeout=None, env=None, cwd=None):
    jt = join(rubyDir, 'tool', 'jt.rb')
    return mx.run(['ruby', jt] + args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, env=env, cwd=cwd)

FNULL = open(os.devnull, 'w')

class BackgroundServerTask:
    def __init__(self, args):
        self.args = args

    def __enter__(self):
        if mx._opts.verbose:
            mx.log(' '.join(['(background)'] + [shlex.quote(arg) for arg in self.args]))
        self.process = subprocess.Popen(self.args, start_new_session=True)
        mx._addSubprocess(self.process, self.args)

    def __exit__(self, exc_type, value, traceback):
        self.process.send_signal(signal.SIGINT)
        self.process.wait()

    def is_running(self):
        return self.process.poll() is None

class BackgroundJT(BackgroundServerTask):
    def __init__(self, args):
        jt = join(rubyDir, 'tool', 'jt.rb')
        BackgroundServerTask.__init__(self, ['ruby', jt] + args)


# Benchmarks

class RubyBenchmarkSuite(mx_benchmark.BenchmarkSuite):
    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'truffleruby'

    def vmArgs(self, bmSuiteArgs):
        return mx_benchmark.splitArgs(bmSuiteArgs, bmSuiteArgs)[0]

    def runArgs(self, bmSuiteArgs):
        return mx_benchmark.splitArgs(bmSuiteArgs, bmSuiteArgs)[1]

    def default_benchmarks(self, bmSuiteArgs):
        return self.benchmarkList(bmSuiteArgs)

    def run(self, benchmarks, bmSuiteArgs):
        def fixUpResult(result):
            result.update({
                'host-vm': os.environ.get('HOST_VM', 'host-vm'),
                'host-vm-config': os.environ.get('HOST_VM_CONFIG', 'host-vm-config'),
                'guest-vm': os.environ.get('GUEST_VM', 'guest-vm'),
                'guest-vm-config': os.environ.get('GUEST_VM_CONFIG', 'guest-vm-config')
            })
            return result

        return [fixUpResult(r) for b in benchmarks or self.default_benchmarks(bmSuiteArgs) for r in self.runBenchmark(b, bmSuiteArgs)]

    def runBenchmark(self, benchmark, bmSuiteArgs):
        raise NotImplementedError()

build_stats_benchmarks = {
    'binary-size': ['size', 'MiB'],
    'build-time': ['time', 's'],
    'runtime-compilable-methods': ['count', '#']
}

class BuildStatsBenchmarkSuite(RubyBenchmarkSuite):
    def benchmarkList(self, bmSuiteArgs):
        return list(build_stats_benchmarks.keys())

    def name(self):
        return 'build-stats'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['build_stats', benchmark, '--json'] + bmSuiteArgs, out=out)

        data = json.loads(out.data)
        (metric_name, metric_unit) = build_stats_benchmarks[benchmark]

        ret = {
            'benchmark': benchmark,
            'metric.name': metric_name,
            'metric.value': data[benchmark],
            'metric.unit': metric_unit,
            'metric.better': 'lower',
        }

        if data[benchmark] == -1:
            ret['error'] = 'failed'

        return [ret]

metrics_benchmarks = {
    'hello': ['-e', "puts 'hello'"],
    'compile-mandelbrot': [join(rubyDir, 'bench', 'metrics', 'mandelbrot.rb')]
}

default_metrics_benchmarks = ['hello', 'compile-mandelbrot']

class MetricsBenchmarkSuite(RubyBenchmarkSuite):
    def benchmarkList(self, bmSuiteArgs):
        return list(metrics_benchmarks.keys())

    def default_benchmarks(self, bmSuiteArgs):
        return default_metrics_benchmarks

class AllocationBenchmarkSuite(MetricsBenchmarkSuite):
    def name(self):
        return 'allocation'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['metrics', 'alloc', '--json'] + metrics_benchmarks[benchmark] + bmSuiteArgs, out=out)

        data = json.loads(out.data)

        return [{
            'benchmark': benchmark,
            'metric.name': 'memory',
            'metric.value': sample,
            'metric.unit': 'B',
            'metric.better': 'lower',
            'metric.iteration': n
        } for n, sample in enumerate(data['samples'])]

class InstructionsBenchmarkSuite(MetricsBenchmarkSuite):
    def name(self):
        return 'instructions'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['metrics', 'instructions', '--json'] + metrics_benchmarks[benchmark] + bmSuiteArgs, out=out)

        data = json.loads(out.data)

        return [{
            'benchmark': benchmark,
            'metric.name': 'count',
            'metric.value': data['instructions'],
            'metric.unit': '#',
            'metric.better': 'lower',
        }]

class MinHeapBenchmarkSuite(MetricsBenchmarkSuite):
    def name(self):
        return 'minheap'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['metrics', 'minheap', '--json'] + metrics_benchmarks[benchmark] + bmSuiteArgs, out=out)

        data = json.loads(out.data)

        return [{
            'benchmark': benchmark,
            'metric.name': 'memory',
            'metric.value': data['min'],
            'metric.unit': 'MiB',
            'metric.better': 'lower'
        }]

class MaxRssBenchmarkSuite(MetricsBenchmarkSuite):
    def name(self):
        return 'maxrss'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['metrics', 'maxrss', '--json'] + metrics_benchmarks[benchmark] + bmSuiteArgs, out=out)

        data = json.loads(out.data)

        return [{
            'benchmark': benchmark,
            'extra.metric.region': region,
            'metric.name': 'memory',
            'metric.value': sample,
            'metric.unit': 'MiB',
            'metric.better': 'lower',
            'metric.iteration': n
        } for region, region_data in data.items() for n, sample in enumerate(region_data['samples'])]

class TimeBenchmarkSuite(MetricsBenchmarkSuite):
    def name(self):
        return 'time'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        out = mx.OutputCapture()

        jt(['metrics', 'time', '--json'] + metrics_benchmarks[benchmark] + bmSuiteArgs, out=out)

        lines = [line for line in out.data.split('\n') if len(line) > 0]
        mx.log('\n'.join(lines[0:-1]))

        json_data = lines[-1]
        mx.log('JSON:')
        mx.log(json_data)
        data = json.loads(json_data)

        return [{
            'benchmark': benchmark,
            'extra.metric.region': region,
            'metric.name': 'time',
            'metric.value': sample,
            'metric.unit': 'ms',
            'metric.better': 'lower',
            'metric.iteration': n
        } for region, region_data in data.items() for n, sample in enumerate(region_data['samples'])]

class AllBenchmarksBenchmarkSuite(RubyBenchmarkSuite):
    def config(self):
        return {'kind': 'simple'}

    def benchmarkList(self, bmSuiteArgs):
        raise NotImplementedError()

    def name(self):
        raise NotImplementedError()

    def time(self):
        raise NotImplementedError()

    def directory(self):
        return self.name()

    def filterLines(self, lines):
        data = []
        for line in lines:
            try:
                data.append(int(line))
            except ValueError:
                try:
                    data.append(float(line))
                except ValueError:
                    mx.log_error(line)
        if len(data) % 3 != 0:
            raise AssertionError("Number of values not a multiple of 3")
        return data

    def runBenchmark(self, benchmark, bmSuiteArgs):
        directory = self.directory()
        if directory is None:
            directory, benchmark = benchmark.split('/')

        arguments = ['benchmark']
        if self.config()['kind'] == 'simple':
            arguments.extend(['--simple', '--iterations', '--elapsed'])
            time = self.time()
            if isinstance(time, dict):
                if benchmark in time:
                    time = str(time[benchmark])
                else:
                    time = str(time['default'])
            else:
                time = str(self.time())
            arguments.extend(['--time', time])
        elif self.config()['kind'] == 'fixed-iterations':
            iterations_config = self.config()['iterations'][benchmark]
            fixed_iterations = sorted(iterations_config.keys())
            fixed_iterations_arg = ','.join([str(i) for i in fixed_iterations])
            arguments.extend(['--iterations', '--elapsed', '--ips'])
            arguments.extend(['--fixed-iterations'])
            arguments.extend([fixed_iterations_arg])
            if iterations_config != {1:'single-shot'}:
                # single-shot benchmarks use subprocesses so startup is already included
                arguments.extend(['--start-time', 'START_TIME_SET_BY_JT_BENCHMARK'])
        else:
            raise AssertionError("Unknown benchmark kind: " + self.config()['kind'])

        if ':' in benchmark:
            benchmark_file, _, benchmark_name = benchmark.partition(':')
            benchmark_names = [benchmark_name]
        else:
            benchmark_file = benchmark
            benchmark_names = []
        arguments.extend(['bench/' + directory + '/' + benchmark_file + '.rb'])
        arguments.extend(benchmark_names)
        arguments.extend(bmSuiteArgs)
        out = mx.OutputCapture()

        if jt(arguments, out=out, nonZeroIsFatal=False) == 0:
            mx.log(out.data)
            lines = out.data.split('\n')[1:-1]

            data = self.filterLines(lines)
            iterations = [d for n, d in enumerate(data) if n % 3 == 0]
            elapsed = [d for n, d in enumerate(data) if n % 3 == 1]
            samples = [d for n, d in enumerate(data) if n % 3 == 2]

            if lines[-1] == 'optimised away':
                return [{
                    'benchmark': benchmark,
                    'metric.name': 'throughput',
                    'metric.value': sample,
                    'metric.unit': 'op/s',
                    'metric.better': 'higher',
                    'metric.iteration': n,
                    'extra.metric.warmedup': 'false',
                    'extra.metric.elapsed-num': e
                } for n, (e, sample) in enumerate(zip(elapsed, samples))] + [{
                    'benchmark': benchmark,
                    'metric.name': 'throughput',
                    'metric.value': 2147483647, # arbitrary high value (--simple won't run more than this many ips)
                    'metric.unit': 'op/s',
                    'metric.better': 'higher',
                    'metric.iteration': len(samples),
                    'extra.metric.warmedup': 'true',
                    'extra.metric.elapsed-num': elapsed[-1] + 2.0 if elapsed else 2.0, # just put the data point beyond the last one a bit
                    'error': 'optimised away'
                }]
            elif self.config()['kind'] == 'fixed-iterations':
                iteration_config = self.config()['iterations'][benchmark]
                return [{
                    'benchmark': benchmark,
                    'metric.name': iteration_config[iteration],
                    'metric.iteration': iteration,
                    'metric.value': e,
                    'metric.unit': 's',
                    'metric.better': 'lower'
                } for n, (e, iteration) in enumerate(zip(elapsed, iterations)) if iteration in iteration_config]
            else:
                return [{
                    'benchmark': benchmark,
                    'metric.name': 'throughput',
                    'metric.value': sample,
                    'metric.unit': 'op/s',
                    'metric.better': 'higher',
                    'metric.iteration': n,
                    'extra.metric.warmedup': 'true' if n / float(len(samples)) >= 0.5 else 'false',
                    'extra.metric.elapsed-num': e
                } for n, (e, sample) in enumerate(zip(elapsed, samples))]
        else:
            mx.log_error("ERROR:")
            mx.log_error(out.data)

            return [{
                'benchmark': benchmark,
                'metric.name': 'throughput',
                'metric.value': 0,
                'metric.unit': 'op/s',
                'metric.better': 'higher',
                'extra.metric.warmedup': 'true',
                'error': 'failed'
            }]

classic_benchmarks = [
    'aobench',
    'binary-trees',
    'deltablue',
    'fannkuch',
    'mandelbrot',
    'matrix-multiply',
    'n-body',
    'neural-net',
    'pidigits',
    'red-black',
    'richards',
    'richards-kwargs',
    'spectral-norm'
]

classic_benchmark_time = 120

class ClassicBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'classic'

    def directory(self):
        return 'classic'

    def benchmarkList(self, bmSuiteArgs):
        return classic_benchmarks

    def time(self):
        return classic_benchmark_time

chunky_benchmarks = [
    'chunky-color-r',
    'chunky-color-g',
    'chunky-color-b',
    'chunky-color-a',
    'chunky-color-compose-quick',
    'chunky-canvas-resampling-bilinear',
    'chunky-canvas-resampling-nearest-neighbor',
    'chunky-canvas-resampling-steps-residues',
    'chunky-canvas-resampling-steps',
    'chunky-decode-png-image-pass',
    'chunky-encode-png-image-pass-to-stream',
    'chunky-operations-compose',
    'chunky-operations-replace'
]

chunky_benchmark_time = 120

class ChunkyBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'chunky'

    def directory(self):
        return 'chunky_png'

    def benchmarkList(self, bmSuiteArgs):
        return chunky_benchmarks

    def time(self):
        return chunky_benchmark_time

psd_benchmarks = [
    'psd-color-cmyk-to-rgb',
    'psd-compose-color-burn',
    'psd-compose-color-dodge',
    'psd-compose-darken',
    'psd-compose-difference',
    'psd-compose-exclusion',
    'psd-compose-hard-light',
    'psd-compose-hard-mix',
    'psd-compose-lighten',
    'psd-compose-linear-burn',
    'psd-compose-linear-dodge',
    'psd-compose-linear-light',
    'psd-compose-multiply',
    'psd-compose-normal',
    'psd-compose-overlay',
    'psd-compose-pin-light',
    'psd-compose-screen',
    'psd-compose-soft-light',
    'psd-compose-vivid-light',
    'psd-imageformat-layerraw-parse-raw',
    'psd-imageformat-rle-decode-rle-channel',
    'psd-imagemode-cmyk-combine-cmyk-channel',
    'psd-imagemode-greyscale-combine-greyscale-channel',
    'psd-imagemode-rgb-combine-rgb-channel',
    'psd-renderer-blender-compose',
    'psd-renderer-clippingmask-apply',
    'psd-renderer-mask-apply',
    'psd-util-clamp',
    'psd-util-pad2',
    'psd-util-pad4'
]

psd_benchmark_time = 120

class PSDBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'psd'

    def directory(self):
        return 'psd.rb'

    def benchmarkList(self, bmSuiteArgs):
        return psd_benchmarks

    def time(self):
        return psd_benchmark_time

image_demo_benchmarks = [
    'image-demo-conv',
    'image-demo-sobel',
]

image_demo_benchmark_time = 120

class ImageDemoBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'image-demo'

    def directory(self):
        return 'image-demo'

    def benchmarkList(self, bmSuiteArgs):
        return image_demo_benchmarks

    def time(self):
        return image_demo_benchmark_time

asciidoctor_benchmarks = [
    'asciidoctor:file-lines',
    'asciidoctor:string-lines',
    'asciidoctor:read-line',
    'asciidoctor:restore-line',
    'asciidoctor:quote-match',
    'asciidoctor:quote-sub',
    'asciidoctor:join-lines',
    'asciidoctor-convert',
    'asciidoctor-load-file',
]

asciidoctor_benchmark_time = {
    'asciidoctor-convert': 400,
    'asciidoctor-load-file': 400,
    'default': 120
}

class AsciidoctorBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'asciidoctor'

    def benchmarkList(self, bmSuiteArgs):
        return asciidoctor_benchmarks

    def time(self):
        return asciidoctor_benchmark_time

class OptcarrotBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'optcarrot'

    def directory(self):
        return 'optcarrot'

    def benchmarkList(self, bmSuiteArgs):
        return ['optcarrot']

    def time(self):
        return 200

synthetic_benchmarks = [
    'acid'
]

synthetic_benchmark_time = 120

class SyntheticBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'synthetic'

    def benchmarkList(self, bmSuiteArgs):
        return synthetic_benchmarks

    def time(self):
        return synthetic_benchmark_time

micro_benchmark_time = 30

class MicroBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'micro'

    def benchmarkList(self, bmSuiteArgs):
        # Generates a list similar to `asciidoctor_benchmarks` to handle multiple benchmarks per file
        ruby_benchmarks = join(rubyDir, 'bench')
        benchmarks = []
        micro_dir = join(ruby_benchmarks, 'micro')
        for root, _, files in os.walk(micro_dir):
            for name in files:
                if name.endswith('.rb'):
                    benchmark_file = join(root, name)
                    out = mx.OutputCapture()
                    jt(['benchmark', 'list', benchmark_file], out=out)
                    benchmark_file_from_micro_dir = benchmark_file[len(micro_dir)+1:-3]
                    benchmarks.extend([benchmark_file_from_micro_dir + ':' + b.strip() for b in out.data.split('\n') if len(b.strip()) > 0])
        return benchmarks

    def time(self):
        return micro_benchmark_time

savina_benchmarks = [
    'savina-apsp',
    'savina-radix-sort',
    'savina-trapezoidal',
]

class SavinaBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'savina'

    def directory(self):
        return 'parallel/savina'

    def benchmarkList(self, bmSuiteArgs):
        return savina_benchmarks

    def time(self):
        return 120

server_benchmarks = [
    'tcp-server',
    'webrick'
]

server_benchmark_time = 60 * 4 # Seems unstable otherwise

class ServerBenchmarkSuite(RubyBenchmarkSuite):
    def benchmarkList(self, bmSuiteArgs):
        return server_benchmarks

    def name(self):
        return 'server'

    def runBenchmark(self, benchmark, bmSuiteArgs):
        arguments = ['ruby']
        if not bmSuiteArgs:
            arguments.extend(['--check-compilation'])
        arguments.extend(['bench/servers/' + benchmark + '.rb'])

        server = BackgroundJT(arguments + bmSuiteArgs)

        with server:
            time.sleep(10)
            out = mx.OutputCapture()
            if mx.run(
                    ['ruby', 'bench/servers/harness.rb', str(server_benchmark_time)],
                    out=out,
                    nonZeroIsFatal=False) == 0 and server.is_running():
                samples = [float(s) for s in out.data.split('\n')[0:-1]]
                mx.log(samples)
                half_samples = len(samples) // 2
                used_samples = samples[len(samples)-half_samples-1:]
                ips = sum(used_samples) / float(len(used_samples))

                return [{
                    'benchmark': benchmark,
                    'metric.name': 'throughput',
                    'metric.value': ips,
                    'metric.unit': 'op/s',
                    'metric.better': 'higher'
                }]
            else:
                sys.stderr.write(out.data)

                # TODO CS 24-Jun-16, how can we fail the wider suite?
                return [{
                    'benchmark': benchmark,
                    'metric.name': 'throughput',
                    'metric.value': 0,
                    'metric.unit': 'op/s',
                    'metric.better': 'higher',
                    'error': 'failed'
                }]

class RubykonBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'rubykon'

    def directory(self):
        return 'rubykon'

    def benchmarkList(self, bmSuiteArgs):
        return ['rubykon']

    def time(self):
        return 120

class LiquidBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def name(self):
        return 'liquid'

    def directory(self):
        return 'liquid'

    def benchmarkList(self, bmSuiteArgs):
        return ['liquid-cart-parse', 'liquid-cart-render', 'liquid-middleware']

    def time(self):
        return 60

warmup_benchmarks = [
    'asciidoctor/asciidoctor-convert',
    'asciidoctor/asciidoctor-load-file',
    'rubykon/rubykon',
]

blog6_benchmarks = [
    'rails/blog6-bundle-install',
    'rails/blog6-rails-routes',
]

class WarmupBenchmarkSuite(AllBenchmarksBenchmarkSuite):
    def config(self):
        # NOTE: also update mx.truffleruby/warmup-fork-counts.json when updating this list
        iterations = {
            'asciidoctor-convert':     {10:'startup', 100:'early-warmup', 500:'late-warmup'},
            'asciidoctor-load-file':   {10:'startup', 100:'early-warmup', 500:'late-warmup'},
            'rubykon':                 {1:'startup', 10:'early-warmup', 30:'late-warmup'},
            'blog6-bundle-install':    {1:'single-shot'},
            'blog6-rails-routes':      {1:'single-shot'},
        }
        return {'kind': 'fixed-iterations', 'iterations': iterations}

    def name(self):
        return 'ruby-warmup'

    def directory(self):
        return None

    def benchmarkList(self, bmSuiteArgs):
        benchmarks = warmup_benchmarks[:]
        if os.getenv('HOST_VM') != "jruby":
            benchmarks.extend(blog6_benchmarks)
        return benchmarks

mx_benchmark.add_bm_suite(BuildStatsBenchmarkSuite())
mx_benchmark.add_bm_suite(AllocationBenchmarkSuite())
mx_benchmark.add_bm_suite(InstructionsBenchmarkSuite())
mx_benchmark.add_bm_suite(MinHeapBenchmarkSuite())
mx_benchmark.add_bm_suite(MaxRssBenchmarkSuite())
mx_benchmark.add_bm_suite(TimeBenchmarkSuite())
mx_benchmark.add_bm_suite(ClassicBenchmarkSuite())
mx_benchmark.add_bm_suite(ChunkyBenchmarkSuite())
mx_benchmark.add_bm_suite(PSDBenchmarkSuite())
mx_benchmark.add_bm_suite(ImageDemoBenchmarkSuite())
mx_benchmark.add_bm_suite(AsciidoctorBenchmarkSuite())
mx_benchmark.add_bm_suite(OptcarrotBenchmarkSuite())
mx_benchmark.add_bm_suite(SyntheticBenchmarkSuite())
mx_benchmark.add_bm_suite(MicroBenchmarkSuite())
mx_benchmark.add_bm_suite(SavinaBenchmarkSuite())
mx_benchmark.add_bm_suite(ServerBenchmarkSuite())
mx_benchmark.add_bm_suite(RubykonBenchmarkSuite())
mx_benchmark.add_bm_suite(LiquidBenchmarkSuite())
mx_benchmark.add_bm_suite(WarmupBenchmarkSuite())
