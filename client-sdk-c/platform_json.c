#include "platform_json.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
  const char *start;
  const char *p;
  const char *end;
  char *errbuf;
  size_t errlen;
} parser;

static void set_error(parser *ps, const char *msg) {
  if (ps->errbuf && ps->errlen > 0) {
    snprintf(ps->errbuf, ps->errlen, "%s at offset %ld", msg, (long)(ps->p - ps->start));
  }
}

static void skip_ws(parser *ps) {
  while (ps->p < ps->end && (*ps->p == ' ' || *ps->p == '\t' || *ps->p == '\n' || *ps->p == '\r')) {
    ps->p++;
  }
}

static pjson *parse_value(parser *ps);

static char *parse_string_raw(parser *ps) {
  if (ps->p >= ps->end || *ps->p != '"') {
    set_error(ps, "expected string");
    return NULL;
  }
  ps->p++;
  size_t cap = 16;
  size_t len = 0;
  char *out = malloc(cap);
  if (!out) {
    return NULL;
  }
  while (ps->p < ps->end && *ps->p != '"') {
    char c = *ps->p++;
    if (c == '\\' && ps->p < ps->end) {
      char esc = *ps->p++;
      switch (esc) {
        case '"': c = '"'; break;
        case '\\': c = '\\'; break;
        case '/': c = '/'; break;
        case 'b': c = '\b'; break;
        case 'f': c = '\f'; break;
        case 'n': c = '\n'; break;
        case 'r': c = '\r'; break;
        case 't': c = '\t'; break;
        case 'u': {
          if (ps->p + 4 > ps->end) {
            free(out);
            set_error(ps, "truncated \\u escape");
            return NULL;
          }
          char hex[5] = {0};
          memcpy(hex, ps->p, 4);
          ps->p += 4;
          unsigned int cp = (unsigned int)strtoul(hex, NULL, 16);
          if (cp < 0x80) {
            c = (char)cp;
          } else if (cp < 0x800) {
            if (len + 2 >= cap) {
              cap *= 2;
              char *tmp = realloc(out, cap);
              if (!tmp) { free(out); return NULL; }
              out = tmp;
            }
            out[len++] = (char)(0xC0 | (cp >> 6));
            out[len++] = (char)(0x80 | (cp & 0x3F));
            continue;
          } else {
            if (len + 3 >= cap) {
              cap *= 2;
              char *tmp = realloc(out, cap);
              if (!tmp) { free(out); return NULL; }
              out = tmp;
            }
            out[len++] = (char)(0xE0 | (cp >> 12));
            out[len++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            out[len++] = (char)(0x80 | (cp & 0x3F));
            continue;
          }
          break;
        }
        default:
          free(out);
          set_error(ps, "bad escape");
          return NULL;
      }
    }
    if (len + 1 >= cap) {
      cap *= 2;
      char *tmp = realloc(out, cap);
      if (!tmp) { free(out); return NULL; }
      out = tmp;
    }
    out[len++] = c;
  }
  if (ps->p >= ps->end) {
    free(out);
    set_error(ps, "unterminated string");
    return NULL;
  }
  ps->p++;
  out[len] = '\0';
  return out;
}

static pjson *new_node(enum pjson_type t) {
  pjson *n = calloc(1, sizeof(pjson));
  if (n) n->type = t;
  return n;
}

static pjson *parse_string_value(parser *ps) {
  char *s = parse_string_raw(ps);
  if (!s) return NULL;
  pjson *n = new_node(PJ_STRING);
  if (!n) { free(s); return NULL; }
  n->v.string = s;
  return n;
}

static pjson *parse_number(parser *ps) {
  const char *start = ps->p;
  if (ps->p < ps->end && (*ps->p == '-' || *ps->p == '+')) ps->p++;
  while (ps->p < ps->end && (isdigit((unsigned char)*ps->p) || *ps->p == '.' || *ps->p == 'e' || *ps->p == 'E' || *ps->p == '+' || *ps->p == '-')) {
    ps->p++;
  }
  char buf[64];
  size_t n = (size_t)(ps->p - start);
  if (n >= sizeof(buf)) n = sizeof(buf) - 1;
  memcpy(buf, start, n);
  buf[n] = '\0';
  pjson *node = new_node(PJ_NUMBER);
  if (!node) return NULL;
  node->v.number = strtod(buf, NULL);
  return node;
}

static int match_literal(parser *ps, const char *lit) {
  size_t n = strlen(lit);
  if ((size_t)(ps->end - ps->p) < n || memcmp(ps->p, lit, n) != 0) {
    return 0;
  }
  ps->p += n;
  return 1;
}

static pjson *parse_array(parser *ps) {
  ps->p++;
  pjson *arr = new_node(PJ_ARRAY);
  if (!arr) return NULL;
  size_t cap = 4;
  arr->v.array.items = malloc(cap * sizeof(pjson *));
  if (!arr->v.array.items) { pjson_free(arr); return NULL; }
  skip_ws(ps);
  if (ps->p < ps->end && *ps->p == ']') { ps->p++; return arr; }
  for (;;) {
    skip_ws(ps);
    pjson *item = parse_value(ps);
    if (!item) { pjson_free(arr); return NULL; }
    if (arr->v.array.count >= cap) {
      cap *= 2;
      pjson **tmp = realloc(arr->v.array.items, cap * sizeof(pjson *));
      if (!tmp) { pjson_free(item); pjson_free(arr); return NULL; }
      arr->v.array.items = tmp;
    }
    arr->v.array.items[arr->v.array.count++] = item;
    skip_ws(ps);
    if (ps->p >= ps->end) { pjson_free(arr); set_error(ps, "unterminated array"); return NULL; }
    if (*ps->p == ',') { ps->p++; continue; }
    if (*ps->p == ']') { ps->p++; break; }
    pjson_free(arr);
    set_error(ps, "expected ',' or ']'");
    return NULL;
  }
  return arr;
}

static pjson *parse_object(parser *ps) {
  ps->p++;
  pjson *obj = new_node(PJ_OBJECT);
  if (!obj) return NULL;
  size_t cap = 4;
  obj->v.object.keys = malloc(cap * sizeof(char *));
  obj->v.object.values = malloc(cap * sizeof(pjson *));
  if (!obj->v.object.keys || !obj->v.object.values) { pjson_free(obj); return NULL; }
  skip_ws(ps);
  if (ps->p < ps->end && *ps->p == '}') { ps->p++; return obj; }
  for (;;) {
    skip_ws(ps);
    char *key = parse_string_raw(ps);
    if (!key) { pjson_free(obj); return NULL; }
    skip_ws(ps);
    if (ps->p >= ps->end || *ps->p != ':') {
      free(key);
      pjson_free(obj);
      set_error(ps, "expected ':'");
      return NULL;
    }
    ps->p++;
    skip_ws(ps);
    pjson *val = parse_value(ps);
    if (!val) { free(key); pjson_free(obj); return NULL; }
    if (obj->v.object.count >= cap) {
      cap *= 2;
      char **tk = realloc(obj->v.object.keys, cap * sizeof(char *));
      pjson **tv = realloc(obj->v.object.values, cap * sizeof(pjson *));
      if (!tk || !tv) {
        free(key);
        pjson_free(val);
        if (tk) obj->v.object.keys = tk;
        if (tv) obj->v.object.values = tv;
        pjson_free(obj);
        return NULL;
      }
      obj->v.object.keys = tk;
      obj->v.object.values = tv;
    }
    obj->v.object.keys[obj->v.object.count] = key;
    obj->v.object.values[obj->v.object.count] = val;
    obj->v.object.count++;
    skip_ws(ps);
    if (ps->p >= ps->end) { pjson_free(obj); set_error(ps, "unterminated object"); return NULL; }
    if (*ps->p == ',') { ps->p++; continue; }
    if (*ps->p == '}') { ps->p++; break; }
    pjson_free(obj);
    set_error(ps, "expected ',' or '}'");
    return NULL;
  }
  return obj;
}

static pjson *parse_value(parser *ps) {
  skip_ws(ps);
  if (ps->p >= ps->end) {
    set_error(ps, "unexpected end");
    return NULL;
  }
  char c = *ps->p;
  if (c == '{') return parse_object(ps);
  if (c == '[') return parse_array(ps);
  if (c == '"') return parse_string_value(ps);
  if (c == '-' || c == '+' || isdigit((unsigned char)c)) return parse_number(ps);
  if (match_literal(ps, "true")) {
    pjson *n = new_node(PJ_BOOL);
    if (n) n->v.boolean = 1;
    return n;
  }
  if (match_literal(ps, "false")) {
    pjson *n = new_node(PJ_BOOL);
    if (n) n->v.boolean = 0;
    return n;
  }
  if (match_literal(ps, "null")) {
    return new_node(PJ_NULL);
  }
  set_error(ps, "unexpected token");
  return NULL;
}

pjson *pjson_parse(const char *text, char *errbuf, size_t errlen) {
  if (errbuf && errlen > 0) errbuf[0] = '\0';
  if (!text) {
    if (errbuf && errlen > 0) snprintf(errbuf, errlen, "null input");
    return NULL;
  }
  parser ps;
  ps.start = text;
  ps.p = text;
  ps.end = text + strlen(text);
  ps.errbuf = errbuf;
  ps.errlen = errlen;
  pjson *root = parse_value(&ps);
  if (!root) {
    return NULL;
  }
  skip_ws(&ps);
  if (errbuf && errlen > 0 && ps.p != ps.end) {
    snprintf(errbuf, errlen, "trailing data at offset %ld", (long)(ps.p - text));
  }
  return root;
}

void pjson_free(pjson *j) {
  if (!j) return;
  switch (j->type) {
    case PJ_STRING:
      free(j->v.string);
      break;
    case PJ_ARRAY:
      for (size_t i = 0; i < j->v.array.count; i++) {
        pjson_free(j->v.array.items[i]);
      }
      free(j->v.array.items);
      break;
    case PJ_OBJECT:
      for (size_t i = 0; i < j->v.object.count; i++) {
        free(j->v.object.keys[i]);
        pjson_free(j->v.object.values[i]);
      }
      free(j->v.object.keys);
      free(j->v.object.values);
      break;
    default:
      break;
  }
  free(j);
}

const char *pjson_string(const pjson *j) {
  if (!j || j->type != PJ_STRING) return NULL;
  return j->v.string;
}

double pjson_number(const pjson *j) {
  if (!j || j->type != PJ_NUMBER) return 0.0;
  return j->v.number;
}

const pjson *pjson_object_get(const pjson *obj, const char *key) {
  if (!obj || obj->type != PJ_OBJECT || !key) return NULL;
  for (size_t i = 0; i < obj->v.object.count; i++) {
    if (strcmp(obj->v.object.keys[i], key) == 0) {
      return obj->v.object.values[i];
    }
  }
  return NULL;
}

size_t pjson_array_len(const pjson *arr) {
  if (!arr || arr->type != PJ_ARRAY) return 0;
  return arr->v.array.count;
}

const pjson *pjson_array_get(const pjson *arr, size_t index) {
  if (!arr || arr->type != PJ_ARRAY || index >= arr->v.array.count) return NULL;
  return arr->v.array.items[index];
}

char *pjson_string_escape(const char *s) {
  if (!s) return NULL;
  size_t cap = strlen(s) * 6 + 1;
  char *out = malloc(cap);
  if (!out) return NULL;
  size_t len = 0;
  for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
    unsigned char c = *p;
    if (c == '"' || c == '\\') {
      out[len++] = '\\';
      out[len++] = (char)c;
    } else if (c == '\b') { out[len++] = '\\'; out[len++] = 'b'; }
    else if (c == '\f') { out[len++] = '\\'; out[len++] = 'f'; }
    else if (c == '\n') { out[len++] = '\\'; out[len++] = 'n'; }
    else if (c == '\r') { out[len++] = '\\'; out[len++] = 'r'; }
    else if (c == '\t') { out[len++] = '\\'; out[len++] = 't'; }
    else if (c < 0x20) {
      len += (size_t)snprintf(out + len, cap - len, "\\u%04x", c);
    } else {
      out[len++] = (char)c;
    }
  }
  out[len] = '\0';
  return out;
}
