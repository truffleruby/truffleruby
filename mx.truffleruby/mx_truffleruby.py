# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

from __future__ import print_function

import os
import shlex
from os.path import join, exists, basename, dirname
import re
import shutil
import sys

import mx
import mx_util
import mx_gate
import mx_sdk
import mx_sdk_vm_ng
import mx_subst
import mx_spotbugs
import mx_truffle
import mx_unittest

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_shaded import ShadedLibraryProject # pylint: disable=unused-import
from mx_sdk_vm_ng import StandaloneLicenses, ThinLauncherProject, LanguageLibraryProject, DynamicPOMDistribution  # pylint: disable=unused-import

jdk = mx.get_jdk(mx.JavaCompliance('17+'), 'building TruffleRuby which requires JDK 17 or newer')

if 'RUBY_BENCHMARKS' in os.environ:
    import mx_truffleruby_benchmark  # pylint: disable=unused-import

_suite = mx.suite('truffleruby')
root = _suite.dir

def add_ext_suffix(name):
    """
    Adds the platform specific C extension suffix to a name
    """
    if mx.is_darwin():
        return name + '.bundle'
    else:
        return name + '.so'

mx_subst.results_substitutions.register_with_arg('extsuffix', add_ext_suffix)

# From org.truffleruby.shared.Platform
def get_cruby_arch():
    arch = mx.get_arch()
    if arch == 'amd64':
        return 'x86_64'
    elif arch == 'aarch64':
        if mx.is_darwin():
            # CRuby makes exception for macOS and uses `arm64` instead of `aarch64`
            return 'arm64'
        else:
            return 'aarch64'
    else:
        raise Exception("Unknown platform " + arch)

mx_subst.results_substitutions.register_no_arg('cruby_arch', get_cruby_arch)

def get_truffleruby_abi_version():
    path = join(root, 'lib/cext/include/truffleruby/truffleruby-abi-version.h')
    with open(path, "r") as f:
        contents = f.read()
    m = re.search('"(.+)"', contents)
    return m.group(1)

mx_subst.results_substitutions.register_no_arg('truffleruby_abi_version', get_truffleruby_abi_version)

class TruffleRubyUnittestConfig(mx_unittest.MxUnittestConfig):
    def __init__(self):
        super(TruffleRubyUnittestConfig, self).__init__('truffleruby')

    def apply(self, config):
        vmArgs, mainClass, mainClassArgs = config
        mx_truffle.enable_truffle_native_access(vmArgs)
        mx_truffle.enable_sun_misc_unsafe(vmArgs)
        return (vmArgs, mainClass, mainClassArgs)

mx_unittest.register_unittest_config(TruffleRubyUnittestConfig())

# Utilities

class VerboseMx:
    def __enter__(self):
        self.verbose = mx.get_opts().verbose
        mx.get_opts().verbose = True

    def __exit__(self, exc_type, exc_value, traceback):
        mx.get_opts().verbose = self.verbose

# Project classes

class TruffleRubyBootstrapLauncherProject(mx.Project):
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        super(TruffleRubyBootstrapLauncherProject, self).__init__(suite, name, subDir=None, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, theLicense=theLicense, **kwArgs)

    def launchers(self):
        result = join(self.get_output_root(), 'miniruby')
        yield result, 'TOOL', 'miniruby'

    def getArchivableResults(self, use_relpath=True, single=False):
        for result, _, prefixed in self.launchers():
            yield result, prefixed

    def getBuildTask(self, args):
        return TruffleRubyBootstrapLauncherBuildTask(self, args, 1)


