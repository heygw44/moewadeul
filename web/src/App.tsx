import NotFoundPage from './app/NotFoundPage';
import { Router } from './app/router';
import { routes } from './app/routes';
import { SessionProvider, useSession } from './app/session/session';
import { UnreadCountSync } from './features/notification/unreadCount';

export default function App() {
  return (
    <SessionProvider>
      <SignedInUnreadSync />
      <Router routes={routes} fallback={<NotFoundPage />} />
    </SessionProvider>
  );
}

function SignedInUnreadSync() {
  const session = useSession();
  return <UnreadCountSync enabled={session.status === 'authenticated' && session.me !== null} />;
}
