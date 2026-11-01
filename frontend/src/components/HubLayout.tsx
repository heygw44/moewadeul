import type { ReactNode } from 'react';
import { BottomTabBar } from '../app/layout/BottomTabBar';
import { SiteFooter } from '../app/layout/SiteFooter';
import { SiteHeader } from '../app/layout/SiteHeader';

type HubLayoutProps = {
  title: string;
  description?: string;
  breadcrumb?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  tabs?: ReactNode;
  aside?: ReactNode;
  width?: 'wide' | 'narrow';
  banner?: ReactNode;
  titleInContent?: boolean;
  children: ReactNode;
};

export function HubLayout({
  title,
  description,
  breadcrumb,
  meta,
  actions,
  tabs,
  aside,
  width = 'wide',
  banner,
  titleInContent = false,
  children,
}: HubLayoutProps) {
  const column = width === 'narrow' && !aside ? 'mx-auto w-full max-w-2xl' : '';
  return (
    <div className="flex min-h-dvh flex-col bg-canvas pb-(--bottom-nav-h)">
      {banner}
      <SiteHeader />
      <div className="bg-canvas">
        <div className={`mx-auto max-w-screen-xl px-4 ${titleInContent ? 'pt-6' : 'pt-8'}`}>
          <div className={column}>
            {breadcrumb && <div className="-mt-2 mb-1 text-sm text-ink-muted">{breadcrumb}</div>}
            <div
              className={titleInContent ? 'sr-only' : 'flex flex-wrap items-end justify-between gap-x-4 gap-y-3 pb-5'}
            >
              <div className="flex min-w-0 flex-col gap-1">
                <h1 className="break-words text-[2rem] leading-tight font-extrabold tracking-[-0.04em] text-ink lg:text-[2.75rem]">
                  {title}
                </h1>
                {description && <p className="text-base text-ink lg:text-lg">{description}</p>}
                {meta}
              </div>
              {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
            </div>
            {tabs}
          </div>
        </div>
      </div>
      <div className="flex-1 bg-canvas">
        <main
          className={`mx-auto max-w-screen-xl px-4 py-6 ${aside ? 'lg:grid lg:grid-cols-[minmax(0,1fr)_18.75rem] lg:items-start lg:gap-12' : ''}`}
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

export function AsideSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-sheet bg-fill p-6">
      <h2 className="text-[1.0625rem] font-extrabold text-ink">{title}</h2>
      <div className="flex flex-col gap-2 text-sm text-ink [&_ul]:flex [&_ul]:flex-col [&_ul]:gap-2">{children}</div>
    </section>
  );
}

export function ListPanel({ header, children }: { header?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      {header && <div className="flex items-center justify-between px-4 py-2 text-sm text-ink-muted">{header}</div>}
      <ul className="flex flex-col gap-0.5">{children}</ul>
    </div>
  );
}
