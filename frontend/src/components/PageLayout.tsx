import type { ReactNode } from 'react';
import { HubLayout } from './HubLayout';

type PageLayoutProps = {
  title: string;
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

export function PageLayout({ wide = false, children, ...rest }: PageLayoutProps) {
  return (
    <HubLayout width={wide ? 'wide' : 'narrow'} {...rest}>
      {children}
    </HubLayout>
  );
}

export function PageCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-sheet bg-fill p-5 lg:p-6">
      <h2 className="text-[1.1875rem] font-extrabold tracking-tight text-ink">{title}</h2>
      {children}
    </section>
  );
}

export function BoardSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-base font-bold text-ink">{title}</h2>
      <div className="overflow-hidden rounded-sheet bg-fill">{children}</div>
    </section>
  );
}
