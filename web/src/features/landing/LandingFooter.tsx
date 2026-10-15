import { useMemo } from 'react';
import { Link } from '../../app/router';
import { formatKstHourMinute, sunEventAt } from '../../lib/sun';

// 설악산 대청봉 근처 좌표다. 바닥글의 "오늘 설악산 일몰" 칸에 쓴다.
const SEORAKSAN = { lat: 38.12, lng: 128.46 };

const PLACES: ReadonlyArray<{ to: string; label: string }> = [
  { to: '/map', label: '지도' },
  { to: '/basecamps', label: '베이스캠프' },
  { to: '/memories', label: '피드' },
  { to: '/community', label: '커뮤니티' },
  { to: '/programs', label: '공식 행사' },
];

// 랜딩 끝의 칸 바닥글이다. 앱 화면의 바닥글(SiteFooter)과 같은 정보(메뉴, 데이터 출처, 고지)를 칸으로 나눠 담는다.
// 위 줄은 문장·고지, 메뉴, 오늘 일몰, 데이터 출처 칸이고, 아래 줄은 큰 이름 칸과 지도 열기 칸이다.
export function LandingFooter() {
  const sunset = useMemo(() => formatKstHourMinute(sunEventAt(SEORAKSAN.lat, SEORAKSAN.lng, new Date(), 'set')), []);

  return (
    <footer className="grid grid-cols-2 gap-1.5 bg-canvas p-1.5 lg:grid-cols-12 lg:gap-2 lg:p-2">
      <div className="col-span-2 flex min-h-40 flex-col justify-between gap-6 rounded-control bg-surface p-5 lg:col-span-4 lg:min-h-[220px] lg:p-6">
        <p className="text-2xl leading-snug font-bold tracking-[-0.02em]">산과 들에서 하룻밤, 자도 되는 곳에서 믿을 사람과.</p>
        <p className="text-sm text-ink-muted">
          공원 경계는 법적 효력이 없는 참고 자료예요. 뫼와들은 장소 정보만 안내하고, 야영 중 생긴 일에는 책임지지 않아요. © 2026 뫼와들
        </p>
      </div>
      <nav aria-label="바닥글 메뉴" className="flex min-h-40 flex-col gap-1.5 rounded-control bg-surface p-5 text-[17px] lg:col-span-3 lg:min-h-[220px] lg:p-6">
        {PLACES.map((place) => (
          <Link key={place.to} to={place.to} className="self-start hover:underline">
            {place.label}
          </Link>
        ))}
      </nav>
      <div className="flex min-h-40 flex-col justify-between rounded-control bg-surface p-5 lg:col-span-2 lg:min-h-[220px] lg:p-6">
        <p className="text-sm text-ink-muted">오늘 설악산 일몰</p>
        <p className="text-2xl font-bold tabular-nums">{sunset}</p>
      </div>
      <div className="col-span-2 flex min-h-40 flex-col justify-between gap-4 rounded-control bg-surface p-5 lg:col-span-3 lg:min-h-[220px] lg:p-6">
        <p className="text-sm text-ink-muted">데이터 출처</p>
        <p className="text-sm">한국관광공사 고캠핑, 산림청, 한국보호지역통합DB, 기상청, 한국천문연구원, 카카오맵</p>
      </div>
      <div className="col-span-2 order-first grid min-h-40 place-items-center rounded-control bg-ink text-surface lg:order-none lg:col-span-8 lg:min-h-[320px]">
        <span aria-hidden="true" className="text-[18vw] leading-none font-extrabold tracking-[-0.05em] lg:text-[9vw]">
          뫼와들
        </span>
      </div>
      <Link
        to="/map"
        className="col-span-2 order-first flex min-h-40 flex-col justify-between rounded-control bg-brand p-5 text-canvas transition-colors hover:bg-brand-deep lg:order-none lg:col-span-4 lg:min-h-[320px] lg:p-6"
      >
        <b className="text-[28px] font-extrabold tracking-[-0.03em]">지도 열기</b>
        <svg aria-hidden="true" viewBox="0 0 56 56" className="size-14 self-end" fill="none" stroke="currentColor" strokeWidth="4">
          <path d="M12 44 44 12M20 12h24v24" />
        </svg>
      </Link>
    </footer>
  );
}
