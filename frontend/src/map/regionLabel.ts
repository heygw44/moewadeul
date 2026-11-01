export type RegionLabel = { kind: 'named'; name: string } | { kind: 'wide' };

export interface RegionNames {
  region_1depth_name: string;
  region_2depth_name: string;
  region_3depth_name: string;
}

const TOWN_MAX_LEVEL = 7;

const WIDE_MIN_LEVEL = 12;

export function nameRegions(regions: RegionNames[], level: number, sampled: number): RegionLabel | null {
  const first = regions[0];
  if (first === undefined) return null;
  if (level >= WIDE_MIN_LEVEL) return { kind: 'wide' };

  const provinces = distinct(regions.map((region) => region.region_1depth_name));
  if (provinces.length >= 3) return { kind: 'wide' };
  if (provinces.length === 2) return { kind: 'named', name: `${provinces.join('·')} 일대` };

  const province = first.region_1depth_name;
  if (regions.length * 2 < sampled) return { kind: 'named', name: `${province} 일대` };

  const sameCity = distinct(regions.map((region) => region.region_2depth_name)).length === 1;
  const sameTown = distinct(regions.map((region) => region.region_3depth_name)).length === 1;
  const city = first.region_2depth_name;
  if (level <= TOWN_MAX_LEVEL && sameCity && sameTown) {
    return { kind: 'named', name: joinNames(city || province, first.region_3depth_name) };
  }
  if (sameCity) return { kind: 'named', name: joinNames(province, city) };
  const cities = distinct(regions.map((region) => cityOf(region.region_2depth_name)));
  const onlyCity = cities.length === 1 ? cities[0] : undefined;
  if (onlyCity) return { kind: 'named', name: joinNames(province, onlyCity) };
  const mainCity = cityOf(first.region_2depth_name);
  const inMainCity = regions.filter((region) => cityOf(region.region_2depth_name) === mainCity).length;
  if (mainCity && inMainCity * 2 > regions.length) {
    return { kind: 'named', name: `${joinNames(province, mainCity)} 일대` };
  }
  return { kind: 'named', name: `${province} 일대` };
}

function cityOf(name: string): string {
  return name.split(' ')[0] ?? '';
}

function distinct(values: string[]): string[] {
  return [...new Set(values)];
}

function joinNames(...names: string[]): string {
  return names.filter((name) => name !== '').join(' ');
}
