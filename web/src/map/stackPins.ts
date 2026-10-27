import type { SpotMarkerData } from './types';

export interface PlacedPin {
  marker: SpotMarkerData;
  x: number;
  y: number;
}

export interface PinStack {
  top: PlacedPin;
  members: PlacedPin[];
}

export interface Offset {
  x: number;
  y: number;
}

const PIN_OVERLAP_WIDTH_PX = 30;
const PIN_OVERLAP_HEIGHT_PX = 44;

function priority(marker: SpotMarkerData, selectedId: number | null): number {
  if (marker.id === selectedId) return 0;
  if (marker.warning) return 1;
  if (marker.count !== undefined && marker.count > 0) return 2;
  return 3;
}

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

const SPREAD_SPACING_PX = 52;
const CIRCLE_MIN_RADIUS_PX = 48;
const CIRCLE_MAX_COUNT = 9;

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
    angle += SPREAD_SPACING_PX / radius;
  }
  return slots;
}

function clockwiseFromTop(x: number, y: number): number {
  const angle = Math.atan2(y, x) + Math.PI / 2;
  return angle < 0 ? angle + 2 * Math.PI : angle;
}

export interface SpreadPlacement<T> {
  item: T;
  origin: Offset;
  at: Offset;
}

export function arrangeSpread<T>(members: { item: T; origin: Offset }[]): SpreadPlacement<T>[] {
  const count = members.length;
  const slots = spreadSlots(count);
  const centerX = members.reduce((sum, member) => sum + member.origin.x, 0) / count;
  const centerY = members.reduce((sum, member) => sum + member.origin.y, 0) / count;
  const sorted = members
    .map((member) => ({ member, angle: clockwiseFromTop(member.origin.x - centerX, member.origin.y - centerY) }))
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
