import { useEffect } from 'react';
import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingStage } from './LandingStage';

export function LandingPage() {
  useEffect(() => {
    document.title = '뫼와들 · 잘 수 있는 박지와 믿을 수 있는 동행';
  }, []);

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
