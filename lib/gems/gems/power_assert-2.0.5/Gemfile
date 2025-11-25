source "http://rubygems.org"

gemspec

# https://github.com/redmine/redmine/blob/3.0.4/Gemfile#L101
local_gemfile = File.join(File.dirname(__FILE__), "Gemfile.local")
if File.exist?(local_gemfile)
  eval_gemfile local_gemfile
end

group :development do
  gem 'test-unit'
  gem 'rake'
  gem 'simplecov'
  gem 'bundler'
  gem 'irb', '>= 1.3.1'
  gem 'byebug'
  gem 'benchmark-ips'
end
