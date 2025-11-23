#!/usr/bin/env ruby

if ARGV.size > 0
  file = ARGV.fetch(0)
  lines = File.readlines(file)
else
  lines = ARGF.readlines
end

# Strip datetime of GitHub Actions logs
lines = lines.map { |line|
  if line =~ /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d+Z /
    $'
  else
    line
  end
}

if file
  File.write file, lines.join
else
  puts lines
end
