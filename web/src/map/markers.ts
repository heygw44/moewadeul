// 지도 마커는 카카오맵 CustomOverlay 안에 들어가는 DOM 요소라서 React 밖에서 직접 만든다.
// Tailwind는 소스 글자를 훑어 클래스를 찾으므로 클래스 이름은 항상 완성된 문자열로 적는다.
import { ICON_PATHS } from '../components/iconPaths';
import type { IconName } from '../components/iconPaths';
import type { Offset } from './stackPins';
import type { ClusterData, MarkerKind, SpotMarkerData } from './types';

// 세 유형은 색(야영장 청록, 휴양림 녹색, 박지 흙색)과 아이콘 모양으로 함께 구분한다. 세 색 모두 흰 아이콘과 대비가 4.5:1 이상이다.
export const KIND_STYLE: Record<MarkerKind, { label: string; icon: IconName; fill: string }> = {
  CAMPSITE: { label: '공공 야영장', icon: 'tent', fill: 'fill-sea' },
  FOREST: { label: '자연휴양림', icon: 'tree', fill: 'fill-forest' },
  BAKJI: { label: '박지', icon: 'backpack', fill: 'fill-earth' },
};

// 표지기 핀 모양이다. 28×28 몸통(좌표 4~32) 아래로 뾰족한 끝이 (18, 42)에 온다.
// 버튼 높이가 44px이고 오버레이를 아래쪽 끝(yAnchor 1)에 맞추므로, 핀 끝이 장소 좌표에 거의 그대로 놓인다.
export const PIN_PATH = 'M7 4h22a3 3 0 0 1 3 3v22a3 3 0 0 1-3 3h-5l-6 10-6-10H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z';

// 흰 윤곽선 굵기다. 선은 경로 양쪽으로 반씩 퍼지고 안쪽 반은 핀 색이 덮으므로, 바깥에 보이는 두께는 절반이다.
// 기본은 바깥 1px, 경고 핀은 황토 테두리(바깥 1px) 밖으로 1px, 선택하면 흰 링이 2px 더 두꺼워진다.
const OUTLINE_WIDTH = 2;
const WARNING_OUTLINE_WIDTH = 4;
const SELECTED_EXTRA_WIDTH = 4;

const FOCUS_RING =
  'outline-none focus-visible:ring-2 focus-visible:ring-sea focus-visible:ring-offset-2 focus-visible:ring-offset-paper';

function iconMarkup(name: IconName, attributes: string): string {
  return (
    `<svg ${attributes} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" ` +
    `stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name]}</svg>`
  );
}

export interface SpotPin {
  element: HTMLButtonElement;
  setSelected(selected: boolean): void;
}

