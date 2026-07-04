#include "platform_client.h"
#include "platform_json.h"

#include <curl/curl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define DEFAULT_TIMEOUT_MS 30000L
#define ERRBUF_SIZE 256

typedef struct {
  char *data;
  size_t size;
} bytebuf;

struct platform_client {
  char *client_id;
  char *client_secret;
  char *issuer_url;
  long timeout_ms;
  char last_error[ERRBUF_SIZE];
};

struct platform_token {
  char *access_token;
  char *token_type;
  char *refresh_token;
  char *scope;
  long expires_in;
};

typedef struct {
  char *type;
  long long id;
} recipient_entry;

struct platform_publish_request {
  char *title;
  char *content;
  char *level;
  char *business_type;
  char *expire_time;
  recipient_entry *recipients;
  size_t recipient_count;
};

struct platform_publish_result {
  long http_status;
  int code;
  int has_code;
  long long message_id;
  int recipient_count;
  int has_data;
  char *message;
  char *raw_body;
};

struct platform_token_manager {
  platform_client *client;
  char *scopes;
  platform_token *token;
};

static void set_err(platform_client *c, const char *msg) {
  if (!c) return;
  snprintf(c->last_error, ERRBUF_SIZE, "%s", msg ? msg : "");
}

static char *str_dup(const char *s) {
  if (!s) return NULL;
  size_t n = strlen(s) + 1;
  char *out = malloc(n);
  if (out) memcpy(out, s, n);
  return out;
}

static char *str_join(const char *a, const char *b) {
  size_t na = strlen(a);
  size_t nb = strlen(b);
  char *out = malloc(na + nb + 1);
  if (!out) return NULL;
  memcpy(out, a, na);
  memcpy(out + na, b, nb);
  out[na + nb] = '\0';
  return out;
}

static char *trim_trailing_slash(const char *url) {
  if (!url) return str_dup("");
  size_t n = strlen(url);
  while (n > 0 && (url[n - 1] == '/')) n--;
  char *out = malloc(n + 1);
  if (!out) return NULL;
  memcpy(out, url, n);
  out[n] = '\0';
  return out;
}

static char *url_encode(CURL *curl, const char *s) {
  if (!s) return str_dup("");
  char *enc = curl_easy_escape(curl, s, 0);
  if (!enc) return NULL;
  char *out = str_dup(enc);
  curl_free(enc);
  return out;
}

static size_t write_cb(char *ptr, size_t size, size_t nmemb, void *userdata) {
  bytebuf *buf = (bytebuf *)userdata;
  size_t total = size * nmemb;
  char *tmp = realloc(buf->data, buf->size + total + 1);
  if (!tmp) return 0;
  buf->data = tmp;
  memcpy(buf->data + buf->size, ptr, total);
  buf->size += total;
  buf->data[buf->size] = '\0';
  return total;
}

static int http_post(platform_client *c,
                     const char *url,
                     const char *body,
                     const char *content_type,
                     const char *basic_user,
                     const char *basic_pass,
                     const char *bearer_token,
                     bytebuf *out,
                     long *http_status) {
  CURL *curl = curl_easy_init();
  if (!curl) {
    set_err(c, "curl_easy_init failed");
    return -1;
  }

  out->data = malloc(1);
  out->size = 0;
  if (out->data) out->data[0] = '\0';

  struct curl_slist *headers = NULL;
  if (content_type) {
    char h[128];
    snprintf(h, sizeof(h), "Content-Type: %s", content_type);
    headers = curl_slist_append(headers, h);
  }
  headers = curl_slist_append(headers, "Accept: application/json");
  if (bearer_token) {
    char h[1024];
    snprintf(h, sizeof(h), "Authorization: Bearer %s", bearer_token);
    headers = curl_slist_append(headers, h);
  }

  curl_easy_setopt(curl, CURLOPT_URL, url);
  curl_easy_setopt(curl, CURLOPT_POST, 1L);
  if (body) {
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body);
  } else {
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, "");
  }
  if (headers) curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
  if (basic_user && basic_pass) {
    curl_easy_setopt(curl, CURLOPT_USERNAME, basic_user);
    curl_easy_setopt(curl, CURLOPT_PASSWORD, basic_pass);
    curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
  }
  curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
  curl_easy_setopt(curl, CURLOPT_WRITEDATA, out);
  curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, c ? c->timeout_ms : DEFAULT_TIMEOUT_MS);
  curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, c ? c->timeout_ms : DEFAULT_TIMEOUT_MS);
  curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
  curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L);

  CURLcode rc = curl_easy_perform(curl);
  if (rc != CURLE_OK) {
    char msg[ERRBUF_SIZE];
    snprintf(msg, sizeof(msg), "HTTP request failed: %s", curl_easy_strerror(rc));
    set_err(c, msg);
    if (headers) curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    free(out->data);
    out->data = NULL;
    out->size = 0;
    return -1;
  }
  long status = 0;
  curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
  *http_status = status;

  if (headers) curl_slist_free_all(headers);
  curl_easy_cleanup(curl);
  return 0;
}

