module Test
  module Unit
    AutoRunner.register_runner(:emacs) do |auto_runner|
      require_relative '../ui/emacs/testrunner'
      Test::Unit::UI::Emacs::TestRunner
    end
  end
end
