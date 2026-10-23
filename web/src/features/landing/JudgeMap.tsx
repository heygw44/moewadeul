import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { contourPaths } from './contours';

// 지도 조각의 좌표계(viewBox)다. 다각형과 핀 위치는 이 좌표로 계산한다.
const MAP_WIDTH = 560;
const MAP_HEIGHT = 476;
// 예시 공원 경계 다각형이다. 실제 공원 경계가 아니라 판정이 어떻게 보이는지 보여 주는 그림이다.
const BOUNDARY: ReadonlyArray<readonly [number, number]> = [
  [120, 40],
  [330, 20],
  [430, 110],
  [400, 260],
  [250, 300],
  [130, 230],
];
const BOUNDARY_PATH = `M${BOUNDARY.map(([x, y]) => `${x} ${y}`).join(' L')} Z`;
// 키보드로 핀을 옮길 때 한 번에 움직이는 거리(지도 좌표)다.
const KEY_STEP = 16;
const KEY_MOVES: Record<string, readonly [number, number]> = {
  ArrowLeft: [-KEY_STEP, 0],
  ArrowRight: [KEY_STEP, 0],
  ArrowUp: [0, -KEY_STEP],
  ArrowDown: [0, KEY_STEP],
};

// 점이 다각형 안에 있는지 판정한다. 점에서 오른쪽으로 그은 반직선이 변을 홀수 번 넘으면 안쪽이다.
function insideBoundary(x: number, y: number): boolean {
  let inside = false;
  for (let i = 0, j = BOUNDARY.length - 1; i < BOUNDARY.length; j = i++) {
    const [xi, yi] = BOUNDARY[i]!;
    const [xj, yj] = BOUNDARY[j]!;
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

function clampPoint(x: number, y: number) {
  return { x: Math.min(MAP_WIDTH, Math.max(0, x)), y: Math.min(MAP_HEIGHT, Math.max(0, y)) };
}

// 이 페이지의 하나뿐인 조작이다. 지도를 누르거나 화살표 키로 핀을 옮기면 공원 경계 안·밖을 다시 판정하고,
// 경계 안이면 베이스캠프 열기 버튼을 막는다. 앱의 실제 규칙(경고 박지로는 베이스캠프를 열 수 없음)을 그대로 보여 준다.
export function JudgeMap() {
  const svgRef = useRef<SVGSVGElement>(null);
  const [pin, setPin] = useState({ x: 291, y: 190 });
  const warned = insideBoundary(pin.x, pin.y);
  const hintId = useId();
  // 지형 등고선이다. 오른쪽 위에 큰 봉우리 하나, 왼쪽 아래에 작은 봉우리 하나를 둔다.
  const peaks = useMemo(
    () => [
      contourPaths({ cx: 470, cy: 150, rx: 300, ry: 210, levels: 7, seed: 2.2 }),
      contourPaths({ cx: 40, cy: 400, rx: 190, ry: 130, levels: 5, seed: 4.1 }),
    ],
    [],
  );

  function moveTo(clientX: number, clientY: number) {
    const matrix = svgRef.current?.getScreenCTM();
    if (!matrix) return;
    // 화면 좌표를 지도 좌표로 바꾼다. 지도가 칸을 넘치게 채워(slice) 잘려 있어도 정확한 자리를 얻는다.
    const point = new DOMPoint(clientX, clientY).matrixTransform(matrix.inverse());
    setPin(clampPoint(point.x, point.y));
  }

  function handleKeyDown(event: KeyboardEvent<SVGSVGElement>) {
    const move = KEY_MOVES[event.key];
    if (!move) return;
    event.preventDefault();
    setPin((current) => clampPoint(current.x + move[0], current.y + move[1]));
  }

  return (
    <div data-ui className="relative aspect-[4/4.6] overflow-hidden rounded-media bg-fill text-ink sm:aspect-[4/3.4]">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`}
        preserveAspectRatio="xMidYMid slice"
        role="application"
        tabIndex={0}
        aria-label="예시 지도. 화살표 키나 누르기로 박지 핀을 옮겨요"
        aria-describedby={hintId}
        onClick={(event) => moveTo(event.clientX, event.clientY)}
        onKeyDown={handleKeyDown}
        className="absolute inset-0 size-full cursor-crosshair touch-manipulation"
      >
        <rect width={MAP_WIDTH} height={MAP_HEIGHT} className="fill-fill" />
        {peaks.map((rings, peak) =>
          rings.map((d, level) => (
            <path key={`${peak}-${level}`} d={d} className={level % 2 === 0 ? 'fill-fill-strong' : 'fill-fill'} />
          )),
        )}
        <path
          d="M-10 170 C80 150 120 210 210 190 S360 120 470 150 S560 130 580 120"
          fill="none"
          stroke="#cfe0ea"
          strokeWidth={14}
          strokeLinecap="round"
        />
        <path d="M40 420 C140 380 220 430 300 400 S470 360 540 380" fill="none" stroke="#fff" strokeWidth={6} />
        <path
          d={BOUNDARY_PATH}
          className="fill-warning stroke-warning"
          fillOpacity={0.08}
          strokeWidth={2}
          strokeDasharray="7 6"
        />
        <text x={226} y={64} fontSize={13} fontWeight={700} className="fill-warning">
          예시 국립공원 경계
        </text>
        {/* 핀은 지도 좌표에 그려서 화면 크기가 바뀌어도 같은 자리에 있다. 끝이 좌표를 가리키는 물방울 모양이다. */}
        <g
          style={{ transform: `translate(${pin.x}px, ${pin.y}px)` }}
          className="transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none"
        >
          <path
            d="M0 0 C-4 -9 -15 -15 -15 -28 A15 15 0 1 1 15 -28 C15 -15 4 -9 0 0Z"
            className={warned ? 'fill-warning' : 'fill-brand'}
            stroke="#fff"
            strokeWidth={3}
          />
          <text y={-23} textAnchor="middle" fontSize={14} fontWeight={800} fill="#fff">
            1
          </text>
        </g>
      </svg>

      <p
        id={hintId}
        className="pointer-events-none absolute top-3 left-3 rounded-control bg-surface px-3.5 py-1.5 text-sm font-semibold shadow-raise"
      >
        지도를 눌러 핀을 옮겨 보세요
      </p>

      {/* 판정 결과 창이다. 지도 위에 떠 있어 그림자를 둔다. 결과가 바뀌면 스크린 리더가 읽는다. */}
      <div className="absolute inset-x-3 bottom-3 rounded-row bg-surface p-4 text-[0.9375rem] shadow-raise sm:inset-x-4 sm:bottom-4">
        <p className="text-[1.0625rem] font-bold">이름 없는 박지 · 사용자 제보</p>
        <p className="text-[0.9375rem] text-ink-muted">경계 자료: 예시 공원 경계 · 2026-06-30 기준</p>
        <p
          aria-live="polite"
          className={`mt-2.5 rounded-xl px-3 py-2 font-semibold ${warned ? 'bg-warning-soft text-warning' : 'bg-accent-soft text-accent'}`}
        >
          {warned ? '공원 경계 안이에요. 야영이 금지된 곳일 수 있어요' : '공원 경계 밖이에요. 경고 없이 등록돼요'}
        </p>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          <span className="text-[0.9375rem] text-ink-muted">
            {warned ? '경고 박지는 베이스캠프를 열 수 없어요' : '이 자리로 베이스캠프를 열 수 있어요'}
          </span>
          <span
            aria-hidden="true"
            className={`inline-flex h-9 w-full items-center justify-center rounded-control px-4 text-[0.9375rem] font-semibold sm:w-auto ${
              warned ? 'bg-fill-strong text-ink-subtle' : 'bg-brand text-white'
            }`}
          >
            베이스캠프 열기
          </span>
        </div>
      </div>
    </div>
  );
}
