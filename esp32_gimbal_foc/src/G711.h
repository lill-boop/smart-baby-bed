#ifndef G711_H
#define G711_H

#include <stdint.h>
#include <vector>

class G711 {
public:
  static void LinearToAlaw(const std::vector<int16_t> &pcm_data,
                           std::vector<uint8_t> &g711_data);
  static void AlawToLinear(const std::vector<uint8_t> &g711_data,
                           std::vector<int16_t> &pcm_data);
  static uint8_t LinearToAlawSample(int16_t pcm_val);
  static int16_t AlawToLinearSample(uint8_t alaw_val);
};

#endif // G711_H
