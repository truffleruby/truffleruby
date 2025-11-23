lines = File.readlines(ARGV[0])

keep_for_main = 40
keep_thread = 25
keep_fiber = 15
keep_other = 15

threads = lines.slice_before(/^"/).select { |thread_stacktrace|
  thread_stacktrace[0].start_with?('"') and
  thread_stacktrace[1] =~ /^\s*java\.lang\.Thread\.State:/
}.map { |thread_stacktrace|
  name = thread_stacktrace[0]
  keep = case name
  when /^"main"/ then keep_for_main
  when /^"Ruby Thread/ then keep_thread
  when /^"Ruby Fiber/ then keep_fiber
  else keep_other
  end
  stacktrace = thread_stacktrace[2, (name.start_with?('"main"') ? keep_for_main : keep)]
  stacktrace = stacktrace.map { |line| line.sub(/SP 0x\h+ IP 0x\h+\s+/, '') }
  stacktrace = stacktrace.map { |line| line.sub(/(parking to wait for\s*)<0x\h+>/, '\1<ADDRESS>') }
  [name, stacktrace]
}

# threads.each { |thread, stacktrace|
#   puts thread
#   puts stacktrace
#   puts
# }

threads.group_by { |thread, stacktrace| stacktrace }.each { |stacktrace, threads|
  puts threads.map(&:first)
  puts stacktrace
  puts
}
