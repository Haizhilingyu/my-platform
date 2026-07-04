#ifndef PLATFORM_JSON_H
#define PLATFORM_JSON_H

#include <stddef.h>

enum pjson_type {
  PJ_NULL = 0,
  PJ_BOOL,
  PJ_NUMBER,
  PJ_STRING,
  PJ_ARRAY,
  PJ_OBJECT,
};

typedef struct pjson {
  enum pjson_type type;
  union {
    int boolean;
    double number;
    char *string;
    struct {
      struct pjson **items;
      size_t count;
    } array;
    struct {
      char **keys;
      struct pjson **values;
      size_t count;
    } object;
  } v;
} pjson;

pjson *pjson_parse(const char *text, char *errbuf, size_t errlen);
void pjson_free(pjson *j);

const char *pjson_string(const pjson *j);
double pjson_number(const pjson *j);
const pjson *pjson_object_get(const pjson *obj, const char *key);
const pjson *pjson_array_get(const pjson *arr, size_t index);
size_t pjson_array_len(const pjson *arr);

char *pjson_string_escape(const char *s);

#endif
