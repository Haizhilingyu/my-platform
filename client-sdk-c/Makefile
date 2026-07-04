CC      ?= cc
AR      ?= ar
CFLAGS  ?= -std=c11 -O2 -Wall -Wextra -Wpedantic -Werror
LDFLAGS ?=
LIBS    := $(shell pkg-config --libs libcurl 2>/dev/null || echo -lcurl)
CFLAGS  += $(shell pkg-config --cflags libcurl 2>/dev/null)

LIB       := libplatformclient.a
LIB_OBJS  := platform_client.o platform_json.o
EXAMPLE   := platform_demo
TEST_BIN  := test_platform_client

.PHONY: all lib example test clean

all: lib example

lib: $(LIB)

$(LIB): $(LIB_OBJS)
	$(AR) rcs $@ $^

%.o: %.c platform_client.h platform_json.h
	$(CC) $(CFLAGS) -c $< -o $@

example: $(EXAMPLE)

$(EXAMPLE): example.c $(LIB)
	$(CC) $(CFLAGS) example.c -o $(EXAMPLE) $(LIB) $(LDFLAGS) $(LIBS)

test: $(TEST_BIN)
	./$(TEST_BIN)

$(TEST_BIN): test_platform_client.c $(LIB)
	$(CC) $(CFLAGS) test_platform_client.c -o $(TEST_BIN) $(LIB) $(LDFLAGS) $(LIBS)

clean:
	rm -f $(LIB) $(LIB_OBJS) $(EXAMPLE) $(TEST_BIN)
