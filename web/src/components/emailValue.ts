export type EmailValue = {
  local: string;
  domainChoice: string;
  customDomain: string;
};

export const CUSTOM_DOMAIN = 'custom';

export const EMAIL_DOMAINS = ['naver.com', 'gmail.com', 'daum.net', 'hanmail.net', 'kakao.com', 'nate.com'] as const;

export const EMPTY_EMAIL: EmailValue = { local: '', domainChoice: '', customDomain: '' };

export function composeEmail(value: EmailValue): string {
  const domain = value.domainChoice === CUSTOM_DOMAIN ? value.customDomain : value.domainChoice;
  return `${value.local.trim()}@${domain.trim()}`;
}

// 브라우저 자동 완성이나 붙여넣기는 전체 주소를 아이디 칸에 넣을 수 있다. 마지막 @를 기준으로 나눠 도메인 칸에 채운다.
export function splitEmail(raw: string, current: EmailValue): EmailValue {
  const at = raw.lastIndexOf('@');
  if (at < 0) return { ...current, local: raw };
  const local = raw.slice(0, at).trim();
  const domain = raw.slice(at + 1).trim();
  if (domain === '') return { ...current, local };
  if ((EMAIL_DOMAINS as readonly string[]).includes(domain.toLowerCase())) {
    return { local, domainChoice: domain.toLowerCase(), customDomain: '' };
  }
  return { local, domainChoice: CUSTOM_DOMAIN, customDomain: domain };
}