platform_client *platform_client_create(const char *client_id,
                                        const char *client_secret,
                                        const char *issuer_url) {
  if (!client_id || !client_secret || !issuer_url) {
    return NULL;
  }
  platform_client *c = calloc(1, sizeof(platform_client));
  if (!c) return NULL;
  c->client_id = str_dup(client_id);
  c->client_secret = str_dup(client_secret);
  c->issuer_url = trim_trailing_slash(issuer_url);
  c->timeout_ms = DEFAULT_TIMEOUT_MS;
  if (!c->client_id || !c->client_secret || !c->issuer_url) {
    platform_client_free(c);
    return NULL;
  }
  return c;
}

void platform_client_free(platform_client *client) {
  if (!client) return;
  free(client->client_id);
  free(client->client_secret);
  free(client->issuer_url);
  free(client);
}

void platform_client_set_timeout_ms(platform_client *client, long timeout_ms) {
  if (client && timeout_ms > 0) client->timeout_ms = timeout_ms;
}

const char *platform_client_last_error(const platform_client *client) {
  return client ? client->last_error : NULL;
}

static platform_token *parse_token_response(platform_client *c, const char *body) {
  char err[ERRBUF_SIZE] = {0};
  pjson *root = pjson_parse(body, err, sizeof(err));
  if (!root) {
    set_err(c, err[0] ? err : "failed to parse token response");
    return NULL;
  }
  const char *access = pjson_string(pjson_object_get(root, "access_token"));
  if (!access) {
    const char *errdesc = pjson_string(pjson_object_get(root, "error_description"));
    if (!errdesc) errdesc = pjson_string(pjson_object_get(root, "error"));
    set_err(c, errdesc ? errdesc : "token response missing access_token");
    pjson_free(root);
    return NULL;
  }
  platform_token *t = calloc(1, sizeof(platform_token));
  if (!t) { pjson_free(root); return NULL; }
  t->access_token = str_dup(access);
  const char *tt = pjson_string(pjson_object_get(root, "token_type"));
  t->token_type = str_dup(tt ? tt : "Bearer");
  const char *rt = pjson_string(pjson_object_get(root, "refresh_token"));
  t->refresh_token = str_dup(rt);
  const char *sc = pjson_string(pjson_object_get(root, "scope"));
  t->scope = str_dup(sc);
  const pjson *exp = pjson_object_get(root, "expires_in");
  t->expires_in = exp ? (long)pjson_number(exp) : 0;
  pjson_free(root);
  return t;
}

