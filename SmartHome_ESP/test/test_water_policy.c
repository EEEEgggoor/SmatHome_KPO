#include <assert.h>
#include "water_policy.h"

int main(void)
{
    assert(water_level_percent(100, 100, 2100) == 0);
    assert(water_level_percent(1100, 100, 2100) == 50);
    assert(water_level_percent(4095, 100, 2100) == 100);
    assert(water_level_percent(-1, 100, 2100) == 255);
    assert(water_level_percent(100, 100, 100) == 255);
    assert(!water_should_close(79, 80));
    assert(water_should_close(80, 80));
    assert(water_should_close(255, 80));
    assert(!water_can_open(76, 80));
    assert(water_can_open(75, 80));
    assert(!water_can_open(255, 80));
    assert(water_can_open(0, 1));
    assert(!water_can_open(1, 1));
    return 0;
}
