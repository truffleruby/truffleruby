#include "rbs_extension.h"

#define RBS_LOC_REQUIRED_P(loc, i) ((loc)->children->required_p & (1 << (i)))
#define RBS_LOC_OPTIONAL_P(loc, i) (!RBS_LOC_REQUIRED_P((loc), (i)))
#define RBS_LOC_CHILDREN_SIZE(cap) (sizeof(rbs_loc_children) + sizeof(rbs_loc_entry) * ((cap) - 1))
#define NULL_LOC_RANGE_P(rg) ((rg).start == -1)

rbs_loc_range RBS_LOC_NULL_RANGE = { -1, -1 };
VALUE RBS_Location;

position rbs_loc_position(int char_pos) {
  position pos = { 0, char_pos, -1, -1 };
  return pos;
}

position rbs_loc_position3(int char_pos, int line, int column) {
  position pos = { 0, char_pos, line, column };
  return pos;
}

rbs_loc_range rbs_new_loc_range(range rg) {
  rbs_loc_range r = { rg.start.char_pos, rg.end.char_pos };
  return r;
}

static void check_children_max(unsigned short n) {
  size_t max = sizeof(rbs_loc_entry_bitmap) * 8;
  if (n > max) {
    rb_raise(rb_eRuntimeError, "Too many children added to location: %d", n);
  }
}

void rbs_loc_alloc_children(rbs_loc *loc, unsigned short cap) {
  check_children_max(cap);

  size_t s = RBS_LOC_CHILDREN_SIZE(cap);
  loc->children = malloc(s);

  *loc->children = (rbs_loc_children) {
    .len = 0,
    .required_p = 0,
    .cap = cap,
    .entries = {{ 0 }},
  };
}

static void check_children_cap(rbs_loc *loc) {
  if (loc->children == NULL) {
    rbs_loc_alloc_children(loc, 1);
  } else {
    if (loc->children->len == loc->children->cap) {
      check_children_max(loc->children->cap + 1);
      size_t s = RBS_LOC_CHILDREN_SIZE(++loc->children->cap);
      loc->children = realloc(loc->children, s);
    }
  }
}

void rbs_loc_add_required_child(rbs_loc *loc, ID name, range r) {
  rbs_loc_add_optional_child(loc, name, r);

  unsigned short last_index = loc->children->len - 1;
  loc->children->required_p |= 1 << last_index;
}

void rbs_loc_add_optional_child(rbs_loc *loc, ID name, range r) {
  check_children_cap(loc);

  unsigned short i = loc->children->len++;
  loc->children->entries[i] = (rbs_loc_entry) {
    .name = name,
    .rg = rbs_new_loc_range(r),
  };
}

void rbs_loc_init(rbs_loc *loc, VALUE buffer, rbs_loc_range rg) {
  *loc = (rbs_loc) {
    .buffer = buffer,
    .rg = rg,
    .children = NULL,
  };
}

void rbs_loc_free(rbs_loc *loc) {
  free(loc->children);
  ruby_xfree(loc);
}

static void rbs_loc_mark(void *ptr)
{
  rbs_loc *loc = ptr;
  rb_gc_mark(loc->buffer);
}

static size_t rbs_loc_memsize(const void *ptr) {
  const rbs_loc *loc = ptr;
  if (loc->children == NULL) {
    return sizeof(rbs_loc);
  } else {
    return sizeof(rbs_loc) + RBS_LOC_CHILDREN_SIZE(loc->children->cap);
  }
}

static rb_data_type_t location_type = {
  "RBS::Location",
  {rbs_loc_mark, (RUBY_DATA_FUNC)rbs_loc_free, rbs_loc_memsize},
  0, 0, RUBY_TYPED_FREE_IMMEDIATELY
};

static VALUE location_s_allocate(VALUE klass) {
  rbs_loc *loc;
  VALUE obj = TypedData_Make_Struct(klass, rbs_loc, &location_type, loc);

  rbs_loc_init(loc, Qnil, RBS_LOC_NULL_RANGE);

  return obj;
}

rbs_loc *rbs_check_location(VALUE obj) {
  return rb_check_typeddata(obj, &location_type);
}

static VALUE location_initialize(VALUE self, VALUE buffer, VALUE start_pos, VALUE end_pos) {
  rbs_loc *loc = rbs_check_location(self);

  int start = FIX2INT(start_pos);
  int end = FIX2INT(end_pos);

  *loc = (rbs_loc) {
    .buffer = buffer,
    .rg = (rbs_loc_range) { start, end },
    .children = NULL,
  };

  return Qnil;
}

static VALUE location_initialize_copy(VALUE self, VALUE other) {
  rbs_loc *self_loc = rbs_check_location(self);
  rbs_loc *other_loc = rbs_check_location(other);

  *self_loc = (rbs_loc) {
    .buffer = other_loc->buffer,
    .rg = other_loc->rg,
    .children = NULL,
  };

  if (other_loc->children != NULL) {
    rbs_loc_alloc_children(self_loc, other_loc->children->cap);
    memcpy(self_loc->children, other_loc->children, RBS_LOC_CHILDREN_SIZE(other_loc->children->cap));
  }

  return Qnil;
}

