#include <ruby.h>
#include <stdlib.h>

VALUE baz(VALUE bob) {
  return rb_funcall(bob, rb_intern("call"), 0);
}

VALUE foo(VALUE self, VALUE bar) {
  return rb_funcall(bar, rb_intern("bar"), 1, rb_proc_new(baz, Qnil));
}

VALUE ruby_callback;

static int compare_function(void *a_ptr, void *b_ptr) {
  int a = *((int*) a_ptr);
  int b = *((int*) b_ptr);
  VALUE result = rb_funcall(ruby_callback, rb_intern("call"), 2, INT2FIX(a), INT2FIX(b));
  return NUM2INT(result);
}

VALUE call_qsort(VALUE mod, VALUE callback) {
  int array[] = {1, 3, 4, 2};

  ruby_callback = callback;
  qsort(array, 4, sizeof(int), compare_function);

  VALUE ary = rb_ary_new();
  for (int i = 0; i < 4; i++) {
    rb_ary_push(ary, INT2FIX(array[i]));
  }
  return ary;
}

// From the nativetestlib
int test_native_callback(int (*callback)(void));

static int nested_callback(void) {
  VALUE result = rb_funcall(ruby_callback, rb_intern("call"), 0);
  return NUM2INT(result);
}

VALUE native_callback(VALUE mod, VALUE callback) {
  ruby_callback = callback;
  return INT2FIX(test_native_callback(nested_callback));
}

void Init_backtraces() {
  VALUE module = rb_define_module("Backtraces");
  rb_define_module_function(module, "foo", &foo, 1);
  rb_define_module_function(module, "qsort", call_qsort, 1);
  rb_define_module_function(module, "native_callback", native_callback, 1);
}
