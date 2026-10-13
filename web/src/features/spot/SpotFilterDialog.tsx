import { useState } from 'react';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { Dialog } from '../../components/Dialog';
import { ToggleChip } from '../../components/ToggleChip';
import { SPOT_SETTING_LABELS, SPOT_TYPE_META } from './spotLabels';
import { NO_SPOT_FILTERS, SPOT_SETTING_ORDER, SPOT_TYPE_ORDER } from './spotFilters';
import type { SpotFilters } from './spotFilters';

type SpotFilterDialogProps = {
  initial: SpotFilters;
  onApply: (filters: SpotFilters) => void;
  onClose: () => void;
};

// 대화상자 안에서는 고르는 중인 값을 따로 들고 있다가, 적용을 눌렀을 때만 지도에 반영한다.
// 그래야 고르는 동안 지도가 매번 다시 조회되지 않는다.
export function SpotFilterDialog({ initial, onApply, onClose }: SpotFilterDialogProps) {
  const [draft, setDraft] = useState<SpotFilters>(initial);

  function toggleType(type: (typeof SPOT_TYPE_ORDER)[number]) {
    setDraft((previous) => ({
      ...previous,
      types: previous.types.includes(type)
        ? previous.types.filter((value) => value !== type)
        : [...previous.types, type],
    }));
  }

  function toggleSetting(setting: (typeof SPOT_SETTING_ORDER)[number]) {
    setDraft((previous) => ({
      ...previous,
      settings: previous.settings.includes(setting)
        ? previous.settings.filter((value) => value !== setting)
        : [...previous.settings, setting],
    }));
  }

  return (
    <Dialog title="장소 조건" onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onApply(draft);
        }}
        className="flex flex-col gap-5"
      >
        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">장소 유형</legend>
          <div className="flex flex-wrap gap-2">
            {SPOT_TYPE_ORDER.map((type) => (
              <ToggleChip key={type} pressed={draft.types.includes(type)} onToggle={() => toggleType(type)}>
                {SPOT_TYPE_META[type].label}
              </ToggleChip>
            ))}
          </div>
          <p className="text-sm text-ink-muted">고르지 않으면 모든 유형이 보여요.</p>
        </fieldset>

        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">입지</legend>
          <div className="flex flex-wrap gap-2">
            {SPOT_SETTING_ORDER.map((setting) => (
              <ToggleChip
                key={setting}
                pressed={draft.settings.includes(setting)}
                onToggle={() => toggleSetting(setting)}
              >
                {SPOT_SETTING_LABELS[setting]}
              </ToggleChip>
            ))}
          </div>
          <p className="text-sm text-ink-muted">고르면 입지 정보가 있는 공공 야영장만 보여요. 박지와 자연휴양림은 빠져요.</p>
        </fieldset>

        <fieldset className="flex min-w-0 flex-col">
          <legend className="mb-1 text-sm font-medium text-ink">박지 시설</legend>
          <Checkbox
            label="식수 있는 박지만"
            checked={draft.hasWater}
            onChange={(event) => setDraft({ ...draft, hasWater: event.target.checked })}
          />
          <Checkbox
            label="화장실 있는 박지만"
            checked={draft.hasToilet}
            onChange={(event) => setDraft({ ...draft, hasToilet: event.target.checked })}
          />
          <p className="text-sm text-ink-muted">야영장과 자연휴양림은 이 조건과 상관없이 보여요.</p>
        </fieldset>

        <fieldset className="flex min-w-0 flex-col">
          <legend className="mb-1 text-sm font-medium text-ink">그 밖의 조건</legend>
          <Checkbox
            label="공원 경계 경고가 있는 곳 빼기"
            checked={draft.excludeWarning}
            onChange={(event) => setDraft({ ...draft, excludeWarning: event.target.checked })}
          />
          <Checkbox
            label="오늘 휴장인 곳 빼기"
            hint="공공데이터의 휴장 정보는 실제와 다를 수 있어요."
            checked={draft.hideClosed}
            onChange={(event) => setDraft({ ...draft, hideClosed: event.target.checked })}
          />
        </fieldset>

        <div className="flex gap-2 border-t border-line pt-4">
          <Button type="button" variant="ghost" className="shrink-0" onClick={() => setDraft(NO_SPOT_FILTERS)}>
            초기화
          </Button>
          <Button type="submit" fullWidth>
            적용
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
