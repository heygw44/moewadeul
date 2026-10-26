import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { findRoute } from './app/router';
import { routes } from './app/routes';
import { loadFontsForFirstScreen } from './app/fonts';
import { installStaleChunkReload } from './app/staleChunkReload';
import { loadKakaoMaps } from './map/kakaoLoader';
import './styles/theme.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('index.html에 #root 요소가 없습니다.');
}

installStaleChunkReload();

// 처음 연 주소의 화면 코드가 따로 나뉘어 있으면 React가 그리기를 기다리지 않고 받기 시작한다.
// 지도 화면이면 MapPage가 그려지기를 기다리지 않고 지도 SDK도 먼저 받는다. 글꼴은 SDK 뒤에 받는다.
const firstRoute = findRoute(routes, window.location.pathname);
firstRoute?.preload?.();
// 지도 SDK 실패는 MapPage가 지도를 열 때 안내한다. 여기서는 글꼴을 받는 시점만 정한다.
void loadFontsForFirstScreen(firstRoute?.opensMap === true, loadKakaoMaps);

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
