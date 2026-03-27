# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Thread#status" do

  it "is not changed by regexp matching" do
    # A regexp taking a long time,
    # from https://swtch.com/~rsc/regexp/regexp1.html
    # Takes around 100 ms so the main thread can observe the status
    # while this thread is matching.
    # NB: We include backreferences, so that TRegex can't "cheat" by
    # using a DFA and executing this in linear time. We also replace
    # 'a?' * 17 with '(a?)' * 17 so that TRegex can't replace 'a?' * 17
    # with 'a{0,17}' and execute in quadratic time.
    n = Truffle::Boot.get_option('use-truffle-regex') ? 17 : 23
    regexp = /(f)\1#{'(a?)' * n}#{'a' * n}\1/
    string = 'ff' + 'a' * n
    # Force compilation of the regex, as TRegex compiles regexes lazily.
    regexp =~ ''

    t = Thread.new do
      regexp =~ string
    end

    saw_status = false
    while status = t.status
      saw_status = true
      status.should == "run"
      Thread.pass
    end
    t.join

    skip 'unfair scheduling' unless saw_status
  end

end
