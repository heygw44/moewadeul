import type { ReactNode } from 'react';
import { HubLayout } from './HubLayout';

type PageLayoutProps = {
  title: string;
  // 표나 목록을 넓게 보여 줄 화면에서 켠다. 기본은 좁은 한 열(max-w-2xl)이다.
  wide?: boolean;
  description?: string;
  breadcrumb?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  tabs?: ReactNode;
  aside?: ReactNode;
  titleInContent?: boolean;
  children: ReactNode;
};

// 지도가 없는 화면(내 정보, 회원 프로필, 행사 등)의 틀이다. 목록 중심 화면과 같은 머리글 띠(HubLayout)를 쓴다.
export function PageLayout({ wide = false, children, ...rest }: PageLayoutProps) {
  return (
    <HubLayout width={wide ? 'wide' : 'narrow'} {...rest}>
      {children}
    </HubLayout>
  );
}

// 화면 안의 한 묶음. 목록판과 같은 흰 판에 제목과 내용을 넣는다.
export function PageCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-sheet border border-line bg-surface p-4 lg:p-5">
      <h2 className="text-lg font-bold text-ink">{title}</h2>
      {children}
    </section>
  );
}

// 안내판의 행 목록이다. 제목은 판 바깥 위에 작은 글자로 두고, 판 안의 행은 끝에서 끝까지 1px 선으로 나눈다.
// 판 안쪽 여백은 행마다 준다. 판에 여백을 주면 행 사이 선이 판 끝까지 닿지 않는다.
export function BoardSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-bold text-ink-muted">{title}</h2>
      <div className="overflow-hidden rounded-sheet border border-line bg-surface">{children}</div>
    </section>
  );
}
