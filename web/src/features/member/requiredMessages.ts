import { CUSTOM_DOMAIN } from '../../components/emailValue';
import type { EmailValue } from '../../components/emailValue';

const EMAIL_REQUIRED = '이메일을 입력해 주세요.';
export const PASSWORD_REQUIRED = '비밀번호를 입력해 주세요.';
export const CURRENT_PASSWORD_REQUIRED = '현재 비밀번호를 입력해 주세요.';
export const NEW_PASSWORD_REQUIRED = '새 비밀번호를 입력해 주세요.';
export const PASSWORD_CONFIRM_REQUIRED = '비밀번호를 한 번 더 입력해 주세요.';
export const NICKNAME_REQUIRED = '닉네임을 입력해 주세요.';
export const AGE_CONFIRM_REQUIRED = '만 14세 이상만 가입할 수 있어요.';
const EMAIL_DOMAIN_REQUIRED = '이메일 도메인을 골라 주세요.';
const EMAIL_CUSTOM_DOMAIN_REQUIRED = '도메인을 입력해 주세요.';

export const PASSWORD_CONFIRM_MISMATCH = '비밀번호가 서로 달라요.';

export function emailRequiredMessage(email: EmailValue): string | null {
  if (email.local.trim() === '') return EMAIL_REQUIRED;
  if (email.domainChoice === '') return EMAIL_DOMAIN_REQUIRED;
  if (email.domainChoice === CUSTOM_DOMAIN && email.customDomain.trim() === '') return EMAIL_CUSTOM_DOMAIN_REQUIRED;
  return null;
}
