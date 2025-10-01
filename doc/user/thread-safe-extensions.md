# Thread-Safe Extensions

Native extensions are by default considered thread-unsafe for maximum compatibility with CRuby and use the global extension lock (unless `--cexts-lock=false` is used).

Extensions can mark themselves as thread-safe either by using `rb_ext_ractor_safe()` or `rb_ext_thread_safe()` (the latter is TruffleRuby-specific).
Such extensions are then run by TruffleRuby without a global extension lock, i.e. in parallel.

Here is an example of an extension marking itself as Ractor-safe.
Such an extension must then satisfy [the conditions for Ractor-safe extensions](https://github.com/ruby/ruby/blob/master/doc/extension.rdoc#appendix-f-ractor-support-).
```c
void Init_my_extension(void) {
    #ifdef HAVE_RB_EXT_RACTOR_SAFE
    rb_ext_ractor_safe(true);
    #endif

    rb_define_method(myClass, "foo", foo_impl, 0); // The C function foo_impl can be called from multiple threads in parallel
}
```

Here is an example of an extension marking itself as thread-safe:
```c
void Init_my_extension(void) {
    #ifdef HAVE_RB_EXT_THREAD_SAFE
    rb_ext_thread_safe(true);
    #endif

    rb_define_method(myClass, "foo", foo_impl, 0); // The C function foo_impl can be called from multiple threads in parallel
}
```

It is possible to mark individual methods as Ractor/Thread-safe by using `rb_ext_ractor_safe/rb_ext_thread_safe(true/false)` around them (these functions actually set a Fiber-local flag):
```c
void Init_my_extension(void) {
    #ifdef HAVE_RB_EXT_RACTOR_SAFE
    rb_ext_ractor_safe(true);
    #endif
    rb_define_method(myClass, "ractor_safe_method", foo_impl, 0); // The C function foo_impl can be called from multiple threads in parallel
    // more Ractor-safe methods

    #ifdef HAVE_RB_EXT_RACTOR_SAFE
    rb_ext_ractor_safe(false);
    #endif
    rb_define_method(myClass, "ractor_unsafe_method", bar_impl, 0); // The C function bar_impl needs a global extension lock for correctness
    // more Ractor-unsafe methods
}
```

Other Ruby C API functions taking a C function like `rb_proc_new()` do not use the global extension lock if:
* Called inside the `Init_my_extension` and `rb_ext_ractor_safe(true)` / `rb_ext_thread_safe(true)` are used.
* Called outside the `Init_my_extension` and the calling function does not hold the global extension lock.

The conditions for an extension to be thread-safe are the following.
This is similar to [the conditions for Ractor-safe extensions](https://github.com/ruby/ruby/blob/master/doc/extension.rdoc#appendix-f-ractor-support-) but not all conditions are necessary.
1. The extension should make it clear in its documentation which objects are safe to share between threads and which are not.
   This already needs to be done on CRuby with the GVL, as threads run concurrently.
   It helps gem users to avoid sharing objects between threads incorrectly.
2. The extension's own code must be thread-safe, e.g. not mutate state shared between threads without synchronization.
   For example accesses to a `struct` shared between threads should typically be synchronized if it's not immutable.
3. If the extension calls native library functions which are not thread-safe it must ensure that function cannot be called from multiple threads at the same time, e.g. using a [lock](https://github.com/truffleruby/truffleruby/blob/fd8dc74a72d107f8e58feaf1be1cfbb2f31d2e85/lib/cext/include/ruby/thread_native.h).
4. Ruby C API functions/macros (like `rb_*()`) are generally thread-safe on TruffleRuby, because most of them end up calling some Ruby method.

These are the differences in comparison to Ractor-safe:
* It is allowed to share Ruby objects between multiple threads from an extension, because it is the same as sharing them with only Ruby code.
* There is no need to mark objects as Ractor-shareable.

Another way to look at this is to reason about the guarantees that a global extension lock provides:
* C functions or sections of C code which does __*not*__ use any Ruby C API functions/macros (like `rb_*()`) are executed sequentially, i.e. one after another.
* Calls to any Ruby C API function/macro have the possibility to trigger thread switching, and so for another part of the extension code to execute, while the current function is "suspended".
* Therefore functions given to `rb_define_method`, if they call Ruby C API functions/macros (very likely), do not really benefit from the global extension lock as thread switching can happen in the middle of them, and they already need to take care about other functions executing in between.
