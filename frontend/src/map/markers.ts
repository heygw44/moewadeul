import { ICON_PATHS } from '../components/iconPaths';
import type { IconName } from '../components/iconPaths';
import type { Offset } from './stackPins';
import type { ClusterData, MarkerKind, SpotMarkerData } from './types';

const KIND_STYLE: Record<MarkerKind, { label: string; icon: IconName; fill: string }> = {
  CAMPSITE: { label: '공공 야영장', icon: 'tent', fill: 'fill-brand' },
  FOREST: { label: '자연휴양림', icon: 'tree', fill: 'fill-brand' },
  BAKJI: { label: '박지', icon: 'backpack', fill: 'fill-bakji' },
};

const PIN_PATH = 'M18 2a14 14 0 0 1 4.6 27.2L18 42l-4.6-12.8A14 14 0 0 1 18 2z';

const OUTLINE_WIDTH = 6;
const WARNING_OUTLINE_WIDTH = 9;
const SELECTED_EXTRA_WIDTH = 4;

const FOCUS_RING =
  'outline-none focus-visible:ring-2 focus-visible:ring-info focus-visible:ring-offset-2 focus-visible:ring-offset-canvas';

function iconMarkup(name: IconName, attributes: string): string {
  return (
    `<svg ${attributes} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" ` +
    `stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name]}</svg>`
  );
}

function labelMarkup(label: string): string {
  const size = label.length >= 3 ? 10 : label.length === 2 ? 12.5 : 14;
  return (
    `<text x="18" y="16" text-anchor="middle" dominant-baseline="central" class="fill-white" ` +
    `font-size="${size}" font-weight="800" style="font-variant-numeric: tabular-nums">${label}</text>`
  );
}

export interface SpotPin {
  element: HTMLButtonElement;
  setSelected(selected: boolean): void;
}

export function createSpotPin(data: SpotMarkerData, onActivate: (id: number) => void, stackedCount = 0): SpotPin {
  const style = KIND_STYLE[data.kind];
  const baseOutline = data.warning ? WARNING_OUTLINE_WIDTH : OUTLINE_WIDTH;

  const button = document.createElement('button');
  button.type = 'button';
  button.className =
    'relative flex h-11 w-11 cursor-pointer items-end justify-center rounded-full origin-bottom ' +
    'transition-[scale] duration-150 motion-reduce:transition-none ' +
    FOCUS_RING;

  const labelParts = data.label ? [`${data.label}번`, data.name, style.label] : [data.name, style.label];
  if (data.warning) labelParts.push('공원 경계 경고');
  if (data.closed) labelParts.push('휴장');
  if (data.count !== undefined) labelParts.push(`모집 중인 베이스캠프 ${data.count}개`);
  if (stackedCount > 0) labelParts.push(`겹친 장소 ${stackedCount}곳 더, 눌러서 펼치기`);
  button.setAttribute('aria-label', labelParts.join(', '));

  const bodyAttributes = data.warning
    ? `class="${style.fill} stroke-warning-line" stroke-width="3"`
    : `class="${style.fill}"`;
  const content = data.label
    ? labelMarkup(data.label.replace(/[^0-9]/g, ''))
    : iconMarkup(style.icon, 'class="text-white" x="9" y="7" width="18" height="18"');
  let markup =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44" aria-hidden="true">' +
    `<path data-pin-outline class="fill-white stroke-white" stroke-width="${baseOutline}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path ${bodyAttributes} stroke-linejoin="round" d="${PIN_PATH}"/>` +
    content +
    '</svg>';

  if (data.closed) {
    markup +=
      '<span class="pointer-events-none absolute -right-0.5 -top-1 flex h-5 w-5 items-center justify-center rounded-full border border-white bg-ink text-white">' +
      iconMarkup('closed', 'width="14" height="14"') +
      '</span>';
  }
  if (data.count !== undefined) {
    markup +=
      '<span data-pin-count class="pointer-events-none absolute bottom-3 -right-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-surface px-1 font-mono text-xs font-extrabold tabular-nums text-brand ring-[1.5px] ring-brand"></span>';
  }
  if (stackedCount > 0) {
    markup +=
      '<span data-pin-stacked class="pointer-events-none absolute bottom-0 -left-2 flex h-5 min-w-5 items-center justify-center rounded-full border border-white bg-ink px-1 font-mono text-xs font-bold tabular-nums text-white"></span>';
  }
  button.innerHTML = markup;
  const countBadge = button.querySelector('[data-pin-count]');
  if (countBadge && data.count !== undefined) countBadge.textContent = String(data.count);
  const stackedBadge = button.querySelector('[data-pin-stacked]');
  if (stackedBadge) stackedBadge.textContent = `+${stackedCount}`;

  const outline = button.querySelector('[data-pin-outline]');

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(data.id);
  });

  function setSelected(selected: boolean) {
    button.classList.toggle('scale-125', selected);
    button.setAttribute('aria-pressed', String(selected));
    outline?.setAttribute('stroke-width', String(selected ? baseOutline + SELECTED_EXTRA_WIDTH : baseOutline));
    outline?.classList.toggle('stroke-white', !selected);
    outline?.classList.toggle('stroke-brand', selected);
  }
  setSelected(false);

  return { element: button, setSelected };
}

