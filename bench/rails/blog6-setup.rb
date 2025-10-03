require_relative '../../tool/jt.rb'
require 'fileutils'

module Blog6Setup
  BLOG6_DIR = File.expand_path('../../../test/truffle/ecosystem/blog6', __FILE__)

  def self.setup_bundler
    Dir.chdir(BLOG6_DIR) do
      FileUtils.cp 'Gemfile.renamed', 'Gemfile'
      FileUtils.cp 'Gemfile.lock.renamed', 'Gemfile.lock'

      JT.ruby(*%w[-S bundle config --local without postgresql mysql])
      JT.ruby(*%w[-S bundle config --local path vendor/bundle])
    end
  end

  def self.bundle_install
    Dir.chdir(BLOG6_DIR) do
      JT.ruby(*%w[-S bundle install])
    end
  end
end
