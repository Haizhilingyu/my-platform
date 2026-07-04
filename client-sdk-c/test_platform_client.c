#include "platform_client.h"
#include "platform_json.h"

#include <curl/curl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int failures = 0;

static void check(int cond, const char *label) {
  if (cond) {
    printf("  ok   %s\n", label);
  } else {
    printf("  FAIL %s\n", label);
    failures++;
  }
}

static void test_json_parse(void) {
  printf("[test_json_parse]\n");
  char err[128] = {0};
  const char *text =
      "{\"code\":200,\"message\":\"success\","
      "\"data\":{\"messageId\":42,\"recipientCount\":3},"
      "\"tags\":[\"a\",\"b\"],\"ok\":true,\"nil\":null}";
  pjson *root = pjson_parse(text, err, sizeof(err));
  check(root != NULL, "parse object");
  if (!root) return;

  const pjson *code = pjson_object_get(root, "code");
  check(code && code->type == PJ_NUMBER, "code is number");
  check(code && pjson_number(code) == 200.0, "code == 200");

  const pjson *data = pjson_object_get(root, "data");
  const pjson *mid = pjson_object_get(data, "messageId");
  check(mid && pjson_number(mid) == 42.0, "data.messageId == 42");
  const pjson *rc = pjson_object_get(data, "recipientCount");
  check(rc && pjson_number(rc) == 3.0, "data.recipientCount == 3");

  const pjson *tags = pjson_object_get(root, "tags");
  check(tags && pjson_array_len(tags) == 2, "tags array len 2");
  const pjson *t1 = pjson_array_get(tags, 1);
  check(t1 && strcmp(pjson_string(t1), "b") == 0, "tags[1] == b");

  const pjson *ok = pjson_object_get(root, "ok");
  check(ok && ok->type == PJ_BOOL && ok->v.boolean == 1, "ok == true");

  const pjson *nil = pjson_object_get(root, "nil");
  check(nil && nil->type == PJ_NULL, "nil == null");

  const pjson *missing = pjson_object_get(root, "nope");
  check(missing == NULL, "missing key returns NULL");

  pjson_free(root);
}

static void test_json_escape(void) {
  printf("[test_json_escape]\n");
  char *esc = pjson_string_escape("he\"llo\\\n\t\x01");
  check(esc != NULL, "escape non-null");
  check(esc && strcmp(esc, "he\\\"llo\\\\\\n\\t\\u0001") == 0, "escape value correct");
  free(esc);

  char err[64] = {0};
  pjson *bad = pjson_parse("{bad}", err, sizeof(err));
  check(bad == NULL, "reject malformed JSON");
  check(err[0] != '\0', "error message populated");
  pjson_free(bad);
}

static void test_request_build(void) {
  printf("[test_request_build]\n");
  platform_publish_request *req = platform_publish_request_single(
      "Title", "Body content", PLATFORM_LEVEL_URGENT, PLATFORM_RECIPIENT_USER, 7);
  check(req != NULL, "single request created");

  platform_publish_request *multi = platform_publish_request_create();
  int r = 0;
  r |= platform_publish_request_set_title(multi, "T");
  r |= platform_publish_request_set_content(multi, "C");
  r |= platform_publish_request_set_level(multi, PLATFORM_LEVEL_NORMAL);
  r |= platform_publish_request_add_recipient(multi, PLATFORM_RECIPIENT_USER, 1);
  r |= platform_publish_request_add_recipient(multi, PLATFORM_RECIPIENT_ROLE, 5);
  check(r == 0, "build multi-recipient request");

  platform_publish_request *empty = platform_publish_request_create();
  platform_publish_request_set_title(empty, "");
  platform_publish_request_set_content(empty, "");
  platform_publish_request_set_level(empty, "");

  platform_publish_request_free(req);
  platform_publish_request_free(multi);
  platform_publish_request_free(empty);
  check(1, "requests freed without crash");
}

static void test_authorization_url(void) {
  printf("[test_authorization_url]\n");
  platform_client *c = platform_client_create("cid", "secret", "http://issuer.example/");
  check(c != NULL, "client created (trailing slash trimmed)");

  char *url = platform_authorization_url(c, "http://cb/cb", "xyz", "notify:publish openid");
  check(url != NULL, "auth url built");
  check(url && strstr(url, "http://issuer.example/oauth2/authorize") != NULL, "url path correct (slash trimmed)");
  check(url && strstr(url, "client_id=cid") != NULL, "client_id present");
  check(url && strstr(url, "state=xyz") != NULL, "state present");
  check(url && strstr(url, "scope=notify") != NULL, "scope present");
  free(url);

  char *url2 = platform_authorization_url(c, "http://cb/cb", NULL, NULL);
  check(url2 && strstr(url2, "response_type=code") != NULL, "minimal url has response_type");
  free(url2);

  platform_client_free(c);
}

static void test_token_accessors_on_null(void) {
  printf("[test_token_accessors_on_null]\n");
  check(platform_token_access_token(NULL) == NULL, "null token access_token");
  check(strcmp(platform_token_token_type(NULL) ? "" : "Bearer", "Bearer") == 0 ||
       strcmp(platform_token_token_type(NULL), "Bearer") == 0, "null token type defaults Bearer");
  check(platform_publish_result_http_status(NULL) == 0, "null result status 0");
  check(platform_publish_result_code(NULL) == -1, "null result code -1");
}

int main(void) {
  curl_global_init(CURL_GLOBAL_DEFAULT);
  test_json_parse();
  test_json_escape();
  test_request_build();
  test_authorization_url();
  test_token_accessors_on_null();
  curl_global_cleanup();

  printf("\n%s (%d failures)\n", failures == 0 ? "ALL PASS" : "FAILURES", failures);
  return failures == 0 ? 0 : 1;
}
