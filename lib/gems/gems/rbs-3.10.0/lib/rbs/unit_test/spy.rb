# frozen_string_literal: true

module RBS
  module UnitTest
    module Spy
      def self.wrap(object, method_name)
        spy = WrapSpy.new(object: object, method_name: method_name)

        if block_given?
          begin
            yield spy, spy.wrapped_object
          end
        else
          spy
        end
      end

      class WrapSpy
        attr_accessor :callback
        attr_reader :object
        attr_reader :method_name

        NO_RETURN = Object.new

        def initialize(object:, method_name:)
          @callback = -> (_) { }
          @object = object
          @method_name = method_name
        end

        def wrapped_object
          spy = self #: WrapSpy[untyped]

          Class.new(BasicObject) do
            # @type self: Class

            define_method(:method_missing) do |name, *args, &block|
              spy.object.__send__(name, *args, &block)
            end

            define_method(
              spy.method_name,
              _ = -> (*args, &block) do
                return_value = NO_RETURN
                exception = nil
                block_calls = [] #: Array[Test::ArgumentsReturn]

                spy_block = if block
                              Object.new.instance_eval do |fresh|
                                proc = -> (*block_args) do
                                  block_exn = nil
                                  block_return = nil

                                  begin
                                    block_return = if self.equal?(fresh)
                                                    # no instance eval
                                                    block.call(*block_args)
                                                  else
                                                    self.instance_exec(*block_args, &block)
                                                  end
                                  rescue Exception => exn
                                    block_exn = exn
                                  end

                                  if block_exn
                                    block_calls << Test::ArgumentsReturn.exception(
                                      arguments: block_args,
                                      exception: block_exn
                                    )
                                  else
                                    block_calls << Test::ArgumentsReturn.return(
                                      arguments: block_args,
                                      value: block_return
                                    )
                                  end

                                  if block_exn
                                    raise block_exn
                                  else
                                    block_return
                                  end
                                end #: Proc

                                proc.ruby2_keywords
                              end
                            end

                begin
                  if spy_block
                    return_value = spy.object.__send__(spy.method_name, *args) do |*a, **k, &b|
                      spy_block.call(*a, **k, &b)
                    end
                  else
                    return_value = spy.object.__send__(spy.method_name, *args, &spy_block)
                  end
                rescue ::Exception => exn
                  exception = exn
                end

                return_value

              ensure
                call =
                  case
                  when exception
                    Test::ArgumentsReturn.exception(
                      arguments: args,
                      exception: exception
                    )
                  when ::RBS::UnitTest::Spy::WrapSpy::NO_RETURN != return_value
                    Test::ArgumentsReturn.return(
                      arguments: args,
                      value: return_value
                    )
                  else
                    # break
                    Test::ArgumentsReturn.break(arguments: args)
                  end
                trace = RBS::Test::CallTrace.new(
                  method_name: spy.method_name,
                  method_call: call,
                  block_calls: block_calls,
                  block_given: block != nil
                )

                spy.callback.call(trace)

                if exception
                  spy.object.__send__(:raise, exception)
                end
              end.ruby2_keywords
            )
          end.allocate()
        end
      end
    end
  end
end
