import { useEffect } from 'react';
import { SiteFooter } from '../../app/layout/SiteFooter';
import { SiteHeader } from '../../app/layout/SiteHeader';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingStage } from './LandingStage';

// 처음 온 사람에게 뫼와들이 무엇을 하는지 보여 주는 첫 화면이다.
// 화면을 채운 실제 지도 위로 장면 문장이 지나가며 차별점(판단 근거, 동행 신뢰, 장소에서 동행·행사로 이어지는 흐름)을 차례로 보여 준다.
export function LandingPage() {
  useEffect(() => {
    document.title = '뫼와들 · 잘 수 있는 박지와 믿을 수 있는 동행';
  }, []);

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <AnnouncementBar />
      <SiteHeader mobileAccount lineOnScroll />

      <main className="flex-1 break-keep">
        <LandingStage />
      </main>

      <SiteFooter />
    </div>
  );
}
