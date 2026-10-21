type ContourOptions = {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  levels: number;
  seed: number;
  // 안쪽 고리로 갈수록 중심이 옮겨 가는 방향이다. 봉우리가 한쪽으로 기운 산처럼 보이게 한다.
  lean?: readonly [number, number];
};

const CONTOUR_POINTS = 72;

// 닫힌 곡선 면 여러 겹의 path를 바깥에서 안으로 돌려준다. 같은 seed는 늘 같은 모양이다.
export function contourPaths({ cx, cy, rx, ry, levels, seed, lean = [0.1, -0.08] }: ContourOptions): string[] {
  const paths: string[] = [];
  for (let level = 0; level < levels; level += 1) {
    const t = level / levels;
    const scale = 1 - t * 0.9;
    const centerX = cx + lean[0] * rx * t * 2;
    const centerY = cy + lean[1] * ry * t * 2;
    const points: string[] = [];
    for (let i = 0; i < CONTOUR_POINTS; i += 1) {
      const angle = (i / CONTOUR_POINTS) * Math.PI * 2;
      const wobble =
        1 +
        0.14 * Math.sin(2 * angle + seed) +
        0.08 * Math.sin(3 * angle + seed * 1.7) +
        0.05 * Math.sin(5 * angle + seed * 2.3);
      const x = centerX + Math.cos(angle) * rx * scale * wobble;
      const y = centerY + Math.sin(angle) * ry * scale * wobble;
      points.push(`${x.toFixed(1)} ${y.toFixed(1)}`);
    }
    paths.push(`M${points.join(' L')} Z`);
  }
  return paths;
}
