Gem::Specification.new do |spec|
  spec.name    = 'hello-world'
  spec.version = '0.0.1'
  spec.author  = 'TruffleRuby'
  spec.homepage = 'https://github.com/truffleruby/truffleruby'
  spec.license = 'MIT'
  spec.summary = 'A gem which prints Hello World!'
  spec.files         = [__FILE__]
  spec.bindir        = 'bin'
  spec.executables   = 'hello-world.rb'
  spec.require_paths = ['lib']
  spec.required_ruby_version = ">= 3.0"
end
