import type { ReactNode } from 'react';
import { BottomTabBar } from '../app/layout/BottomTabBar';
import { SiteFooter } from '../app/layout/SiteFooter';
import { SiteHeader } from '../app/layout/SiteHeader';
import { AnnouncementBar } from '../features/program/AnnouncementBar';

type HubLayoutProps = {
  title: string;
  description?: string;
  // 제목 위에 놓는 이동 경로(뒤로 가기 링크 등)
  breadcrumb?: ReactNode;
  // 제목 바로 아래 한 줄(분류, 글쓴이, 시각 같은 정보)
  meta?: ReactNode;
  actions?: ReactNode;
  tabs?: ReactNode;
  aside?: ReactNode;
  // 'narrow'는 안내 열이 없을 때 본문 열을 max-w-2xl로 좁힌다(폼·카드가 퍼지지 않게). 띠와 본문 바깥 폭은 그대로다.
  width?: 'wide' | 'narrow';
  // 공식 행사 알림 띠를 맨 위에 둘지 정한다. 행사 목록처럼 행사를 찾는 화면에서만 켠다.
  // 작업하는 화면(내 정보, 알림, 커뮤니티 등)에 두면 할 일과 상관없는 홍보가 맨 위를 차지한다.
  announcement?: boolean;
  // 본문 첫 판이 제목을 크게 보여 줄 때 켠다. 머리 띠에는 이동 경로만 남기고 제목(h1)은 스크린 리더에만 읽힌다.
  titleInContent?: boolean;
  children: ReactNode;
};

// 목록 중심 화면(커뮤니티, 공식 행사)의 틀이다. 제목과 탭은 위 띠에 두고, 목록은 그 아래 선으로 나눈 패널에 둔다.
// 오른쪽 안내 열(aside)은 넓은 화면에서만 옆에 놓고, 좁은 화면에서는 목록 아래로 내려간다.
export function HubLayout({
  title,
  description,
  breadcrumb,
  meta,
  actions,
  tabs,
  aside,
  width = 'wide',
  announcement = false,
  titleInContent = false,
  children,
}: HubLayoutProps) {
  // 머리 띠 안쪽과 본문은 같은 열을 쓴다. 그래야 제목·탭·오른쪽 버튼의 가장자리가 본문 가장자리와 맞는다.
  // 좁은 화면은 그 열을 가운데의 max-w-2xl로 줄이고, 안내 열이 있거나 넓은 화면은 바깥 폭을 그대로 쓴다.
  const column = width === 'narrow' && !aside ? 'mx-auto w-full max-w-2xl' : '';
  return (
    <div className="flex min-h-dvh flex-col bg-canvas pb-(--bottom-nav-h)">
      {announcement && <AnnouncementBar />}
      <SiteHeader />
      <div className="bg-canvas">
        <div className={`mx-auto max-w-screen-xl px-4 ${titleInContent ? 'pt-6' : 'pt-8'}`}>
          {/* 띠 아래 선은 화면 끝까지 긋지 않고 본문과 같은 열 안에서 끝낸다. 좁은 한 열 화면에서 선만 넓게 뻗지 않게 하기 위해서다. */}
          <div className={titleInContent ? column : `border-b border-line ${column}`}>
            {breadcrumb && <div className="-mt-2 mb-1 text-sm text-ink-muted">{breadcrumb}</div>}
            <div className={titleInContent ? 'sr-only' : 'flex flex-wrap items-start justify-between gap-x-4 gap-y-3 pb-3'}>
              <div className="flex min-w-0 flex-col gap-1">
                <h1 className="break-words text-3xl font-extrabold tracking-tight text-ink lg:text-4xl">{title}</h1>
                {description && <p className="text-base text-ink">{description}</p>}
                {meta}
              </div>
              {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
            </div>
            {tabs}
          </div>
        </div>
      </div>
      <div className="flex-1 bg-canvas">
        {/* 안내 열이 없으면 두 열 격자를 쓰지 않는다. 쓰면 빈 오른쪽 열 폭만큼 본문이 머리 띠보다 좁아진다. */}
        <main
          className={`mx-auto max-w-screen-xl px-4 py-6 ${aside ? 'lg:grid lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start lg:gap-10' : ''}`}
        >
          <div className={`flex min-w-0 flex-col gap-4 ${column}`}>{children}</div>
          {aside && <aside className="mt-6 flex flex-col gap-4 lg:mt-0">{aside}</aside>}
        </main>
      </div>
      <SiteFooter />
      <BottomTabBar />
    </div>
  );
}

// 안내 열의 한 묶음이다. 목록판과 같은 흰 판에 넣는다.
// 본문 목록은 text-sm이고 항목 사이를 띄운다.
export function AsideSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 rounded-sheet border border-line bg-surface p-4">
      <h2 className="text-base font-bold text-ink">{title}</h2>
      <div className="flex flex-col gap-2 text-sm text-ink [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-2">{children}</div>
    </section>
  );
}

// 목록을 감싸는 흰 목록판이다. 탐방로 안내판의 행처럼 항목 사이에 옅은 선을 긋는다.
export function ListPanel({ header, children }: { header?: ReactNode; children: ReactNode }) {
  return (
    <div className="overflow-hidden rounded-sheet border border-line bg-surface">
      {header && (
        <div className="flex items-center justify-between border-b border-line px-4 py-2 text-sm text-ink-muted">
          {header}
        </div>
      )}
      <ul className="divide-y divide-line">{children}</ul>
    </div>
  );
}
