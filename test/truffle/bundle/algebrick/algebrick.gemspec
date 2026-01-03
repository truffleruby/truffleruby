Gem::Specification.new do |s|

  git_files = `git ls-files`.split("\n")

  s.name             = 'algebrick'
  s.version          = File.read(File.join(File.dirname(__FILE__), 'VERSION'))
  s.date             = Time.now.strftime('%Y-%m-%d')
  s.summary          = 'Algebraic types and pattern matching for Ruby'
  s.description      = 'Provides algebraic type definitions and pattern matching'
  s.authors          = ['Petr Chalupa']
  s.email            = 'git+algebrick@pitr.ch'
  s.homepage         = 'https://github.com/pitr-ch/algebrick'
  s.extra_rdoc_files = %w(LICENSE.txt README.md README_FULL.md VERSION) + Dir['doc/*out.rb'] & git_files
  s.files            = Dir['lib/**/*.rb'] & git_files
  s.require_paths    = %w(lib)
  s.license          = 'Apache-2.0'
  s.test_files       = Dir['spec/algebrick_test.rb']

  s.add_development_dependency 'minitest', '~> 5.11.3'
  s.add_development_dependency 'minitest-reporters', '1.3.0'
  s.add_development_dependency 'yard', '~> 0.9'
  s.add_development_dependency 'kramdown', '~> 1.13'
  s.add_development_dependency 'rake', '~> 13.0'
  s.add_development_dependency 'mutex_m', '~> 0.3.0'

  # lock under 1.9 which has refinements
  s.add_development_dependency 'ruby-progressbar', '~> 1.8.3'
end

