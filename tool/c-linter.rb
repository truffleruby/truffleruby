#!/usr/bin/env ruby

files = Dir['src/main/c/cext/*.c'] + Dir['spec/ruby/optional/capi/ext/*.c']
files -= %w[src/main/c/cext/st.c]

files.each do |file|
  contents = File.read(file)
  if contents =~ /^.+\)\s*\n\s*\{/
    raise "#{file}: The function definition opening brace should be on the same line: ...args) {\n#{$&}"
  end

  if contents.include? '){'
    raise "#{file}: There should be a space between ) and {\n){"
  end

  if contents.include? '() {'
    raise "#{file}: The function declaration should use function(void) {\n() {"
  end

  if contents =~ /\bif\(/
    raise "#{file}: There should be a space between if and (\n#{$&}"
  end

  if contents.include? "\t"
    raise "#{file}: There should be no tabs"
  end
end
