# frozen_string_literal: true

module RBS
  class Diff
    def initialize(type_name:, library_options:, after_path: [], before_path: [], detail: false)
      @type_name = type_name
      @library_options = library_options
      @after_path = after_path
      @before_path = before_path
      @detail = detail
    end

    def each_diff(&block)
      return to_enum(:each_diff) unless block

      before_instance_methods, before_singleton_methods, before_constant_children = build_methods(@before_path)
      after_instance_methods, after_singleton_methods, after_constant_children = build_methods(@after_path)

      each_diff_methods(:instance, before_instance_methods, after_instance_methods, &block)
      each_diff_methods(:singleton, before_singleton_methods, after_singleton_methods, &block)

      each_diff_constants(before_constant_children, after_constant_children, &block)
    end

    private

    def each_diff_methods(kind, before_methods, after_methods)
      all_keys = before_methods.keys.to_set + after_methods.keys.to_set
      all_keys.each do |key|
        before = definition_method_to_s(key, kind, before_methods[key]) or next
        after = definition_method_to_s(key, kind, after_methods[key]) or next
        next if before == after

        yield before, after
      end
    end

    def each_diff_constants(before_constant_children, after_constant_children)
      all_keys = before_constant_children.keys.to_set + after_constant_children.keys.to_set
      all_keys.each do |key|
        before = constant_to_s(before_constant_children[key]) or next
        after = constant_to_s(after_constant_children[key]) or next
        next if before == after

        yield before, after
      end
    end

    def build_methods(path)
      env = build_env(path)
      builder = build_builder(env)

      instance_methods = begin
        builder.build_instance(@type_name).methods
      rescue => e
        RBS.logger.warn("#{path}: (#{e.class}) #{e.message}")
        {} #: Hash[Symbol, Definition::Method]
      end
      singleton_methods = begin
        builder.build_singleton(@type_name).methods
      rescue => e
        RBS.logger.warn("#{path}: (#{e.class}) #{e.message}")
        {} #: Hash[Symbol, Definition::Method]
      end

      constant_children = begin
        constant_resolver = RBS::Resolver::ConstantResolver.new(builder: builder)
        constant_resolver.children(@type_name)
      rescue => e
        RBS.logger.warn("#{path}: (#{e.class}) #{e.message}")
        {} #: Hash[Symbol, Constant]
      end

      [ instance_methods, singleton_methods, constant_children ]
    end

    def build_env(path)
      loader = @library_options.loader()
      path&.each do |dir|
        dir_pathname = Pathname(dir)
        loader.add(path: dir_pathname)

        manifest_pathname = dir_pathname / 'manifest.yaml'
        if manifest_pathname.exist?
          manifest = YAML.safe_load(manifest_pathname.read)
          if manifest && manifest['dependencies']
            manifest['dependencies'].each do |dependency|
              loader.add(library: dependency['name'], version: nil)
            end
          end
        end
      end
      Environment.from_loader(loader)
    end

    def build_builder(env)
      DefinitionBuilder.new(env: env.resolve_type_names)
    end

    def definition_method_to_s(key, kind, definition_method)
      if definition_method
        prefix = kind == :instance ? "" : "self."

        detail_to_s = @detail ? "[#{definition_method.defined_in} #{definition_method.accessibility}] " : ""
        if definition_method.alias_of
          first_def = definition_method.alias_of.defs.first #: Definition::Method::TypeDef
          "#{detail_to_s}alias #{prefix}#{key} #{prefix}#{first_def.member.name}"
        else
          "#{detail_to_s}def #{prefix}#{key}: #{definition_method.method_types.join(" | ")}"
        end
      else
        +"-"
      end
    end

    def constant_to_s(constant)
      if constant
        detail_to_s = @detail ? "[#{constant.name.namespace.to_type_name.to_s}] " : ""
        "#{detail_to_s}#{constant.name.name}: #{constant.type}"
      else
        +"-"
      end
    end
  end
end
