module ModuleSpecs::Autoload
  AssignThenRaise = :assign_then_raise
  ScratchPad << defined?(AssignThenRaise)
  raise "raise after assigning"
end
