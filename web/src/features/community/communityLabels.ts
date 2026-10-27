import { buttonClassName } from '../../components/buttonClass';
import type { CommunityReportReason } from '../../api/types';
import type { ChoiceOption } from '../../components/ChoiceGroup';

export const REPORT_REASON_OPTIONS: ReadonlyArray<ChoiceOption<CommunityReportReason>> = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ABUSE', label: '욕설·혐오' },
  { value: 'ILLEGAL_CAMPING', label: '불법 야영 유도' },
  { value: 'PRIVACY', label: '개인정보 노출' },
  { value: 'MONEY_SCAM', label: '금전 요구·사기' },
  { value: 'OTHER', label: '기타' },
];

export function parseSpotId(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

export const UNVERIFIED_WRITE_REASON = '이메일 인증을 마치면 쓸 수 있어요';

export const LINK_BUTTON_CLASS = buttonClassName('primary');
export const SECONDARY_LINK_CLASS = buttonClassName('secondary');
