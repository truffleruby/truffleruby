# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Gem
  module GemDirsVerification
    extend self

    MARKER_NAME = 'truffleruby_gem_dir_marker.txt'

    def install_hook
      install_hook = -> tool do
        install_dir = tool.gem_home
        return unless install_dir # gem is not being installed, just e.g. registered by bundler from a path

        bad_dirs = Truffle::GemUtil.bad_gem_dirs Gem.path
        bad_install_dir = bad_dirs.include? install_dir
        unless bad_dirs.empty?
          unless tool.ask_yes_no <<-TXT.gsub(/^ +/, '').chomp, true

            The gem directories are not configured properly.
            The gem install directory #{install_dir} #{bad_install_dir ? 'is not' : 'might not be'} correct.
            It has to be marked with the #{MARKER_NAME} file as belonging to TruffleRuby.
            The gem might be installed into a gem directory belonging to another Ruby implementation.
            Continue installing?
          TXT
            raise Gem::InstallError,
                  "The gem directory #{install_dir} is not correct. Gem installation aborted."
          end
        end

        # install_dir and its sub directories like specifications are already created
        # by gem installer at this point if they did not exist
        if Dir.empty?(File.join(install_dir, 'specifications'))
          # If the directory is empty then the very first gem is being installed in it.
          # Therefore mark (claim) it as TruffleRuby's gem directory.
          marker_path = "#{install_dir}/#{Truffle::GemUtil::MARKER_NAME}"
          File.write marker_path, <<-TXT.gsub(/^ */, '')
            DO NOT DELETE: This file is used by TruffleRuby to distinguish its
            gem installation directory from that of other Ruby installations.
          TXT
        end

        true
      end

      Gem.pre_install(&install_hook)
      install_hook
    end

    # Currently unused, documents how to remove the hooks if necessary for testing or other cases.
    def remove_hook(install_hook)
      Gem.pre_install_hooks.delete(install_hook)
    end
  end
end
