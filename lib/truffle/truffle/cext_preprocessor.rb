# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# rubocop:disable TruffleRuby/*

require_relative 'patches/json_patches'
require_relative 'patches/oci8_patches'
require_relative 'patches/pg_patches'
require_relative 'patches/tk_patches'

module Truffle
  module CExt
    class Preprocessor

      PATCHED_FILES = {}

      def self.add_gem_patches(patch_hash, gem_patches)
        gem = gem_patches[:gem]
        patch_list = gem_patches[:patches]
        patch_list.each do |path_parts, patch|
          processed_patch = {}
          if path_parts.kind_of?(String)
            key = path_parts
          else
            key = path_parts.last
            processed_patch[:ext_dir] = path_parts.first if path_parts.size > 1
          end
          processed_patch[:patches] = patch
          processed_patch[:gem] = gem
          processed_patch[:non_standard_dir_structure] = gem_patches[:non_standard_dir_structure]
          raise "Duplicate patch file #{key}." if patch_hash.include?(key)
          patch_hash[key] = processed_patch
        end
      end

      add_gem_patches(PATCHED_FILES, ::JsonPatches::PATCHES)
      add_gem_patches(PATCHED_FILES, ::OCI8Patches::PATCHES)
      add_gem_patches(PATCHED_FILES, ::PgPatches::PATCHES)
      add_gem_patches(PATCHED_FILES, ::TkPatches::PATCHES)

      def self.makefile_matcher(command1, command2)
        file_list = Hash.new { |h,k| h[k] = [] }
        PATCHED_FILES.each_pair do |file, patch|
          dir = if patch[:ext_dir]
                  if patch[:non_standard_dir_structure]
                    File.join('ext', patch[:ext_dir])
                  else
                    File.join(patch[:gem], 'ext', patch[:ext_dir])
                  end
                else
                  "/#{patch[:gem]}"
                end
          file_list[dir] << file
        end

        make_function = <<-EOF
$(if\\
  $(or\\
EOF
        file_list.each_pair do |dir, files|
          if !files.empty?
            make_function += <<-EOF
    $(and\\
      $(findstring #{dir}, $(realpath $(<))),\\
      $(or\\
EOF
            files.each do |file|
              make_function += <<-EOF
        $(findstring #{file}, $(<)),\\
EOF
            end
            make_function += <<-EOF
      )\\
    ),\\
EOF
          end
        end
        make_function += <<-EOF
  ),\\
  #{command1},\\
  #{command2}\\
)
EOF

      end

      def self.patch(file, contents, directory)
        if patched_file = PATCHED_FILES[File.basename(file)]
          matched = if patched_file[:ext_dir]
                      if patched_file[:non_standard_dir_structure]
                        directory.end_with?('ext', patched_file[:ext_dir])
                      else
                        directory.end_with?(File.join(patched_file[:gem], 'ext', patched_file[:ext_dir]))
                      end
                    else
                      regexp = /^#{Regexp.escape(patched_file[:gem])}\b/
                      directory.split('/').last(3).any? { |part| part =~ regexp } || file.split('/').last(2).any? { |part| part =~ regexp }
                    end
          if matched
            # Generally we strip any trailing newlines and whitespce
            # from a patch, but on occasions we need to patch with a
            # preprocessor macro which _must_ end with a newline and
            # so requires that we preserve the trailing whitespace.
            patched_file[:patches].each do |patch|
              predicate = patch[:predicate] || -> { true }
              if predicate.call
                replacement = patch[:replacement].rstrip
                last_line = replacement.lines.last || replacement # .lines returns an empty Array if String#empty?
                last_line = last_line.lstrip
                contents = contents.gsub(patch[:match],
                                         if last_line && last_line.start_with?('#')
                                           patch[:replacement]
                                         else
                                           patch[:replacement].rstrip
                                         end)
              end
            end
          end
        end
        contents
      end
    end
  end
end
