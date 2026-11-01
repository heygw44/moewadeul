import type { ClusterData } from './types';

export interface PlacedCluster {
  cluster: ClusterData;
  x: number;
  y: number;
}

interface Group {
  x: number;
  y: number;
  count: number;
  latSum: number;
  lngSum: number;
}

export function mergeClusters(placed: PlacedCluster[], minDistancePx: number): ClusterData[] {
  const sorted = [...placed].sort((a, b) => b.cluster.count - a.cluster.count);
  const groups: Group[] = [];
  const limit = minDistancePx * minDistancePx;

  for (const { cluster, x, y } of sorted) {
    let nearest: Group | null = null;
    let nearestDistance = limit;
    for (const group of groups) {
      const distance = (group.x - x) ** 2 + (group.y - y) ** 2;
      if (distance < nearestDistance) {
        nearest = group;
        nearestDistance = distance;
      }
    }
    const weightedLat = cluster.position.lat * cluster.count;
    const weightedLng = cluster.position.lng * cluster.count;
    if (nearest === null) {
      groups.push({ x, y, count: cluster.count, latSum: weightedLat, lngSum: weightedLng });
    } else {
      nearest.count += cluster.count;
      nearest.latSum += weightedLat;
      nearest.lngSum += weightedLng;
    }
  }

  return groups.map((group) => ({
    position: { lat: group.latSum / group.count, lng: group.lngSum / group.count },
    count: group.count,
  }));
}
