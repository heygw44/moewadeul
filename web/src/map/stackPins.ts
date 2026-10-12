import type { SpotMarkerData } from './types';

// 화면 픽셀 좌표를 함께 받은 장소 핀이다. 좌표는 핀 끝(장소 위치)이고, 지도 컨테이너 왼쪽 위가 원점이다.
export interface PlacedPin {
  marker: SpotMarkerData;
  x: number;
  y: number;
}

// 화면에서 겹친 핀 묶음이다. top은 맨 위에 그릴 핀이고, members는 top을 맨 앞에 둔 묶음 전체다. 혼자인 핀은 members가 하나다.
export interface PinStack {
  top: PlacedPin;
  members: PlacedPin[];
}

export interface Offset {
  x: number;
  y: number;
}

// 두 핀 끝의 가로·세로 거리가 모두 이보다 짧으면 핀이 겹친다.
// 몸통은 폭 28px에 흰 윤곽선이 양쪽으로 1px씩 붙는다. 세로로는 핀 버튼 높이(44px) 안이면 위 핀의 뾰족한 끝이 아래 핀 몸통에 얹힌다.
const PIN_OVERLAP_WIDTH_PX = 30;
const PIN_OVERLAP_HEIGHT_PX = 44;

// 맨 위에 올릴 핀의 순서다. 고른 핀이 가려지면 무엇을 골랐는지 안 보이고, 경고 핀이 가려지면 안전 정보를 놓치기 때문이다.
function priority(marker: SpotMarkerData, selectedId: number | null): number {
  if (marker.id === selectedId) return 0;
  if (marker.warning) return 1;
  if (marker.count !== undefined && marker.count > 0) return 2;
  return 3;
}

// 화면에서 몸통이 겹치는 핀을 묶는다.
// 우선순위가 높은 핀부터 묶음의 기준점이 되고, 나머지는 몸통이 겹치는 기준점 중 가장 가까운 것에 붙는다.
// 기준점을 옮기지 않아서 이웃끼리 줄줄이 이어져 묶음이 한없이 커지지 않는다.
// 정렬은 안정 정렬이라 같은 순위의 핀은 받은 순서를 지킨다.
export function stackPins(placed: PlacedPin[], selectedId: number | null): PinStack[] {
  const sorted = [...placed].sort((a, b) => priority(a.marker, selectedId) - priority(b.marker, selectedId));
  const stacks: PinStack[] = [];

  for (const pin of sorted) {
    let nearest: PinStack | null = null;
    let nearestDistance = Infinity;
    for (const stack of stacks) {
      const dx = Math.abs(stack.top.x - pin.x);
      const dy = Math.abs(stack.top.y - pin.y);
      if (dx >= PIN_OVERLAP_WIDTH_PX || dy >= PIN_OVERLAP_HEIGHT_PX) continue;
      const distance = dx * dx + dy * dy;
      if (distance < nearestDistance) {
        nearest = stack;
        nearestDistance = distance;
      }
    }
    if (nearest === null) {
      stacks.push({ top: pin, members: [pin] });
    } else {
      nearest.members.push(pin);
    }
  }
  return stacks;
}

// 펼친 핀 끝 사이의 최소 간격이다. 52px이면 어느 방향으로 이웃해도 가로 30px 이상이나 세로 38px 이상 떨어진다.
// 몸통 위 끝이 핀 끝에서 38px 위라서 몸통끼리 겹치지 않는다.
const SPREAD_SPACING_PX = 52;
const CIRCLE_MIN_RADIUS_PX = 48;
// 원에 고르게 놓을 수 있는 최대 개수다. 이보다 많으면 원이 너무 커져서 나선으로 감는다.
const CIRCLE_MAX_COUNT = 9;

