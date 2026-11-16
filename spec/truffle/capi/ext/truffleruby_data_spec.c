/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include "ruby.h"
#include "rubyspec.h"

#ifdef __cplusplus
extern "C" {
#endif

/* A native tree owned by the Ruby object of its root node, where every node caches its Ruby object,
 * like the markly gem (cmark nodes). Freeing the root frees the whole tree and clears the data pointer
 * of the Ruby object of every node: the root's own Ruby object, which is dead and being freed,
 * and the Ruby objects of children, which may already be dead but not yet freed. */

#define TREE_CHILDREN 10

typedef struct tree_node {
  struct tree_node *parent;
  struct tree_node *children[TREE_CHILDREN];
  VALUE ruby_object; /* 0 if none */
} tree_node;

static volatile long freed_roots = 0;
static volatile long freed_children = 0;

static void tree_node_free(void *data) {
  tree_node *node = data;
  if (node->parent == NULL) {
    /* The Ruby object of the root is dead and currently being freed, like on CRuby where its slot is still accessible */
    RTYPEDDATA_DATA(node->ruby_object) = NULL;
    node->ruby_object = 0;
    for (int i = 0; i < TREE_CHILDREN; i++) {
      tree_node *child = node->children[i];
      if (child->ruby_object) {
        /* The Ruby object of the child may be dead and not yet freed, like on CRuby where its slot is still accessible */
        RTYPEDDATA_DATA(child->ruby_object) = NULL;
        child->ruby_object = 0;
        freed_children++;
      }
      free(child);
    }
    free(node);
    freed_roots++;
  } else {
    /* The parent still owns the node, only forget the Ruby object */
    node->ruby_object = 0;
    freed_children++;
  }
}

static void tree_node_mark(void *data) {
  tree_node *node = data;
  if (node->parent) {
    rb_gc_mark(node->parent->ruby_object);
  } else {
    for (int i = 0; i < TREE_CHILDREN; i++) {
      VALUE child = node->children[i]->ruby_object;
      if (child) {
        rb_gc_mark(child);
      }
    }
  }
}

static const rb_data_type_t tree_node_type = {
  "CApiTruffleRubyDataSpecs::TreeNode",
  { tree_node_mark, tree_node_free, NULL, },
  NULL, NULL,
  RUBY_TYPED_FREE_IMMEDIATELY,
};

static VALUE tree_node_wrap(VALUE klass, tree_node *node) {
  if (node->ruby_object) {
    return node->ruby_object;
  }
  VALUE object = TypedData_Wrap_Struct(klass, &tree_node_type, node);
  node->ruby_object = object;
  return object;
}

static VALUE new_tree(VALUE self, VALUE klass) {
  tree_node *root = calloc(1, sizeof(tree_node));
  for (int i = 0; i < TREE_CHILDREN; i++) {
    tree_node *child = calloc(1, sizeof(tree_node));
    child->parent = root;
    root->children[i] = child;
  }
  return tree_node_wrap(klass, root);
}

static VALUE tree_node_children(VALUE self) {
  tree_node *node;
  TypedData_Get_Struct(self, tree_node, &tree_node_type, node);
  VALUE children = rb_ary_new();
  if (node->parent == NULL) {
    for (int i = 0; i < TREE_CHILDREN; i++) {
      rb_ary_push(children, tree_node_wrap(rb_obj_class(self), node->children[i]));
    }
  }
  return children;
}

static VALUE tree_freed_roots(VALUE self) {
  return LONG2NUM(freed_roots);
}

static VALUE tree_freed_children(VALUE self) {
  return LONG2NUM(freed_children);
}

void Init_truffleruby_data_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubyDataSpecs", rb_cObject);
  rb_define_const(cls, "TREE_CHILDREN", INT2NUM(TREE_CHILDREN));
  rb_define_method(cls, "new_tree", new_tree, 1);
  rb_define_method(cls, "freed_roots", tree_freed_roots, 0);
  rb_define_method(cls, "freed_children", tree_freed_children, 0);

  VALUE tree_node_class = rb_define_class_under(cls, "TreeNode", rb_cObject);
  rb_undef_alloc_func(tree_node_class);
  rb_define_method(tree_node_class, "children", tree_node_children, 0);
}

#ifdef __cplusplus
}
#endif