static platform_token *do_token_request(platform_client *c,
                                        const char *grant_type,
                                        const char *code,
                                        const char *redirect_uri,
                                        const char *refresh_token_value,
                                        const char *scopes) {
  if (!c) return NULL;
  CURL *curl = curl_easy_init();
  if (!curl) {
    set_err(c, "curl init failed");
    return NULL;
  }

  char *enc_grant = url_encode(curl, grant_type);
  char *enc_code = code ? url_encode(curl, code) : NULL;
  char *enc_redirect = redirect_uri ? url_encode(curl, redirect_uri) : NULL;
  char *enc_refresh = refresh_token_value ? url_encode(curl, refresh_token_value) : NULL;
  char *enc_scopes = scopes ? url_encode(curl, scopes) : NULL;

  size_t cap = strlen(enc_grant) + 32;
  char *body = calloc(1, cap);
  snprintf(body, cap, "grant_type=%s", enc_grant);
  if (enc_code) {
    char *tmp = str_join(body, "&code=");
    free(body); body = str_join(tmp, enc_code); free(tmp);
  }
  if (enc_redirect) {
    char *tmp = str_join(body, "&redirect_uri=");
    free(body); body = str_join(tmp, enc_redirect); free(tmp);
  }
  if (enc_refresh) {
    char *tmp = str_join(body, "&refresh_token=");
    free(body); body = str_join(tmp, enc_refresh); free(tmp);
  }
  if (enc_scopes) {
    char *tmp = str_join(body, "&scope=");
    free(body); body = str_join(tmp, enc_scopes); free(tmp);
  }

  curl_free(enc_grant);
  curl_free(enc_code);
  curl_free(enc_redirect);
  curl_free(enc_refresh);
  curl_free(enc_scopes);

  size_t url_cap = strlen(c->issuer_url) + 32;
  char *url = malloc(url_cap);
  snprintf(url, url_cap, "%s/oauth2/token", c->issuer_url);

  bytebuf out = {0};
  long status = 0;
  int rc = http_post(c, url, body, "application/x-www-form-urlencoded",
                     c->client_id, c->client_secret, NULL, &out, &status);

  curl_easy_cleanup(curl);
  free(body);
  free(url);

  if (rc != 0) {
    free(out.data);
    return NULL;
  }
  platform_token *tok = parse_token_response(c, out.data ? out.data : "");
  free(out.data);
  if (!tok) {
    char msg[ERRBUF_SIZE];
    snprintf(msg, sizeof(msg), "token endpoint returned HTTP %ld", status);
    if (c->last_error[0] == '\0') set_err(c, msg);
    return NULL;
  }
  return tok;
}

platform_token *platform_client_credentials(platform_client *client, const char *scopes) {
  return do_token_request(client, "client_credentials", NULL, NULL, NULL, scopes);
}

platform_token *platform_exchange_code(platform_client *client,
                                       const char *code,
                                       const char *redirect_uri,
                                       const char *scopes) {
  if (!code || code[0] == '\0') {
    set_err(client, "code is required");
    return NULL;
  }
  return do_token_request(client, "authorization_code", code, redirect_uri, NULL, scopes);
}

platform_token *platform_refresh_token(platform_client *client,
                                       const char *refresh_token,
                                       const char *scopes) {
  if (!refresh_token || refresh_token[0] == '\0') {
    set_err(client, "refresh_token is required");
    return NULL;
  }
  return do_token_request(client, "refresh_token", NULL, NULL, refresh_token, scopes);
}

char *platform_authorization_url(platform_client *client,
                                 const char *redirect_uri,
                                 const char *state,
                                 const char *scopes) {
  if (!client || !redirect_uri) return NULL;
  CURL *curl = curl_easy_init();
  if (!curl) return NULL;
  char *enc_cid = url_encode(curl, client->client_id);
  char *enc_redirect = url_encode(curl, redirect_uri);
  char *enc_state = state ? url_encode(curl, state) : NULL;
  char *enc_scopes = scopes ? url_encode(curl, scopes) : NULL;

  size_t cap = strlen(client->issuer_url) + strlen(enc_cid) + strlen(enc_redirect) + 128;
  if (enc_state) cap += strlen(enc_state) + 8;
  if (enc_scopes) cap += strlen(enc_scopes) + 8;
  char *out = malloc(cap);
  if (!out) {
    curl_easy_cleanup(curl);
    return NULL;
  }
  int n = snprintf(out, cap, "%s/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s",
                   client->issuer_url, enc_cid, enc_redirect);
  if (enc_state) {
    n += snprintf(out + n, cap - (size_t)n, "&state=%s", enc_state);
  }
  if (enc_scopes) {
    n += snprintf(out + n, cap - (size_t)n, "&scope=%s", enc_scopes);
  }

  curl_free(enc_cid);
  curl_free(enc_redirect);
  curl_free(enc_state);
  curl_free(enc_scopes);
  curl_easy_cleanup(curl);
  return out;
}

