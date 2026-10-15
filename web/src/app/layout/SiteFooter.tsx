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

const PLACES: ReadonlyArray<{ to: string; label: string }> = [
  { to: '/map', label: '지도' },
  { to: '/basecamps', label: '베이스캠프' },
  { to: '/memories', label: '피드' },
  { to: '/community', label: '커뮤니티' },
  { to: '/programs', label: '공식 행사' },
];

const LINK_CLASS = 'inline-flex min-h-11 items-center text-brand-ink hover:text-white hover:underline md:min-h-8';

// 랜딩과 지도가 없는 화면 맨 아래에 둔다. 지도 화면은 화면 전체를 지도가 쓰므로 바닥글이 없다.
// 화면 내용보다 커지지 않게 작게 둔다. 첫 줄은 이름과 주 화면 링크, 그 아래는 계정 링크, 데이터 출처, 고지 순서다.
// 칸은 선으로 나누지 않고 간격으로만 나눈다.
export function SiteFooter() {
  const session = useSession();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    // 바닥글은 포인트 면(깊은 숲) 전체 띠다. 짙은 바탕이라 글자는 흰색과 옅은 숲색이고, 포커스 링도 흰색으로 바꾼다.
    <footer className="bg-brand-deep text-[15px] break-keep text-white [&_:focus-visible]:outline-white">
      <div className="mx-auto flex max-w-screen-xl flex-col gap-6 px-4 py-10 lg:px-10 lg:py-12">
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <Link to="/" className="inline-flex min-h-11 items-center self-start text-2xl font-extrabold tracking-[-0.03em] text-canvas">
            뫼와들
          </Link>
          <nav aria-label="바닥글 메뉴" className="flex flex-wrap gap-x-6">
            {PLACES.map((place) => (
              <Link key={place.to} to={place.to} className="inline-flex min-h-11 items-center text-base font-semibold text-canvas hover:underline">
                {place.label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="flex flex-col gap-2 text-brand-ink">
          <p className="flex flex-wrap items-center gap-x-5">
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
            <a href="https://github.com/heygw44/moewadeul" target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
              GitHub
            </a>
            <a href="#top" className={LINK_CLASS}>
              맨 위로
            </a>
          </p>
          <p>데이터 출처: {DATA_SOURCES.join(' · ')}</p>
          <p className="max-w-[80ch]">
            공원 경계는 법적 효력이 없는 참고 자료예요. 공식 경계는 고시 도면에서 확인해 주세요. 뫼와들은 장소 정보만 안내하고, 야영 중에
            생긴 일에는 책임지지 않아요. © 2026 뫼와들
          </p>
        </div>
      </div>
    </footer>
  );
}
