import type { ReactNode } from 'react';
import { BrandMark } from '../../components/BrandMark';
import { useSession } from '../../features/member/session';
import { Link } from '../router';

// 화면에 보여 주는 데이터의 출처다. 장소·경계는 공공데이터, 날씨·일몰은 기상청·천문연, 바탕 지도는 카카오맵이다.
const DATA_SOURCES = [
  '한국관광공사 고캠핑',
  '산림청 국립자연휴양림관리소',
  '전국휴양림표준데이터',
  '한국보호지역통합DB(KDPA)',
  '기상청',
  '한국천문연구원',
  '카카오맵',
];

const LINK_CLASS = 'inline-flex min-h-11 items-center text-sm text-brand-ink hover:text-white hover:underline lg:min-h-8';

// 랜딩과 지도가 없는 화면 맨 아래에 둔다. 지도 화면은 화면 전체를 지도가 쓰므로 바닥글이 없다.
// 위에서부터 글자 열 셋, 데이터 출처 한 줄, 고지 줄, 아래 끝에서 잘리는 큰 워드마크 순서다.
export function SiteFooter() {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    // 바닥글은 포인트 면(깊은 숲) 전체 띠다. 짙은 바탕이라 글자는 흰색과 옅은 숲색이고, 포커스 링도 흰색으로 바꾼다.
    <footer className="bg-brand text-white [&_:focus-visible]:outline-white">
      <div className="mx-auto grid max-w-screen-xl gap-8 px-4 py-10 md:grid-cols-[2fr_1fr_1fr]">
        <div className="flex flex-col gap-2">
          <Link to="/" className="self-start text-white">
            <BrandMark className="text-lg" />
          </Link>
          <p className="max-w-xs text-sm text-brand-ink">
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

      <div className="border-t border-white/15">
        {/* 출처는 판단 근거라서 지우지 않고, 열 하나를 차지하지 않게 한 줄로 모은다. */}
        <p className="mx-auto max-w-screen-xl px-4 pt-5 text-xs leading-relaxed text-brand-ink">
          데이터 · {DATA_SOURCES.join(' · ')}
        </p>
        <div className="mx-auto flex max-w-screen-xl flex-col gap-2 px-4 pt-3 pb-5 text-xs text-brand-ink md:flex-row md:items-center md:justify-between">
          <p>
            공원 경계는 법적 효력이 없는 참고 자료예요. 공식 경계는 고시 도면에서 확인해 주세요. 뫼와들은 장소 정보만
            안내하고, 야영 중에 생긴 일에는 책임지지 않아요.
          </p>
          <p className="flex items-center gap-3">
            <span>© 2026 뫼와들</span>
            <a
              href="https://github.com/heygw44/moewadeul"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center underline underline-offset-2 hover:text-white md:min-h-0"
            >
              GitHub
            </a>
          </p>
        </div>
      </div>

      {/* 화면 폭만 한 워드마크를 아래 끝에서 잘리게 깐다. 장식이라 스크린 리더와 선택에서 뺀다.
          글자 크기를 화면 폭에 맞추고, 높이를 글자보다 낮게 잡아 아래쪽을 잘라 낸다. */}
      <div aria-hidden="true" className="mx-auto h-[0.62em] max-w-screen-xl overflow-hidden px-4 text-[30vw] select-none xl:text-[26rem]">
        <span className="block leading-[0.8] font-bold tracking-tight text-white/10">뫼와들</span>
      </div>
    </footer>
  );
}

function FooterColumn({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="mb-1 text-sm text-white">{title}</h2>
      {children}
    </div>
  );
}
