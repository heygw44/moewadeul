import { BasecampManagePage } from '../features/basecamp/BasecampManagePage';
import { MyBasecampsPage } from '../features/basecamp/MyBasecampsPage';
import { NotificationsPage } from '../features/notification/NotificationsPage';
import { CompanionReviewsPage } from '../features/review/CompanionReviewsPage';
import { AccountSettingsPage } from '../features/member/AccountSettingsPage';
import { LoginPage } from '../features/member/LoginPage';
import { PasswordResetPage } from '../features/member/PasswordResetPage';
import { MemberProfilePage } from '../features/member/MemberProfilePage';
import { MyPage } from '../features/member/MyPage';
import { SignupPage } from '../features/member/SignupPage';
import { VerifyEmailPage } from '../features/member/VerifyEmailPage';
import { IdentityVerificationPage } from '../features/trust/IdentityVerificationPage';
import { MapPage } from '../features/spot/MapPage';
import NotFoundPage from './NotFoundPage';
import { lazyPage } from './lazyPage';
import type { LatLng } from '../map/types';
import type { RouteDef } from './router';

// 관리자, 랜딩, 추억, 커뮤니티, 행사 화면은 코드를 따로 받는다. 지도만 열어도 이 화면들의 코드까지 받지 않게 하려는 것이다.
// 링크에 포인터를 올리거나 초점을 두면 preload가 그 화면의 코드를 미리 받는다.
const AdminReportDetailPage = lazyPage(
  () => import('../features/admin/AdminReportDetailPage'),
  'AdminReportDetailPage',
);
const AdminReportsPage = lazyPage(() => import('../features/admin/AdminReportsPage'), 'AdminReportsPage');
const AdminMemoriesPage = lazyPage(() => import('../features/admin/AdminMemoriesPage'), 'AdminMemoriesPage');
const AdminSyncJobsPage = lazyPage(() => import('../features/admin/AdminSyncJobsPage'), 'AdminSyncJobsPage');
const AdminSpotsPage = lazyPage(() => import('../features/admin/AdminSpotsPage'), 'AdminSpotsPage');
const CommunityListPage = lazyPage(() => import('../features/community/CommunityListPage'), 'CommunityListPage');
const CommunityPostDetailPage = lazyPage(
  () => import('../features/community/CommunityPostDetailPage'),
  'CommunityPostDetailPage',
);
const CommunityPostFormPage = lazyPage(
  () => import('../features/community/CommunityPostFormPage'),
  'CommunityPostFormPage',
);
const MyProgramApplicationsPage = lazyPage(
  () => import('../features/program/MyProgramApplicationsPage'),
  'MyProgramApplicationsPage',
);
const ProgramDetailPage = lazyPage(() => import('../features/program/ProgramDetailPage'), 'ProgramDetailPage');
const ProgramListPage = lazyPage(() => import('../features/program/ProgramListPage'), 'ProgramListPage');
const LandingPage = lazyPage(() => import('../features/landing/LandingPage'), 'LandingPage');
const MemoryDetailPage = lazyPage(() => import('../features/memory/MemoryDetailPage'), 'MemoryDetailPage');
const MemoryFeedPage = lazyPage(() => import('../features/memory/MemoryFeedPage'), 'MemoryFeedPage');
const MemoryFormPage = lazyPage(() => import('../features/memory/MemoryFormPage'), 'MemoryFormPage');
const MemberMemoriesPage = lazyPage(() => import('../features/memory/MemberMemoriesPage'), 'MemberMemoriesPage');
const MyMemoriesPage = lazyPage(() => import('../features/memory/MyMemoriesPage'), 'MyMemoriesPage');

