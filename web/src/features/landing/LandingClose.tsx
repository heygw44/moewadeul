import { useState, type CSSProperties, type FormEvent } from 'react';
import { navigate } from '../../app/router';
import { buttonClassName } from '../../components/buttonClass';

const NIGHTS = [1, 2, 3] as const;

// 페이지의 끝이다. 깊은 숲 면 왼쪽 아래에 마무리 문장을 두고, 오른쪽에 베이스캠프 열기 첫 단계를 이미 펼쳐 둔다.
// 대화상자 조각은 실제로 쓸 수 있다. 제목과 박 수를 고르고 버튼을 누르면 베이스캠프 화면으로 간다.
// 실제 열기는 그 화면에서 장소를 고른 뒤에 하므로, 여기서 서버에 무엇도 보내지 않는다.
export function LandingClose() {
  const [title, setTitle] = useState('11월 첫 주말, 대청봉 아래 1박');
  const [nights, setNights] = useState<(typeof NIGHTS)[number]>(1);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    navigate('/basecamps');
  };

  return (
    <section
      aria-labelledby="landing-close"
      className="grid min-h-[90svh] items-end gap-10 bg-brand px-4 pt-20 pb-12 text-canvas lg:grid-cols-12 lg:gap-6 lg:px-10 [&_:focus-visible]:outline-white"
    >
      <div className="lg:col-span-5 lg:pb-2">
        <h2 id="landing-close" data-reveal className="text-[13vw] leading-[0.96] font-extrabold tracking-[-0.05em] lg:text-[clamp(56px,7vw,112px)]">
          오늘 밤
          <br />
          잘 곳부터
        </h2>
        <p data-reveal style={{ '--reveal-order': 1 } as CSSProperties} className="mt-5 max-w-[38ch] text-[17px] text-brand-ink">
          제목과 박 수를 정하면 장소를 고르는 단계로 넘어가요. 베이스캠프를 열려면 본인확인을 먼저 마쳐야 해요.
        </p>
      </div>
      <form
        data-ui
        onSubmit={submit}
        className="flex w-full flex-col gap-[18px] self-center rounded-sheet bg-surface p-6 text-ink shadow-[0_30px_60px_-20px_rgb(0_0_0/0.45)] lg:col-span-5 lg:col-start-7 lg:max-w-[440px]"
      >
        <div>
          <p className="text-sm text-ink-muted">베이스캠프 열기 · 1/3</p>
          <h3 className="text-[22px] font-extrabold tracking-[-0.02em]">어떤 밤인가요?</h3>
        </div>
        <label className="flex flex-col gap-2">
          <span className="text-sm font-semibold">제목</span>
          <input
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={60}
            className="min-h-12 rounded-control border border-fill-strong bg-surface px-3.5 text-base focus:border-ink focus:outline-none"
          />
        </label>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-semibold">며칠 묵나요</legend>
          <div className="grid grid-cols-3 gap-1 rounded-control bg-fill p-1">
            {NIGHTS.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={nights === value}
                onClick={() => setNights(value)}
                className={`min-h-10 rounded text-[15px] ${nights === value ? 'bg-surface font-bold text-ink' : 'text-ink-muted'}`}
              >
                {value}박
              </button>
            ))}
          </div>
        </fieldset>
        <button type="submit" className={`${buttonClassName('primary')} w-full`}>
          장소 고르기
        </button>
      </form>
    </section>
  );
}
