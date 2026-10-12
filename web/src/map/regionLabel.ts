// 지도 화면이 어느 지역인지 나타내는 이름이다. 화면에 시·도가 세 곳 이상 걸치면 한 이름으로 부를 수 없어서 wide로 둔다.
export type RegionLabel = { kind: 'named'; name: string } | { kind: 'wide' };

// 행정구역 결과 중 이름 짓기에 쓰는 부분이다. 카카오 coord2RegionCode 결과와 같은 이름을 쓴다.
export interface RegionNames {
  region_1depth_name: string;
  region_2depth_name: string;
  region_3depth_name: string;
}

// 많이 확대하면 동네까지 적는다.
const TOWN_MAX_LEVEL = 7;

// 이 확대 수준부터는 한 시·도가 화면을 채울 수 없어서 늘 넓은 화면으로 본다. 지역 검색은 시·도를 확대 수준 11로 연다.
const WIDE_MIN_LEVEL = 12;

// 화면 안 여러 점의 행정구역을 받아, 그 점들이 같이 속하는 가장 작은 행정구역 이름을 만든다.
// 바다처럼 행정구역이 없는 점은 부르는 쪽이 미리 뺀다. sampled는 빼기 전에 조회한 점 수다. 남은 점이 없으면 null이다.
export function nameRegions(regions: RegionNames[], level: number, sampled: number): RegionLabel | null {
  const first = regions[0];
  if (first === undefined) return null;
  if (level >= WIDE_MIN_LEVEL) return { kind: 'wide' };

  const provinces = distinct(regions.map((region) => region.region_1depth_name));
  if (provinces.length >= 3) return { kind: 'wide' };
  if (provinces.length === 2) return { kind: 'named', name: `${provinces.join('·')} 일대` };

  const province = first.region_1depth_name;
  // 남은 점이 절반이 안 되면 화면 대부분이 바다라서, 남은 점의 시·군·구가 화면 전체를 대표하지 못한다.
  // 예를 들어 넓은 화면에서 가운데 점 하나만 남으면 전국을 보는데도 그 점의 군 이름이 붙는다. 그래서 시·도까지만 적는다.
  if (regions.length * 2 < sampled) return { kind: 'named', name: `${province} 일대` };

  const sameCity = distinct(regions.map((region) => region.region_2depth_name)).length === 1;
  const sameTown = distinct(regions.map((region) => region.region_3depth_name)).length === 1;
  // 세종시처럼 시·군·구가 없는 곳은 시·도 이름이 그 자리를 대신한다.
  const city = first.region_2depth_name;
  if (level <= TOWN_MAX_LEVEL && sameCity && sameTown) {
    return { kind: 'named', name: joinNames(city || province, first.region_3depth_name) };
  }
  if (sameCity) return { kind: 'named', name: joinNames(province, city) };
  return { kind: 'named', name: `${province} 일대` };
}

function distinct(values: string[]): string[] {
  return [...new Set(values)];
}

function joinNames(...names: string[]): string {
  return names.filter((name) => name !== '').join(' ');
}
