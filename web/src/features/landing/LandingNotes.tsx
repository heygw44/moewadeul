import { useState, type ReactNode } from 'react';
import { Link } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';

type PartKey = 'when' | 'leader' | 'cond' | 'seats' | 'contact' | 'apply';
type Note = { key: PartKey; title: string; text: string; top: number };

// 베이스캠프 카드 양옆의 여백 메모다. 메모의 top은 데스크톱에서 그 메모가 가리키는 카드 부분과 높이를 맞춘 값(px)이다.
// 값은 정책과 맞춘다. 예를 들어 출발일은 다음 날부터 60일 안, 정원은 2~6명이다.
const LEFT: Note[] = [
  { key: 'when', title: '일정', text: '출발일과 몇 박인지. 다음 날부터 60일 안, 1~3박이에요.', top: 190 },
  { key: 'leader', title: '캠프 리더', text: '신뢰 단계와 지난 동행 후기를 보고 판단해요.', top: 330 },
  { key: 'cond', title: '합류 조건', text: '신뢰 단계, 연령대, 같은 성별만. 본인확인 정보로만 따져요.', top: 470 },
];
const RIGHT: Note[] = [
  { key: 'seats', title: '인원', text: '리더를 포함해 2~6명. 찬 자리와 빈 자리를 바로 세어요.', top: 250 },
  { key: 'contact', title: '연락 수단', text: '확정 전에는 잠겨 있어요. 확정된 멤버만 링크를 받아요.', top: 420 },
  { key: 'apply', title: '합류 신청', text: '조건이 맞으면 바로, 안 맞으면 이유를 알려 줘요.', top: 560 },
];

// 베이스캠프 한 장을 설명서의 그림처럼 가운데 두고, 양옆 메모가 각 부분을 설명한다.
// 메모에 마우스를 올리거나 초점을 두면 카드의 그 부분만 경고 면으로 칠한다. 카드는 랜딩 전용 정적 조각이고 이름·제목은 예시다.
export function LandingNotes() {
  const [lit, setLit] = useState<PartKey | null>(null);

  const renderNote = (note: Note, side: 'left' | 'right') => (
    <li
      key={note.key}
      tabIndex={0}
      onMouseEnter={() => setLit(note.key)}
      onMouseLeave={() => setLit(null)}
      onFocus={() => setLit(note.key)}
      onBlur={() => setLit(null)}
      style={{ top: note.top }}
      className={`rounded-control lg:absolute lg:w-[26ch] ${side === 'left' ? 'lg:right-0 lg:text-right' : 'lg:left-0'}`}
    >
      <b className="block text-lg font-bold text-ink">{note.title}</b>
      <span className="mt-0.5 block text-[15px] text-ink-muted">{note.text}</span>
    </li>
  );

  return (
    <section aria-labelledby="landing-notes" className="mx-auto max-w-screen-xl px-4 pt-6 pb-24 lg:px-10 lg:pt-14 lg:pb-36">
      <div className="lg:grid lg:grid-cols-12 lg:gap-6">
        <div className="relative lg:col-span-4 lg:min-h-[720px]">
          <h2
            id="landing-notes"
            data-reveal
            className="mb-6 text-[28px] leading-[1.15] font-extrabold tracking-[-0.035em] text-ink lg:text-right lg:text-[40px]"
          >
            베이스캠프
            <br />한 장
          </h2>
          <ul className="hidden lg:block">{LEFT.map((note) => renderNote(note, 'left'))}</ul>
        </div>
        <div className="lg:col-span-4 lg:justify-self-center">
          <BasecampCardFragment lit={lit} />
        </div>
        <div className="relative lg:col-span-4 lg:min-h-[720px]">
          <ul className="hidden lg:block">{RIGHT.map((note) => renderNote(note, 'right'))}</ul>
        </div>
      </div>
      {/* 모바일은 메모를 카드 아래에 두 열로 모은다 */}
      <ul className="mt-8 grid grid-cols-2 gap-5 lg:hidden">{[...LEFT, ...RIGHT].map((note) => renderNote(note, 'right'))}</ul>
    </section>
  );
}

function Part({ part, lit, children, className = '' }: { part: PartKey | 'place'; lit: PartKey | null; children: ReactNode; className?: string }) {
  const on = lit === part;
  return (
    <div className={`rounded-control transition-[background-color,outline-color] duration-200 ${on ? 'bg-warning-soft outline-6 outline-warning-soft' : ''} ${className}`}>
      {children}
    </div>
  );
}

function BasecampCardFragment({ lit }: { lit: PartKey | null }) {
  return (
    <article data-ui aria-label="베이스캠프 카드 예시" className="flex w-full max-w-[384px] flex-col gap-4 rounded-sheet bg-surface px-5 pt-5 pb-4 text-[15px] text-ink lg:w-[384px]">
      <Part part="when" lit={lit}>
        <p className="text-sm text-ink-muted tabular-nums">11월 7일 (토) ~ 11월 8일 (일) · 1박</p>
        <h3 className="text-xl leading-snug font-extrabold tracking-[-0.02em]">대청봉 아래 첫눈 기다리는 밤</h3>
      </Part>
      <Part part="place" lit={lit}>
        <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-accent">
          <Icon name="check" size={15} />
          설악산 오색 야영장 · 공공 야영장
        </span>
      </Part>
      <Part part="leader" lit={lit} className="flex items-center gap-3">
        <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-full bg-fill-strong font-bold">
          산
        </span>
        <span>
          <b className="block">산바람</b>
          <span className="text-sm text-ink-muted">신뢰 회원 · 동행 7번 · 다시 동행 92%</span>
        </span>
      </Part>
      <Part part="seats" lit={lit}>
        <p className="text-sm text-ink-muted">인원 3/5명</p>
        <span aria-hidden="true" className="mt-1.5 flex gap-1.5">
          {[true, true, true, false, false].map((filled, index) => (
            <i key={index} className={`size-[22px] rounded-full ${filled ? 'bg-ink' : 'bg-fill-strong'}`} />
          ))}
        </span>
      </Part>
      <Part part="cond" lit={lit}>
        <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-2.5">
          <dt className="text-ink-muted">합류 조건</dt>
          <dd className="text-right">본인확인 · 30~40대</dd>
          <dt className="text-ink-muted">모집 마감</dt>
          <dd className="text-right tabular-nums">11월 6일 0시</dd>
        </dl>
      </Part>
      <Part part="contact" lit={lit}>
        <p className="rounded-control bg-canvas px-3.5 py-3 text-sm text-ink-muted">오픈채팅 링크는 베이스캠프가 확정되면 멤버에게만 보여요.</p>
      </Part>
      <Part part="apply" lit={lit}>
        <Link to="/basecamps" className={`${buttonClassName('primary')} w-full`}>
          합류 신청
        </Link>
      </Part>
    </article>
  );
}
