import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RegionSearchError, normalizeRegionQuery, searchRegions } from './regionSearch';

vi.mock('./kakaoLoader', () => ({ loadKakaoMaps: vi.fn(async () => undefined) }));

const Status = { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' };

type AddressItem = Record<string, unknown>;

function address(overrides: AddressItem): AddressItem {
  return { address_name: '이름', x: '128.7', y: '36.5', ...overrides };
}

describe('[F-30] 지역 검색', () => {
  let addressResult: { items: AddressItem[]; status: string };
  let placeResult: { items: Record<string, string>[]; status: string };
  let addressKeywords: string[];

  beforeEach(() => {
    addressResult = { items: [], status: Status.ZERO_RESULT };
    placeResult = { items: [], status: Status.ZERO_RESULT };
    addressKeywords = [];
    vi.stubGlobal('kakao', {
      maps: {
        services: {
          Status,
          Geocoder: class {
            addressSearch(keyword: string, callback: (items: AddressItem[], status: string) => void) {
              addressKeywords.push(keyword);
              callback(addressResult.items, addressResult.status);
            }
          },
          Places: class {
            keywordSearch(_keyword: string, callback: (items: Record<string, string>[], status: string) => void) {
              callback(placeResult.items, placeResult.status);
            }
          },
        },
      },
    });
  });

  it('검색어 끝의 주변, 주위, 근처를 빼고 찾는다', async () => {
    expect(normalizeRegionQuery(' 원주 주변 ')).toBe('원주');
    expect(normalizeRegionQuery('원주근처')).toBe('원주');
    expect(normalizeRegionQuery('원주 주위')).toBe('원주');

    await searchRegions('원주 주변');

    expect(addressKeywords).toEqual(['원주']);
  });

  it('시·도는 11, 시·군·구는 10, 읍·면·동은 7로 보여 주고 핀을 두지 않는다', async () => {
    addressResult = {
      status: Status.OK,
      items: [
        address({ address_type: 'REGION', address: { region_1depth_name: '강원' } }),
        address({ address_type: 'REGION', address: { region_2depth_name: '원주시' } }),
        address({ address_type: 'REGION', address: { region_2depth_name: '원주시', region_3depth_name: '문막읍' } }),
      ],
    };

    const candidates = await searchRegions('원주');

    expect(candidates.map(({ level, pinned }) => [level, pinned])).toEqual([
      [11, false],
      [10, false],
      [7, false],
    ]);
  });

  it('번지까지 찾은 주소는 확대 수준 3과 핀으로 보여 주고, 입력한 쪽 주소를 이름으로 쓴다', async () => {
    addressResult = {
      status: Status.OK,
      items: [
        address({
          address_type: 'ROAD_ADDR',
          address: { address_name: '평창군 횡계리 123', main_address_no: '123' },
          road_address: { address_name: '평창군 올림픽로 12', main_building_no: '12' },
        }),
      ],
    };

    const [candidate] = await searchRegions('올림픽로 12');

    expect(candidate).toEqual({
      name: '평창군 올림픽로 12',
      detail: '지번 평창군 횡계리 123',
      position: { lat: 36.5, lng: 128.7 },
      level: 3,
      pinned: true,
    });
  });

  it('번지 없이 도로 이름만 찾으면 확대 수준 7이고 핀을 두지 않는다', async () => {
    addressResult = {
      status: Status.OK,
      items: [address({ address_type: 'ROAD', road_address: { address_name: '올림픽로' } })],
    };

    const [candidate] = await searchRegions('올림픽로');

    expect(candidate).toMatchObject({ level: 7, pinned: false });
  });

  it('주소로 찾을 수 없으면 키워드 장소 결과를 확대 수준 3, 핀 없는 후보로 준다', async () => {
    placeResult = {
      status: Status.OK,
      items: [{ place_name: '설악산', road_address_name: '', address_name: '속초시', x: '128.4', y: '38.1' }],
    };

    const candidates = await searchRegions('설악산');

    expect(candidates).toEqual([
      { name: '설악산', detail: '속초시', position: { lat: 38.1, lng: 128.4 }, level: 3, pinned: false },
    ]);
  });

  it('둘 다 결과가 없으면 빈 목록이고, 카카오 오류는 RegionSearchError다', async () => {
    await expect(searchRegions('없는곳')).resolves.toEqual([]);

    addressResult = { items: [], status: Status.ERROR };

    await expect(searchRegions('없는곳')).rejects.toBeInstanceOf(RegionSearchError);
  });
});
