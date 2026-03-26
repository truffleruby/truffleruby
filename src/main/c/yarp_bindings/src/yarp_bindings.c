#include <stdlib.h>
#include <string.h>

#include "prism.h"
#include "org_ruby_lang_prism_Parser.h"

JNIEXPORT jbyteArray JNICALL Java_org_ruby_1lang_prism_Parser_parseAndSerialize(JNIEnv *env, jclass clazz, jbyteArray source, jbyteArray options) {
  jsize size = (*env)->GetArrayLength(env, source);
  // Null-terminate for safety, as parsers are prone to read further than the end
  jbyte *bytes = malloc(size + 4);
  (*env)->GetByteArrayRegion(env, source, 0, size, bytes);
  memset(bytes + size, 0, 4);

  // get options bytes
  jbyte *options_bytes;
  if (options) {
      options_bytes = (*env)->GetByteArrayElements(env, options, NULL);
  } else {
      options_bytes = NULL;
  }

  pm_buffer_t *buffer = pm_buffer_new();

  pm_serialize_parse(buffer, (uint8_t *) bytes, size, (char *) options_bytes);

  free(bytes);
  if (options) {
      (*env)->ReleaseByteArrayElements(env, options, options_bytes, JNI_ABORT);
  }

  char* buffer_value = pm_buffer_value(buffer);
  size_t buffer_length = pm_buffer_length(buffer);

  jbyteArray serialized = (*env)->NewByteArray(env, buffer_length);
  (*env)->SetByteArrayRegion(env, serialized, 0, buffer_length, (jbyte *) buffer_value);

  pm_buffer_free(buffer);

  return serialized;
}
