import { AdminReportDetailPage } from '../features/admin/AdminReportDetailPage';
import { AdminReportsPage } from '../features/admin/AdminReportsPage';
import { AdminSpotsPage } from '../features/admin/AdminSpotsPage';
import { BasecampManagePage } from '../features/basecamp/BasecampManagePage';
import { CommunityListPage } from '../features/community/CommunityListPage';
import { CommunityPostDetailPage } from '../features/community/CommunityPostDetailPage';
import { CommunityPostFormPage } from '../features/community/CommunityPostFormPage';
import { MyProgramApplicationsPage } from '../features/program/MyProgramApplicationsPage';
import { ProgramDetailPage } from '../features/program/ProgramDetailPage';
import { ProgramListPage } from '../features/program/ProgramListPage';
import { MyBasecampsPage } from '../features/basecamp/MyBasecampsPage';
import { NotificationsPage } from '../features/notification/NotificationsPage';
import { CompanionReviewsPage } from '../features/review/CompanionReviewsPage';
import { AccountSettingsPage } from '../features/member/AccountSettingsPage';
import { LoginPage } from '../features/member/LoginPage';
import { MemberProfilePage } from '../features/member/MemberProfilePage';
import { MyPage } from '../features/member/MyPage';
import { SignupPage } from '../features/member/SignupPage';
import { VerifyEmailPage } from '../features/member/VerifyEmailPage';
import { LandingPage } from '../features/landing/LandingPage';
import { IdentityVerificationPage } from '../features/trust/IdentityVerificationPage';
import { MapPage } from '../features/spot/MapPage';
import { MemoryDetailPage } from '../features/memory/MemoryDetailPage';
import { MemoryFormPage } from '../features/memory/MemoryFormPage';
import NotFoundPage from './NotFoundPage';
import type { LatLng } from '../map/types';
import type { RouteDef } from './router';

