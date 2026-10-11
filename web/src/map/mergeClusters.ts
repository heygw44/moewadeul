import type { ClusterData } from './types';

// 화면 픽셀 좌표를 함께 받은 묶음이다. 좌표는 지도 컨테이너 왼쪽 위가 원점이다.
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

// 화면에서 minDistancePx 안에 모인 묶음을 하나로 합친다.
// 개수가 많은 묶음부터 그룹의 기준점이 되고, 나머지는 가장 가까운 기준점에 붙는다.
// 기준점을 옮기지 않아서 이웃끼리 줄줄이 이어져 그룹이 한없이 커지지 않는다.
// 합친 위치는 개수로 가중한 평균이라, 서버가 준 칸별 평균을 다시 평균 내면 실제 장소들의 평균 위치가 된다.
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
