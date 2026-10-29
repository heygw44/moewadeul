import type { ReactNode } from 'react';
import { BrandMark } from '../../components/BrandMark';
import { useSession } from '../../features/member/session';
import { Link } from '../router';
import { CONTACT_EMAIL } from '../legal/siteInfo';
import { DATA_SOURCES } from './dataSources';

const LINK_CLASS = 'inline-flex min-h-11 items-center text-sm text-ink-muted hover:text-ink hover:underline lg:min-h-8';
const PRIVACY_LINK_CLASS = 'inline-flex min-h-11 items-center text-sm font-bold text-ink hover:underline lg:min-h-8';

export function SiteFooter() {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    <footer className="bg-fill text-ink">
      <div className="mx-auto grid max-w-screen-xl grid-cols-2 gap-8 px-4 pt-14 pb-10 md:grid-cols-[2fr_1fr_1fr]">
        <div className="col-span-2 flex flex-col gap-2 md:col-span-1">
          <Link to="/" className="self-start text-ink">
            <BrandMark className="text-xl" />
          </Link>
          <p className="max-w-xs text-sm text-ink-muted">
            잘 수 있는 곳을 찾고 믿을 수 있는 사람과 함께 가는 백패킹 서비스예요.
          </p>
        </div>

        <FooterColumn title="서비스">
          <Link to="/map" className={LINK_CLASS}>
            지도
          </Link>
          <Link to="/basecamps" className={LINK_CLASS}>
            베이스캠프
          </Link>
          <Link to="/memories" className={LINK_CLASS}>
            피드
          </Link>
          <Link to="/community" className={LINK_CLASS}>
            커뮤니티
          </Link>
          <Link to="/programs" className={LINK_CLASS}>
            공식 행사
          </Link>
        </FooterColumn>

        <FooterColumn title="계정">
          {signedIn ? (
            <>
              <Link to="/me" className={LINK_CLASS}>
                내 정보
              </Link>
              <Link to="/me/notifications" className={LINK_CLASS}>
                알림
              </Link>
            </>
          ) : (
            <>
              <Link to="/login" className={LINK_CLASS}>
                로그인
              </Link>
              <Link to="/signup" className={LINK_CLASS}>
                회원가입
              </Link>
            </>
          )}
        </FooterColumn>
      </div>

      <div>
        <div className="mx-auto flex max-w-screen-xl flex-wrap items-center gap-x-5 px-4">
          <Link to="/privacy" className={PRIVACY_LINK_CLASS}>
            개인정보 처리방침
          </Link>
          <Link to="/terms" className={LINK_CLASS}>
            이용약관
          </Link>
        </div>
        <p className="mx-auto max-w-screen-xl px-4 pt-2 text-[0.9375rem] leading-relaxed text-ink-muted">
          <span className="block max-w-[72ch]">
            뫼와들은 개인이 운영하는 비영리 포트폴리오 서비스예요. 문의{' '}
            <a href={`mailto:${CONTACT_EMAIL}`} className="underline underline-offset-2 hover:text-ink">
              {CONTACT_EMAIL}
            </a>
          </span>
        </p>
        <p className="mx-auto max-w-screen-xl px-4 pt-2 text-[0.9375rem] leading-relaxed text-ink-muted">
          <span className="block max-w-[72ch]">데이터 · {DATA_SOURCES.join(' · ')}</span>
        </p>
        <div className="mx-auto flex max-w-screen-xl flex-col gap-2 px-4 pt-3 pb-10 text-[0.9375rem] text-ink-muted md:flex-row md:items-center md:justify-between">
          <p className="max-w-[72ch]">
            공원 경계는 법적 효력이 없는 참고 자료예요. 야영 중에 생긴 일에는 책임지지 않아요. 본인확인과 결제는 시연용
            가짜 구현이에요. 자세한 내용은 이용약관에서 확인해 주세요.
          </p>
          <p className="flex items-center gap-3">
            <span>© 2026 뫼와들</span>
            <a
              href="https://github.com/heygw44/moewadeul"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center underline underline-offset-2 hover:text-ink md:min-h-0"
            >
              GitHub
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}

function FooterColumn({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="mb-1 text-sm font-bold text-ink">{title}</h2>
      {children}
    </div>
  );
}
