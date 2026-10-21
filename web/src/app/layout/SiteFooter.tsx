import type { ReactNode } from 'react';
import { BrandMark } from '../../components/BrandMark';
import { useSession } from '../../features/member/session';
import { Link } from '../router';
import { DATA_SOURCES } from './dataSources';

const LINK_CLASS = 'inline-flex min-h-11 items-center text-sm text-ink-muted hover:text-ink hover:underline lg:min-h-8';

// 랜딩과 지도가 없는 화면 맨 아래에 둔다. 지도 화면은 화면 전체를 지도가 쓰므로 바닥글이 없다.
// 위에서부터 글자 열 셋, 데이터 출처 한 줄, 고지 줄 순서다.
export function SiteFooter() {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    // 바닥글은 세이지 면이다. 흰 본문과 색 한 단계로 나누고, 안의 묶음도 선 대신 간격으로 나눈다.
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
        {/* 출처는 판단 근거라서 지우지 않고, 열 하나를 차지하지 않게 한 줄로 모은다. */}
        <p className="mx-auto max-w-screen-xl px-4 pt-2 text-[0.9375rem] leading-relaxed text-ink-muted">
          <span className="block max-w-[72ch]">데이터 · {DATA_SOURCES.join(' · ')}</span>
        </p>
        <div className="mx-auto flex max-w-screen-xl flex-col gap-2 px-4 pt-3 pb-10 text-[0.9375rem] text-ink-muted md:flex-row md:items-center md:justify-between">
          <p className="max-w-[72ch]">
            공원 경계는 법적 효력이 없는 참고 자료예요. 공식 경계는 고시 도면에서 확인해 주세요. 뫼와들은 장소 정보만
            안내하고, 야영 중에 생긴 일에는 책임지지 않아요. 본인확인과 결제는 시연용 가짜 구현이에요.
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
