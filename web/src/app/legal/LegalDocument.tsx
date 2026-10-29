import type { ReactNode } from 'react';
import { HubLayout } from '../../components/HubLayout';
import { CONTACT_EMAIL } from './siteInfo';
import { useDocumentTitle } from '../../components/useDocumentTitle';

export type LegalSection = { id: string; title: string; body: ReactNode };

type LegalDocumentProps = {
  title: string;
  effectiveDate: string;
  sections: LegalSection[];
};

export function LegalDocument({ title, effectiveDate, sections }: LegalDocumentProps) {
  useDocumentTitle(`${title} · 뫼와들`);

  return (
    <HubLayout title={title} width="narrow" meta={<p className="text-sm text-ink-muted">시행일 {effectiveDate}</p>}>
      <nav aria-label={`${title} 목차`} className="rounded-sheet bg-fill p-5">
        <ol className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
          {sections.map((section, index) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                className="inline-flex min-h-11 items-center text-ink hover:underline lg:min-h-8"
              >
                {index + 1}. {section.title}
              </a>
            </li>
          ))}
        </ol>
      </nav>
      {sections.map((section, index) => (
        <section key={section.id} id={section.id} className="flex scroll-mt-20 flex-col gap-3 pt-4">
          <h2 className="text-xl font-extrabold tracking-tight text-ink">
            {index + 1}. {section.title}
          </h2>
          <div className="flex flex-col gap-3 break-keep text-[0.9375rem] leading-relaxed text-ink">{section.body}</div>
        </section>
      ))}
    </HubLayout>
  );
}

export function LegalList({ children }: { children: ReactNode }) {
  return <ul className="flex list-disc flex-col gap-1.5 pl-5">{children}</ul>;
}

type LegalTableProps = { columns: string[]; rows: ReactNode[][]; caption: string };

export function LegalTable({ columns, rows, caption }: LegalTableProps) {
  return (
    <div className="overflow-x-auto rounded-row border border-line">
      <table className="w-full min-w-[40rem] border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-fill text-ink">
          <tr>
            {columns.map((column) => (
              <th key={column} scope="col" className="px-3 py-2 font-bold">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((cells, rowIndex) => (
            <tr key={rowIndex} className="border-t border-line align-top">
              {cells.map((cell, cellIndex) => (
                <td key={cellIndex} className="px-3 py-2">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function MailLink() {
  return (
    <a href={`mailto:${CONTACT_EMAIL}`} className="font-semibold text-accent underline underline-offset-2">
      {CONTACT_EMAIL}
    </a>
  );
}
