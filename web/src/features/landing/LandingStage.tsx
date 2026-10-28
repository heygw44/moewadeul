import { useId, useRef, useState, type ReactNode } from 'react';
import { DATA_SOURCES } from '../../app/layout/dataSources';
import { Link, navigate } from '../../app/router';
import { BASE_CLASSES, buttonClassName } from '../../components/buttonClass';
import { Icon } from '../../components/icons';
import { Contours, HeroScene, MiniScene, RidgeEdge } from './LandingArt';
import { JudgeMap } from './JudgeMap';
import { Marquee, Photo, FragmentRow } from './landingParts';
import {
  DISPLAY_CLASS,
  FRAGMENT_CLASS,
  H2_CLASS,
  NUDGE_CLASS,
  TAG_OK_CLASS,
  WHITE_BUTTON_CLASS,
} from './landingStyles';
import { approach, useFrameWhileVisible, usePrefersReducedMotion, useRise } from './motion';

export function LandingStage() {
  return (
    <>
      <Hero />
      <SourcesStrip />
      <HowSection />
      <ApproachSection />
      <ServicesSection />
      <JudgeSection />
      <ProgramSection />
      <TrustSection />
      <FaqSection />
      <ClosingSection />
    </>
  );
}

function Hero() {
  const { ref, rise } = useRise<HTMLElement>();
  const title = rise(0);
  const intro = rise(1);
  const action = rise(2);
  return (
    <section
      ref={ref}
      aria-labelledby="hero-title"
      className="relative isolate overflow-hidden bg-brand text-white [&_:focus-visible]:outline-white"
    >
      <div className="relative z-10 mx-auto min-h-[max(44rem,calc(100svh_-_7rem))] max-w-[90rem] px-4 pt-14 lg:px-8 lg:pt-24">
        <h1 id="hero-title" className={`${DISPLAY_CLASS} ${title.className}`} style={title.style}>
          어디서 잘지,
          <br />
          누구와 갈지.
        </h1>
        <p className={`mt-6 max-w-[30ch] text-lg text-brand-ink lg:text-xl ${intro.className}`} style={intro.style}>
          합법적인 박지를 지도에서 찾고, 신뢰 단계를 확인한 사람과 함께 떠나요.
        </p>
        <div className={`mt-8 ${action.className}`} style={action.style}>
          <Link to="/map" className={`${BASE_CLASSES} ${WHITE_BUTTON_CLASS} group min-h-13 px-7 text-lg`}>
            지도 열기
            <Icon name="chevronRight" size={20} className={NUDGE_CLASS} />
          </Link>
        </div>
      </div>
      <HeroScene />
    </section>
  );
}

function SourcesStrip() {
  const { ref, rise } = useRise<HTMLElement>();
  const title = rise(0);
  const strip = rise(1);
  return (
    <section ref={ref} aria-labelledby="sources-title" className="pb-24 lg:pb-40">
      <div className="mx-auto max-w-[90rem] px-4 lg:px-8">
        <h2 id="sources-title" className={`text-xl font-bold ${title.className}`} style={title.style}>
          장소와 공원 경계, 날씨는 이 자료로 보여 줘요
        </h2>
      </div>
      <div className={`mt-5 ${strip.className}`} style={strip.style}>
        <Marquee items={DATA_SOURCES} label="데이터 출처" />
      </div>
    </section>
  );
}

function StepArt({ className, children }: { className: string; children: ReactNode }) {
  return (
    <div data-ui className={`relative isolate flex h-72 items-center justify-center overflow-hidden px-5 ${className}`}>
      {children}
    </div>
  );
}

