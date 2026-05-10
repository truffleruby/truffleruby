# frozen_string_literal: true

module RBS
  module Collection
    module Sources
      module Base
        def dependencies_of(name, version)
          manifest = manifest_of(name, version) or return
          manifest['dependencies']
        end
      end
    end
  end
end