class TruffleRubyBootstrapLauncherBuildTask(mx.BuildTask):
    def __str__(self):
        return "Generating " + self.subject.name

    def newestOutput(self):
        return mx.TimeStampFile.newest([result for result, _, _ in self.subject.launchers()])

    def needsBuild(self, newestInput):
        sup = super(TruffleRubyBootstrapLauncherBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup

        for result, _, _ in self.subject.launchers():
            if not exists(result):
                return True, result + ' does not exist'
            with open(result, "r") as f:
                on_disk = f.read()
            if on_disk != self.contents(result):
                return True, 'command line changed for ' + basename(result)

        return False, 'up to date'

    def build(self):
        mx_util.ensure_dir_exists(self.subject.get_output_root())
        for result, _, _ in self.subject.launchers():
            with open(result, "w") as f:
                f.write(self.contents(result))
            os.chmod(result, 0o755)

    def clean(self, forBuild=False):
        if exists(self.subject.get_output_root()):
            mx.rmtree(self.subject.get_output_root())

    def contents(self, result):
        classpath_deps = [dep for dep in self.subject.buildDependencies if isinstance(dep, mx.ClasspathDependency)]
        jvm_args = [shlex.quote(arg) for arg in mx.get_runtime_jvm_args(classpath_deps)]
        mx_truffle.enable_truffle_native_access(jvm_args)
        mx_truffle.enable_sun_misc_unsafe(jvm_args)

        debug_args = mx.java_debug_args()
        jvm_args.extend(debug_args)
        if debug_args:
            jvm_args.extend(['-ea', '-esa'])

        bootstrap_home = mx.distribution('TRUFFLERUBY_BOOTSTRAP_HOME').get_output()
        jvm_args.append('-Dorg.graalvm.language.ruby.home=' + bootstrap_home)

        jvm_args.append('-Dtruffleruby.repository=' + root)

        main_class = 'org.truffleruby.launcher.RubyLauncher'
        ruby_options = [
            '--experimental-options',
            '--building-core-cexts', # This lets the process know it's miniruby
            '--launcher=' + result,
            '--disable-gems',
            '--disable-rubyopt',
        ]
        trufflerubyopt = os.environ.get("TRUFFLERUBYOPT")
        if trufflerubyopt and '--cexts-sulong' in trufflerubyopt:
            ruby_options.append('--cexts-sulong')

        command = [jdk.java] + jvm_args + ['-m', 'org.graalvm.ruby.launcher/' + main_class] + ruby_options + ['"$@"']
        return "#!/usr/bin/env bash\n" + "exec " + " ".join(command) + "\n"


class YARPNativeProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, output=None, **kwArgs):
        path = join(root, kwArgs.pop('dir'))
        super(YARPNativeProject, self).__init__(
            suite, name, subDir=None, srcDirs=[path], deps=deps, workingSets=workingSets,
            results=kwArgs.pop('results'),
            output=path, d=path, vpath=False, **kwArgs)

class LibSSLProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, output=None, **kwArgs):
        base = join(root, 'src/main/c')
        self.build_dir = join(base, 'libssl_build')
        self.install_dir = join(base, 'libssl')
        # d is the directory in which `make` will be called.
        # output is used as a prefix of results.
        super(LibSSLProject, self).__init__(
            suite, name, subDir=None, srcDirs=[], deps=deps, workingSets=workingSets,
            results=kwArgs.pop('results'),
            output=dirname(self.install_dir), d=self.build_dir, vpath=False, **kwArgs)

    def getBuildTask(self, args):
        return LibSSLBuildTask(args, self)

