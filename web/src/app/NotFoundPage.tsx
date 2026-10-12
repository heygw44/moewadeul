import { SiteHeader } from './layout/SiteHeader';
import { Link } from './router';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <SiteHeader mobileAccount />
      <main className="flex flex-1 items-center justify-center bg-canvas px-4 py-10">
        <section className="w-full max-w-sm rounded-control border border-line bg-surface p-6">
          <h1 className="text-xl font-bold tracking-tight">페이지를 찾을 수 없어요</h1>
          <p className="mt-2 text-ink-muted">주소가 바뀌었거나 없는 페이지예요.</p>
          <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-2">
            <Link
              to="/map"
              className="inline-flex min-h-11 items-center rounded-control bg-ink px-4 font-semibold text-white hover:bg-ink-muted"
            >
              지도로 가기
            </Link>
            <Link
              to="/"
              className="inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-4 hover:text-accent-strong"
            >
              첫 화면으로
            </Link>
          </div>
        </section>
      </main>
    </div>
  );
}
