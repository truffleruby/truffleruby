require 'ripper'

module PowerAssert
  class Parser
    Ident = Struct.new(:type, :name, :column)

    attr_reader :line, :path, :lineno, :binding

    def initialize(line, path, lineno, binding, assertion_method_name = nil, assertion_proc = nil)
      @line = line
      @line_for_parsing = (valid_syntax?(line) ? line : slice_expression(line)).b
      @path = path
      @lineno = lineno
      @binding = binding
      @proc_local_variables = binding.eval('local_variables').map(&:to_s)
      @assertion_method_name = assertion_method_name
      @assertion_proc = assertion_proc
    end

    def idents
      @idents ||= extract_idents(Ripper.sexp(@line_for_parsing))
    end

    def call_paths
      collect_paths(idents).uniq
    end

    def method_id_set
      methods = idents.flatten.find_all {|i| i.type == :method }
      @method_id_set ||= methods.map(&:name).map(&:to_sym).each_with_object({}) {|i, h| h[i] = true }
    end

    private

    def valid_syntax?(str)
      verbose, $VERBOSE = $VERBOSE, nil
      RubyVM::InstructionSequence.compile(str)
      true
    rescue SyntaxError
      false
    ensure
      $VERBOSE = verbose
    end

    def slice_expression(str)
      str = str.chomp
      str.sub!(/\A\s*(?:if|unless|elsif|case|while|until) /) {|i| ' ' * i.length }
      str.sub!(/\A\s*(?:\}|\]|end)?\./) {|i| ' ' * i.length }
      str.sub!(/[\{\.\\]\z/, '')
      str.sub!(/(?:&&|\|\|)\z/, '')
      str.sub!(/ (?:do|and|or)\z/, '')
      str
    end

    class Branch < Array
    end

    AND_OR_OPS = %i(and or && ||)

    #
    # Returns idents as graph structure.
    #
    #                                                  +--c--b--+
    #  extract_idents(Ripper.sexp('a&.b(c).d')) #=> a--+        +--d
    #                                                  +--------+
    #
    def extract_idents(sexp)
      case sexp
      in [:arg_paren | :assoc_splat | :fcall | :hash | :method_add_block | :string_literal | :return, s, *]
        extract_idents(s)
      in [:assign | :massign, _, s]
        extract_idents(s)
      in [:opassign, _, [_, op_name, [_, op_column]], s]
        extract_idents(s) + [Ident[:method, op_name.sub(/=\z/, ''), op_column]]
      in [:dyna_symbol, [Symbol, *] => s]
        # s can be [:string_content, [..]] while parsing an expression like { "a": 1 }
        extract_idents(s)
      in [:dyna_symbol, ss]
        ss.flat_map {|s| extract_idents(s) }
      in [:assoclist_from_args | :bare_assoc_hash | :paren | :string_embexpr | :regexp_literal | :xstring_literal, ss, *]
        ss.flat_map {|s| extract_idents(s) }
      in [:command, s0, s1]
        [s1, s0].flat_map {|s| extract_idents(s) }
      in [:assoc_new | :dot2 | :dot3 | :string_content, *ss]
        ss.flat_map {|s| extract_idents(s) }
      in [:unary, mid, s]
        handle_columnless_ident([], mid, extract_idents(s))
      in [:binary, s0, op, s1] if AND_OR_OPS.include?(op)
        extract_idents(s0) + [Branch[extract_idents(s1), []]]
      in [:binary, s0, op, s1]
        handle_columnless_ident(extract_idents(s0), op, extract_idents(s1))
      in [:call, recv, [op_sym, op_name, _], method]
        with_safe_op = ((op_sym == :@op and op_name == '&.') or op_sym == :"&.")
        if method == :call
          handle_columnless_ident(extract_idents(recv), :call, [], with_safe_op)
        else
          extract_idents(recv) + (with_safe_op ? [Branch[extract_idents(method), []]] : extract_idents(method))
        end
      in [:array, ss]
        ss ? ss.flat_map {|s| extract_idents(s) } : []
      in [:command_call, s0, _, s1, s2]
        [s0, s2, s1].flat_map {|s| extract_idents(s) }
      in [:aref, s0, s1]
        handle_columnless_ident(extract_idents(s0), :[], extract_idents(s1))
      in [:method_add_arg, s0, s1]
        case extract_idents(s0)
        in []
          # idents(s0) may be empty(e.g. ->{}.())
          extract_idents(s1)
        in [*is0, Branch[is1, []]]
          # Safe navigation operator is used. See :call clause also.
          is0 + [Branch[extract_idents(s1) + is1, []]]
        in [*is, i]
          is + extract_idents(s1) + [i]
        end
      in [:args_add_block, [:args_add_star, ss0, *ss1], _]
        (ss0 + ss1).flat_map {|s| extract_idents(s) }
      in [:args_add_block, ss, _]
        ss.flat_map {|s| extract_idents(s) }
      in [:vcall, [:@ident, name, [_, column]]]
        [Ident[@proc_local_variables.include?(name) ? :ref : :method, name, column]]
      in [:vcall, _]
        []
      in [:program, [[:method_add_block, [:method_add_arg, [:fcall, [:@ident | :@const, ^@assertion_method_name, _]], _], [:brace_block | :do_block, _, ss]]]]
        ss.flat_map {|s| extract_idents(s) }
      in [:program, [s, *]]
        extract_idents(s)
      in [:ifop, s0, s1, s2]
        [*extract_idents(s0), Branch[extract_idents(s1), extract_idents(s2)]]
      in [:if | :unless, s0, ss0, [_, ss1]]
        [*extract_idents(s0), Branch[ss0.flat_map {|s| extract_idents(s) }, ss1.flat_map {|s| extract_idents(s) }]]
      in [:if | :unless, s0, ss0, _]
        [*extract_idents(s0), Branch[ss0.flat_map {|s| extract_idents(s) }, []]]
      in [:if_mod | :unless_mod, s0, s1]
        [*extract_idents(s0), Branch[extract_idents(s1), []]]
      in [:var_ref | :var_field, [:@kw, 'self', [_, column]]]
        [Ident[:ref, 'self', column]]
      in [:var_ref | :var_field, [:@ident | :@const | :@cvar | :@ivar | :@gvar, ref_name, [_, column]]]
        [Ident[:ref, ref_name, column]]
      in [:var_ref | :var_field, _]
        []
      in [:@ident | :@const | :@op, method_name, [_, column]]
        [Ident[:method, method_name, column]]
      else
        []
      end
    end

    def str_indices(str, re, offset, limit)
      idx = str.index(re, offset)
      if idx and idx <= limit
        [idx, *str_indices(str, re, idx + 1, limit)]
      else
        []
      end
    end

    MID2SRCTXT = {
      :[] => '[',
      :+@ => '+',
      :-@ => '-',
      :call => '('
    }

    def handle_columnless_ident(left_idents, mid, right_idents, with_safe_op = false)
      left_max = left_idents.flatten.max_by(&:column)
      right_min = right_idents.flatten.min_by(&:column)
      bg = left_max ? left_max.column + left_max.name.length : 0
      ed = right_min ? right_min.column - 1 : @line_for_parsing.length - 1
      mname = mid.to_s
      srctxt = MID2SRCTXT[mid] || mname
      re = /
        #{'\b' if /\A\w/ =~ srctxt}
        #{Regexp.escape(srctxt)}
        #{'\b' if /\w\z/ =~ srctxt}
      /x
      indices = str_indices(@line_for_parsing, re, bg, ed)
      if indices.length == 1 or !(right_idents.empty? and left_idents.empty?)
        ident = Ident[:method, mname, right_idents.empty? ? indices.first : indices.last]
        left_idents + right_idents + (with_safe_op ? [Branch[[ident], []]] : [ident])
      else
        left_idents + right_idents
      end
    end

    def collect_paths(idents, prefixes = [[]], index = 0)
      if index < idents.length
        node = idents[index]
        if node.kind_of?(Branch)
          prefixes = node.flat_map {|n| collect_paths(n, prefixes, 0) }
        else
          prefixes = prefixes.map {|prefix| prefix + [node] }
        end
        collect_paths(idents, prefixes, index + 1)
      else
        prefixes
      end
    end

    class DummyParser < Parser
      def initialize
        super('', nil, nil, TOPLEVEL_BINDING)
      end

      def idents
        []
      end

      def call_paths
        []
      end
    end
    DUMMY = DummyParser.new
  end
  private_constant :Parser
end
