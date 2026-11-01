#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "usb_line_parser.h"

static void feed(usb_line_parser_t *parser, const char *text)
{
    while (*text)
        usb_line_parser_push(parser, *text++);
}

static void two_lines_preserve_spaces_and_crlf(void)
{
    char ssid[33], password[64];
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, ssid, sizeof(ssid), password, sizeof(password));
    feed(&parser, " My WiFi \r\n password with spaces \r\n");
    assert(parser.current_line == 2 && !parser.overflow);
    assert(strcmp(ssid, " My WiFi ") == 0);
    assert(strcmp(password, " password with spaces ") == 0);
}

static void split_usb_packets_keep_partial_lines(void)
{
    char ssid[33], password[64];
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, ssid, sizeof(ssid), password, sizeof(password));
    feed(&parser, "Net");
    assert(parser.current_line == 0);
    feed(&parser, "work\npass");
    assert(parser.current_line == 1 && strcmp(ssid, "Network") == 0);
    feed(&parser, "word");
    assert(parser.current_line == 1);
    assert(usb_line_parser_push(&parser, '\n'));
    assert(strcmp(password, "password") == 0);
}

static void full_capacity_leaves_null_terminators(void)
{
    char ssid[33], password[64];
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, ssid, sizeof(ssid), password, sizeof(password));
    for (int i = 0; i < 32; ++i)
        assert(!usb_line_parser_push(&parser, 's'));
    assert(!usb_line_parser_push(&parser, '\n'));
    for (int i = 0; i < 63; ++i)
        assert(!usb_line_parser_push(&parser, 'p'));
    assert(usb_line_parser_push(&parser, '\n'));
    assert(!parser.overflow && strlen(ssid) == 32 && strlen(password) == 63);
}

static void overflow_is_detected_without_overwriting_guards(void)
{
    struct
    {
        char before;
        char data[4];
        char after;
    } first = {'A', {0}, 'Z'}, second = {'B', {0}, 'Y'};
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, first.data, sizeof(first.data), second.data, sizeof(second.data));
    feed(&parser, "abcdef\n123456\n");
    assert(parser.overflow && parser.current_line == 2);
    assert(strcmp(first.data, "abc") == 0 && strcmp(second.data, "123") == 0);
    assert(first.before == 'A' && first.after == 'Z' && second.before == 'B' && second.after == 'Y');
    feed(&parser, "ignored\n");
    assert(strcmp(first.data, "abc") == 0 && strcmp(second.data, "123") == 0);
}

static void empty_lines_and_zero_capacity_are_safe(void)
{
    char second[1] = {'X'};
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, NULL, 0, second, sizeof(second));
    feed(&parser, "a\nb\n");
    assert(parser.overflow && parser.current_line == 2 && second[0] == '\0');
    char a[2], b[2];
    usb_line_parser_init(&parser, a, sizeof(a), b, sizeof(b));
    feed(&parser, "\n\n");
    assert(!parser.overflow && parser.current_line == 2 && a[0] == '\0' && b[0] == '\0');
}

static void reinitialization_discards_old_input_and_overflow(void)
{
    char a[3], b[3];
    usb_line_parser_t parser;
    usb_line_parser_init(&parser, a, sizeof(a), b, sizeof(b));
    feed(&parser, "long\nline\n");
    assert(parser.overflow);
    usb_line_parser_init(&parser, a, sizeof(a), b, sizeof(b));
    assert(!parser.overflow && parser.current_line == 0 && a[0] == '\0' && b[0] == '\0');
    feed(&parser, "a\nb\n");
    assert(!parser.overflow && strcmp(a, "a") == 0 && strcmp(b, "b") == 0);
}

int main(void)
{
    two_lines_preserve_spaces_and_crlf();
    split_usb_packets_keep_partial_lines();
    full_capacity_leaves_null_terminators();
    overflow_is_detected_without_overwriting_guards();
    empty_lines_and_zero_capacity_are_safe();
    reinitialization_discards_old_input_and_overflow();
    puts("USB parser: 6 scenarios passed");
    return 0;
}
