# frozen_string_literal: true

# Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module FeatureLoader

    DOT_DLEXT = ".#{Truffle::Platform::DLEXT}"

    #
    # The following $LOADED_FEATURES:
    # ["/one/path.rb", "/two/path.so", "three/path" ]
    #
    # would create the @loaded_features_index:
    #
    # {
    #     "path" => [
    #             #<FeatureEntry @feature="/one/path.rb" @index=0>,
    #             #<FeatureEntry @feature="/two/path.so" @index=1>,
    #             #<FeatureEntry @feature="/three/path" @index=2>,
    #         ]
    # }
    #
    # For the feature "path" or "nested/path", all three feature entries are in the same hash key and `include?`
    # the feature. However, for feature "path.rb", only the first entry with the same extension would be at the same
    # hash key and not `include?` the feature.
    #
    @loaded_features_index = {}
    # A snapshot of $LOADED_FEATURES, to check if the @loaded_features_index cache is up to date.
    @loaded_features_version = -1

    @expanded_load_path = []
    # A snapshot of $LOAD_PATH, to check if the @expanded_load_path cache is up to date.
    @load_path_version = -1
    # nil if there is no relative path in $LOAD_PATH, a copy of the cwd to check if the cwd changed otherwise.
    @working_directory_copy = nil

    def self.clear_cache
      @loaded_features_index.clear
      @loaded_features_version = -1
      @expanded_load_path.clear
      @load_path_version = -1
      @working_directory_copy = nil
    end

    class FeatureEntry
      attr_accessor :index
      attr_reader :feature, :ext, :feature_no_ext, :base

      def initialize(feature)
        @ext = Truffle::FeatureLoader.extension(feature)
        @feature = feature
        @feature_no_ext = @ext ? feature[0...(-@ext.size)] : feature
        @base = File.basename(@feature_no_ext)
        @index = nil
      end

      def include?(lookup)
        if lookup.ext
          feature.end_with?(lookup.feature)
        else
          feature_no_ext.end_with?(lookup.feature_no_ext)
        end
      end
    end

    def self.find_file(feature)
      feature = File.expand_path(feature) if feature.start_with?('~')
      Primitive.find_file(feature)
    end

    # MRI: search_required
    def self.find_feature_or_file(feature, use_feature_provided = true)
      feature_ext = extension_symbol(feature)
      if feature_ext
        case feature_ext
        when :rb
          if use_feature_provided && feature_provided?(feature, false)
            return [:feature_loaded, nil, :rb]
          end
          path = find_file(feature)
          return expanded_path_provided(path, :rb, use_feature_provided) if path
          return [:not_found, nil, nil]
        when :so
          if use_feature_provided && feature_provided?(feature, false)
            return [:feature_loaded, nil, :so]
          else
            feature_no_ext = feature[0...-3] # remove ".so"
            path = find_file("#{feature_no_ext}.#{Truffle::Platform::DLEXT}")
            return expanded_path_provided(path, :so, use_feature_provided) if path
          end
        when :dlext
          if use_feature_provided && feature_provided?(feature, false)
            return [:feature_loaded, nil, :so]
          else
            path = find_file(feature)
            return expanded_path_provided(path, :so, use_feature_provided) if path
          end
        end
      else
        found = use_feature_provided && feature_provided?(feature, false)
        if found == :rb
          return [:feature_loaded, nil, :rb]
        else
          found = :so if found == :unknown
        end
      end

      path = find_file(feature)
      if path
        ext_normalized = extension_symbol(path) == :rb ? :rb : :so
        if found && ext_normalized != :rb
          [:feature_loaded, nil, found]
        else
          found_expanded = use_feature_provided && feature_provided?(path, true)
          if found_expanded
            [:feature_loaded, nil, ext_normalized]
          else
            [:feature_found, path, ext_normalized]
          end
        end
      else
        if found
          [:feature_loaded, nil, found]
        else
          found = use_feature_provided && feature_provided?(feature, true)
          if found
            found = :so if found == :unknown
            [:feature_loaded, nil, found]
          else
            [:not_found, nil, nil]
          end
        end
      end
    end

    def self.expanded_path_provided(path, ext, use_feature_provided)
      if use_feature_provided && feature_provided?(path, true)
        [:feature_loaded, path, ext]
      else
        [:feature_found, path, ext]
      end
    end

    # MRI: rb_feature_p
    # Whether feature is already loaded, i.e., part of $LOADED_FEATURES,
    # using the @loaded_features_index to lookup faster.
    # expanded is true if feature is an expanded path (and exists).
    def self.feature_provided?(feature, expanded)
      feature_ext = extension_symbol(feature)
      feature_has_rb_ext = feature_ext == :rb

      with_synchronized_features do
        get_loaded_features_index
        feature_entry = FeatureEntry.new(feature)
        if @loaded_features_index.key?(feature_entry.base)
          @loaded_features_index[feature_entry.base].each do |fe|
            if fe.include?(feature_entry)
              loaded_feature = $LOADED_FEATURES[fe.index]

              next if loaded_feature.size < feature.size
              found_feature_path = if loaded_feature.start_with?(feature)
                                     true
                                   else
                                     if expanded
                                       false
                                     else
                                       feature_path_loaded?(loaded_feature, feature, get_expanded_load_path)
                                     end
                                   end
              if found_feature_path
                loaded_feature_ext = extension_symbol(loaded_feature)
                if !loaded_feature_ext
                  return :unknown unless feature_ext
                else
                  if (!feature_has_rb_ext || !feature_ext) && binary_ext?(loaded_feature_ext)
                    return :so
                  end
                  if (feature_has_rb_ext || !feature_ext) && loaded_feature_ext == :rb
                    return :rb
                  end
                end
              end
            end
          end
        end

        false
      end
    end

    # MRI: loaded_feature_path
    # Search if $LOAD_PATH[i]/feature corresponds to loaded_feature.
    # Returns the $LOAD_PATH entry containing feature.
    # This method is optimized to avoid unnecessary allocations, e.g., created by string interpolation.
    # See https://github.com/truffleruby/truffleruby/pull/3010#discussion_r1172486950
    def self.feature_path_loaded?(loaded_feature, feature, load_path)
      name_ext = extension(loaded_feature)

      if name_ext && (suffix_with_ext = "/#{feature}#{name_ext}") && loaded_feature.end_with?(suffix_with_ext)
        path = loaded_feature[0...-suffix_with_ext.size]
      elsif loaded_feature.end_with?(feature) && loaded_feature.getbyte(-(feature.bytesize + 1)) == 47 # '/'.ord = 47
        path = loaded_feature[0...-(feature.size + 1)]
      else
        return false
      end

      load_path.include?(path)
    end

    # MRI: rb_provide_feature
    # Add feature to $LOADED_FEATURES and the index, called from RequireNode
    def self.provide_feature(feature)
      raise '$LOADED_FEATURES is frozen; cannot append feature' if $LOADED_FEATURES.frozen?
      #feature.freeze # TODO freeze these but post-boot.rb issue using replace
      with_synchronized_features do
        get_loaded_features_index
        $LOADED_FEATURES << feature
        features_index_add(feature, $LOADED_FEATURES.size - 1)
        @loaded_features_version = $LOADED_FEATURES.version
      end
    end

    def self.relative_feature(expanded_path)
      load_path_entries = get_expanded_load_path.select do |load_dir|
        expanded_path.start_with?(load_dir) and expanded_path[load_dir.size] == '/'
      end
      if !load_path_entries.empty?
        load_path_entry = load_path_entries.max_by(&:length)
        before_dot_rb = expanded_path.end_with?('.rb') ? -4 : -1
        expanded_path[load_path_entry.size + 1..before_dot_rb]
      else
        nil
      end
    end

    def self.binary_ext?(ext)
      ext == :so || ext == :dlext
    end

    # Done this way to avoid many duplicate Strings representing the file extensions
    def self.extension_symbol(path)
      if !Primitive.nil?(path)
        if path.end_with?('.rb')
          :rb
        elsif path.end_with?('.so')
          :so
        elsif path.end_with?(DOT_DLEXT)
          :dlext
        else
          ext = File.extname(path)
          ext.empty? ? nil : :other
        end
      else
        nil
      end
    end

    # Done this way to avoid many duplicate Strings representing the file extensions
    def self.extension(path)
      if !Primitive.nil?(path)
        if path.end_with?('.rb')
          '.rb'
        elsif path.end_with?('.so')
          '.so'
        elsif path.end_with?(DOT_DLEXT)
          DOT_DLEXT
        else
          ext = File.extname(path)
          ext.empty? ? nil : ext
        end
      else
        nil
      end
    end

    def self.with_synchronized_features
      TruffleRuby.synchronized(KernelOperations::FEATURE_LOADING_LOCK) do
        yield
      end
    end

    # MRI: get_loaded_features_index
    # always called inside #with_synchronized_features
    def self.get_loaded_features_index
      unless @loaded_features_version == $LOADED_FEATURES.version
        raise '$LOADED_FEATURES is frozen; cannot append feature' if $LOADED_FEATURES.frozen?
        @loaded_features_index.clear
        $LOADED_FEATURES.map! do |val|
          val = StringValue(val)
          #val.freeze # TODO freeze these but post-boot.rb issue using replace
          val
        end
        $LOADED_FEATURES.each_with_index do |val, idx|
          features_index_add(val, idx)
        end
        @loaded_features_version = $LOADED_FEATURES.version
      end
      @loaded_features_index
    end

    # MRI: features_index_add
    # always called inside #with_synchronized_features
    #
    def self.features_index_add(feature, offset)
      feature_entry = FeatureEntry.new(feature)
      feature_entry.index = offset
      if @loaded_features_index.key?(feature_entry.base)
        @loaded_features_index[feature_entry.base] << feature_entry
      else
        @loaded_features_index[feature_entry.base] = [feature_entry]
      end
    end

    def self.get_expanded_load_path
      with_synchronized_features do
        unless @load_path_version == $LOAD_PATH.version && same_working_directory_for_load_path?
          @expanded_load_path = $LOAD_PATH.map do |path|
            path = Truffle::Type.coerce_to_path(path)
            unless @working_directory_copy
              unless File.absolute_path?(path)
                @working_directory_copy = Primitive.working_directory
              end
            end
            Primitive.canonicalize_path(path)
          end
          @load_path_version = $LOAD_PATH.version
        end
        @expanded_load_path
      end
    end

    def self.same_working_directory_for_load_path?
      if working_directory_copy = @working_directory_copy
        if Primitive.working_directory == working_directory_copy
          true
        else
          @working_directory_copy = Primitive.working_directory
          false
        end
      else
        true # no relative path in $LOAD_PATH, no need to check the working directory
      end
    end
  end
end
