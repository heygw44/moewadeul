import { useCallback, useEffect, useState } from 'react';
import type { LatLng, MapView } from '../../map/types';

const DRAFT_FOCUS_LEVEL = 5;

export function usePlacementDraft(map: MapView | null, placing: boolean, initialDraft: LatLng | undefined) {
  const [draft, setDraft] = useState<LatLng | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [appliedDraftKey, setAppliedDraftKey] = useState<string | null>(null);

  if (!placing && submitted) setSubmitted(false);

  const initialLat = initialDraft?.lat;
  const initialLng = initialDraft?.lng;
  const initialDraftKey = initialDraft ? `${initialLat},${initialLng}` : null;
  if (initialDraftKey !== appliedDraftKey) {
    setAppliedDraftKey(initialDraftKey);
    if (initialDraft && initialDraftKey !== null) setDraft(initialDraft);
  }

  useEffect(() => {
    if (!map || !placing || submitted) return;
    const offClick = map.onMapClick(setDraft);
    return () => {
      offClick();
      map.showDraft(null);
      setDraft(null);
    };
  }, [map, placing, submitted]);

  useEffect(() => {
    map?.showDraft(draft);
  }, [map, draft]);

  useEffect(() => {
    if (!map || initialLat === undefined || initialLng === undefined) return;
    const { sw, ne } = map.getBounds();
    const inside = initialLat >= sw.lat && initialLat <= ne.lat && initialLng >= sw.lng && initialLng <= ne.lng;
    if (!inside) map.setCenter({ lat: initialLat, lng: initialLng }, Math.min(map.getLevel(), DRAFT_FOCUS_LEVEL));
  }, [map, initialLat, initialLng]);

  const finishPlacing = useCallback(() => {
    setSubmitted(true);
    setDraft(null);
  }, []);

  return { draft, setDraft, finishPlacing };
}
