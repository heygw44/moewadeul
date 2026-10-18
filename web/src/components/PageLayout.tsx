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

// 화면 안의 한 묶음. 테두리 없는 세이지 카드에 제목과 내용을 넣는다.
export function PageCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-sheet bg-fill p-5 lg:p-6">
      <h2 className="text-[1.1875rem] font-extrabold tracking-tight text-ink">{title}</h2>
      {children}
    </section>
  );
}

// 행 목록 묶음이다. 제목은 카드 바깥 위에 두고, 행은 세이지 카드 안에 놓는다.
// 카드 안쪽 여백은 행마다 준다. 행이 hover 면을 카드 끝까지 채우게 하기 위해서다.
export function BoardSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-base font-bold text-ink">{title}</h2>
      <div className="overflow-hidden rounded-sheet bg-fill">{children}</div>
    </section>
  );
}
