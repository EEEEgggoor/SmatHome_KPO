#pragma once

#include <stdbool.h>
#include <stddef.h>

typedef struct {
    char *lines[2];
    size_t capacities[2];
    size_t lengths[2];
    size_t current_line;
    bool overflow;
} usb_line_parser_t;

void usb_line_parser_init(usb_line_parser_t *parser,
                          char *first, size_t first_size,
                          char *second, size_t second_size);

bool usb_line_parser_push(usb_line_parser_t *parser, char byte);