class LibSSLBuildTask(mx.NativeBuildTask):
    def needsBuild(self, newestInput):
        libssl = join(self.subject.install_dir, mx.add_lib_suffix('lib/libssl'))
        if exists(libssl):
            return False, f"{libssl} is built"
        else:
            return (True, f"{libssl} does not exist")

    def build(self):
        libssl_sources = mx.distribution('LIBSSL_LAYOUT_DIST').get_output()
        build_dir = self.subject.build_dir
        install_dir = self.subject.install_dir

        self.clean()
        mx.copytree(libssl_sources, build_dir)

        # logic based on https://github.com/rbenv/ruby-build/blob/master/bin/ruby-build build_package_openssl()

        # This quoting is a bit complicated, we need to escape $ in the Makefile with $$, and avoid expansion from the shell
        rpath = ["-Wl,-rpath,'$$ORIGIN/../lib'"] if mx.is_linux() else []

        mx.run([
            './Configure',
            f'--prefix={install_dir}',
            f'--openssldir={install_dir}/ssl',
            '--libdir=lib',
            'zlib-dynamic',
            'no-ssl3',
            'shared',
            *rpath,
        ], cwd=build_dir)

        super(LibSSLBuildTask, self).build() # make

        mx.run(['make', 'install_sw', 'install_ssldirs'], cwd=build_dir)

    def clean(self, forBuild=False):
        build_dir = self.subject.build_dir
        install_dir = self.subject.install_dir
        if exists(build_dir):
            shutil.rmtree(build_dir)
        if exists(install_dir):
            shutil.rmtree(install_dir)

# Functions called from suite.py

def truffleruby_standalone_deps():
    include_truffle_runtime = not mx.env_var_to_bool("EXCLUDE_TRUFFLE_RUNTIME")
    return mx_truffle.resolve_truffle_dist_names(use_optimized_runtime=include_truffle_runtime)

def librubyvm_build_args():
    if mx_sdk_vm_ng.is_nativeimage_ee() and mx.get_os() == 'linux' and 'NATIVE_IMAGE_AUXILIARY_ENGINE_CACHE' not in os.environ:
        return ['--gc=G1', '-H:-ProtectionKeys']
    else:
        return []

# Commands

def jt(*args):
    mx.log("\n$ " + ' '.join(['jt'] + list(args)) + "\n")
    mx.run([join(root, 'bin/jt')] + list(args))

def build_truffleruby(args):
    mx.command_function('sversions')([])
    jt('build')

def ruby_check_heap_dump(input_args, out=None):
    print("mx ruby_check_heap_dump " + " ".join(input_args))
    dists = ['TRUFFLERUBY', 'TRUFFLE_NFI', 'SULONG_NATIVE', 'TRUFFLERUBY-TEST-INTERNAL']
    mx.command_function('build')(['--dependencies', ','.join(dists)])
    args = input_args
    args.insert(0, "--experimental-options")
    vm_args, truffleruby_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    vm_args += mx.get_runtime_jvm_args(dists)
    mx_truffle.enable_truffle_native_access(vm_args)
    mx_truffle.enable_sun_misc_unsafe(vm_args)
    # vm_args.append("-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y")
    vm_args.append("org.truffleruby.test.internal.LeakTest")
    out = mx.OutputCapture() if out is None else out
    retval = mx.run_java(vm_args + truffleruby_args, jdk=jdk, nonZeroIsFatal=False, out=out)
    if retval == 0:
        print("PASSED")
        print(out.data)
    elif os.environ.get("CI") and "--keep-dump" not in input_args:
        # rerun once with heap dumping enabled
        out = mx.OutputCapture()
        ruby_check_heap_dump(["--keep-dump"] + input_args, out=out)
        path = out.data.strip().partition("Dump file:")[2].strip()
        if path:
            save_path = join(root, "dumps", "leak_test")
            try:
                os.makedirs(save_path)
            except OSError:
                pass
            dest = shutil.copy(path, save_path) # pylint: disable=assignment-from-no-return
            print("Heapdump file kept in " + dest)
            raise Exception("heap dump check failed")
    else:
        print("FAILED")
        print(out.data)
        raise Exception("heap dump check failed")

def ruby_run_ruby(args):
    """run TruffleRuby in mxbuild/$OS-$ARCH/TRUFFLERUBY_JVM_STANDALONE, needs an env including a ruby standalone. Use bin/jt for more control and shortcuts"""
    standalone_home = mx.distribution('TRUFFLERUBY_JVM_STANDALONE').output
    ruby = join(standalone_home, 'bin/ruby')
    os.execlp(ruby, ruby, *args)