// stackedCount는 이 핀 아래에 겹쳐 가린 장소 수다. 1 이상이면 "+n" 배지를 달고, 누르면 펼친다는 것을 이름에 적는다.
export function createSpotPin(data: SpotMarkerData, onActivate: (id: number) => void, stackedCount = 0): SpotPin {
  const style = KIND_STYLE[data.kind];
  const baseOutline = data.warning ? WARNING_OUTLINE_WIDTH : OUTLINE_WIDTH;

  const button = document.createElement('button');
  button.type = 'button';
  button.className =
    'relative flex h-11 w-11 cursor-pointer items-end justify-center rounded-control origin-bottom ' +
    'transition-[scale] duration-150 motion-reduce:transition-none ' +
    FOCUS_RING;

  const labelParts = [data.name, style.label];
  if (data.warning) labelParts.push('공원 경계 경고');
  if (data.closed) labelParts.push('휴장');
  if (data.count !== undefined) labelParts.push(`모집 중 베이스캠프 ${data.count}개`);
  if (stackedCount > 0) labelParts.push(`겹친 장소 ${stackedCount}곳 더, 눌러서 펼치기`);
  button.setAttribute('aria-label', labelParts.join(', '));

  // 경고 핀은 몸통에 황토 테두리를 두르고, 그 밖을 흰 윤곽선이 한 번 더 감싼다.
  const bodyAttributes = data.warning
    ? `class="${style.fill} stroke-warning-line" stroke-width="2"`
    : `class="${style.fill}"`;
  let markup =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44" aria-hidden="true">' +
    `<path data-pin-outline class="fill-white stroke-white" stroke-width="${baseOutline}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path ${bodyAttributes} stroke-linejoin="round" d="${PIN_PATH}"/>` +
    iconMarkup(style.icon, 'class="text-white" x="8" y="8" width="20" height="20"') +
    '</svg>';

  // 배지는 몸통 모서리에 걸친다. 휴장이어도 핀을 숨기거나 흐리게 하지 않고 배지만 더한다.
  if (data.warning) {
    markup +=
      '<span class="pointer-events-none absolute -left-0.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-control border border-warning-line bg-warning-soft text-warning">' +
      iconMarkup('alert', 'width="14" height="14"') +
      '</span>';
  }
  if (data.closed) {
    markup +=
      '<span class="pointer-events-none absolute -right-0.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-control border border-white bg-ink text-white">' +
      iconMarkup('closed', 'width="14" height="14"') +
      '</span>';
  }
  // 베이스캠프 수는 몸통 오른쪽 아래에 걸친다. 휴장 배지는 오른쪽 위라서 겹치지 않는다.
  if (data.count !== undefined) {
    markup +=
      '<span data-pin-count class="pointer-events-none absolute bottom-2 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full border border-white bg-ink px-1 font-mono text-xs tabular-nums text-white"></span>';
  }
  // 겹친 장소 수는 몸통 왼쪽 아래에 걸친다. 먹 바탕인 베이스캠프 수와 헷갈리지 않게 흰 바탕에 먹 글자로 그린다.
  if (stackedCount > 0) {
    markup +=
      '<span data-pin-stacked class="pointer-events-none absolute bottom-2 -left-1 flex h-5 min-w-5 items-center justify-center rounded-full border border-ink bg-card px-1 font-mono text-xs tabular-nums text-ink"></span>';
  }
  button.innerHTML = markup;
  const countBadge = button.querySelector('[data-pin-count]');
  if (countBadge && data.count !== undefined) countBadge.textContent = String(data.count);
  const stackedBadge = button.querySelector('[data-pin-stacked]');
  if (stackedBadge) stackedBadge.textContent = `+${stackedCount}`;

  const outline = button.querySelector('[data-pin-outline]');

  // 키보드의 Enter·Space도 click으로 들어온다. 지도까지 클릭이 전달되지 않게 여기서 멈춘다.
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(data.id);
  });

  function setSelected(selected: boolean) {
    button.classList.toggle('scale-125', selected);
    button.setAttribute('aria-pressed', String(selected));
    outline?.setAttribute('stroke-width', String(selected ? baseOutline + SELECTED_EXTRA_WIDTH : baseOutline));
  }
  setSelected(false);

  return { element: button, setSelected };
}

// 핀 버튼의 크기다(h-11 w-11). 펼친 핀은 핀 끝이 놓일 자리에서 가로로 절반, 세로로 전체만큼 옮겨 놓는다.
const PIN_BUTTON_SIZE_PX = 44;
const SPREAD_ANIMATION_MS = 150;

export interface SpreadEntry {
  marker: SpotMarkerData;
  // 펼친 핀 끝의 위치다.
  at: Offset;
  // 장소의 실제 위치(원래 핀 끝)다.
  origin: Offset;
}