export const routes: RouteDef[] = [
  { path: '/', render: () => <LandingPage /> },
  { path: '/map', render: () => <MapPage /> },
  {
    path: '/spots/:spotId',
    // 장소 ID는 양의 정수만 있다. 그 밖의 값은 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: (params) => {
      const raw = params.spotId ?? '';
      const spotId = Number(raw);
      return /^\d+$/.test(raw) && Number.isSafeInteger(spotId) && spotId > 0 ? (
        <MapPage spotId={spotId} />
      ) : (
        <NotFoundPage />
      );
    },
  },
  {
    path: '/bakjis/new',
    // 지도 메뉴에서 넘어오면 주소에 처음 찍을 위치(lat, lng)와 제보 뒤 갈 곳(next=basecamp)이 붙는다.
    // 좌표가 숫자가 아니거나 범위를 벗어나면 버리고 빈 제보 화면을 연다.
    render: () => {
      const search = new URLSearchParams(window.location.search);
      const initialDraft = toLatLng(search.get('lat'), search.get('lng'));
      return (
        <MapPage
          mode="report"
          initialDraft={initialDraft ?? undefined}
          nextAfterReport={search.get('next') === 'basecamp' ? 'basecamp' : undefined}
        />
      );
    },
  },
  { path: '/basecamps', render: () => <MapPage mode="basecamps" /> },
  {
    path: '/basecamps/new',
    // 장소 ID는 주소의 spotId에서 읽는다. 양의 정수가 아니면 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: () => {
      const spotId = toPositiveId(new URLSearchParams(window.location.search).get('spotId') ?? undefined);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="basecampOpen" openSpotId={spotId} />;
    },
  },
  {
    path: '/basecamps/:basecampId',
    // 베이스캠프 ID도 양의 정수만 있다. 위의 '/basecamps/new'가 먼저 맞아야 하므로 이 경로는 그 뒤에 둔다.
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <MapPage mode="basecamps" basecampId={basecampId} />;
    },
  },
  {
    path: '/basecamps/:basecampId/manage',
    // 세그먼트 수가 달라서 '/basecamps/:basecampId'와 겹치지 않는다.
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <BasecampManagePage key={basecampId} basecampId={basecampId} />;
    },
  },
  { path: '/community', render: () => <CommunityListPage /> },
  // '/community/new'가 '/community/:postId'보다 먼저 맞아야 하므로 이 경로를 앞에 둔다.
  { path: '/community/new', render: () => <CommunityPostFormPage mode="create" /> },
  {
    path: '/community/:postId/edit',
    render: (params) => {
      const postId = toPositiveId(params.postId);
      return postId === null ? (
        <NotFoundPage />
      ) : (
        <CommunityPostFormPage key={postId} mode="edit" postId={postId} />
      );
    },
  },
  {
    path: '/community/:postId',
    render: (params) => {
      const postId = toPositiveId(params.postId);
      return postId === null ? <NotFoundPage /> : <CommunityPostDetailPage key={postId} postId={postId} />;
    },
  },
  // 지도 메뉴는 누른 좌표(lat, lng)를, 장소 상세는 장소 좌표와 spotId를 주소에 붙인다. 폼이 값을 읽고 검사한다.
  // '/memories/new'가 '/memories/:memoryId'보다 먼저 맞아야 하므로 이 경로를 앞에 둔다.
  { path: '/memories/new', render: () => <MemoryFormPage mode="create" /> },
  {
    path: '/memories/:memoryId/edit',
    render: (params) => {
      const memoryId = toPositiveId(params.memoryId);
      return memoryId === null ? <NotFoundPage /> : <MemoryFormPage key={memoryId} mode="edit" memoryId={memoryId} />;
    },
  },
  {
    path: '/memories/:memoryId',
    render: (params) => {
      const memoryId = toPositiveId(params.memoryId);
      return memoryId === null ? <NotFoundPage /> : <MemoryDetailPage key={memoryId} memoryId={memoryId} />;
    },
  },
  { path: '/programs', render: () => <ProgramListPage /> },
  {
    path: '/programs/:programId',
    render: (params) => {
      const programId = toPositiveId(params.programId);
      return programId === null ? <NotFoundPage /> : <ProgramDetailPage key={programId} programId={programId} />;
    },
  },
  { path: '/me/settings', render: () => <AccountSettingsPage /> },
  { path: '/me/basecamps', render: () => <MyBasecampsPage /> },
  { path: '/me/program-applications', render: () => <MyProgramApplicationsPage /> },
  { path: '/me/companion-reviews', render: () => <CompanionReviewsPage /> },
  { path: '/me/notifications', render: () => <NotificationsPage /> },
  { path: '/admin/reports', render: () => <AdminReportsPage /> },
  {
    path: '/admin/reports/:reportId',
    render: (params) => {
      const reportId = toPositiveId(params.reportId);
      return reportId === null ? <NotFoundPage /> : <AdminReportDetailPage key={reportId} reportId={reportId} />;
    },
  },
  { path: '/admin/spots', render: () => <AdminSpotsPage /> },
  { path: '/login', render: () => <LoginPage /> },
  { path: '/signup', render: () => <SignupPage /> },
  { path: '/verify-email', render: () => <VerifyEmailPage /> },
  { path: '/identity-verification', render: () => <IdentityVerificationPage /> },
  { path: '/me', render: () => <MyPage /> },
  {
    path: '/members/:memberId',
    // 회원 ID도 양의 정수만 있다. 그 밖의 값은 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: (params) => {
      const memberId = toPositiveId(params.memberId);
      return memberId === null ? <NotFoundPage /> : <MemberProfilePage key={memberId} memberId={memberId} />;
    },
  },
];

function toPositiveId(raw: string | undefined): number | null {
  const value = Number(raw ?? '');
  return /^\d+$/.test(raw ?? '') && Number.isSafeInteger(value) && value > 0 ? value : null;
}

function toLatLng(rawLat: string | null, rawLng: string | null): LatLng | null {
  if (rawLat === null || rawLng === null || rawLat.trim() === '' || rawLng.trim() === '') return null;
  const lat = Number(rawLat);
  const lng = Number(rawLng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  return { lat, lng };
}
