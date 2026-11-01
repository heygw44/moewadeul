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
      <p className="font-semibold">이 조건으로 찾았어요</p>
      {summary.unparsed.length > 0 && (
        <p className="text-ink-muted">반영하지 못한 말: {summary.unparsed.join(' · ')}</p>
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
        <p className="text-ink-muted">
          검색할 조건을 찾지 못했어요. 지역 이름이나 장소 유형을 넣어 다시 검색해 주세요.
        </p>
      )}
    </div>
  );
}