const char *platform_token_access_token(const platform_token *token) {
  return token ? token->access_token : NULL;
}
const char *platform_token_refresh_token(const platform_token *token) {
  return token ? token->refresh_token : NULL;
}
const char *platform_token_token_type(const platform_token *token) {
  return token && token->token_type ? token->token_type : "Bearer";
}
long platform_token_expires_in(const platform_token *token) {
  return token ? token->expires_in : 0;
}
const char *platform_token_scope(const platform_token *token) {
  return token ? token->scope : NULL;
}

void platform_token_free(platform_token *token) {
  if (!token) return;
  free(token->access_token);
  free(token->token_type);
  free(token->refresh_token);
  free(token->scope);
  free(token);
}

platform_publish_request *platform_publish_request_create(void) {
  return calloc(1, sizeof(platform_publish_request));
}

void platform_publish_request_free(platform_publish_request *req) {
  if (!req) return;
  free(req->title);
  free(req->content);
  free(req->level);
  free(req->business_type);
  free(req->expire_time);
  for (size_t i = 0; i < req->recipient_count; i++) {
    free(req->recipients[i].type);
  }
  free(req->recipients);
  free(req);
}

static int set_field(char **field, const char *value) {
  if (!value) return -1;
  char *dup = str_dup(value);
  if (!dup) return -1;
  free(*field);
  *field = dup;
  return 0;
}

int platform_publish_request_set_title(platform_publish_request *req, const char *title) {
  return req ? set_field(&req->title, title) : -1;
}
int platform_publish_request_set_content(platform_publish_request *req, const char *content) {
  return req ? set_field(&req->content, content) : -1;
}
int platform_publish_request_set_level(platform_publish_request *req, const char *level) {
  return req ? set_field(&req->level, level) : -1;
}
int platform_publish_request_set_business_type(platform_publish_request *req, const char *business_type) {
  return req ? set_field(&req->business_type, business_type) : -1;
}
int platform_publish_request_set_expire_time(platform_publish_request *req, const char *expire_time) {
  return req ? set_field(&req->expire_time, expire_time) : -1;
}

int platform_publish_request_add_recipient(platform_publish_request *req, const char *type, long long id) {
  if (!req || !type) return -1;
  recipient_entry *tmp = realloc(req->recipients, (req->recipient_count + 1) * sizeof(recipient_entry));
  if (!tmp) return -1;
  req->recipients = tmp;
  req->recipients[req->recipient_count].type = str_dup(type);
  req->recipients[req->recipient_count].id = id;
  if (!req->recipients[req->recipient_count].type) return -1;
  req->recipient_count++;
  return 0;
}

platform_publish_request *platform_publish_request_single(const char *title,
                                                          const char *content,
                                                          const char *level,
                                                          const char *recipient_type,
                                                          long long recipient_id) {
  platform_publish_request *req = platform_publish_request_create();
  if (!req) return NULL;
  if (platform_publish_request_set_title(req, title) != 0 ||
      platform_publish_request_set_content(req, content) != 0 ||
      platform_publish_request_set_level(req, level) != 0 ||
      platform_publish_request_add_recipient(req, recipient_type, recipient_id) != 0) {
    platform_publish_request_free(req);
    return NULL;
  }
  return req;
}