def ruby_run_ruby_from_classpath(args):
    """run TruffleRuby from the classpath"""
    dists = ['RUBY_COMMUNITY', 'TRUFFLERUBY-LAUNCHER']
    if '--skip-build' in args:
        args.remove('--skip-build')
    else:
        mx.command_function('build')(['--dependencies', ','.join(dists)])
    classpath = mx.classpath(dists)
    jvm_args = ['-cp', classpath]
    mx_truffle.enable_truffle_native_access(jvm_args)
    mx_truffle.enable_sun_misc_unsafe(jvm_args)
    mx.run_java([*jvm_args, 'org.truffleruby.launcher.RubyLauncher', *args], jdk=jdk, nonZeroIsFatal=True)

def ruby_run_specs(ruby, args):
    with VerboseMx():
        jt('--use', ruby, 'test', 'specs', *args)

def ruby_jacoco_args(args):
    mx_gate.add_jacoco_whitelisted_packages(['org.truffleruby'])
    print(' '.join(mx_gate.get_jacoco_agent_args('append')))

def ruby_testdownstream_hello(args):
    """Run a minimal Hello World test"""
    build_truffleruby([])
    jt('ruby', '-e', 'puts "Hello Ruby!"')

def ruby_testdownstream_aot(args):
    """Run tests for the native image"""
    if len(args) > 3:
        mx.abort("Incorrect argument count: mx ruby_testdownstream_aot <aot_bin> [<format>] [<build_type>]")

    aot_bin = args[0]
    fast = ['--excl-tag', 'slow']

    ruby_run_specs(aot_bin, [])

    # Run "jt test fast --native :truffle" to catch slow specs in Truffle which only apply to native
    ruby_run_specs(aot_bin, fast + [':truffle'])

def ruby_spotbugs(args):
    """Run SpotBugs with custom options to detect more issues"""
    # SpotBugs needs all Java projects to be built
    # GR-52408: mx.command_function('build')(['--no-native']) should be enough but it fails
    mx.command_function('build')([])

    filters = join(root, 'mx.truffleruby', 'spotbugs-filters.xml')
    spotbugsArgs = ['-textui', '-low', '-longBugCodes', '-include', filters]
    if mx.is_interactive():
        spotbugsArgs.append('-progress')
    sys.exit(mx_spotbugs.spotbugs(['--primary', *args], spotbugsArgs))

def verify_ci(args):
    """Verify CI configuration"""
    mx.verify_ci(args, mx.suite('truffle'), _suite, ['common.json', 'ci/common.jsonnet'])

def ruby_maven_deploy_public(args):
    mx.command_function('build')([])
    licenses = ['EPL-2.0', 'PSF-License', 'GPLv2-CPE', 'ICU,GPLv2', 'BSD-simplified', 'BSD-new', 'UPL', 'MIT']
    mx_sdk.maven_deploy_public(args, licenses=licenses, deploy_snapshots=False)

def ruby_maven_deploy_public_repo_dir(args):
    print(mx_sdk.maven_deploy_public_repo_dir())

mx.update_commands(_suite, {
    'ruby': [ruby_run_ruby, ''],
    'ruby_from_classpath': [ruby_run_ruby_from_classpath, ''],
    'build_truffleruby': [build_truffleruby, ''],
    'ruby_check_heap_dump': [ruby_check_heap_dump, ''],
    'ruby_testdownstream_aot': [ruby_testdownstream_aot, 'aot_bin'],
    'ruby_testdownstream_hello': [ruby_testdownstream_hello, ''],
    'ruby_spotbugs': [ruby_spotbugs, ''],
    'verify-ci': [verify_ci, '[options]'],
    'ruby_jacoco_args': [ruby_jacoco_args, ''],
    'ruby_maven_deploy_public': [ruby_maven_deploy_public, ''],
    'ruby_maven_deploy_public_repo_dir': [ruby_maven_deploy_public_repo_dir, ''],
})