static VALUE location_buffer(VALUE self) {
  rbs_loc *loc = rbs_check_location(self);
  return loc->buffer;
}

static VALUE location_start_pos(VALUE self) {
  rbs_loc *loc = rbs_check_location(self);
  return INT2FIX(loc->rg.start);
}

static VALUE location_end_pos(VALUE self) {
  rbs_loc *loc = rbs_check_location(self);
  return INT2FIX(loc->rg.end);
}

static VALUE location_add_required_child(VALUE self, VALUE name, VALUE start, VALUE end) {
  rbs_loc *loc = rbs_check_location(self);

  range rg;
  rg.start = rbs_loc_position(FIX2INT(start));
  rg.end = rbs_loc_position(FIX2INT(end));

  rbs_loc_add_required_child(loc, SYM2ID(name), rg);

  return Qnil;
}

static VALUE location_add_optional_child(VALUE self, VALUE name, VALUE start, VALUE end) {
  rbs_loc *loc = rbs_check_location(self);

  range rg;
  rg.start = rbs_loc_position(FIX2INT(start));
  rg.end = rbs_loc_position(FIX2INT(end));

  rbs_loc_add_optional_child(loc, SYM2ID(name), rg);

  return Qnil;
}

static VALUE location_add_optional_no_child(VALUE self, VALUE name) {
  rbs_loc *loc = rbs_check_location(self);

  rbs_loc_add_optional_child(loc, SYM2ID(name), NULL_RANGE);

  return Qnil;
}

VALUE rbs_new_location(VALUE buffer, range rg) {
  rbs_loc *loc;
  VALUE obj = TypedData_Make_Struct(RBS_Location, rbs_loc, &location_type, loc);

  rbs_loc_init(loc, buffer, rbs_new_loc_range(rg));

  return obj;
}

static VALUE rbs_new_location_from_loc_range(VALUE buffer, rbs_loc_range rg) {
  rbs_loc *loc;
  VALUE obj = TypedData_Make_Struct(RBS_Location, rbs_loc, &location_type, loc);

  rbs_loc_init(loc, buffer, rg);

  return obj;
}

static VALUE location_aref(VALUE self, VALUE name) {
  rbs_loc *loc = rbs_check_location(self);

  ID id = SYM2ID(name);

  if (loc->children != NULL) {
    for (unsigned short i = 0; i < loc->children->len; i++) {
      if (loc->children->entries[i].name == id) {
        rbs_loc_range result = loc->children->entries[i].rg;

        if (RBS_LOC_OPTIONAL_P(loc, i) && NULL_LOC_RANGE_P(result)) {
          return Qnil;
        } else {
          return rbs_new_location_from_loc_range(loc->buffer, result);
        }
      }
    }
  }

  VALUE string = rb_funcall(name, rb_intern("to_s"), 0);
  rb_raise(rb_eRuntimeError, "Unknown child name given: %s", RSTRING_PTR(string));
}

static VALUE location_optional_keys(VALUE self) {
  VALUE keys = rb_ary_new();

  rbs_loc *loc = rbs_check_location(self);
  rbs_loc_children *children = loc->children;
  if (children == NULL) {
    return keys;
  }

  for (unsigned short i = 0; i < children->len; i++) {
    if (RBS_LOC_OPTIONAL_P(loc, i)) {
      rb_ary_push(keys, ID2SYM(children->entries[i].name));

    }
  }

  return keys;
}

static VALUE location_required_keys(VALUE self) {
  VALUE keys = rb_ary_new();

  rbs_loc *loc = rbs_check_location(self);
  rbs_loc_children *children = loc->children;
  if (children == NULL) {
    return keys;
  }

  for (unsigned short i = 0; i < children->len; i++) {
    if (RBS_LOC_REQUIRED_P(loc, i)) {
      rb_ary_push(keys, ID2SYM(children->entries[i].name));
    }
  }

  return keys;
}

VALUE rbs_location_pp(VALUE buffer, const position *start_pos, const position *end_pos) {
  range rg = { *start_pos, *end_pos };
  rg.start = *start_pos;
  rg.end = *end_pos;

  return rbs_new_location(buffer, rg);
}

void rbs__init_location(void) {
  RBS_Location = rb_define_class_under(RBS, "Location", rb_cObject);
  rb_define_alloc_func(RBS_Location, location_s_allocate);
  rb_define_private_method(RBS_Location, "initialize", location_initialize, 3);
  rb_define_private_method(RBS_Location, "initialize_copy", location_initialize_copy, 1);
  rb_define_method(RBS_Location, "buffer", location_buffer, 0);
  rb_define_method(RBS_Location, "start_pos", location_start_pos, 0);
  rb_define_method(RBS_Location, "end_pos", location_end_pos, 0);
  rb_define_method(RBS_Location, "_add_required_child", location_add_required_child, 3);
  rb_define_method(RBS_Location, "_add_optional_child", location_add_optional_child, 3);
  rb_define_method(RBS_Location, "_add_optional_no_child", location_add_optional_no_child, 1);
  rb_define_method(RBS_Location, "_optional_keys", location_optional_keys, 0);
  rb_define_method(RBS_Location, "_required_keys", location_required_keys, 0);
  rb_define_method(RBS_Location, "[]", location_aref, 1);
}