static char *build_publish_body(const platform_publish_request *req) {
  char *esc_title = pjson_string_escape(req->title ? req->title : "");
  char *esc_content = pjson_string_escape(req->content ? req->content : "");
  char *esc_level = pjson_string_escape(req->level ? req->level : "");
  char *esc_business = req->business_type ? pjson_string_escape(req->business_type) : NULL;
  char *esc_expire = req->expire_time ? pjson_string_escape(req->expire_time) : NULL;
  if (!esc_title || !esc_content || !esc_level) {
    free(esc_title); free(esc_content); free(esc_level);
    free(esc_business); free(esc_expire);
    return NULL;
  }

  size_t cap = strlen(esc_title) + strlen(esc_content) + strlen(esc_level) + 128;
  if (esc_business) cap += strlen(esc_business) + 20;
  if (esc_expire) cap += strlen(esc_expire) + 20;
  for (size_t i = 0; i < req->recipient_count; i++) {
    cap += (req->recipients[i].type ? strlen(req->recipients[i].type) : 0) + 48;
  }
  char *body = malloc(cap);
  if (!body) {
    free(esc_title); free(esc_content); free(esc_level);
    free(esc_business); free(esc_expire);
    return NULL;
  }
  int n = snprintf(body, cap, "{\"title\":\"%s\",\"content\":\"%s\",\"level\":\"%s\"",
                   esc_title, esc_content, esc_level);
  if (esc_business) {
    n += snprintf(body + n, cap - (size_t)n, ",\"businessType\":\"%s\"", esc_business);
  }
  if (esc_expire) {
    n += snprintf(body + n, cap - (size_t)n, ",\"expireTime\":\"%s\"", esc_expire);
  }
  n += snprintf(body + n, cap - (size_t)n, ",\"recipients\":[");
  for (size_t i = 0; i < req->recipient_count; i++) {
    const char *t = req->recipients[i].type ? req->recipients[i].type : "";
    n += snprintf(body + n, cap - (size_t)n,
                  "%s{\"type\":\"%s\",\"id\":%lld}",
                  i == 0 ? "" : ",", t, (long long)req->recipients[i].id);
  }
  n += snprintf(body + n, cap - (size_t)n, "]}");

  free(esc_title); free(esc_content); free(esc_level);
  free(esc_business); free(esc_expire);
  return body;
}

static platform_publish_result *parse_publish_response(platform_client *c,
                                                      const char *body,
                                                      long http_status) {
  platform_publish_result *r = calloc(1, sizeof(platform_publish_result));
  if (!r) return NULL;
  r->http_status = http_status;
  r->code = -1;
  r->has_code = 0;
  r->raw_body = str_dup(body ? body : "");
  if (!body || body[0] == '\0') {
    return r;
  }
  char err[ERRBUF_SIZE] = {0};
  pjson *root = pjson_parse(body, err, sizeof(err));
  if (!root) {
    set_err(c, err[0] ? err : "publish response not JSON");
    return r;
  }
  const pjson *code_node = pjson_object_get(root, "code");
  if (code_node && code_node->type == PJ_NUMBER) {
    r->code = (int)pjson_number(code_node);
    r->has_code = 1;
  }
  const char *msg = pjson_string(pjson_object_get(root, "message"));
  if (msg) r->message = str_dup(msg);
  const pjson *data = pjson_object_get(root, "data");
  if (data && data->type == PJ_OBJECT) {
    const pjson *mid = pjson_object_get(data, "messageId");
    const pjson *rc = pjson_object_get(data, "recipientCount");
    if (mid && mid->type == PJ_NUMBER) {
      r->message_id = (long long)pjson_number(mid);
      r->has_data = 1;
    }
    if (rc && rc->type == PJ_NUMBER) {
      r->recipient_count = (int)pjson_number(rc);
    }
  }
  pjson_free(root);
  return r;
}

