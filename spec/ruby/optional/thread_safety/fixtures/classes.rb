module ThreadSafetySpecs
  def self.processors
    require 'etc'
    Etc.nprocessors
  end

  class Counter
    def initialize
      @value = 0
      @mutex = Mutex.new
    end

    def get
      @mutex.synchronize { @value }
    end

    def increment
      @mutex.synchronize do
        @value += 1
      end
    end
  end

  class Barrier
    def initialize(parties)
      @parties = parties
      @counter = Counter.new
    end

    def wait
      @counter.increment
      Thread.pass until @counter.get == @parties
    end
  end
end
