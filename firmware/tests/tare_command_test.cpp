#include <cassert>
#include <string>

#include "../four_plate_ble_service/tare_command.h"

int main() {
  assert(isTareCommand("TARE"));
  assert(!isTareCommand(""));
  assert(!isTareCommand("tare"));
  assert(!isTareCommand("TARE\n"));
  assert(!isTareCommand("RESET"));
  return 0;
}
