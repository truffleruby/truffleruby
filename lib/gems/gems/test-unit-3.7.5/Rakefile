# -*- ruby -*-
#
# Copyright (C) 2008-2024  Sutou Kouhei <kou@clear-code.com>

Encoding.default_internal = "UTF-8" if defined?(Encoding.default_internal)

# TODO: Remove me when we drop Ruby 1.9 support.
unless "".respond_to?(:b)
  class String
    def b
      dup.force_encoding("ASCII-8BIT")
    end
  end
end

require "erb"
require "yaml"
require "rubygems"
require "rake/clean"
require "yard"
require "bundler/gem_helper"
require "packnga"

task :default => :test

base_dir = File.dirname(__FILE__)

helper = Bundler::GemHelper.new(base_dir)
def helper.version_tag
  version
end

helper.install
spec = helper.gemspec

document_task = Packnga::DocumentTask.new(spec) do |task|
  task.original_language = "en"
  task.translate_languages = ["ja"]
end

Packnga::ReleaseTask.new(spec) do |task|
  test_unit_github_io_dir_candidates = [
    "../../www/test-unit.github.io",
  ]
  test_unit_github_io_dir = test_unit_github_io_dir_candidates.find do |dir|
    File.directory?(dir)
  end
  task.index_html_dir = test_unit_github_io_dir
end

task :test do
  ruby("test/run.rb")
end

namespace :doc do
  task :add_permalink do
    news_md = File.read("doc/text/news.md")
    applied_permalink = news_md.gsub(/(?<pre>\[GitHub#(?<ref>\d+)\])(?!\()/) do
      "#{Regexp.last_match[:pre]}(https://github.com/test-unit/test-unit/issues/#{Regexp.last_match[:ref]})"
    end

    File.write("doc/text/news.md", applied_permalink)
  end
end

release_task = Rake.application["release"]
# We use Trusted Publishing.
release_task.prerequisites.delete("build")
release_task.prerequisites.delete("release:rubygem_push")
release_task_comment = release_task.comment
if release_task_comment
  release_task.clear_comments
  release_task.comment = release_task_comment.gsub(/ and build.*$/, "")
end
