#ifndef PLATFORM_CLIENT_H
#define PLATFORM_CLIENT_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>

#define PLATFORM_LEVEL_URGENT    "URGENT"
#define PLATFORM_LEVEL_IMPORTANT "IMPORTANT"
#define PLATFORM_LEVEL_NORMAL    "NORMAL"

#define PLATFORM_RECIPIENT_USER "USER"
#define PLATFORM_RECIPIENT_ROLE "ROLE"
#define PLATFORM_RECIPIENT_UNIT "UNIT"

#define PLATFORM_HTTP_UNAUTHORIZED 401L

typedef struct platform_client platform_client;
typedef struct platform_token platform_token;
typedef struct platform_publish_request platform_publish_request;
typedef struct platform_publish_result platform_publish_result;
typedef struct platform_token_manager platform_token_manager;

platform_client *platform_client_create(const char *client_id,
                                        const char *client_secret,
                                        const char *issuer_url);
void platform_client_free(platform_client *client);

void platform_client_set_timeout_ms(platform_client *client, long timeout_ms);

const char *platform_client_last_error(const platform_client *client);

platform_token *platform_client_credentials(platform_client *client, const char *scopes);
platform_token *platform_exchange_code(platform_client *client,
                                       const char *code,
                                       const char *redirect_uri,
                                       const char *scopes);
platform_token *platform_refresh_token(platform_client *client,
                                       const char *refresh_token,
                                       const char *scopes);

char *platform_authorization_url(platform_client *client,
                                 const char *redirect_uri,
                                 const char *state,
                                 const char *scopes);

const char *platform_token_access_token(const platform_token *token);
const char *platform_token_refresh_token(const platform_token *token);
const char *platform_token_token_type(const platform_token *token);
long platform_token_expires_in(const platform_token *token);
const char *platform_token_scope(const platform_token *token);
void platform_token_free(platform_token *token);

platform_publish_request *platform_publish_request_create(void);
void platform_publish_request_free(platform_publish_request *req);
int platform_publish_request_set_title(platform_publish_request *req, const char *title);
int platform_publish_request_set_content(platform_publish_request *req, const char *content);
int platform_publish_request_set_level(platform_publish_request *req, const char *level);
int platform_publish_request_set_business_type(platform_publish_request *req, const char *business_type);
int platform_publish_request_set_expire_time(platform_publish_request *req, const char *expire_time);
int platform_publish_request_add_recipient(platform_publish_request *req, const char *type, long long id);
platform_publish_request *platform_publish_request_single(const char *title,
                                                          const char *content,
                                                          const char *level,
                                                          const char *recipient_type,
                                                          long long recipient_id);

platform_publish_result *platform_publish_message(platform_client *client,
                                                  const char *access_token,
                                                  const platform_publish_request *req);

long platform_publish_result_http_status(const platform_publish_result *result);
int platform_publish_result_code(const platform_publish_result *result);
const char *platform_publish_result_message(const platform_publish_result *result);
long long platform_publish_result_message_id(const platform_publish_result *result);
int platform_publish_result_recipient_count(const platform_publish_result *result);
const char *platform_publish_result_raw_body(const platform_publish_result *result);
void platform_publish_result_free(platform_publish_result *result);

platform_token_manager *platform_token_manager_create(platform_client *client,
                                                      const platform_token *initial,
                                                      const char *scopes);
platform_publish_result *platform_token_manager_publish(platform_token_manager *manager,
                                                        const platform_publish_request *req);
const platform_token *platform_token_manager_current(const platform_token_manager *manager);
void platform_token_manager_free(platform_token_manager *manager);

#ifdef __cplusplus
}
#endif

#endif
