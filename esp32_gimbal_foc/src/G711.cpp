#include "G711.h"
#include <cmath>

#define SIGN_BIT (0x80)
#define QUANT_MASK (0xf)
#define SEG_SHIFT (4)
#define SEG_MASK (0x70)

static int16_t seg_end[8] = {0xFF,  0x1FF,  0x3FF,  0x7FF,
                             0xFFF, 0x1FFF, 0x3FFF, 0x7FFF};

static int16_t search(int16_t val, int16_t *table, int size) {
  for (int i = 0; i < size; i++) {
    if (val <= table[i]) {
      return i;
    }
  }
  return size;
}

uint8_t G711::LinearToAlawSample(int16_t pcm_val) {
  int16_t mask;
  int16_t seg;
  uint8_t aval;

  if (pcm_val >= 0) {
    mask = 0xD5;
  } else {
    mask = 0x55;
    pcm_val = -pcm_val - 8;
  }

  if (pcm_val < 0) {
    pcm_val = 0;
  }

  seg = search(pcm_val, seg_end, 8);

  if (seg >= 8) {
    return (uint8_t)(0x7F ^ mask);
  } else {
    aval = (uint8_t)(seg << SEG_SHIFT);
    if (seg < 2) {
      aval |= (pcm_val >> 4) & QUANT_MASK;
    } else {
      aval |= (pcm_val >> (seg + 3)) & QUANT_MASK;
    }
    return (aval ^ mask);
  }
}

int16_t G711::AlawToLinearSample(uint8_t alaw_val) {
  alaw_val ^= 0x55;
  int16_t t;
  int16_t seg;

  alaw_val &= 0x7f;
  if (alaw_val < 16) {
    t = (alaw_val << 4) + 8;
  } else {
    seg = (alaw_val >> 4) & 0x07;
    t = ((alaw_val & 0x0f) << 4) + 0x108;
    t <<= (seg - 1);
  }
  return ((alaw_val & 0x80) ? t : -t);
}

void G711::LinearToAlaw(const std::vector<int16_t> &pcm_data,
                        std::vector<uint8_t> &g711_data) {
  g711_data.resize(pcm_data.size());
  for (size_t i = 0; i < pcm_data.size(); ++i) {
    g711_data[i] = LinearToAlawSample(pcm_data[i]);
  }
}

void G711::AlawToLinear(const std::vector<uint8_t> &g711_data,
                        std::vector<int16_t> &pcm_data) {
  pcm_data.resize(g711_data.size());
  for (size_t i = 0; i < g711_data.size(); ++i) {
    pcm_data[i] = AlawToLinearSample(g711_data[i]);
  }
}
