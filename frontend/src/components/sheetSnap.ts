export type SheetSnap = 'peek' | 'half' | 'full';

export const SHEET_SNAP_DVH: Record<SheetSnap, number> = { peek: 28, half: 55, full: 92 };

export function sheetTopFromBottom(snap: SheetSnap, aboveBottomNav: boolean): string {
  if (snap === 'full' || !aboveBottomNav) return `${SHEET_SNAP_DVH[snap]}dvh`;
  return `calc(${SHEET_SNAP_DVH[snap]}dvh + var(--bottom-nav-h))`;
}

export function sheetHeight(snap: SheetSnap, aboveBottomNav: boolean): string {
  if (snap === 'full' && aboveBottomNav) return `calc(${SHEET_SNAP_DVH.full}dvh - var(--bottom-nav-h))`;
  return `${SHEET_SNAP_DVH[snap]}dvh`;
}