platform_publish_result *platform_publish_message(platform_client *client,
                                                  const char *access_token,
                                                  const platform_publish_request *req) {
  if (!client || !access_token || !req) {
    set_err(client, "invalid arguments to platform_publish_message");
    return NULL;
  }
  if (!req->title || !req->content || !req->level || req->recipient_count == 0) {
    set_err(client, "publish request missing required fields");
    return NULL;
  }
  char *body = build_publish_body(req);
  if (!body) {
    set_err(client, "failed to build publish body");
    return NULL;
  }
  size_t url_cap = strlen(client->issuer_url) + 32;
  char *url = malloc(url_cap);
  snprintf(url, url_cap, "%s/openapi/notify/publish", client->issuer_url);

  bytebuf out = {0};
  long status = 0;
  int rc = http_post(client, url, body, "application/json", NULL, NULL, access_token, &out, &status);
  free(body);
  free(url);
  if (rc != 0) {
    free(out.data);
    return NULL;
  }
  platform_publish_result *result = parse_publish_response(client, out.data ? out.data : "", status);
  free(out.data);
  return result;
}

long platform_publish_result_http_status(const platform_publish_result *result) {
  return result ? result->http_status : 0;
}
int platform_publish_result_code(const platform_publish_result *result) {
  return result ? result->code : -1;
}
const char *platform_publish_result_message(const platform_publish_result *result) {
  return result ? result->message : NULL;
}
long long platform_publish_result_message_id(const platform_publish_result *result) {
  return result ? result->message_id : 0;
}
int platform_publish_result_recipient_count(const platform_publish_result *result) {
  return result ? result->recipient_count : 0;
}
const char *platform_publish_result_raw_body(const platform_publish_result *result) {
  return result ? result->raw_body : NULL;
}

void platform_publish_result_free(platform_publish_result *result) {
  if (!result) return;
  free(result->message);
  free(result->raw_body);
  free(result);
}

platform_token_manager *platform_token_manager_create(platform_client *client,
                                                      const platform_token *initial,
                                                      const char *scopes) {
  if (!client) return NULL;
  platform_token_manager *m = calloc(1, sizeof(platform_token_manager));
  if (!m) return NULL;
  m->client = client;
  m->scopes = str_dup(scopes);
  if (initial) {
    m->token = calloc(1, sizeof(platform_token));
    if (m->token) {
      m->token->access_token = str_dup(initial->access_token);
      m->token->token_type = str_dup(initial->token_type);
      m->token->refresh_token = str_dup(initial->refresh_token);
      m->token->scope = str_dup(initial->scope);
      m->token->expires_in = initial->expires_in;
    }
  }
  return m;
}

static int refresh_locked(platform_token_manager *m) {
  if (!m || !m->client) return -1;
  platform_token *fresh = NULL;
  if (m->token && m->token->refresh_token) {
    fresh = platform_refresh_token(m->client, m->token->refresh_token, m->scopes);
  }
  if (!fresh) {
    fresh = platform_client_credentials(m->client, m->scopes);
  }
  if (!fresh) return -1;
  platform_token_free(m->token);
  m->token = fresh;
  return 0;
}

platform_publish_result *platform_token_manager_publish(platform_token_manager *manager,
                                                        const platform_publish_request *req) {
  if (!manager || !manager->token || !manager->token->access_token) {
    set_err(manager ? manager->client : NULL, "token manager has no token");
    return NULL;
  }
  platform_publish_result *first = platform_publish_message(manager->client, manager->token->access_token, req);
  if (!first) return NULL;
  if (first->http_status != PLATFORM_HTTP_UNAUTHORIZED) {
    return first;
  }
  platform_publish_result_free(first);

  if (refresh_locked(manager) != 0) {
    set_err(manager->client, "auto-refresh failed");
    return NULL;
  }
  return platform_publish_message(manager->client, manager->token->access_token, req);
}

const platform_token *platform_token_manager_current(const platform_token_manager *manager) {
  return manager ? manager->token : NULL;
}

void platform_token_manager_free(platform_token_manager *manager) {
  if (!manager) return;
  platform_token_free(manager->token);
  free(manager->scopes);
  free(manager);
}
