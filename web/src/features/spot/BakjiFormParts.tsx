import type { ReactNode } from 'react';
import type { BakjiSubmissionResponse, SignalLevel } from '../../api/types';
import { Link } from '../../app/router';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Icon } from '../../components/icons';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import type { LatLng } from '../../map/types';
import { PRIMARY_LINK_CLASS } from './bakjiFormModel';
import type { BakjiFormValues, FieldErrors, GroundChoice, Unknown, YesNo } from './bakjiFormModel';
import { LocationLabel } from './LocationLabel';
import { ParkWarningNotice } from './ParkWarningNotice';
import { SIGNAL_LEVEL_LABELS } from './spotLabels';

const NAME_MAX = 100;
const DESCRIPTION_MAX = 2000;

const YES_NO_OPTIONS: ChoiceOption<YesNo>[] = [
  { value: 'YES', label: '있음' },
  { value: 'NO', label: '없음' },
];

const SIGNAL_OPTIONS: ChoiceOption<Unknown | SignalLevel>[] = [
  { value: 'UNKNOWN', label: '모름' },
  { value: 'NONE', label: SIGNAL_LEVEL_LABELS.NONE },
  { value: 'WEAK', label: SIGNAL_LEVEL_LABELS.WEAK },
  { value: 'GOOD', label: SIGNAL_LEVEL_LABELS.GOOD },
];

export function BakjiFields({
  draft,
  values,
  onChange,
  fieldErrors,
  groundOptions,
  groundHint,
}: {
  draft: LatLng | null;
  values: BakjiFormValues;
  onChange: (values: BakjiFormValues) => void;
  fieldErrors: FieldErrors;
  groundOptions: ChoiceOption<GroundChoice>[];
  groundHint?: string;
}) {
  const set = <K extends keyof BakjiFormValues>(key: K, value: BakjiFormValues[K]) =>
    onChange({ ...values, [key]: value });

  return (
    <>
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium text-ink">위치</p>
        {draft === null ? (
          <p className="text-base text-ink">지도를 눌러 박지 위치를 찍어 주세요</p>
        ) : (
          <>
            <LocationLabel position={draft} />
            <p className="text-sm text-ink-muted">지도를 다시 누르면 위치가 바뀌어요.</p>
          </>
        )}
        {fieldErrors.location && (
          <p role="alert" className="flex items-start gap-1 text-sm text-danger">
            <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
            <span>{fieldErrors.location}</span>
          </p>
        )}
      </div>

      <TextField
        label="이름"
        name="name"
        maxLength={NAME_MAX}
        value={values.name}
        onChange={(event) => set('name', event.target.value)}
        error={fieldErrors.name}
      />
      <TextArea
        label="설명 (선택)"
        name="description"
        maxLength={DESCRIPTION_MAX}
        value={values.description}
        onChange={(event) => set('description', event.target.value)}
        error={fieldErrors.description}
      />
      <ChoiceGroup
        legend="식수"
        name="hasWater"
        options={YES_NO_OPTIONS}
        value={values.hasWater}
        onChange={(value) => set('hasWater', value)}
        error={fieldErrors.hasWater}
      />
      <ChoiceGroup
        legend="화장실"
        name="hasToilet"
        options={YES_NO_OPTIONS}
        value={values.hasToilet}
        onChange={(value) => set('hasToilet', value)}
        error={fieldErrors.hasToilet}
      />
      <ChoiceGroup
        legend="통신 상태 (선택)"
        name="signalLevel"
        options={SIGNAL_OPTIONS}
        value={values.signalLevel}
        onChange={(value) => set('signalLevel', value)}
        error={fieldErrors.signalLevel}
      />
      <ChoiceGroup
        legend="바닥 (선택)"
        name="groundType"
        options={groundOptions}
        value={values.groundType}
        onChange={(value) => set('groundType', value)}
        error={fieldErrors.groundType}
        hint={groundHint}
      />
    </>
  );
}

export function BakjiResultView({
  title,
  linkLabel,
  result,
  warnedNote,
}: {
  title: string;
  linkLabel: string;
  result: BakjiSubmissionResponse;
  warnedNote?: ReactNode;
}) {
  const { parkWarning, guide, notice, duplicateCandidates } = result;

  return (
    <div role="status" className="flex flex-col gap-4">
      <h2 className="text-xl font-bold tracking-tight text-ink">{title}</h2>

      {parkWarning.warned ? (
        <>
          <ParkWarningNotice warning={parkWarning} />
          {warnedNote}
        </>
      ) : (
        <>
          <p className="text-base text-ink">{guide}</p>
          {notice && <p className="text-sm text-ink-muted">{notice}</p>}
        </>
      )}

      {duplicateCandidates.length > 0 && (
        <section aria-labelledby="duplicate-title" className="flex flex-col gap-2">
          <h3 id="duplicate-title" className="text-[1.0625rem] font-extrabold text-ink">
            50m 안에 비슷한 박지가 있어요
          </h3>
          <p className="text-sm text-ink">같은 곳이라면 아래 박지의 정보를 확인해 주세요.</p>
          <ul className="flex flex-col gap-0.5 rounded-sheet bg-fill p-1.5">
            {duplicateCandidates.map((candidate) => (
              <li key={candidate.spotId}>
                <Link
                  to={`/spots/${candidate.spotId}`}
                  className="flex min-h-11 items-center justify-between gap-3 rounded-row px-3 py-2 text-accent underline underline-offset-2 hover:bg-fill-strong"
                >
                  <span className="min-w-0 break-words">{candidate.name}</span>
                  <span className="shrink-0 font-mono text-sm tabular-nums text-ink-muted">{candidate.distanceM}m</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex flex-wrap gap-2">
        <Link to={`/spots/${result.spotId}`} className={PRIMARY_LINK_CLASS}>
          {linkLabel}
        </Link>
      </div>
    </div>
  );
}
