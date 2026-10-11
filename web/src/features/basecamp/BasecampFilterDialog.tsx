import { useState } from 'react';
import { Button } from '../../components/Button';
import { Checkbox } from '../../components/Checkbox';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/icons';
import { TextField } from '../../components/TextField';
import { ToggleChip } from '../../components/ToggleChip';
import { SPOT_TYPE_META } from '../spot/spotLabels';
import { SPOT_TYPE_ORDER } from '../spot/spotFilters';
import { NIGHT_OPTIONS, NO_BASECAMP_FILTERS, isDateRangeReversed } from './basecampFilters';
import type { BasecampFilters } from './basecampFilters';

type BasecampFilterDialogProps = {
  initial: BasecampFilters;
  onApply: (filters: BasecampFilters) => void;
  onClose: () => void;
};

function toggled<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value];
}

// 대화상자 안에서는 고르는 중인 값을 따로 들고 있다가, 적용을 눌렀을 때만 검색에 반영한다.
// 출발일 범위가 거꾸로면 서버가 400으로 거부하므로 적용하지 못하게 막고 이유를 보여 준다.
export function BasecampFilterDialog({ initial, onApply, onClose }: BasecampFilterDialogProps) {
  const [draft, setDraft] = useState<BasecampFilters>(initial);
  const reversed = isDateRangeReversed(draft);

  return (
    <Dialog title="베이스캠프 조건" onClose={onClose}>
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          if (!reversed) onApply(draft);
        }}
        className="flex flex-col gap-5"
      >
        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 text-sm font-medium text-ink">출발일</legend>
          <div className="grid grid-cols-2 gap-2">
            <TextField
              label="출발일 시작"
              type="date"
              value={draft.fromDate}
              onChange={(event) => setDraft({ ...draft, fromDate: event.target.value })}
            />
            <TextField
              label="출발일 끝"
              type="date"
              value={draft.toDate}
              min={draft.fromDate || undefined}
              onChange={(event) => setDraft({ ...draft, toDate: event.target.value })}
            />
          </div>
          {reversed && (
            <p role="alert" className="flex items-start gap-1 text-sm text-danger">
              <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
              <span>출발일 끝이 시작보다 빠를 수 없어요.</span>
            </p>
          )}
          <Checkbox
            label="빈자리 있는 것만"
            checked={draft.hasVacancy}
            onChange={(event) => setDraft({ ...draft, hasVacancy: event.target.checked })}
          />
        </fieldset>

        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">장소 유형</legend>
          <div className="flex flex-wrap gap-2">
            {SPOT_TYPE_ORDER.map((type) => (
              <ToggleChip
                key={type}
                pressed={draft.types.includes(type)}
                onToggle={() => setDraft({ ...draft, types: toggled(draft.types, type) })}
              >
                {SPOT_TYPE_META[type].label}
              </ToggleChip>
            ))}
          </div>
        </fieldset>

        <fieldset className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-ink">박 수</legend>
          <div className="flex flex-wrap gap-2">
            {NIGHT_OPTIONS.map((night) => (
              <ToggleChip
                key={night}
                pressed={draft.nights.includes(night)}
                onToggle={() => setDraft({ ...draft, nights: toggled(draft.nights, night) })}
              >
                {night}박
              </ToggleChip>
            ))}
          </div>
          <p className="text-sm text-ink-muted">고르지 않으면 모든 유형과 박 수를 보여 줘요.</p>
        </fieldset>

        <fieldset className="flex min-w-0 flex-col">
          <legend className="mb-1 text-sm font-medium text-ink">합류 조건</legend>
          <Checkbox
            label="본인확인만 하면 합류할 수 있는 곳"
            hint="신뢰 회원(단계 2)을 요구하는 베이스캠프를 빼요."
            checked={draft.maxTrustLevel !== null}
            onChange={(event) => setDraft({ ...draft, maxTrustLevel: event.target.checked ? 1 : null })}
          />
          <Checkbox
            label="연령 제한 없는 곳"
            checked={draft.noAgeLimit}
            onChange={(event) => setDraft({ ...draft, noAgeLimit: event.target.checked })}
          />
          <Checkbox
            label="동성만 받지 않는 곳"
            checked={draft.noSameGenderOnly}
            onChange={(event) => setDraft({ ...draft, noSameGenderOnly: event.target.checked })}
          />
        </fieldset>

        <div className="flex gap-2 border-t border-contour pt-4">
          <Button type="button" variant="ghost" className="shrink-0" onClick={() => setDraft(NO_BASECAMP_FILTERS)}>
            초기화
          </Button>
          <Button type="submit" fullWidth disabled={reversed}>
            적용
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
