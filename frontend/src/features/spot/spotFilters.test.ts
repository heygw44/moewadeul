import { describe, expect, it, vi } from 'vitest';
import { NO_SPOT_FILTERS, filtersFromInterpretation, spotFilterChips } from './spotFilters';
import type { SpotFilters } from './spotFilters';

describe('장소 조건 칩', () => {
  it('[F-31][AI-08] 칩을 빼면 그 조건만 빠지고 나머지 조건은 그대로다', () => {
    const filters: SpotFilters = {
      ...NO_SPOT_FILTERS,
      types: ['CAMPSITE'],
      settings: ['VALLEY', 'BEACH'],
      hasWater: true,
    };
    const onChange = vi.fn();

    const chips = spotFilterChips(filters, onChange);
    chips.find((chip) => chip.key === 'setting-VALLEY')?.onRemove();

    expect(chips.map((chip) => chip.key)).toEqual(['types', 'setting-VALLEY', 'setting-BEACH', 'hasWater']);
    expect(onChange).toHaveBeenCalledWith({ ...filters, settings: ['BEACH'] });
  });

  it('[F-31] 켜진 조건이 없으면 칩도 없다', () => {
    expect(spotFilterChips(NO_SPOT_FILTERS, vi.fn())).toEqual([]);
  });
});

describe('AI 해석 결과를 조건으로 바꾸기', () => {
  it('[F-27][AI-08] 해석한 조건을 지도 조건으로 옮기고, 없는 조건은 꺼 둔다', () => {
    const { filters, droppedSettings } = filtersFromInterpretation({
      types: ['CAMPSITE'],
      settings: ['VALLEY'],
      excludeWarning: true,
      unparsed: [],
    });

    expect(filters).toEqual({
      ...NO_SPOT_FILTERS,
      types: ['CAMPSITE'],
      settings: ['VALLEY'],
      excludeWarning: true,
    });
    expect(droppedSettings).toEqual([]);
  });

  it('[F-27][F-31] 입지는 공공 야영장에만 있어서, 박지만 찾는 해석에서는 입지를 빼고 뺀 값을 알려 준다', () => {
    const { filters, droppedSettings } = filtersFromInterpretation({
      types: ['BAKJI'],
      settings: ['VALLEY'],
      hasWater: true,
      unparsed: [],
    });

    expect(filters.settings).toEqual([]);
    expect(filters.hasWater).toBe(true);
    expect(droppedSettings).toEqual(['VALLEY']);
  });
});
