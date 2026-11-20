require_relative '../spec_helper'

describe "The interpreter when no script is given" do
  it "reads the script from stdin" do
    script = tmp("stdin_script.rb")
    touch(script) { |f| f.puts "puts 1 + 2" }
    begin
      ruby_exe(nil, args: "< #{script}").should == "3\n"
    ensure
      rm_r script
    end
  end

  platform_is_not :windows do
    it "reads the script from a terminal until the first end-of-file and leaves the rest as input for the script" do
      require 'pty'

      output = IO.pipe do |r, w|
        PTY.open do |master, slave|
          pid = Process.spawn(ruby_cmd(nil), in: slave, out: w)
          w.close
          # Everything is written before the interpreter starts reading,
          # so the input for the script is already available when the script is read.
          # \C-d at the start of a line is an end-of-file for the reader of the terminal.
          master.print "print $stdin.read.upcase\n\C-d"
          master.print "input\n\C-d"
          # An extra end-of-file, so that if the script is read past the first one
          # $stdin.read returns instead of waiting forever.
          master.print "\C-d"
          begin
            r.read
          ensure
            Process.wait(pid)
          end
        end
      end

      output.should == "INPUT\n"
    end
  end
end
