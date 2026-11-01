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

const firstRoute = findRoute(routes, window.location.pathname);
firstRoute?.preload?.();
void loadFontsForFirstScreen(firstRoute?.opensMap === true, loadKakaoMaps);

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
