import { useEffect, useRef } from 'react';
import { AnnouncementBar } from '../program/AnnouncementBar';
import { LandingAgenda } from './LandingAgenda';
import { LandingClose } from './LandingClose';
import { LandingFooter } from './LandingFooter';
import { LandingHero } from './LandingHero';
import { LandingIntro } from './LandingIntro';
import { LandingNotes } from './LandingNotes';
import { LandingShowcase } from './LandingShowcase';
import { useReveal } from './useReveal';
import './landing.css';

// 처음 온 사람에게 뫼와들이 무엇을 하는지 보여 주는 첫 화면이다. 사진 대신 제품 조각을 실제 크기로 보여 준다.
// 순서는 무대 위 장소 패널 → 첫 문단과 네 칸 → 베이스캠프 한 장 → 어두운 줄 → 공식 행사 → 열린 첫 단계 → 칸 바닥글이다.
export function LandingPage() {
  const mainRef = useRef<HTMLElement>(null);
  useReveal(mainRef);

  useEffect(() => {
    document.title = '뫼와들 · 자도 되는 곳에서, 믿을 사람과';
  }, []);

  return (
    <div id="top" className="flex min-h-dvh flex-col bg-canvas">
      <AnnouncementBar />
      <main ref={mainRef} className="flex-1 break-keep">
        <LandingHero />
        <LandingIntro />
        <LandingNotes />
        <LandingShowcase />
        <LandingAgenda />
        <LandingClose />
      </main>
      <LandingFooter />
    </div>
  );
}
