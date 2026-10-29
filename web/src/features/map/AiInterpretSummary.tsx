import type { SpotSetting } from '../../api/types';
import { SPOT_SETTING_LABELS } from '../../lib/spotLabels';

export type AiSummary = {
  unparsed: string[];
  droppedSettings?: SpotSetting[];
  missingRegion?: string;
  empty: boolean;
};

export function AiInterpretSummary({ summary, className }: { summary: AiSummary; className?: string }) {
  return (
    <div
      className={[
        'pointer-events-auto flex flex-col gap-0.5 rounded-sheet bg-surface px-4 py-3 text-sm text-ink shadow-raise',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <p className="font-semibold">AI가 문장을 해석했어요</p>
      {summary.unparsed.length > 0 && (
        <p className="text-ink-muted">해석하지 못한 말: {summary.unparsed.join(' · ')}</p>
      )}
      {summary.missingRegion !== undefined && (
        <p className="text-ink-muted">'{summary.missingRegion}' 지역을 찾지 못해서 지도는 옮기지 않았어요.</p>
      )}
      {summary.droppedSettings !== undefined && summary.droppedSettings.length > 0 && (
        <p className="text-ink-muted">
          입지({summary.droppedSettings.map((setting) => SPOT_SETTING_LABELS[setting]).join('·')})는 공공 야영장에만
          있는 정보라서 조건에서 뺐어요.
        </p>
      )}
      {summary.empty && (
        <p className="text-ink-muted">문장에서 찾을 조건이 없었어요. 지역 이름이나 장소 유형을 넣어 다시 써 주세요.</p>
      )}
    </div>
  );
}
