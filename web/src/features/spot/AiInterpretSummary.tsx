import type { SpotSetting } from '../../api/types';
import type { ActiveFilterChip } from '../../components/ActiveFilterChips';
import { Icon } from '../../components/icons';
import { SPOT_SETTING_LABELS } from './spotLabels';

// AI가 문장을 읽은 결과 중 조건 칩에 담기지 않는 것을 알린다.
export type AiSummary = {
  // 조건으로 바꾸지 못한 말이다.
  unparsed: string[];
  // 유형과 맞지 않아 조건에서 뺀 입지다.
  droppedSettings?: SpotSetting[];
  // 문장에 지역 이름이 있었지만 지도에서 찾지 못했을 때 그 이름이다.
  missingRegion?: string;
  // 지역이나 조건을 하나도 읽지 못했을 때 켠다.
  empty: boolean;
};

const AI_PRIVACY = '문장은 OpenAI가 검색 조건으로만 바꿔요. 장소 정보는 보내지 않아요.';

// AI가 읽어 낸 조건을 먹색 칩으로 보여 주는 카드다. 칩은 지도 화면의 적용한 조건과 같은 것이라, 누르면 그 조건만 빠지고 목록이 다시 바뀐다.
// 칩으로 담지 못한 말과 지역은 칩 아래 한 단락으로 알린다.
export function AiInterpretSummary({
  summary,
  chips,
  className,
}: {
  summary: AiSummary;
  chips: ReadonlyArray<ActiveFilterChip>;
  className?: string;
}) {
  const notes: string[] = [];
  if (summary.unparsed.length > 0) notes.push(`조건으로 읽지 못한 말: ${summary.unparsed.join(' · ')}.`);
  if (summary.missingRegion !== undefined) notes.push(`'${summary.missingRegion}' 지역을 찾지 못해서 지도는 옮기지 않았어요.`);
  if (summary.droppedSettings !== undefined && summary.droppedSettings.length > 0) {
    notes.push(
      `입지(${summary.droppedSettings.map((setting) => SPOT_SETTING_LABELS[setting]).join('·')})는 공공 야영장에만 있는 정보라서 조건에서 뺐어요.`,
    );
  }
  if (summary.empty) notes.push('문장에서 찾을 조건이 없었어요. 지역 이름이나 장소 유형을 넣어 다시 써 주세요.');

  return (
    <section
      aria-label="AI 검색 조건"
      className={[
        'pointer-events-auto flex flex-col gap-2.5 rounded-control bg-surface px-4 py-3.5 text-sm text-ink shadow-raise',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <h2 className="text-[15px] font-bold">이렇게 찾았어요</h2>
      {chips.length > 0 && (
        <ul className="flex flex-wrap gap-1.5">
          {chips.map((chip) => (
            <li key={chip.key}>
              <button
                type="button"
                aria-label={`${chip.label} 조건 빼기`}
                onClick={chip.onRemove}
                className="inline-flex min-h-11 items-center gap-1.5 rounded-control bg-ink pr-2.5 pl-3 text-sm font-semibold text-surface transition-[transform,background-color] duration-150 hover:bg-ink/85 active:scale-[0.97]"
              >
                {chip.label}
                <Icon name="close" size={14} className="shrink-0 opacity-70" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {notes.length > 0 && <p className="text-ink-muted">{notes.join(' ')}</p>}
      <p className="text-ink-muted">{AI_PRIVACY}</p>
    </section>
  );
}
