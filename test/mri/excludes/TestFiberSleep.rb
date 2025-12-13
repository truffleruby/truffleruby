exclude :test_broken_sleep, "[RuntimeError] exception expected, not #<NoMethodError: undefined method `scheduler' for class Fiber>."
exclude :test_sleep, "NoMethodError: undefined method `set_scheduler' for class Fiber"
exclude :test_sleep_returns_seconds_slept, "NoMethodError: undefined method `set_scheduler' for class Fiber"
