require "bundler/gem_tasks"
require "rake/testtask"

Rake::TestTask.new(:test) do |t|
  t.libs << "test"
  t.libs << "lib"
  t.test_files = FileList["test/**/test_*.rb"]
end

require 'rake/extensiontask'
Rake::ExtensionTask.new("syslog_ext") do |ext|
  ext.ext_dir = 'ext/syslog'

  # In contrast to "gem install" a "rake compile" is expecting the C-ext file even on Windows.
  # Work around by creating a dummy so file.
  task "#{ext.tmp_dir}/#{ext.platform}/stage/lib" do |t|
    touch "#{ext.tmp_dir}/#{ext.platform}/#{ext.name}/#{RUBY_VERSION}/#{ext.name}.so"
  end
end
task :default => :test
