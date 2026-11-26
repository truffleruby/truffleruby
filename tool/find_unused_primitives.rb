# These primitives are unused, but kept around as debugging helpers.
PRIMITIVES_FOR_DEBUGGING = %w[
  frame_declaration_context_to_string
  java_breakpoint
  vm_java_version
]

primitive_names = Truffle::Debug.primitive_names - PRIMITIVES_FOR_DEBUGGING
puts "#{primitive_names.size} primitives"

unused_primitives = primitive_names.select do |primitive|
  print '.'
  !system("git grep --quiet -F 'Primitive.#{primitive}'")
end
puts

unless unused_primitives.empty?
  abort "Unused primitives:\n#{unused_primitives.sort.join("\n")}"
end
