# frozen_string_literal: true

require "rbs/version"

require "set"
require "json"
require "pathname" unless defined?(Pathname)
require "pp"
require "ripper"
require "logger"
require "tsort"
require "strscan"

require "rbs/errors"
require "rbs/buffer"
require "rbs/namespace"
require "rbs/type_name"
require "rbs/types"
require "rbs/method_type"
require "rbs/file_finder"
require "rbs/ast/type_param"
require "rbs/ast/directives"
require "rbs/ast/declarations"
require "rbs/ast/members"
require "rbs/ast/annotation"
require "rbs/ast/visitor"
require "rbs/environment"
require "rbs/environment/use_map"
require "rbs/environment_loader"
require "rbs/builtin_names"
require "rbs/definition"
require "rbs/definition_builder"
require "rbs/definition_builder/ancestor_builder"
require "rbs/definition_builder/method_builder"
require "rbs/diff"
require "rbs/variance_calculator"
require "rbs/substitution"
require "rbs/constant"
require "rbs/resolver/constant_resolver"
require "rbs/resolver/type_name_resolver"
require "rbs/ast/comment"
require "rbs/writer"
require "rbs/prototype/helpers"
require "rbs/prototype/rbi"
require "rbs/prototype/rb"
require "rbs/prototype/runtime"
require "rbs/prototype/node_usage"
require "rbs/environment_walker"
require "rbs/vendorer"
require "rbs/validator"
require "rbs/factory"
require "rbs/repository"
require "rbs/subtractor"
require "rbs/ancestor_graph"
require "rbs/locator"
require "rbs/type_alias_dependency"
require "rbs/type_alias_regularity"
require "rbs/collection"

require "rbs_extension"
require "rbs/parser_aux"
require "rbs/location_aux"

module RBS
  class <<self
    attr_reader :logger_level
    attr_reader :logger_output

    def logger
      @logger ||= Logger.new(logger_output || STDERR, level: logger_level || Logger::WARN, progname: "rbs")
    end

    def logger_output=(val)
      @logger = nil
      @logger_output = val
    end

    def logger_level=(level)
      @logger_level = level
      @logger = nil
    end

    def print_warning()
      @warnings ||= Set[]

      message = yield()

      unless @warnings.include?(message)
        @warnings << message
        logger.warn { message }
      end
    end
  end
end
