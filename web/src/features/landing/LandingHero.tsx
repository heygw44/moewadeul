import type { CSSProperties } from 'react';
import { Link, useLocation } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';

// 첫 화면이다. 왼쪽 위에 제목과 한 문장, 버튼 하나를 두고, 화면 오른쪽 아래를 깊은 숲 무대가 차지한다.
// 무대 위에는 공원 경계 경고가 붙은 박지의 장소 패널 조각을 실제 크기로 올린다. 사진 대신 제품이 무엇을 하는지 보여 주기 위해서다.
// 조각은 실제 장소 상세 컴포넌트가 아니라 랜딩 전용 정적 조각이고, 장소 이름은 예시다.
export function LandingHero() {
  return (
    <section aria-labelledby="landing-title" className="relative overflow-hidden lg:min-h-svh">
      <LandingTopBar />
      <div className="relative z-10 mx-auto max-w-screen-xl px-4 pt-8 lg:px-10 lg:pt-[74px]">
        <h1
          id="landing-title"
          className="landing-rise text-[11vw] leading-[1.04] font-extrabold tracking-[-0.045em] text-ink lg:text-[clamp(52px,5.4vw,84px)]"
        >
          자도 되는 곳에서,
          <br />
          믿을 사람과
        </h1>
        <p className="landing-rise mt-5 max-w-[40ch] text-[17px] leading-relaxed text-ink lg:text-lg" style={{ '--i': 1 } as CSSProperties}>
          뫼와들은 공원 경계 안쪽 박지를 미리 경고하고, 본인확인한 사람끼리 하룻밤을 함께 묵게 이어 주는 백패킹 지도예요.
        </p>
        <div className="landing-rise mt-7" style={{ '--i': 2 } as CSSProperties}>
          <Link to="/map" className={buttonClassName('primary')}>
            지도 열기
          </Link>
        </div>
      </div>

      {/* 데스크톱은 화면 오른쪽 아래를 차지하고, 모바일은 글 아래에 화면 폭으로 붙인다. 조각이 넘치면 오른쪽·아래 끝에서 잘린다. */}
      <div className="landing-stage relative -mx-0 mt-10 h-[600px] overflow-hidden bg-brand lg:absolute lg:inset-x-auto lg:top-[40%] lg:right-0 lg:bottom-0 lg:left-[36%] lg:mt-0 lg:h-auto">
        <div className="landing-stage-fragment absolute top-10 left-4 w-[560px] lg:top-14 lg:left-14 lg:w-[620px]">
          <SpotPanelFragment />
        </div>
      </div>
    </section>
  );
}

// 무대 위 장소 패널 조각이다. 장소 상세 화면의 경고 상자, 가까운 지정 야영장, 베이스캠프 열기 금지를 줄여 담는다.
function SpotPanelFragment() {
  return (
    <article data-ui aria-label="장소 상세 화면 예시" className="rounded-t-sheet bg-surface px-6 pt-5 pb-7 text-[15px] text-ink">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-[22px] font-extrabold tracking-[-0.02em]">울산바위 아래 너럭바위</h2>
          <p className="mt-0.5 text-sm text-ink-muted">박지 · 강원 속초시 설악동 · 회원 제보</p>
        </div>
        <span aria-hidden="true" className="grid size-8 place-items-center rounded-control bg-fill text-ink-muted">
          <Icon name="close" size={16} />
        </span>
      </div>
      <div className="mt-4 flex flex-col gap-2 rounded-control bg-warning-soft px-4 py-4">
        <span className="inline-flex items-center gap-1.5 font-semibold text-warning">
          <Icon name="alert" size={16} />
          공원 경계 안으로 보여요
        </span>
        <p>설악산국립공원 안이에요. 자연공원에서는 지정된 야영장 밖 야영과 취사가 금지돼 있고, 어기면 과태료 대상이에요.</p>
        <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-1.5 text-sm">
          <dt className="text-ink-muted">경계 자료</dt>
          <dd className="text-right">한국보호지역통합DB(KDPA)</dd>
          <dt className="text-ink-muted">자료 기준일</dt>
          <dd className="text-right tabular-nums">2025-12-31</dd>
        </dl>
      </div>
      <h3 className="mt-5 mb-2.5 text-sm font-semibold text-ink-muted">가까운 지정 야영장</h3>
      <ul className="flex flex-col gap-1">
        <li className="flex items-center justify-between rounded-control bg-fill px-3.5 py-3">
          <b>설악동자동차야영장</b>
          <span className="text-sm text-ink-muted tabular-nums">2.1km · 공공 야영장</span>
        </li>
        <li className="flex items-center justify-between rounded-control bg-canvas px-3.5 py-3">
          <b>설악산 오색 야영장</b>
          <span className="text-sm text-ink-muted tabular-nums">11.4km · 공공 야영장</span>
        </li>
      </ul>
      <div className="mt-4 flex items-center justify-between gap-4 rounded-control bg-canvas px-3.5 py-3.5 text-sm text-ink-muted">
        <span>경고가 붙은 박지라 베이스캠프를 열 수 없어요</span>
        <span className="inline-flex min-h-9 items-center rounded-control bg-fill px-3.5 font-semibold">베이스캠프 열기</span>
      </div>
    </article>
  );
}

// 첫 화면 맨 위 줄이다. 랜딩은 붙어 다니는 머리글 대신 이 줄만 둔다.
function LandingTopBar() {
  const session = useSession();
  const { pathname, search } = useLocation();
  const signedIn = session.status === 'authenticated' && session.me !== null;

  return (
    <header className="relative z-10 mx-auto flex min-h-[76px] max-w-screen-xl items-center justify-between px-4 text-[15px] lg:px-10">
      <Link to="/" className="text-[22px] font-extrabold tracking-[-0.04em] text-ink">
        뫼와들
      </Link>
      <nav aria-label="주 메뉴" className="flex items-center gap-7">
        <span className="hidden items-center gap-7 sm:flex">
          <TopLink to="/map">지도</TopLink>
          <TopLink to="/basecamps">베이스캠프</TopLink>
          <TopLink to="/programs">공식 행사</TopLink>
        </span>
        {signedIn && session.me ? (
          <Link to="/me" className="inline-flex min-h-11 items-center font-semibold text-ink">
            {session.me.nickname}
          </Link>
        ) : session.status === 'loading' ? null : (
          <Link to={withNext('/login', pathname + search)} className="inline-flex min-h-11 items-center font-semibold text-ink">
            로그인
          </Link>
        )}
      </nav>
    </header>
  );
}

function TopLink({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className="inline-flex min-h-11 items-center text-ink-muted transition-colors hover:text-ink">
      {children}
    </Link>
  );
}
