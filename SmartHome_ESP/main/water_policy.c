#include "water_policy.h"

uint8_t water_level_percent(int raw, int dry, int wet)
{
    if (raw < 0 || wet <= dry)
        return 255;
    if (raw <= dry)
        return 0;
    if (raw >= wet)
        return 100;
    return (uint8_t)(((raw - dry) * 100) / (wet - dry));
}

bool water_should_close(uint8_t level, uint8_t threshold)
{
    return threshold < 1 || threshold > 100 || level > 100 || level >= threshold;
}

bool water_can_open(uint8_t level, uint8_t threshold)
{
    if (water_should_close(level, threshold))
        return false;
    unsigned margin = threshold > 5 ? 5 : 1;
    return level <= threshold - margin;
}
