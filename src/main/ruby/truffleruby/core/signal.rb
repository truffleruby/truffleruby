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

module Signal
  Names = {
    'EXIT' => 0
  }

  Numbers = {
    0 => 'EXIT'
  }

  NSIG = Truffle::Config['platform.limits.NSIG']

  # Fill the Names and Numbers Hash.
  prefix = 'platform.signal.'
  Truffle::Config.section(prefix) do |name, number|
    name = name[prefix.size+3..-1]
    Names[name] = number
    Numbers[number] = name
  end

  # Define CLD as CHLD if it's not defined by the platform
  unless Names.key? 'CLD'
    Names['CLD'] = Names['CHLD']
  end

  # Signal.signame(number) always returns the original and not the alias when they have the same signal numbers
  # for CLD => CHLD and IOT => ABRT.
  # CLD and IOT is not always recognized by `new sun.misc.Signal(name)` (IOT is known on linux).
  Numbers[Names['CHLD']] = 'CHLD'
  Numbers[Names['ABRT']] = 'ABRT'

  @handlers = {}

  def self.trap(signal, handler = nil, &block)
    unless Primitive.is_a?(signal, Symbol) || Primitive.is_a?(signal, String) \
        || Primitive.is_a?(signal, Integer)
      raise ArgumentError, "bad signal type #{Primitive.class(signal)}"
    end

    signal = signal.to_s if Primitive.is_a?(signal, Symbol)

    if Primitive.is_a?(signal, String)
      if signal.start_with? 'SIG'
        signal = signal[3..-1]
      end

      unless number = Names[signal]
        raise ArgumentError, "unsupported signal 'SIG#{signal}'"
      end
    else
      number = Primitive.rb_to_int signal

      unless Numbers.key? number
        raise ArgumentError, "invalid signal number (#{number})"
      end
    end

    signame = self.signame(number)

    if %w[SEGV BUS ILL FPE VTALRM].include?(signame)
      raise ArgumentError, "can't trap reserved signal: SIG#{signame}"
    end

    handler ||= block
    handler = handler.to_s if Primitive.is_a?(handler, Symbol)

    case handler
    when 'DEFAULT', 'SIG_DFL'
      handler = 'DEFAULT'
    when 'SYSTEM_DEFAULT'
      handler = 'SYSTEM_DEFAULT'
    when 'IGNORE', 'SIG_IGN'
      handler = 'IGNORE'
    when nil # Same as 'IGNORE', except that it is kept as null in @handlers
      handler = nil
    when 'EXIT'
      handler = proc { exit }
    when String
      raise ArgumentError, "Unsupported command '#{handler}'"
    when Proc
      # handler is already callable
    else
      underlying_handler = handler
      handler = ->(signo) {
        underlying_handler.call(signo)
      }
    end

    had_old = @handlers.key?(number)

    if handler == 'DEFAULT' || handler == 'SYSTEM_DEFAULT'
      old = @handlers.delete(number)
    else
      old = @handlers[number]
      @handlers[number] = handler
    end

    if number != Names['EXIT']
      ret = Primitive.vm_watch_signal(signame, false, handler || 'IGNORE')
      if handler == 'DEFAULT' && !ret
        return +'SYSTEM_DEFAULT'
      end
    end

    if !had_old && handler != 'SYSTEM_DEFAULT'
      +'DEFAULT'
    else
      old ? old : nil
    end
  end

  def self.list
    Names.dup
  end

  def self.signame(signo)
    index = Primitive.rb_to_int signo

    Numbers[index]
  end
end