export const routes: RouteDef[] = [
  { path: '/', render: () => <LandingPage />, preload: LandingPage.preload },
  { path: '/map', render: () => <MapPage />, opensMap: true },
  {
    path: '/spots/:spotId',
    opensMap: true,
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
    opensMap: true,
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
  {
    path: '/bakjis/:spotId/edit',
    opensMap: true,
    // 세그먼트 수가 달라서 '/bakjis/new'와 겹치지 않는다. 제보한 회원인지는 수정 화면과 서버가 확인한다.
    render: (params) => {
      const spotId = toPositiveId(params.spotId);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="bakjiEdit" editSpotId={spotId} />;
    },
  },
  { path: '/basecamps', render: () => <MapPage mode="basecamps" />, opensMap: true },
  {
    path: '/basecamps/new',
    opensMap: true,
    // 장소 ID는 주소의 spotId에서 읽는다. 양의 정수가 아니면 서버에 묻지 않고 없는 페이지로 보여 준다.
    render: () => {
      const spotId = toPositiveId(new URLSearchParams(window.location.search).get('spotId') ?? undefined);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="basecampOpen" openSpotId={spotId} />;
    },
  },
  {
    path: '/basecamps/:basecampId',
    opensMap: true,
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
  { path: '/community', render: () => <CommunityListPage />, preload: CommunityListPage.preload },
  // '/community/new'가 '/community/:postId'보다 먼저 맞아야 하므로 이 경로를 앞에 둔다.
  {
    path: '/community/new',
    render: () => <CommunityPostFormPage mode="create" />,
    preload: CommunityPostFormPage.preload,
  },
  {
    path: '/community/:postId/edit',
    preload: CommunityPostFormPage.preload,
    render: (params) => {
      const postId = toPositiveId(params.postId);
      return postId === null ? <NotFoundPage /> : <CommunityPostFormPage key={postId} mode="edit" postId={postId} />;
    },
  },
  {
    path: '/community/:postId',
    preload: CommunityPostDetailPage.preload,
    render: (params) => {
      const postId = toPositiveId(params.postId);
      return postId === null ? <NotFoundPage /> : <CommunityPostDetailPage key={postId} postId={postId} />;
    },
  },
  // 지도 메뉴는 누른 좌표(lat, lng)를, 장소 상세는 장소 좌표와 spotId를 주소에 붙인다. 폼이 값을 읽고 검사한다.
  // '/memories/new'가 '/memories/:memoryId'보다 먼저 맞아야 하므로 이 경로를 앞에 둔다.
  // 세그먼트 수가 달라서 '/memories/new', '/memories/:memoryId'와 겹치지 않는다.
  { path: '/memories', render: () => <MemoryFeedPage />, preload: MemoryFeedPage.preload },
  { path: '/memories/new', render: () => <MemoryFormPage mode="create" />, preload: MemoryFormPage.preload },
  {
    path: '/memories/:memoryId/edit',
    preload: MemoryFormPage.preload,
    render: (params) => {
      const memoryId = toPositiveId(params.memoryId);
      return memoryId === null ? <NotFoundPage /> : <MemoryFormPage key={memoryId} mode="edit" memoryId={memoryId} />;
    },
  },
  {
    path: '/memories/:memoryId',
    preload: MemoryDetailPage.preload,
    render: (params) => {
      const memoryId = toPositiveId(params.memoryId);
      return memoryId === null ? <NotFoundPage /> : <MemoryDetailPage key={memoryId} memoryId={memoryId} />;
    },
  },
  { path: '/programs', render: () => <ProgramListPage />, preload: ProgramListPage.preload },
  {
    path: '/programs/:programId',
    preload: ProgramDetailPage.preload,
    render: (params) => {
      const programId = toPositiveId(params.programId);
      return programId === null ? <NotFoundPage /> : <ProgramDetailPage key={programId} programId={programId} />;
    },
  },
  { path: '/me/settings', render: () => <AccountSettingsPage /> },
  { path: '/me/basecamps', render: () => <MyBasecampsPage /> },
  {
    path: '/me/program-applications',
    render: () => <MyProgramApplicationsPage />,
    preload: MyProgramApplicationsPage.preload,
  },
  { path: '/me/memories', render: () => <MyMemoriesPage />, preload: MyMemoriesPage.preload },
  { path: '/me/companion-reviews', render: () => <CompanionReviewsPage /> },
  { path: '/me/notifications', render: () => <NotificationsPage /> },
  { path: '/admin/reports', render: () => <AdminReportsPage />, preload: AdminReportsPage.preload },
  {
    path: '/admin/reports/:reportId',
    preload: AdminReportDetailPage.preload,
    render: (params) => {
      const reportId = toPositiveId(params.reportId);
      return reportId === null ? <NotFoundPage /> : <AdminReportDetailPage key={reportId} reportId={reportId} />;
    },
  },
  { path: '/admin/spots', render: () => <AdminSpotsPage />, preload: AdminSpotsPage.preload },
  { path: '/admin/memories', render: () => <AdminMemoriesPage />, preload: AdminMemoriesPage.preload },
  { path: '/admin/sync-jobs', render: () => <AdminSyncJobsPage />, preload: AdminSyncJobsPage.preload },
  { path: '/login', render: () => <LoginPage /> },
  { path: '/signup', render: () => <SignupPage /> },
  { path: '/password-reset', render: () => <PasswordResetPage /> },
  { path: '/verify-email', render: () => <VerifyEmailPage /> },
  { path: '/identity-verification', render: () => <IdentityVerificationPage /> },
  { path: '/me', render: () => <MyPage /> },
  {
    path: '/members/:memberId/memories',
    preload: MemberMemoriesPage.preload,
    render: (params) => {
      const memberId = toPositiveId(params.memberId);
      return memberId === null ? <NotFoundPage /> : <MemberMemoriesPage key={memberId} memberId={memberId} />;
    },
  },
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
