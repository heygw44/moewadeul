import { useRef, useState } from 'react';
import type { KeyboardEvent, MouseEvent, PointerEvent, ReactNode } from 'react';

type SheetSnap = 'peek' | 'half' | 'full';

type SheetProps = {
  snap: SheetSnap;
  onSnapChange: (snap: SheetSnap) => void;
  header?: ReactNode;
  toolbar?: ReactNode;
  children: ReactNode;
  label: string;
  aboveBottomNav?: boolean;
};

const SNAP_ORDER: readonly SheetSnap[] = ['peek', 'half', 'full'];

const SNAP_RATIO: Record<SheetSnap, number> = {
  peek: 0.28,
  half: 0.55,
  full: 0.92,
};

const SNAP_HEIGHT_CLASSES: Record<SheetSnap, string> = {
  peek: 'h-[28dvh]',
  half: 'h-[55dvh]',
  full: 'h-[92dvh]',
};

const FULL_ABOVE_NAV_CLASS = 'h-[calc(92dvh-var(--bottom-nav-h))]';

const SNAP_LABELS: Record<SheetSnap, string> = {
  peek: '접힘',
  half: '절반',
  full: '전체',
};

const NEXT_SNAP: Record<SheetSnap, SheetSnap> = {
  peek: 'half',
  half: 'full',
  full: 'peek',
};

const DRAG_THRESHOLD_PX = 6;

const DESKTOP_QUERY = '(min-width: 64rem)';

type DragState = {
  pointerId: number;
  startY: number;
  startHeight: number;
  bottomInset: number;
  moved: boolean;
};

function snapHeight(snap: SheetSnap, viewportHeight: number, bottomInset: number): number {
  const height = SNAP_RATIO[snap] * viewportHeight;
  return snap === 'full' ? height - bottomInset : height;
}

function nearestSnap(height: number, viewportHeight: number, bottomInset: number): SheetSnap {
  let best: SheetSnap = 'peek';
  let bestDistance = Number.POSITIVE_INFINITY;
  for (const snap of SNAP_ORDER) {
    const distance = Math.abs(height - snapHeight(snap, viewportHeight, bottomInset));
    if (distance < bestDistance) {
      best = snap;
      bestDistance = distance;
    }
  }
  return best;
}

export function Sheet({ snap, onSnapChange, header, toolbar, children, label, aboveBottomNav = false }: SheetProps) {
  const sectionRef = useRef<HTMLElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const suppressClickRef = useRef(false);
  const [dragHeight, setDragHeight] = useState<number | null>(null);

  function handlePointerDown(event: PointerEvent<HTMLButtonElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    const section = sectionRef.current;
    if (!section) return;
    suppressClickRef.current = false;
    dragRef.current = {
      pointerId: event.pointerId,
      startY: event.clientY,
      startHeight: section.getBoundingClientRect().height,
      bottomInset: parseFloat(getComputedStyle(section).bottom) || 0,
      moved: false,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const deltaY = event.clientY - drag.startY;
    if (!drag.moved && Math.abs(deltaY) < DRAG_THRESHOLD_PX) return;
    drag.moved = true;
    const viewportHeight = window.innerHeight;
    const minHeight = SNAP_RATIO.peek * viewportHeight;
    const maxHeight = snapHeight('full', viewportHeight, drag.bottomInset);
    setDragHeight(Math.min(maxHeight, Math.max(minHeight, drag.startHeight - deltaY)));
  }

  function finishDrag(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (!drag.moved) return;
    suppressClickRef.current = true;
    const finalHeight = drag.startHeight - (event.clientY - drag.startY);
    setDragHeight(null);
    const next = nearestSnap(finalHeight, window.innerHeight, drag.bottomInset);
    if (next !== snap) onSnapChange(next);
  }

  function handlePointerCancel(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    setDragHeight(null);
  }

  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    const fromKeyboard = event.detail === 0;
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      if (!fromKeyboard) return;
    }
    onSnapChange(NEXT_SNAP[snap]);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key !== 'Escape' || snap === 'peek') return;
    if (window.matchMedia(DESKTOP_QUERY).matches) return;
    event.preventDefault();
    onSnapChange('peek');
  }

  const dragging = dragHeight !== null;

  return (
    <section
      ref={sectionRef}
      data-sheet
      aria-label={label}
      onKeyDown={handleKeyDown}
      style={dragging ? { height: `${dragHeight}px` } : undefined}
      className={[
        'fixed inset-x-0 z-10 flex flex-col rounded-t-drawer bg-canvas shadow-raise',
        aboveBottomNav ? 'bottom-(--bottom-nav-h)' : 'bottom-0',
        dragging
          ? 'transition-none'
          : `${aboveBottomNav && snap === 'full' ? FULL_ABOVE_NAV_CLASS : SNAP_HEIGHT_CLASSES[snap]} transition-[height] duration-200 ease-out motion-reduce:transition-none`,
        'lg:static lg:z-auto lg:h-full lg:w-[440px] lg:shrink-0 lg:rounded-none lg:shadow-none',
      ].join(' ')}
    >
      <button
        type="button"
        aria-label={`시트 크기 바꾸기, 지금 ${SNAP_LABELS[snap]}`}
        onClick={handleClick}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={finishDrag}
        onPointerCancel={handlePointerCancel}
        className="flex h-11 w-full shrink-0 cursor-grab touch-none items-center justify-center active:cursor-grabbing lg:hidden"
      >
        <span aria-hidden="true" className="h-1.5 w-10 rounded-full bg-ink-faint/50" />
      </button>
      {toolbar && <div className="hidden shrink-0 flex-col gap-3 px-6 pt-4 lg:flex">{toolbar}</div>}
      {header && <div className="shrink-0 px-4 pb-3 text-ink lg:px-6 lg:pt-6 lg:pb-2">{header}</div>}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
    </section>
  );
}
