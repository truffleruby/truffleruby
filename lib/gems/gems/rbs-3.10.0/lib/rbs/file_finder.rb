# frozen_string_literal: true

module RBS
  module FileFinder
    module_function

    def self.each_file(path, immediate: nil, skip_hidden:, &block)
      return enum_for((__method__ or raise), path, immediate: immediate, skip_hidden: skip_hidden) unless block

      case
      when path.file?
        yield path

      when path.directory?
        paths = Pathname.glob("#{path}/**/*.rbs")

        if skip_hidden
          paths.select! do |child|
            child.relative_path_from(path).ascend.drop(1).none? { _1.basename.to_s.start_with?("_") }
          end
        end
        paths.sort_by!(&:to_s)

        paths.each(&block)
      end
    end
  end
end
