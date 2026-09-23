require 'mspec/helpers/ruby_exe'
require 'mspec/helpers/tmp'

# Runs the given spec files or directories in a single mspec-run subprocess
# and returns its output. Raises if the subprocess does not exit successfully,
# which includes when any of the specs fail.
#
# This is useful for specs which need an environment variable (+env+) or
# a command-line option (+options+) to be set when the process starts.
# The subprocess uses the same config files as the current process.
def run_specs_in_subprocess(*files, options: nil, env: {})
  mspec_run = File.expand_path("../../../bin/mspec-run", __dir__)
  config = MSpecScript.loaded_config_files.flat_map { |file| ["-B", file] }

  env = {
    # Otherwise under `mspec -j` the subprocess would wait for files on STDIN
    "MSPEC_MULTI" => nil,
    # Otherwise the subprocess would delete the SPEC_TEMP_DIR of this process when it exits
    "SPEC_TEMP_DIR" => tmp("specs_subprocess"),
  }.merge(env)

  ruby_exe(nil, options: [*options, mspec_run, *config].join(" "), args: files.join(" "), env: env)
end