const HOW_STEPS = [
  {
    title: '갈 곳을 골라요',
    body: '지역을 넣으면 가까운 순으로 박지와 야영장이 나와요. 반경은 50km까지 넓힐 수 있어요.',
    art: (
      <StepArt className="bg-fill-strong">
        <Contours tone="light" seed={2} />
        <div className={`${FRAGMENT_CLASS} relative max-w-[21rem]`}>
          <div className="flex items-center gap-2.5 px-4 py-3">
            <Icon name="search" size={18} className="shrink-0 text-ink-muted" />
            <span className="flex-1 font-semibold">가평 북면</span>
            <span className="text-[0.9375rem] text-ink-muted">반경 20km</span>
          </div>
          <FragmentRow number={1} name="명지산 계곡 야영장" meta="공공데이터 · 운영 중" distance="3.2km" />
          <FragmentRow number={2} name="적목리 강변 박지" meta="사용자 제보 · 확인 7명" distance="5.8km" />
        </div>
      </StepArt>
    ),
  },
  {
    title: '합류를 신청해요',
    body: '합류 조건을 확인하고 신청해요. 정원은 캠프 리더를 포함해 2명에서 6명이에요.',
    art: (
      <StepArt className="bg-fill-strong">
        <Contours tone="light" seed={5} />
        <div className={`${FRAGMENT_CLASS} relative max-w-[21rem] p-4`}>
          <p className="rounded-xl bg-fill px-3 py-2.5">처음 1박이라 장비를 같이 봐 주시면 좋겠어요.</p>
          <div className="mt-3 flex items-center justify-between gap-2">
            <div>
              <p className="flex items-center gap-1.5 font-bold">
                합류 승인됨 <span className={TAG_OK_CLASS}>4 / 4명</span>
              </p>
              <p className="text-[0.9375rem] text-ink-muted">캠프 리더 지윤 · 신뢰 단계 2</p>
            </div>
          </div>
        </div>
      </StepArt>
    ),
  },
  {
    title: '출발 전날 0시에 확정돼요',
    body: '두 명 이상이면 확정되고, 확정된 멤버에게만 연락 수단이 보여요.',
    art: (
      <StepArt className="items-start bg-brand pt-8">
        <MiniScene />
        <div className={`${FRAGMENT_CLASS} relative z-10 max-w-[21rem] p-4`}>
          <div className="flex items-center justify-between gap-2">
            <p className="font-bold">명지산 계곡 야영장</p>
            <span className={TAG_OK_CLASS}>확정</span>
          </div>
          <p className="mt-0.5 text-[0.9375rem] text-ink-muted">11월 8일 1박 · 멤버 4명</p>
          <p className="mt-2 rounded-xl bg-fill px-3 py-2 text-[0.9375rem]">다녀온 뒤 7일까지 연락 수단이 보여요.</p>
        </div>
      </StepArt>
    ),
  },
];

function HowSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  return (
    <section ref={ref} aria-labelledby="how-title" className="mx-auto max-w-[90rem] px-4 pb-24 lg:px-8 lg:pb-40">
      <h2 id="how-title" className={`${H2_CLASS} text-center ${heading.className}`} style={heading.style}>
        지도 하나로, 출발까지
      </h2>
      <ol className="mt-10 grid gap-4 lg:mt-16 lg:grid-cols-[5fr_4fr_4fr]">
        {HOW_STEPS.map((step, index) => {
          const r = rise(index + 1);
          return (
            <li
              key={step.title}
              className={`flex flex-col overflow-hidden rounded-media bg-fill ${r.className}`}
              style={r.style}
            >
              {step.art}
              <div className="p-6 lg:p-8">
                <h3 className="text-[1.625rem] leading-tight font-extrabold tracking-[-0.03em]">{step.title}</h3>
                <p className="mt-2 max-w-[34ch] text-[1.0625rem]">{step.body}</p>
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

const APPROACH_ITEMS = [
  {
    title: '공원 경계로 판정해요',
    body: '국립·도립·군립공원 경계 자료로 박지를 판정하고, 자료의 출처와 기준일을 함께 보여 줘요.',
  },
  {
    title: '신뢰 단계를 매번 계산해요',
    body: '본인확인과 동행 기록으로 단계를 정해요. 저장해 둔 값이 아니라 볼 때마다 지금 기록으로 다시 계산해요.',
  },
  {
    title: '신고하면 바로 막아요',
    body: '성희롱이나 위협을 신고하면 확인 전이라도 72시간 동안 이용을 멈춰요. 제재는 경고, 7일, 30일, 영구 정지 순서예요.',
  },
];

function ApproachSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const reduced = usePrefersReducedMotion();
  const frameRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);
  const offset = useRef(0);

  useFrameWhileVisible(
    frameRef,
    () => {
      const frame = frameRef.current;
      const image = imageRef.current;
      if (!frame || !image) return;
      const rect = frame.getBoundingClientRect();
      const distance = (rect.top + rect.height / 2 - innerHeight / 2) / innerHeight;
      offset.current = approach(offset.current, Math.max(-1, Math.min(1, distance)) * -24, 0.1);
      image.style.transform = `scale(1.08) translateY(${offset.current}px)`;
    },
    !reduced,
  );

  const heading = rise(0);
  const photo = rise(4);
  const action = rise(4);
  return (
    <section
      ref={ref}
      aria-labelledby="approach-title"
      className="mx-auto grid max-w-screen-xl items-center gap-10 px-4 pb-24 lg:grid-cols-[5fr_7fr] lg:gap-16 lg:px-8 lg:pb-40"
    >
      <div>
        <h2 id="approach-title" className={`${H2_CLASS} ${heading.className}`} style={heading.style}>
          판단 근거부터 보여 줘요
        </h2>
        <ul className="mt-9 flex flex-col gap-6">
          {APPROACH_ITEMS.map((item, index) => {
            const r = rise(index + 1);
            return (
              <li key={item.title} className={`max-w-[44ch] ${r.className}`} style={r.style}>
                <p className="text-xl font-bold tracking-[-0.02em]">{item.title}</p>
                <p className="mt-1 text-[1.0625rem]">{item.body}</p>
              </li>
            );
          })}
        </ul>
        <div className={`mt-9 ${action.className}`} style={action.style}>
          <Link to="/map" className={`${buttonClassName('secondary')} group`}>
            지도 열기
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </Link>
        </div>
      </div>
      <div
        ref={frameRef}
        className={`aspect-[7/5] overflow-hidden rounded-media bg-fill ${photo.className}`}
        style={photo.style}
      >
        <img
          ref={imageRef}
          src="/landing/photos/tent-dome.webp"
          alt="고원 풀밭에 세운 돔 텐트"
          loading="lazy"
          className="size-full scale-[1.08] object-cover mix-blend-multiply"
        />
      </div>
    </section>
  );
}

const SERVICES = [
  {
    to: '/map',
    name: '박지 지도',
    body: '공원 경계 경고와 출처·기준일이 붙은 박지, 야영장, 휴양림을 지도에서 찾아요.',
    tags: ['반경 50km', '경계 경고', '휴장 표시', '날씨·일몰'],
    action: '지도 열기',
  },
  {
    to: '/basecamps',
    name: '베이스캠프',
    body: '같은 날 같은 곳에서 1박 이상 함께 야영할 사람을 모아요. 정원은 2명에서 6명이에요.',
    tags: ['합류 조건', '신뢰 단계', '확정 뒤 연락 수단'],
    action: '둘러보기',
  },
  {
    to: '/programs',
    name: '공식 행사',
    body: '관리자가 연 백패킹 행사에 선착순으로 신청하고 결제해요.',
    tags: ['선착순', '결제 15분', '3일 전 환불'],
    action: '일정 보기',
  },
  {
    to: '/memories',
    name: '기록',
    body: '다녀온 곳을 사진과 글로 지도에 남겨요. 기본은 나만 보여요.',
    tags: ['기본 비공개', '사진', '피드'],
    action: '피드 보기',
  },
  {
    to: '/community',
    name: '커뮤니티',
    body: '게시판 하나에 글을 올리고, 댓글과 좋아요로 이야기해요.',
    tags: ['이미지 5장', '댓글', '좋아요'],
    action: '글 보기',
  },
] as const;

function ServicesSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  return (
    <section ref={ref} aria-labelledby="services-title" className="pb-24 lg:pb-40">
      <div className="mx-auto grid max-w-screen-xl gap-8 px-4 lg:grid-cols-[4fr_8fr] lg:gap-16 lg:px-8">
        <h2
          id="services-title"
          className={`${H2_CLASS} self-start lg:sticky lg:top-28 ${heading.className}`}
          style={heading.style}
        >
          뫼와들에서
          <br />할 수 있는 일
        </h2>
        <ul className="flex flex-col gap-2">
          {SERVICES.map((service, index) => {
            const r = rise(index + 1);
            return (
              <li key={service.to} className={r.className} style={r.style}>
                <Link
                  to={service.to}
                  className="group flex flex-col gap-3 rounded-sheet bg-fill p-6 transition-[background-color,transform] duration-300 hover:bg-fill-strong motion-safe:hover:-translate-y-0.5 lg:p-8"
                >
                  <span className="text-[1.625rem] leading-tight font-extrabold tracking-[-0.03em]">
                    {service.name}
                  </span>
                  <span className="max-w-[46ch] text-[1.0625rem]">{service.body}</span>
                  <span className="mt-1 flex flex-wrap items-center justify-between gap-3">
                    <span className="text-[0.9375rem] text-ink-muted">{service.tags.join(' · ')}</span>
                    <span className="inline-flex items-center gap-1 font-semibold text-brand-strong">
                      {service.action}
                      <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
                    </span>
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}

const JUDGE_POINTS = [
  '국립·도립·군립공원 경계 자료로 판정해요',
  '출처와 기준일이 항상 같이 보여요',
  '경고 박지로는 베이스캠프를 열 수 없어요',
];

const JUDGE_PHOTOS = [
  { src: '/landing/photos/tent-valley.webp', alt: '산비탈 아래 골짜기에 세운 텐트 한 동' },
  { src: '/landing/photos/tent-cone.webp', alt: '초원에 세운 흰 원뿔 텐트와 뒤쪽 산 능선' },
  { src: '/landing/photos/meadow.webp', alt: '산 아래 풀밭에 누워 쉬는 백패커' },
];

function JudgeSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  const body = rise(1);
  const map = rise(2);
  return (
    <section ref={ref} aria-labelledby="judge-title" className="mx-auto max-w-[90rem] px-4 pb-24 lg:px-8 lg:pb-40">
      <div className="relative isolate overflow-hidden rounded-media bg-brand-raised text-white [&_:focus-visible]:outline-white">
        <Contours tone="dark" seed={6} />
        <div className="relative grid items-center gap-10 p-6 sm:p-10 lg:grid-cols-[5fr_6fr] lg:gap-16 lg:p-16">
          <div>
            <h2 id="judge-title" className={`${H2_CLASS} ${heading.className}`} style={heading.style}>
              여기서 자도 될까요?
            </h2>
            <div className={body.className} style={body.style}>
              <p className="mt-4 max-w-[34ch] text-lg text-brand-ink">
                핀을 옮겨 보세요. 공원 경계 안인지 바로 판정해요.
              </p>
              <ul className="mt-7 flex flex-col gap-3">
                {JUDGE_POINTS.map((point) => (
                  <li key={point} className="flex items-start gap-3 text-[1.0625rem]">
                    <Icon name="check" size={20} className="mt-0.5 shrink-0" />
                    {point}
                  </li>
                ))}
              </ul>
              <Link to="/map" className={`${BASE_CLASSES} ${WHITE_BUTTON_CLASS} group mt-9 px-6`}>
                지도 열기
                <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
              </Link>
            </div>
          </div>
          <div className={`${map.className} mx-auto w-full max-w-[35rem]`} style={map.style}>
            <JudgeMap />
          </div>
        </div>
        <div className="relative grid gap-3 px-6 pb-6 sm:grid-cols-3 sm:px-10 sm:pb-10 lg:px-16 lg:pb-16">
          {JUDGE_PHOTOS.map((photo) => (
            <Photo key={photo.src} src={photo.src} alt={photo.alt} ratio="aspect-[4/3]" />
          ))}
        </div>
      </div>
    </section>
  );
}

const PROGRAM_POINTS = [
  {
    title: '선착순으로 정원만큼만',
    body: '신청이 한꺼번에 몰려도 정원만큼만 받고, 한 사람은 한 번만 신청해요.',
  },
  {
    title: '결제는 15분 안에',
    body: '기한을 넘기면 자리는 다음 사람에게 돌아가요. 정원이 찼다면 빈자리 알림을 신청해 두세요.',
  },
  {
    title: '시작 3일 전까지는 전액 환불',
    body: '그 뒤로는 취소할 수 없어요. 숙박 행사는 신뢰 단계 1부터 신청할 수 있어요.',
  },
];

function ProgramSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const card = rise(0);
  const heading = rise(1);
  return (
    <section
      ref={ref}
      aria-labelledby="program-title"
      className="mx-auto grid max-w-screen-xl items-center gap-10 px-4 pb-24 lg:grid-cols-2 lg:gap-16 lg:px-8 lg:pb-40"
    >
      <div className={`rounded-media bg-fill p-5 lg:p-8 ${card.className}`} style={card.style}>
        <Photo src="/landing/photos/stove.webp" alt="풀밭에 놓인 버너와 주전자" ratio="aspect-[16/10]" />
        <div data-ui className={`${FRAGMENT_CLASS} mt-4 p-5`}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-[1.0625rem] font-bold">가을 둘레길 백패킹</p>
              <p className="mt-0.5 text-[0.9375rem] text-ink-muted">11월 15일~16일 · 숙박 행사</p>
            </div>
            <div className="text-right">
              <p className="text-[0.8125rem] text-ink-muted">참가비</p>
              <p className="tabular-nums text-2xl font-extrabold tracking-[-0.03em]">45,000원</p>
            </div>
          </div>
          <div className="mt-4 flex justify-between font-bold">
            <span>남은 자리</span>
            <span className="tabular-nums">12 / 200</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-fill-strong">
            <div className="h-full w-[94%] rounded-full bg-brand" />
          </div>
          <span className="mt-4 flex h-10 items-center justify-center rounded-control bg-brand text-[0.9375rem] font-semibold text-white">
            신청하기
          </span>
        </div>
      </div>
      <div>
        <h2 id="program-title" className={`${H2_CLASS} ${heading.className}`} style={heading.style}>
          공식 행사 자리,
          <br />
          놓치지 않게
        </h2>
        <ul className="mt-9 flex flex-col gap-6">
          {PROGRAM_POINTS.map((point, index) => {
            const r = rise(index + 2);
            return (
              <li key={point.title} className={`flex gap-4 ${r.className}`} style={r.style}>
                <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-accent-soft text-accent">
                  <Icon name="check" size={16} />
                </span>
                <div className="max-w-[40ch]">
                  <p className="text-xl font-bold tracking-[-0.02em]">{point.title}</p>
                  <p className="mt-1 text-[1.0625rem]">{point.body}</p>
                </div>
              </li>
            );
          })}
        </ul>
        <Link to="/programs" className={`${buttonClassName('primary')} group mt-9`}>
          행사 일정 보기
          <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
        </Link>
      </div>
    </section>
  );
}

type TrustLevel = {
  level: number;
  name: string;
  condition: string;
  abilities: string[];
  action: { to: string; label: string };
  featured?: boolean;
};

const TRUST_LEVELS: TrustLevel[] = [
  {
    level: 0,
    name: '이메일 인증',
    condition: '가입하고 이메일을 인증하면 바로 돼요.',
    abilities: ['박지 탐색·제보·확인·신고', '장소 후기', '숙박 없는 행사 신청', '베이스캠프 둘러보기'],
    action: { to: '/signup', label: '회원가입' },
  },
  {
    level: 1,
    name: '본인확인 · 성인',
    condition: '본인확인을 마친 만 19세 이상이에요.',
    abilities: ['베이스캠프 열기와 합류', '숙박 행사 신청', '단계 0에서 하는 모든 일'],
    action: { to: '/identity-verification', label: '본인확인하기' },
    featured: true,
  },
  {
    level: 2,
    name: '신뢰 회원',
    condition: '완료한 동행 3회 이상, 다시 동행 80% 이상, 최근 180일 확정 제재 없음이에요.',
    abilities: ['"신뢰 회원만" 조건의 베이스캠프 합류', '단계 1에서 하는 모든 일'],
    action: { to: '/basecamps', label: '베이스캠프 둘러보기' },
  },
];

function TrustSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  return (
    <section ref={ref} aria-labelledby="trust-title" className="mx-auto max-w-[90rem] px-4 pb-24 lg:px-8 lg:pb-40">
      <div className={heading.className} style={heading.style}>
        <h2 id="trust-title" className={H2_CLASS}>
          믿을 수 있는 동행은
          <br />
          단계로 확인해요
        </h2>
        <p className="mt-4 max-w-[40ch] text-lg">단계는 저장해 두지 않고, 볼 때마다 지금 기록으로 다시 계산해요.</p>
      </div>
      <ul className="mt-10 grid gap-6 lg:mt-14 lg:grid-cols-[4fr_5fr_4fr] lg:gap-4">
        {TRUST_LEVELS.map((item, index) => {
          const r = rise(index + 1);
          return (
            <li
              key={item.level}
              className={`relative isolate flex flex-col overflow-hidden rounded-media p-7 lg:p-9 ${
                item.featured ? 'bg-brand text-white [&_:focus-visible]:outline-white' : 'bg-fill'
              } ${r.className}`}
              style={r.style}
            >
              {item.featured && <Contours tone="dark" seed={8} />}
              <p className="relative flex items-baseline gap-2">
                <span
                  className={`text-[3.5rem] leading-none font-extrabold tracking-[-0.04em] ${item.featured ? '' : 'text-brand'}`}
                >
                  {item.level}
                </span>
                <span className="text-lg font-bold">단계</span>
              </p>
              <h3 className="relative mt-4 text-[1.625rem] leading-tight font-extrabold tracking-[-0.03em]">
                {item.name}
              </h3>
              <p className={`relative mt-2 text-[1.0625rem] ${item.featured ? 'text-brand-ink' : 'text-ink-muted'}`}>
                {item.condition}
              </p>
              <ul className="relative mt-6 flex flex-1 flex-col gap-2.5">
                {item.abilities.map((ability) => (
                  <li key={ability} className="flex items-start gap-3 text-base">
                    <Icon name="check" size={18} className="mt-0.5 shrink-0" />
                    {ability}
                  </li>
                ))}
              </ul>
              <Link
                to={item.action.to}
                className={
                  item.featured
                    ? `${BASE_CLASSES} ${WHITE_BUTTON_CLASS} group relative mt-8`
                    : `${buttonClassName('secondary')} group relative mt-8`
                }
              >
                {item.action.label}
                <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

const FAQ = [
  {
    q: '경고가 붙은 박지는 가면 안 되나요?',
    a: '경고는 공원 경계 자료로 판정한 참고 정보이고 법적 효력은 없어요. 다만 경고 박지로는 베이스캠프를 열 수 없어요.',
  },
  {
    q: '본인확인 때 무엇을 저장하나요?',
    a: '출생연도와 성별, 그리고 한 사람이 계정 하나만 쓰도록 확인하는 값의 해시만 저장해요. 이름과 전화번호는 남기지 않아요.',
  },
  {
    q: '베이스캠프는 몇 명이 함께 가나요?',
    a: '캠프 리더를 포함해 2명에서 6명까지예요. 출발일은 다음 날부터 60일 안에서 고르고, 1박에서 3박까지 갈 수 있어요.',
  },
  {
    q: '베이스캠프는 언제 확정되나요?',
    a: '출발 전날 0시에 두 명 이상이면 자동으로 확정돼요. 그 시각에 한 명뿐이면 자동으로 취소돼요.',
  },
  {
    q: '멤버와는 어떻게 연락하나요?',
    a: '베이스캠프가 확정되면 멤버끼리 연락 수단이 보여요. 다녀온 뒤 7일이 지나면 다시 가려져요.',
  },
  {
    q: '동행 후기는 언제 보이나요?',
    a: '내가 상대에게 후기를 쓰거나 다녀온 지 14일이 지나야 받은 후기를 볼 수 있어요. 쓴 뒤에는 고치거나 지울 수 없어요.',
  },
];

function FaqSection() {
  const { ref, rise } = useRise<HTMLElement>();
  const heading = rise(0);
  const list = rise(1);
  return (
    <section
      ref={ref}
      aria-labelledby="faq-title"
      className="mx-auto grid max-w-screen-xl gap-8 px-4 pb-24 lg:grid-cols-[4fr_8fr] lg:gap-16 lg:px-8 lg:pb-40"
    >
      <div className={`self-start lg:sticky lg:top-28 ${heading.className}`} style={heading.style}>
        <h2 id="faq-title" className={H2_CLASS}>
          자주 묻는 질문
        </h2>
        <p className="mt-4 max-w-[28ch] text-lg">궁금한 점이 더 있다면 커뮤니티에 물어보세요.</p>
        <Link to="/community" className={`${buttonClassName('secondary')} group mt-6`}>
          커뮤니티 열기
          <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
        </Link>
      </div>
      <ul className={`flex flex-col gap-2 ${list.className}`} style={list.style}>
        {FAQ.map((item, index) => (
          <li key={item.q}>
            <details name="landing-faq" open={index === 0} className="group rounded-sheet bg-fill">
              <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 px-6 py-4 text-lg font-bold tracking-[-0.01em] [&::-webkit-details-marker]:hidden">
                {item.q}
                <Icon
                  name="plus"
                  size={20}
                  className="shrink-0 transition-transform duration-300 group-open:rotate-45 motion-reduce:transition-none"
                />
              </summary>
              <p className="px-6 pb-6 text-[1.0625rem] leading-normal">{item.a}</p>
            </details>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ClosingSection() {
  const [region, setRegion] = useState('가평 북면');
  const { ref, rise } = useRise<HTMLElement>();
  const inputId = useId();
  const heading = rise(0);
  const dialog = rise(2);

  function submit() {
    const query = region.trim();
    navigate(query === '' ? '/map' : `/map?q=${encodeURIComponent(query)}`);
  }

  return (
    <section
      ref={ref}
      aria-labelledby="closing-title"
      className="relative overflow-hidden bg-brand-raised text-white [&_:focus-visible]:outline-white"
    >
      <video
        autoPlay
        muted
        loop
        playsInline
        preload="metadata"
        poster="/landing/video/campfire-poster.webp"
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 size-full object-cover opacity-40 mix-blend-multiply grayscale contrast-125 motion-reduce:hidden"
      >
        <source src="/landing/video/campfire.webm" type="video/webm" />
      </video>
      <div className="relative mx-auto grid min-h-[88svh] max-w-screen-xl items-end gap-10 px-4 pt-20 pb-36 lg:grid-cols-[7fr_5fr] lg:px-8 lg:pt-24 lg:pb-48">
        <div className={heading.className} style={heading.style}>
          <h2 id="closing-title" className={`${DISPLAY_CLASS} whitespace-nowrap`}>
            이번 주말,
            <br />
            어디서 잘까요?
          </h2>
          <p className="mt-5 max-w-[34ch] text-lg text-brand-ink lg:text-xl">
            지역을 넣으면 그 일대의 박지와 야영장을 지도에서 바로 볼 수 있어요. 가입하지 않아도 돼요.
          </p>
        </div>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
          data-ui
          className={`w-full max-w-[27.5rem] self-center justify-self-center rounded-sheet bg-surface p-7 text-ink shadow-raise [&_:focus-visible]:outline-info ${dialog.className}`}
          style={dialog.style}
        >
          <h3 className="text-lg font-bold">어디로 떠나세요?</h3>
          <label htmlFor={inputId} className="mt-4 block text-sm font-semibold text-ink-muted">
            지역
          </label>
          <input
            id={inputId}
            value={region}
            onChange={(event) => setRegion(event.target.value)}
            maxLength={50}
            autoComplete="off"
            className="mt-1.5 h-12 w-full rounded-field border-[1.5px] border-ink-faint bg-surface px-4 text-base focus:border-brand"
          />
          <button type="submit" className={`${buttonClassName('primary')} group mt-5 min-h-13 w-full`}>
            지도에서 보기
            <Icon name="chevronRight" size={18} className={NUDGE_CLASS} />
          </button>
        </form>
      </div>
      <RidgeEdge />
    </section>
  );
}
