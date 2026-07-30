#pragma once

#include <string>

inline bool isTareCommand(const std::string &command) {
  return command == "TARE";
}
