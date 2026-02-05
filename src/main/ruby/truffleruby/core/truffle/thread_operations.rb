# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
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

# Copyright (c) 2011, Evan Phoenix
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
# * Neither the name of the Evan Phoenix nor the names of its contributors
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

module Truffle::ThreadOperations

  # detect_recursion will return if there's a recursion
  # on obj.
  # If there is one, it returns true.
  # Otherwise, it will yield once and return false.
  def self.detect_recursion(obj, &block)
    unless Primitive.array_can_contain_object? obj
      yield
      return false
    end

    Primitive.thread_detect_recursion_single obj, block
  end
  Primitive.always_split singleton_class, :detect_recursion

  # detect_recursion will return if there's a recursion
  # on the pair obj+paired_obj.
  # If there is one, it returns true.
  # Otherwise, it will yield once and return false.
  def self.detect_pair_recursion(obj, paired_obj)
    unless Primitive.array_can_contain_object? obj
      yield
      return false
    end

    id = obj.object_id
    pair_id = paired_obj.object_id
    objects = Primitive.thread_recursive_objects

    case objects[id]

    # Default case, we haven't seen +obj+ yet, so we add it and run the block.
    when nil
      objects[id] = pair_id
      begin
        yield
      ensure
        objects.delete id
      end

    # We've seen +obj+ before and it's got multiple paired objects associated
    # with it, so check the pair and yield if there is no recursion.
    when Hash
      return true if objects[id][pair_id]

      objects[id][pair_id] = true
      begin
        yield
      ensure
        objects[id].delete pair_id
      end

    # We've seen +obj+ with one paired object, so check the stored one for
    # recursion.
    #
    # This promotes the value to a Hash since there is another new paired
    # object.
    else
      previous = objects[id]
      return true if previous == pair_id

      objects[id] = { previous => true, pair_id => true }
      begin
        yield
      ensure
        objects[id] = previous
      end
    end

    false
  end
  Primitive.always_split singleton_class, :detect_pair_recursion

  class InnerRecursionDetected < Exception; end # rubocop:disable Lint/InheritException

  # Similar to detect_recursion, but will short circuit all inner recursion levels
  def self.detect_outermost_recursion(obj, &block)
    rec = Primitive.thread_recursive_objects

    if rec[:__detect_outermost_recursion__]
      if detect_recursion(obj, &block)
        raise InnerRecursionDetected
      end
      false
    else
      rec[:__detect_outermost_recursion__] = true
      begin
        begin
          detect_recursion(obj, &block)
        rescue InnerRecursionDetected
          return true
        end
        nil
      ensure
        rec.delete :__detect_outermost_recursion__
      end
    end
  end

  def self.report_exception(thread, exception)
    message = "#{thread.inspect} terminated with exception (report_on_exception is true):\n#{exception.full_message}"
    $stderr.write message
  end

  # To ignore this thread for the MSpec LeakChecker
  def self.mark_as_detached_thread(thread)
    thread.thread_variable_set(:"\0__detached_thread__", true)
  end
end
