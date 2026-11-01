import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { useDocumentTitle } from '../../components/useDocumentTitle';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingStage } from './LandingStage';

export function LandingPage() {
  useDocumentTitle('뫼와들 · 잘 곳과 함께 갈 사람을 한 번에 찾아요');

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <AnnouncementBar />
      <SiteHeader mobileAccount />

      <main className="flex-1 break-keep">
        <LandingStage />
      </main>

      <SiteFooter />
    </div>
  );
}
