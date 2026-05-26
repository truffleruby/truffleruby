# frozen_string_literal: true

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Truffle::FileOperations.exist? is used instead of File.exist? so that adding
# a mock for File.exist? does not affect Dir.glob.

class Dir
  module Glob
    no_meta_chars = /[^*?\[\]{}\\]/
    no_meta_chars_unescaped = /(?:#{no_meta_chars}|\\\\|\\\*|\\\?|\\\[|\\\]|\\\{|\\\})/
    NO_GLOB_META_CHARS = /\A#{no_meta_chars}+\z/
    NO_GLOB_META_CHARS_UNESCAPED = /\A#{no_meta_chars_unescaped}+\z/
    TRAILING_BRACES = /\A(#{no_meta_chars_unescaped}+)(?:\{(#{no_meta_chars_unescaped}*)\})?\z/

    class Node
      def initialize(nxt, flags)
        @flags = flags | File::FNM_EXTGLOB
        @next = nxt
        @separator = nil
      end

      attr_writer :separator

      def sorted?
        (@flags & File::FNM_GLOB_NOSORT) == 0
      end

      def separator
        @separator || '/'
      end

      def path_join(parent, ent)
        Dir::Glob.path_join(parent, ent, separator)
      end

      def process_directory(matches, parent, entry, glob_base_dir)
        # Process a directory. The entry is passed as the parent
        # directory and an entry in that to make it faster to make any
        # calls required to process entry. Any matches found should be
        # added to matches.
        raise 'invalid call to Node base method'
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        # Process an entry within a directory. This differs from
        # process_directory in that in many cases only this entry need
        # be examined. For example a file name matching node need only
        # check the entry (and possibly parent). Existence of the
        # entry is checked already by the caller (often by reading
        # entries from a directory stream and so should not be checked
        # here by the node.
        raise 'invalid call to Node base method'
      end

      def dir_entries(dir, resolve_type, exclude_self)
        # Internal method used by nodes to retrieve the entries for a
        # directory, sorting them if required.
        # The dir argument should be an already open directory object,
        # resolve_type indicate whether the file type should be resolved while scanning the directory,
        # and exclude_self whether the '.' entry should be returned.
        res = []
        if dir
          begin
            begin
              read_res = Truffle::DirOperations.readdir_multiple(dir, resolve_type, exclude_self, res)
            end until read_res == 0;
            res.sort! if sorted?
          ensure
            dir.close
          end
        end
        res
      end

      def subdir_entries(glob_base_dir, path)
        # This method will return the set of directory entries for a
        # sub directory (path) relative to glob_base_dir, which can be
        # nil.
        full = if path
                 path_join(glob_base_dir, path)
               elsif glob_base_dir
                 glob_base_dir
               else
                 '.'
               end
        dir = Dir.allocate.send(:initialize_internal, full)
        res = dir_entries(dir, true, true)
        res
      end

      def is_directory(glob_base_dir, parent, entry, entry_type)
        if entry_type == Truffle::DirOperations::DT_DIR
          true
        elsif entry_type == Truffle::DirOperations::DT_LNK
          full = path_join(path_join(glob_base_dir, parent), entry)
          File.directory?(full)
        else
          false
        end
      end
    end

    class ConstantDirectory < Node
      def initialize(nxt, flags, dir)
        super nxt, flags
        @dir = dir
      end

      def process_directory(matches, parent, entry, glob_base_dir)
        # Don't check if full exists. It just costs us time
        # and the downstream node will be able to check properly.
        @next.process_directory matches, path_join(parent, entry), @dir, glob_base_dir
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        #Check an entry with the guarantee that the previous node has checked it exists.
        @next.process_directory matches, path_join(parent, entry), @dir, glob_base_dir
      end
    end

    class ConstantEntry < Node
      def initialize(nxt, flags, name)
        super nxt, flags
        @name = name
      end

      def process_directory(matches, parent, entry, glob_base_dir)
        parent = path_join(parent, entry)
        path = path_join(parent, @name)

        if Truffle::FileOperations.exist? path_join(glob_base_dir, path)
          matches << path
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        if entry == @name
          matches << path_join(parent, entry)
        end
      end
    end

    class RootDirectory < Node
      def process_directory(matches, parent, entry, glob_base_dir)
        @next.process_directory matches, nil, '/', glob_base_dir
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        @next.process_directory matches, nil, '/', glob_base_dir
      end
    end

    class RecursiveDirectories < Node
      def process_directory(matches, parent, entry, glob_base_dir)
        start = Dir::Glob.path_join(parent, entry)
        return if !start || !Truffle::FileOperations.exist?(path_join(glob_base_dir, start))

        matched = @next.dup
        matched.separator = @separator
        case @next
        when Match
          matched.process_entry('.', Truffle::DirOperations::DT_DIR, matches, start, glob_base_dir)
        else
          matched.process_entry(entry, Truffle::DirOperations::DT_DIR, matches, parent, glob_base_dir)
        end

        stack = [[matched, start, separator, subdir_entries(glob_base_dir, start)]]

        allow_dots = ((@flags & File::FNM_DOTMATCH) != 0)

        until stack.empty?
          matched, path, sep, dir_entries = *stack.pop
          while entry = dir_entries.shift
            ent = entry.name
            type = entry.type

            full = Dir::Glob.path_join(path, ent, sep)
            if (type == Truffle::DirOperations::DT_DIR) and (allow_dots or ent.getbyte(0) != 46) # ?.
              matched.process_entry ent, type, matches, path, glob_base_dir
              stack << [matched, path, sep, dir_entries]
              path = full
              sep = '/'
              matched = @next.dup
              matched.separator = sep
              dir_entries = subdir_entries(glob_base_dir, full)
            elsif (allow_dots or ent.getbyte(0) != 46) # ?.
              matched.process_entry ent, type, matches, path, glob_base_dir
            end
          end
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        process_directory(matches, parent, entry, glob_base_dir) if is_directory(glob_base_dir, parent, entry, entry_type)
      end
    end

    class StartRecursiveDirectories < Node
      def process_directory(matches, parent, entry, glob_base_dir)
        raise 'invalid usage' if parent || entry

        stack = [[nil, subdir_entries(glob_base_dir, nil)]]

        allow_dots = ((@flags & File::FNM_DOTMATCH) != 0)

        if glob_base_dir
          if Primitive.is_a?(@next, DirectoriesOnly)
            @next.process_entry '', Truffle::DirOperations::DT_DIR, matches, parent, glob_base_dir
          else
            @next.process_entry '.', Truffle::DirOperations::DT_DIR, matches, parent, glob_base_dir if allow_dots
          end
        end

        until stack.empty?
          path, dir_entries = *stack.pop

          while entry = dir_entries.shift
            ent = entry.name
            type = entry.type

            full = path_join(path, ent)
            if (type == Truffle::DirOperations::DT_DIR)
              if (allow_dots or ent.getbyte(0) != 46) # ?.
                @next.process_entry ent, type, matches, path, glob_base_dir

                stack << [path, dir_entries]
                path = full
                dir_entries = subdir_entries(glob_base_dir, full)
              end
            else
              @next.process_entry ent, type, matches, path, glob_base_dir
            end
          end
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        raise 'Invalid usage'
      end
    end

    class Match < Node
      def initialize(nxt, flags, glob)
        super nxt, flags
        @glob = glob || +''
      end

      def match?(str)
        File.fnmatch @glob, str, @flags
      end
    end

    class DirectoryMatch < Match
      def initialize(nxt, flags, glob)
        super

        @glob.gsub! '**', '*'
      end

      def process_directory(matches, parent, entry, glob_base_dir)
        path = path_join(parent, entry)
        return if path and !File.directory?(path_join(glob_base_dir, path))

        dir = Dir.allocate.send(:initialize_internal, path_join(glob_base_dir, path ? path : '.'))
        dir_entries(dir, false, false).each do |e|
          if match? e.name
            if File.directory? path_join(glob_base_dir, path_join(path, e.name))
              @next.process_directory matches, path, e.name, glob_base_dir
            end
          end
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        @next.process_directory matches, parent, entry, glob_base_dir if is_directory(glob_base_dir, parent, entry, entry_type) && match?(entry)
      end
    end

    class EntryMatch < Match
      def process_directory(matches, parent, entry, glob_base_dir)
        path = path_join(parent, entry)
        return if path and !Truffle::FileOperations.exist?("#{path_join(glob_base_dir, path)}/.")

        dir_path = path_join(glob_base_dir, path ? path : '.')
        dir = Dir.allocate.send(:initialize_internal, dir_path)
        dir_entries(dir, false, false).each do |e|
          if match? e.name
            matches << path_join(path, e.name)
          end
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        matches << path_join(parent, entry) if match? entry
      end
    end

    class AllNameEntryMatch < EntryMatch
      def match?(entry)
        allow_dots = ((@flags & File::FNM_DOTMATCH) != 0)
        allow_dots or entry.getbyte(0) != 46 # ?.
      end
    end

    class EndsWithEntryMatch < EntryMatch
      def initialize(nxt, flags, glob, suffix)
        super nxt, flags, glob
        @suffix = suffix
      end

      def match?(entry)
        allow_dots = ((@flags & File::FNM_DOTMATCH) != 0)
        entry.end_with?(@suffix) and (allow_dots or entry.getbyte(0) != 46) # ?.
      end
    end

    class DirectoriesOnly < Node
      def process_directory(matches, parent, entry, glob_base_dir)
        path = path_join(parent, entry)
        if path and Truffle::FileOperations.exist?("#{path_join(glob_base_dir, path)}/.")
          matches << "#{path}/"
        end
      end

      def process_entry(entry, entry_type, matches, parent, glob_base_dir)
        matches << "#{path_join(parent, entry)}/" if entry_type == Truffle::DirOperations::DT_DIR
      end
    end

    def self.unescape(pattern)
      pattern.gsub(/\\(.)/, '\\1')
    end

    def self.path_split(str)
      start = 0
      ret = []

      last_match = nil

      while match = Truffle::RegexpOperations.match_from(%r!/+!, str, start)
        cur_start = Primitive.match_data_byte_begin(match, 0)
        cur_end = Primitive.match_data_byte_end(match, 0)
        ret << str.byteslice(start, cur_start - start)
        ret << str.byteslice(cur_start, cur_end - cur_start)

        start = cur_end

        last_match = match
      end

      if last_match
        ret << last_match.post_match
      else
        ret << str
      end

      # Trim from end
      if !ret.empty?
        while s = ret.last and s.empty?
          ret.pop
        end
      end

      ret
    end

    def self.single_compile(glob, flags = 0)
      escape = (flags & File::FNM_NOESCAPE) == 0
      if escape && glob.getbyte(-1) != 47 && NO_GLOB_META_CHARS_UNESCAPED.match?(glob) # byte value 47 = ?/
        return ConstantEntry.new nil, flags, unescape(glob)
      elsif !escape && glob.getbyte(-1) != 47 && NO_GLOB_META_CHARS.match?(glob) # byte value 47 = ?/
        return ConstantEntry.new nil, flags, glob
      end

      parts = path_split(glob)

      if glob.getbyte(-1) == 47 # ?/
        last = DirectoriesOnly.new nil, flags
      else
        file = parts.pop
        if !escape && NO_GLOB_META_CHARS.match?(file)
          last = ConstantEntry.new nil, flags, file
        elsif escape && NO_GLOB_META_CHARS_UNESCAPED.match?(file)
          last = ConstantEntry.new nil, flags, unescape(file)
        elsif file == '*'
          last = AllNameEntryMatch.new nil, flags, file
        elsif file && file[0] == '*' && NO_GLOB_META_CHARS.match?(file[1..])
          last = EndsWithEntryMatch.new nil, flags, file, file[1..]
        else
          last = EntryMatch.new nil, flags, file
        end
      end

      until parts.empty?
        last.separator = parts.pop
        dir = parts.pop

        if dir == '**'
          if parts.empty?
            last = StartRecursiveDirectories.new last, flags
          else
            last = RecursiveDirectories.new last, flags
          end
        elsif !escape && NO_GLOB_META_CHARS.match?(dir)
          while NO_GLOB_META_CHARS.match?(parts[-2])
            next_sep = parts.pop
            next_sect = parts.pop

            dir = next_sect << next_sep << dir
          end

          last = ConstantDirectory.new last, flags, dir
        elsif escape && NO_GLOB_META_CHARS_UNESCAPED.match?(dir)

          dir = unescape(dir)
          while NO_GLOB_META_CHARS_UNESCAPED.match?(parts[-2])
            next_sep = parts.pop
            next_sect = unescape(parts.pop)

            dir = next_sect << next_sep << dir
          end

          last = ConstantDirectory.new last, flags, dir
        elsif !dir.empty?
          last = DirectoryMatch.new last, flags, dir
        end
      end

      if glob.getbyte(0) == 47  # ?/
        last = RootDirectory.new last, flags
      end

      last
    end

    def self.run(node, all_matches, glob_base_dir, flags = 0)
      if Primitive.is_a?(node, ConstantEntry)
        node.process_directory all_matches, nil, nil, glob_base_dir
      else
        matches = []
        node.process_directory matches, nil, nil, glob_base_dir

        all_matches.concat(matches)
      end
    end

    def self.path_join(parent, entry, separator = '/')
      return entry unless parent

      if parent == '/'
        "/#{entry}"
      else
        "#{parent}#{separator}#{entry}"
      end
    end

    def self.glob(base_dir, pattern, flags, matches)
      # Rubygems uses Dir[] as a glorified File.exist? to check for multiple
      # extensions. So we went ahead and sped up that specific case.

      if flags == 0 and m = TRAILING_BRACES.match(pattern)
        # no meta characters, so this is a glorified
        # File.exist? check. We allow for a brace expansion
        # only as a suffix.

        if braces = m[2]
          stem = unescape(m[1])

          braces.split(',').each do |s|
            s = unescape(s)
            path = "#{stem}#{s}"
            if Truffle::FileOperations.exist? path_join(base_dir, path)
              matches << path
            end
          end

          # Split strips an empty closing part, so we need to add it back in
          if braces.getbyte(-1) == 44 # ?,
            matches << stem if Truffle::FileOperations.exist? path_join(base_dir, stem)
          end
        else
          pattern = unescape(pattern)
          matches << pattern if Truffle::FileOperations.exist?(path_join(base_dir, pattern))
        end

        return matches
      end

      left_brace_match = pattern.match(/(\\*)(\{)/)
      left_brace_index = left_brace_match && left_brace_match[1].size.even? ? left_brace_match.begin(2) : nil
      if left_brace_index
        patterns = compile(pattern, left_brace_index, flags)

        patterns.each do |node|
          run node, matches, base_dir, flags
        end
      elsif node = single_compile(pattern, flags)
        run node, matches, base_dir, flags
      else
        matches
      end
    end

    def self.compile(pattern, left_brace_index, flags, patterns = [])
      escape = (flags & File::FNM_NOESCAPE) == 0

      lbrace = left_brace_index
      rbrace = nil

      i = left_brace_index

      # If there was a { found, then search
      if i
        nest = 0

        while i < pattern.size
          char = pattern[i]

          if char == '{'
            nest += 1
          elsif char == '}'
            nest -= 1

            if nest == 0
              rbrace = i
              break
            end
          elsif char == '\\' and escape
            i += 1
          end

          i += 1
        end
      end

      # There was a full {} expression detected, expand each part of it
      # recursively.
      if lbrace and rbrace
        pos = lbrace
        front = pattern[0...lbrace]
        back = pattern[(rbrace + 1)..-1]

        while pos < rbrace
          nest = 0
          pos += 1
          last = pos

          while pos < rbrace and not (pattern[pos] == ',' and nest == 0)
            char = pattern[pos]

            if char == '{'
              nest += 1
            elsif char == '}'
              nest -= 1
            elsif char == '\\' and escape
              pos += 1
              break if pos == rbrace
            end

            pos += 1
          end

          middle = pattern[last...pos]
          brace_pattern = "#{front}#{middle}#{back}"

          # The front part of the constructed string can't possibly have a '{' character, but the other parts might.
          # By searching each part rather than the constructed string, we can reduce the number of characters that
          # need to be checked.
          next_left_brace = middle.index('{')
          if next_left_brace
            next_left_brace += front.size
          else
            next_left_brace = back.index('{')
            if next_left_brace
              next_left_brace += front.size + middle.size
            end
          end

          compile brace_pattern, next_left_brace, flags, patterns
        end

        # No braces found, match the pattern normally
      else
        # Don't use .glob here because this code can detect properly
        # if a { is a brace or a just a normal character, but .glob can't.
        # if .glob is used and there is a { as a normal character, it will
        # recurse forever.
        if node = single_compile(pattern, flags)
          patterns << node
        end
      end

      patterns
    end
  end
end
