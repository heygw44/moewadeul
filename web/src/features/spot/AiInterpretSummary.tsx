import type { SpotSetting } from '../../api/types';
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

// 칩 줄 아래에 흰 면 한 줄로 띄운다. 상태 안내라서 상자(Notice)로 감싸지 않는다.
export function AiInterpretSummary({ summary, className }: { summary: AiSummary; className?: string }) {
  return (
    <div
      className={[
        'pointer-events-auto flex flex-col gap-0.5 rounded-control bg-surface px-3 py-2 text-sm text-ink shadow-raise',
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
