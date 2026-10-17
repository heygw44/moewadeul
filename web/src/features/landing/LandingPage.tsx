import { useEffect } from 'react';
import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingStage } from './LandingStage';

// 처음 온 사람에게 뫼와들이 무엇을 하는지 보여 주는 첫 화면이다.
// 첫 화면에서 공원 경계 판정을 직접 해 보고, 아래로 내려가며 박지 찾기·동행 구하기·공식 행사를 탭으로 골라 본다.
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
