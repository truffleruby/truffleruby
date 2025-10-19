require_relative '../../spec_helper'
require_relative 'fixtures/classes'

describe "Hash thread safety" do
  it "supports concurrent #[]=" do
    n_threads = ThreadSafetySpecs.processors

    n = 1_000
    operations = n * n_threads

    h = {}
    barrier = ThreadSafetySpecs::Barrier.new(n_threads + 1)

    threads = n_threads.times.map { |t|
      Thread.new {
        barrier.wait
        base = t * n
        n.times do |j|
          h[base+j] = t
        end
      }
    }

    barrier.wait
    threads.each(&:join)

    h.size.should == operations
    h.each_pair { |key, value|
      (key / n).should == value
    }
  end

  it "supports concurrent #delete" do
    n_threads = ThreadSafetySpecs.processors

    n = 1_000
    operations = n * n_threads / 2

    h = {}
    barrier = ThreadSafetySpecs::Barrier.new(n_threads + 1)

    threads = n_threads.times.map { |t|
      base = t * n
      n.times do |j|
        h[base+j] = t
      end

      Thread.new {
        barrier.wait
        n.times do |j|
          # delete only even keys
          h.delete(base+j).should == t if (base+j).even?
        end
      }
    }

    barrier.wait
    threads.each(&:join)

    # odd keys are left
    h.size.should == operations
    h.each_pair { |key, value|
      key.should.odd?
      (key / n).should == value
    }
  end


  it "supports concurrent #[]= and #[]" do
    n_threads = ThreadSafetySpecs.processors

    n = 1_000
    operations = n * n_threads / 2

    h = {}
    barrier = ThreadSafetySpecs::Barrier.new(n_threads + 1)

    threads = n_threads.times.map { |t|
      Thread.new {
        barrier.wait
        base = (t / 2) * n

        if t.even?
          n.times do |j|
            k = base + j
            h[k] = k
          end
        else
          n.times do |j|
            k = base + j
            Thread.pass until v = h[k]
            v.should == k
          end
        end
      }
    }

    barrier.wait
    threads.each(&:join)

    h.size.should == operations
    h.each_pair { |key, value|
      key.should == value
    }
  end

  it "supports concurrent #[]= and #delete and always returns a #size >= 0" do
    n_threads = ThreadSafetySpecs.processors

    n = 1_000
    operations = n * n_threads / 2

    h = {}
    barrier = ThreadSafetySpecs::Barrier.new(n_threads + 1)
    deleted = ThreadSafetySpecs::Counter.new

    threads = n_threads.times.map { |t|
      Thread.new {
        barrier.wait
        key = t / 2

        if t.even?
          n.times {
            Thread.pass until h.delete(key)
            h.size.should >= 0
            deleted.increment
          }
        else
          n.times {
            h[key] = key
            Thread.pass while h.key?(key)
          }
        end
      }
    }

    barrier.wait
    threads.each(&:join)

    deleted.get.should == operations
    h.size.should == 0
    h.should.empty?
  end
end
