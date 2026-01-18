# frozen_string_literal: true

require_relative "test_helper"

# We don't want to generate snapshots when running
# against files outside of the project folder.
return if Prism::TestCase::Fixture.custom_base_path?

module Prism
  class SnapshotsTest < TestCase
    # When we pretty-print the trees to compare against the snapshots, we want
    # to be certain that we print with the same external encoding. This is
    # because methods like Symbol#inspect take into account external encoding
    # and it could change how the snapshot is generated. On machines with
    # certain settings (like LANG=C or -Eascii-8bit) this could have been
    # changed. So here we're going to force it to be UTF-8 to keep the snapshots
    # consistent.
    def setup
      @previous_default_external = Encoding.default_external
      ignore_warnings { Encoding.default_external = Encoding::UTF_8 }
    end

    def teardown
      ignore_warnings { Encoding.default_external = @previous_default_external }
    end

    except = []

    # These fail on TruffleRuby due to a difference in Symbol#inspect:
    # :测试 vs :"测试"
    if RUBY_ENGINE == "truffleruby"
      except.push(
        "emoji_method_calls.txt",
      )
    end

    Fixture.each_with_all_versions(except: except) do |fixture, version|
      define_method(fixture.test_name(version)) { assert_snapshot(fixture, version) }
    end

    private

    def assert_snapshot(fixture, version)
      source = fixture.read

      result = Prism.parse(source, filepath: fixture.path, version: version)
      assert result.success?

      printed = result.value.inspect
      snapshot = fixture.snapshot_path

      if File.exist?(snapshot)
        saved = File.read(snapshot)

        # If the snapshot file exists, but the printed value does not match the
        # snapshot, then update the snapshot file.
        if printed != saved
          if ENV["UPDATE_SNAPSHOTS"]
            File.write(snapshot, printed)
          else
            warn("Snapshot at #{snapshot} outdated for #{version}. Run with UPDATE_SNAPSHOTS=1 " \
              "to regenerate the files. If modifying behaviour for a subset of ruby versions, " \
              "instead move the fixture and snapshot into versioned directories. For more details, " \
              "see `Prism::TestCase.ruby_versions_for`.")
          end
        end

        # If the snapshot file exists, then assert that the printed value
        # matches the snapshot.
        assert_equal(saved, printed)
      else
        # If the snapshot file does not yet exist, then write it out now.
        directory = File.dirname(snapshot)
        FileUtils.mkdir_p(directory) unless File.directory?(directory)

        File.write(snapshot, printed)
        warn("Created snapshot at #{snapshot}.")
      end
    end
  end
end
