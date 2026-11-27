require_relative 'spec_helper'

ruby_version_is "4.0" do
  guard_not -> { ruby_box_enabled? } do
    describe "Ruby::Box specs" do
      it "pass when run in a subprocess with RUBY_BOX=1" do
        output = run_specs_in_subprocess(__dir__, options: "-W:no-experimental", env: { "RUBY_BOX" => "1" })
        output.should =~ /\b0 failures, 0 errors\b/
      end
    end
  end
end
