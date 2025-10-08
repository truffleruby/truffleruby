require 'json'

json = `sjsonnet ci.jsonnet`

builds = JSON.load(json).fetch('builds')
builds = builds.select { _1.fetch('targets').join.include?('tier') }

builds.each do |build|
  puts nil, build.fetch('name')
  commands = build.fetch('setup') + build.fetch('run')
  commands.each do |command|
    command = command.join(' ')
    command = command.sub('bin/jt', 'jt')
    unless ['set -o pipefail', 'ruby --version', 'openssl version', 'locale', 'mx sversions'].include?(command) or command.start_with?('set-export RUBY_BIN')
      puts "$ #{command}"
    end
  end
end
