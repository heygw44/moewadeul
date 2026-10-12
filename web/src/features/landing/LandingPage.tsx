import { useEffect } from 'react';
import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { Link } from '../../app/router';
import { StatusText } from '../../components/StatusText';
import { Icon } from '../../components/icons';
import { trustLevelBadge } from '../member/memberLabels';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingHero } from './LandingHero';
import { TrustPreview, WarningPreview } from './LandingPreviews';

const SECTION_TITLE_CLASS = 'text-3xl font-bold tracking-tight leading-tight text-ink lg:text-[2.5rem]';
const SECTION_LEAD_CLASS = 'mt-4 max-w-2xl text-lg leading-relaxed text-ink-muted';

// 신뢰 단계마다 무엇을 뜻하는지 한 줄로 적는다. 단계는 서버가 매번 계산하고, 여기서는 기준만 소개한다.
const TRUST_LEVEL_MEANINGS = [
  '이메일 인증을 마친 회원이에요. 장소를 둘러보고 후기를 남길 수 있어요.',
  '본인확인으로 성인임을 확인한 회원이에요. 베이스캠프를 열거나 합류할 수 있어요.',
  '동행을 3번 이상 마쳤고, 함께 간 사람 80% 이상이 다시 가고 싶다고 했어요. 최근 180일 동안 제재가 없어요.',
];

// 장소 상세에서 근거로 보여 주는 것들이다. 예시 화면 옆에 한 줄씩 적는다.
const EVIDENCE_FACTS = [
  { title: '공원 경계 경고', body: '국립공원 같은 자연공원 경계 안일 수 있으면 경고하고, 어느 기관 자료를 언제 기준으로 봤는지 적어요.' },
  { title: '베이스캠프 제한', body: '경고가 뜬 박지로는 베이스캠프를 열 수 없어요. 여럿이 함께 야영 금지 구역에 들어가지 않게 하려는 거예요.' },
  { title: '휴장 표시', body: '공공 야영장, 자연휴양림, 회원이 제보한 박지를 한 지도에서 보고, 휴장 중인 곳도 숨기지 않고 표시해요.' },
  { title: '날씨와 일몰', body: '기상청과 한국천문연구원 자료를 써요. 날씨를 불러오지 못해도 나머지 정보는 그대로 보여 드려요.' },
];

// 처음 온 사람에게 뫼와들이 무엇을 하는지 보여 주는 첫 화면이다.
// 첫 화면은 실제 지도와 근거 시트이고, 아래 섹션은 차별점(판단 근거, 동행 신뢰, 장소에서 동행·행사로 이어지는 흐름)을 차례로 설명한다.
export function LandingPage() {
  useEffect(() => {
    document.title = '뫼와들 · 자도 되는 박지와 믿을 수 있는 동행';
  }, []);

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <AnnouncementBar />
      <SiteHeader mobileAccount />

      <main className="flex-1 break-keep">
        <LandingHero />

        <section aria-labelledby="landing-evidence" className="border-t border-line">
          <div className="mx-auto max-w-screen-xl px-4 py-16 lg:py-28">
            <h2 id="landing-evidence" className={SECTION_TITLE_CLASS}>
              여기서 자도 되는지,
              <br />
              근거를 같이 보여 줘요
            </h2>
            <p className={SECTION_LEAD_CLASS}>
              경고는 겁주려는 게 아니라 판단하라고 있어요. 그래서 경고마다 출처와 기준일을 함께 적어요.
            </p>
            <div className="mt-12 grid items-start gap-10 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-16">
              <WarningPreview />
              <FactList items={EVIDENCE_FACTS} />
            </div>
          </div>
        </section>

        <section aria-labelledby="landing-trust" className="border-t border-line">
          <div className="mx-auto max-w-screen-xl px-4 py-16 lg:py-28">
            <h2 id="landing-trust" className={SECTION_TITLE_CLASS}>
              이 사람과 가도 되는지,
              <br />
              쌓인 기록으로 판단해요
            </h2>
            <p className={SECTION_LEAD_CLASS}>
              동행 후기는 내가 먼저 써야 상대 후기를 볼 수 있어요. 그래서 서로 눈치 보지 않고 솔직하게 남겨요. 성희롱이나
              위협 신고는 접수되는 즉시 상대를 임시 정지해요.
            </p>
            <div className="mt-12 grid items-start gap-10 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-16">
              <TrustPreview />
              <dl className="flex flex-col">
                {TRUST_LEVEL_MEANINGS.map((meaning, level) => {
                  const badge = trustLevelBadge(level);
                  return (
                    <div key={badge.label} className="flex flex-col gap-1.5 border-t border-line py-5 first:border-t-0 first:pt-0">
                      <dt>
                        <StatusText tone={badge.tone} icon={badge.icon}>
                          {badge.label}
                        </StatusText>
                      </dt>
                      <dd className="text-base leading-relaxed text-ink">{meaning}</dd>
                    </div>
                  );
                })}
              </dl>
            </div>
          </div>
        </section>

        <section aria-labelledby="landing-flow" className="border-t border-line">
          <div className="mx-auto max-w-screen-xl px-4 py-16 lg:py-28">
            <h2 id="landing-flow" className={SECTION_TITLE_CLASS}>
              장소를 고르면
              <br />
              동행과 행사까지 이어져요
            </h2>
            {/* 세 단계를 같은 모양 칸으로 나누지 않고, 한 줄로 이어지는 길로 보여 준다. 단계 이름마다 그 화면으로 가는 링크다. */}
            <ol className="mt-10 flex flex-wrap items-center gap-x-3 gap-y-2 text-2xl font-bold tracking-tight text-ink lg:text-[2rem]">
              <FlowStep to="/map" title="장소 찾기" />
              <FlowStep to="/basecamps" title="베이스캠프 합류" />
              <FlowStep to="/programs" title="공식 행사 신청" last />
            </ol>
            <p className="mt-6 max-w-3xl text-lg leading-relaxed text-ink-muted">
              지도에서 갈 곳을 고르면 시설과 후기, 날씨, 그곳으로 가는 베이스캠프가 한 화면에 모여요. 같은 날 같은 곳으로
              가는 베이스캠프에 합류 신청을 보내고, 확정되면 멤버끼리만 연락 수단이 공개돼요. 혼자 가기 망설여지면 공식
              행사에 신청해요. 먼저 신청한 순서대로 정원만큼 받아요.
            </p>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  );
}

function FactList({ items }: { items: ReadonlyArray<{ title: string; body: string }> }) {
  return (
    <dl className="flex flex-col">
      {items.map((item) => (
        <div key={item.title} className="flex flex-col gap-1.5 border-t border-line py-5 first:border-t-0 first:pt-0">
          <dt className="text-base font-semibold text-ink">{item.title}</dt>
          <dd className="text-base leading-relaxed text-ink-muted">{item.body}</dd>
        </div>
      ))}
    </dl>
  );
}

// 흐름의 한 단계다. 단계 이름이 그 화면으로 가는 링크이고, 마지막 단계가 아니면 뒤에 다음 단계로 이어지는 화살표를 둔다.
function FlowStep({ to, title, last = false }: { to: string; title: string; last?: boolean }) {
  return (
    <li className="inline-flex items-center gap-3">
      <Link to={to} className="underline decoration-line decoration-2 underline-offset-[0.25em] hover:text-accent hover:decoration-accent">
        {title}
      </Link>
      {!last && <Icon name="chevronRight" size={28} className="shrink-0 text-ink-subtle" />}
    </li>
  );
}
