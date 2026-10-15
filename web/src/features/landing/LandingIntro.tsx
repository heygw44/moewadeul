import type { CSSProperties } from 'react';

// 뫼와들이 무엇인지 한 문단으로 열고, 아래 네 칸이 누가·무엇이·어디서·누구와를 답한다.
// 값은 정책과 맞춘다. 예를 들어 글쓰기는 이메일 인증, 베이스캠프는 본인확인이 필요하다.
const COLUMNS = [
  {
    lead: '누가 쓰나요.',
    text: '성인 백패커예요. 처음 박지를 고르는 사람도, 혼자 다니던 사람도 써요. 글을 쓰려면 이메일 인증을, 베이스캠프를 열거나 합류하려면 본인확인을 마쳐야 해요.',
  },
  {
    lead: '무엇이 있나요.',
    text: '공공 야영장과 자연휴양림, 그리고 회원이 알려 준 박지예요. 자연공원 경계 안으로 보이는 박지에는 경고와 자료 출처, 기준일을 함께 붙여요.',
  },
  {
    lead: '어디서 찾나요.',
    text: '지도에서 산, 숲, 계곡, 바다, 섬, 호수로 걸러 봐요. 지역 이름이나 문장으로 찾을 수 있고, 장소마다 날씨와 일몰 시각을 같이 보여 줘요.',
  },
  {
    lead: '누구와 가나요.',
    text: '같은 날 같은 곳에서 자는 모임, 베이스캠프에 합류해요. 오픈채팅 링크는 확정된 멤버에게만 열리고, 다녀온 뒤에는 서로 블라인드 후기를 남겨요.',
  },
];

export function LandingIntro() {
  return (
    <section aria-label="뫼와들 소개" className="mx-auto max-w-screen-xl px-4 py-20 lg:px-10 lg:py-28">
      <p data-reveal className="max-w-[34em] text-[22px] leading-[1.35] tracking-[-0.02em] text-ink lg:text-[clamp(22px,2.2vw,32px)]">
        뫼와들은 뫼(산)와 들의 순우리말이에요. 산과 들에서 하룻밤을 보내려는 사람이 자도 되는 자리를 고르고, 함께 갈 사람을 기록으로 고르고,
        혼자가 어려우면 공식 행사에 신청하는 곳이에요.
      </p>
      <div className="mt-10 grid gap-7 text-[15px] leading-relaxed text-ink-muted md:grid-cols-2 lg:mt-[72px] lg:grid-cols-4 lg:gap-6">
        {COLUMNS.map((column, index) => (
          <p key={column.lead} data-reveal style={{ '--reveal-order': index } as CSSProperties}>
            <b className="font-bold text-ink">{column.lead}</b> {column.text}
          </p>
        ))}
      </div>
    </section>
  );
}