export function createSpotDot(data: SpotMarkerData, onActivate: (id: number) => void): SpotPin {
  const style = KIND_STYLE[data.kind];
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'flex h-11 w-11 cursor-pointer items-center justify-center rounded-full ' + FOCUS_RING;
  const labelParts = [data.name, style.label];
  if (data.warning) labelParts.push('공원 경계 경고');
  if (data.closed) labelParts.push('휴장');
  button.setAttribute('aria-label', labelParts.join(', '));
  const dot = document.createElement('span');
  button.appendChild(dot);

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(data.id);
  });

  function setSelected(selected: boolean) {
    dot.className = selected
      ? 'block size-[18px] rounded-full bg-brand ring-[3px] ring-white shadow-raise'
      : 'block size-2.5 rounded-full bg-ink ring-2 ring-white';
    button.setAttribute('aria-pressed', String(selected));
  }
  setSelected(false);

  return { element: button, setSelected };
}

const PIN_BUTTON_SIZE_PX = 44;
const SPREAD_ANIMATION_MS = 150;

export interface SpreadEntry {
  marker: SpotMarkerData;
  at: Offset;
  origin: Offset;
}

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
    'flex h-11 min-w-11 cursor-pointer items-center justify-center rounded-full bg-brand px-2 shadow-raise ' +
    'font-mono text-sm font-bold tabular-nums text-white ring-2 ring-white ' +
    FOCUS_RING;
  button.textContent = cluster.count.toLocaleString('ko-KR');
  button.setAttribute('aria-label', `장소 ${cluster.count}곳, 눌러서 확대`);

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate(cluster);
  });

  return button;
}

export function createDraftPin(): HTMLElement {
  const element = document.createElement('div');
  element.setAttribute('aria-hidden', 'true');
  element.className = 'pointer-events-none flex h-11 w-11 items-end justify-center';
  element.innerHTML =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">' +
    `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path class="fill-bakji stroke-white" stroke-width="2" stroke-dasharray="4 3" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    iconMarkup('backpack', 'class="text-white" x="9" y="7" width="18" height="18"') +
    '</svg>';
  return element;
}

export function createSearchPin(): HTMLElement {
  const element = document.createElement('div');
  element.setAttribute('aria-hidden', 'true');
  element.className = 'pointer-events-none flex h-11 w-11 items-end justify-center';
  element.innerHTML =
    '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44">' +
    `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
    `<path class="fill-ink" d="${PIN_PATH}"/>` +
    '<circle class="fill-white" cx="18" cy="16" r="5"/>' +
    '</svg>';
  return element;
}

export interface PhotoPin {
  element: HTMLButtonElement;
  yAnchor: number;
  setSelected(selected: boolean): void;
}

export function createPhotoPin(data: { thumbnailUrl?: string; label: string }, onActivate: () => void): PhotoPin {
  const button = document.createElement('button');
  button.type = 'button';
  button.setAttribute('aria-label', data.label);

  let yAnchor = 0.5;
  if (data.thumbnailUrl) {
    button.className =
      'relative block h-11 w-11 cursor-pointer overflow-hidden rounded-row border-2 border-white bg-bakji ' +
      'transition-[scale] duration-150 motion-reduce:transition-none ' +
      FOCUS_RING;
    const image = document.createElement('img');
    image.src = data.thumbnailUrl;
    image.alt = '';
    image.draggable = false;
    image.className = 'block h-full w-full object-cover';
    image.addEventListener('error', () => image.remove());
    button.appendChild(image);
  } else {
    yAnchor = 1;
    button.className =
      'relative flex h-11 w-11 cursor-pointer items-end justify-center rounded-control origin-bottom ' +
      'transition-[scale] duration-150 motion-reduce:transition-none ' +
      FOCUS_RING;
    button.innerHTML =
      '<svg class="block overflow-visible" width="36" height="44" viewBox="0 0 36 44" aria-hidden="true">' +
      `<path class="fill-white stroke-white" stroke-width="${OUTLINE_WIDTH}" stroke-linejoin="round" d="${PIN_PATH}"/>` +
      `<path class="fill-bakji" d="${PIN_PATH}"/>` +
      iconMarkup('image', 'class="text-white" x="9" y="7" width="18" height="18"') +
      '</svg>';
  }

  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onActivate();
  });

  function setSelected(selected: boolean) {
    button.classList.toggle('scale-125', selected);
    button.classList.toggle('ring-2', selected && Boolean(data.thumbnailUrl));
    button.classList.toggle('ring-white', selected && Boolean(data.thumbnailUrl));
    button.setAttribute('aria-pressed', String(selected));
  }
  setSelected(false);

  return { element: button, yAnchor, setSelected };
}
