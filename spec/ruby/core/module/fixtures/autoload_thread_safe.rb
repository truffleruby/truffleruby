module ModuleSpecs::Autoload
  class ThreadSafe
    def self.bar # to illustrate partial loading
    end

    barrier = ScratchPad.recorded
    barrier.await

    # the main thread should be waiting for this file to be fully loaded
    Thread.pass until Thread.main.stop?
    10.times do
      Thread.pass
      Thread.main.should.stop?
    end

    def self.foo
      42
    end
  end
end