// 겹친 핀을 펼친 모습이다. 크기가 0인 기준 요소를 묶음 위치에 놓고, 그 점을 원점으로 연결선과 핀을 그린다.
// 연결선은 장소의 실제 위치에서 펼친 핀 끝까지 긋는다.
// 핀은 실제 위치에서 제자리로 짧게 퍼져 나간다. 움직임 줄이기 설정이면 바로 제자리에 그린다.
export function createSpread(
  entries: SpreadEntry[],
  selectedId: number | null,
  onActivate: (id: number) => void,
): { element: HTMLElement; buttons: HTMLButtonElement[] } {
  const element = document.createElement('div');
  element.className = 'relative h-0 w-0';
  const animate = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const lines = entries
    .map(
      ({ at, origin }) =>
        `<line class="stroke-ink" stroke-width="1.5" x1="${origin.x}" y1="${origin.y}" x2="${at.x}" y2="${at.y}"/>` +
        `<circle class="fill-ink" cx="${origin.x}" cy="${origin.y}" r="2.5"/>`,
    )
    .join('');
  element.innerHTML =
    '<svg class="pointer-events-none absolute left-0 top-0 overflow-visible" width="1" height="1" aria-hidden="true">' +
    `${lines}</svg>`;
  if (animate) {
    element.firstElementChild?.animate([{ opacity: 0 }, { opacity: 1 }], { duration: SPREAD_ANIMATION_MS });
  }

  const buttons = entries.map(({ marker, at, origin }) => {
    const pin = createSpotPin(marker, onActivate);
    pin.setSelected(marker.id === selectedId);
    // 핀 버튼에는 배지를 걸려고 relative 클래스가 있다. 같은 속성의 클래스를 더하면 CSS 순서로 승패가 갈리므로 인라인 스타일로 덮는다.
    pin.element.style.position = 'absolute';
    pin.element.style.left = `${at.x - PIN_BUTTON_SIZE_PX / 2}px`;
    pin.element.style.top = `${at.y - PIN_BUTTON_SIZE_PX}px`;
    element.appendChild(pin.element);
    if (animate) {
      pin.element.animate(
        [{ transform: `translate(${origin.x - at.x}px, ${origin.y - at.y}px)` }, { transform: 'translate(0, 0)' }],
        { duration: SPREAD_ANIMATION_MS, easing: 'ease-out' },
      );
    }
    return pin.element;
  });

  return { element, buttons };
}

export function createClusterButton(
  cluster: ClusterData,
  onActivate: (cluster: ClusterData) => void,
): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className =
    'flex h-11 min-w-11 cursor-pointer items-center justify-center rounded-full border border-white bg-ink px-2 ' +
    'font-mono text-sm tabular-nums text-white ' +
    FOCUS_RING;
  button.textContent = cluster.count.toLocaleString('ko-KR');
  button.setAttribute('aria-label', `장소 ${cluster.count}곳, 눌러서 확대`);

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(cluster);
  });

  return button;
}

// 박지를 제보할 위치를 임시로 보여 주는 핀이다. 박지 핀과 같은 모양에 점선 테두리를 둬서 아직 등록 전임을 드러낸다.
// 위치는 제보 폼에서 글자로도 보여 주므로 스크린 리더에는 숨긴다. 핀 위를 다시 눌러도 지도 클릭으로 위치를 옮길 수 있게 클릭을 받지 않는다.
export function createDraftPin(): HTMLElement {
  const element = document.createElement('div');
  element.setAttribute('aria-hidden', 'true');
  element.className = 'pointer-events-none flex h-11 w-11 items-end justify-center';
  element.innerHTML =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">' +
    `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path class="fill-earth stroke-white" stroke-width="2" stroke-dasharray="4 3" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    iconMarkup('backpack', 'class="text-white" x="8" y="8" width="20" height="20"') +
    '</svg>';
  return element;
}

// 주소 검색으로 찾은 지점을 알려 주는 핀이다. 장소가 아니라서 누를 수 없고, 먹색 몸통 가운데 흰 점으로 장소 핀과 구분한다.
export function createSearchPin(): HTMLElement {
  const element = document.createElement('div');
  element.setAttribute('aria-hidden', 'true');
  element.className = 'pointer-events-none flex h-11 w-11 items-end justify-center';
  element.innerHTML =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">' +
    `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path class="fill-ink" d="${PIN_PATH}"/>` +
    '<circle class="fill-white" cx="18" cy="18" r="5"/>' +
    '</svg>';
  return element;
}