// 펼친 핀 끝이 놓일 자리를 돌려준다. 자리는 묶음 기준점에서 떨어진 픽셀 거리이고, 위쪽에서 시작해 시계 방향 순서다.
// 핀 끝을 둘레에 놓으므로 아래쪽 핀도 몸통이 실제 위치보다 아래에 있어 가리지 않는다.
// 9개 이하는 원에 놓는다. 반지름은 48px에서 시작하고, 둘레를 나눈 간격이 핀 간격보다 좁아지면 그만큼 늘린다.
// 그보다 많으면 한 바퀴에 핀 간격만큼 멀어지는 나선(아르키메데스 나선)을 따라 핀 간격마다 하나씩 놓는다.
export function spreadSlots(count: number): Offset[] {
  if (count <= CIRCLE_MAX_COUNT) {
    const radius = Math.max(CIRCLE_MIN_RADIUS_PX, (count * SPREAD_SPACING_PX) / (2 * Math.PI));
    return Array.from({ length: count }, (_, i) => {
      const angle = -Math.PI / 2 + (2 * Math.PI * i) / count;
      return { x: radius * Math.cos(angle), y: radius * Math.sin(angle) };
    });
  }

  const growthPerRadian = SPREAD_SPACING_PX / (2 * Math.PI);
  const slots: Offset[] = [];
  let angle = 0;
  for (let i = 0; i < count; i++) {
    const radius = CIRCLE_MIN_RADIUS_PX + growthPerRadian * angle;
    slots.push({ x: radius * Math.cos(angle - Math.PI / 2), y: radius * Math.sin(angle - Math.PI / 2) });
    // 반지름이 radius인 곳에서 호 길이가 핀 간격이 되도록 각을 넘긴다.
    angle += SPREAD_SPACING_PX / radius;
  }
  return slots;
}

// 위쪽을 0으로 두고 시계 방향으로 잰 각이다. 화면 좌표는 y가 아래로 커지므로 atan2가 그대로 시계 방향이다.
function clockwiseFromTop(x: number, y: number): number {
  const angle = Math.atan2(y, x) + Math.PI / 2;
  return angle < 0 ? angle + 2 * Math.PI : angle;
}

export interface SpreadPlacement<T> {
  item: T;
  // 장소의 실제 위치다.
  origin: Offset;
  // 펼친 핀 끝의 위치다.
  at: Offset;
}

// 묶인 핀마다 펼칠 자리를 정한다. 위치는 모두 묶음 기준점에서 떨어진 픽셀 거리다.
// 실제 위치끼리 떨어져 있을 때 순서를 아무렇게나 정하면 연결선이 서로 엇갈린다.
// 그래서 실제 위치를 무게중심에서 본 방향(위쪽부터 시계 방향)으로 줄 세우고, 자리도 같은 방향 순서로 하나씩 짝짓는다.
// 짝을 몇 칸 돌려 놓을지는 실제 위치와 자리 사이 거리 제곱의 합이 가장 작은 쪽으로 고른다.
// 실제 위치가 모두 같으면 방향이 같아 받은 순서가 그대로 남으므로, 맨 위 핀이 첫 자리(위쪽)에 온다.
export function arrangeSpread<T>(members: { item: T; origin: Offset }[]): SpreadPlacement<T>[] {
  const count = members.length;
  const slots = spreadSlots(count);
  const centerX = members.reduce((sum, member) => sum + member.origin.x, 0) / count;
  const centerY = members.reduce((sum, member) => sum + member.origin.y, 0) / count;
  const sorted = members
    .map((member) => ({ member, angle: clockwiseFromTop(member.origin.x - centerX, member.origin.y - centerY) }))
    // 무게중심과 겹친 위치는 방향이 없어서 0이 나온다. 같은 각끼리는 안정 정렬로 받은 순서를 지킨다.
    .sort((a, b) => a.angle - b.angle)
    .map(({ member }) => member);

  let bestShift = 0;
  let bestCost = Infinity;
  for (let shift = 0; shift < count; shift++) {
    let cost = 0;
    sorted.forEach(({ origin }, index) => {
      const slot = slots[(index + shift) % count] ?? origin;
      cost += (slot.x - origin.x) ** 2 + (slot.y - origin.y) ** 2;
    });
    if (cost < bestCost) {
      bestCost = cost;
      bestShift = shift;
    }
  }

  return sorted.map(({ item, origin }, index) => ({
    item,
    origin,
    at: slots[(index + bestShift) % count] ?? origin,
  }));
}
