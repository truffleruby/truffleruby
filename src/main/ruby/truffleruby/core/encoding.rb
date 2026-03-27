# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

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

class Encoding
  Truffle::EncodingOperations.build_encoding_map
  EncodingMap = Truffle::EncodingOperations::EncodingMap

  Truffle::Boot.redo do
    @default_internal = Truffle::EncodingOperations.setup_default_encoding('internal', :INTERNAL)
    @default_external = Truffle::EncodingOperations.setup_default_encoding('external', :EXTERNAL)
    Truffle::EncodingOperations.setup_default_encoding('locale', :LOCALE)
    Truffle::EncodingOperations.setup_default_encoding('filesystem', :FILESYSTEM)
  end

  Truffle::Boot.delay do
    LOCALE = Primitive.encoding_get_default_encoding 'locale'
  end

  def self.aliases
    aliases = {}
    EncodingMap.each do |_name, pair|
      alias_name, enc = pair
      if alias_name and enc
        aliases[alias_name] = enc.name if alias_name
      end
    end
    aliases
  end

  class << self
    attr_reader :default_external, :default_internal

    # The filesystem Encoding is always the same as the external encoding,
    # except on Windows (see Init_enc_set_filesystem_encoding() in MRI).
    alias_method :filesystem, :default_external
  end

  def self.default_external=(enc)
    raise ArgumentError, 'default external encoding cannot be nil' if Primitive.nil? enc

    enc = find(enc)
    Truffle::EncodingOperations.change_default_encoding 'external', enc
    Truffle::EncodingOperations.change_default_encoding 'filesystem', enc
    @default_external = enc
    Primitive.encoding_set_default_external enc
  end

  def self.default_internal=(enc)
    enc = find(enc) unless Primitive.nil? enc
    Truffle::EncodingOperations.change_default_encoding 'internal', enc
    @default_internal = enc
    Primitive.encoding_set_default_internal enc
  end

  # Does not exist on CRuby
  def self.try_convert(obj)
    case obj
    when Encoding
      return obj
    when String
      str = obj
    else
      str = Primitive.convert_with_to_str(obj)
    end

    key = str.upcase.to_sym

    pair = EncodingMap[key]
    if pair
      return pair.last
    end

    false
  end

  def self.find(name)
    enc = try_convert(name)
    return enc unless false == enc

    raise ArgumentError, "unknown encoding name - #{name}"
  end

  def self.name_list
    EncodingMap.map do |_name, pair|
      alias_name, enc = pair
      alias_name || (enc&.name)
    end
  end

  def self.compatible?(first, second)
    Primitive.encoding_compatible? first, second
  end

  def inspect
    "#<Encoding:#{name}#{" (dummy)" if dummy?}>"
  end

  def names
    entry = EncodingMap[name.upcase.to_sym]
    names = [name]
    EncodingMap.each do |_name, pair|
      alias_name, enc = pair
      names << alias_name if alias_name and enc == entry.last
    end
    names
  end

  def _dump(depth)
    name
  end

  def self._load(name)
    find name
  end
end
