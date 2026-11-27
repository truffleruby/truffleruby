require 'spec_helper'
require 'mspec/guards'
require 'mspec/helpers'
require 'mspec/utils/script'

RSpec.describe Object, "#run_specs_in_subprocess" do
  before :each do
    @mspec_run = File.expand_path("../../bin/mspec-run", __dir__)
    allow(MSpecScript).to receive(:loaded_config_files).and_return([])
    allow(self).to receive(:tmp).with("specs_subprocess").and_return("/tmp/specs_subprocess")
    @env = { "MSPEC_MULTI" => nil, "SPEC_TEMP_DIR" => "/tmp/specs_subprocess" }
  end

  it "runs the given files with mspec-run using ruby_exe and returns its output" do
    expect(self).to receive(:ruby_exe).with(nil, options: @mspec_run, args: "a_spec.rb dir", env: @env).and_return("output")
    expect(run_specs_in_subprocess("a_spec.rb", "dir")).to eq("output")
  end

  it "passes the loaded config files to the subprocess" do
    allow(MSpecScript).to receive(:loaded_config_files).and_return(["/a/default.mspec", "/b/other.mspec"])
    options = "#{@mspec_run} -B /a/default.mspec -B /b/other.mspec"
    expect(self).to receive(:ruby_exe).with(nil, options: options, args: "a_spec.rb", env: @env)
    run_specs_in_subprocess("a_spec.rb")
  end

  it "passes the options before mspec-run and merges the env" do
    env = @env.merge("RUBY_BOX" => "1")
    expect(self).to receive(:ruby_exe).with(nil, options: "-W:no-experimental #{@mspec_run}", args: "a_spec.rb", env: env)
    run_specs_in_subprocess("a_spec.rb", options: "-W:no-experimental", env: { "RUBY_BOX" => "1" })
  end
end
