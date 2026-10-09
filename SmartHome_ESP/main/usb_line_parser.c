#include "usb_line_parser.h"

void usb_line_parser_init(usb_line_parser_t *parser,
                          char *first, size_t first_size,
                          char *second, size_t second_size)
{
    *parser = (usb_line_parser_t) {
        .lines = { first, second },
        .capacities = { first_size, second_size },
    };

    if (first_size > 0) {
        first[0] = '\0';
    }
    if (second_size > 0) {
        second[0] = '\0';
    }
}

bool usb_line_parser_push(usb_line_parser_t *parser, char byte)
{
    if (parser->current_line >= 2) {
        return true;
    }

    if (byte == '\r') {
        return false;
    }

    if (byte == '\n') {
        parser->current_line++;
        return parser->current_line == 2;
    }

    const size_t line = parser->current_line;
    const size_t capacity = parser->capacities[line];
    if (capacity == 0 || parser->lengths[line] + 1 >= capacity) {
        parser->overflow = true;
        return false;
    }

    parser->lines[line][parser->lengths[line]++] = byte;
    parser->lines[line][parser->lengths[line]] = '\0';
    return false;
}
