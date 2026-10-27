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
    render: (params) => {
      const spotId = toPositiveId(params.spotId);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="bakjiEdit" editSpotId={spotId} />;
    },
  },
  { path: '/basecamps', render: () => <MapPage mode="basecamps" />, opensMap: true },
  {
    path: '/basecamps/new',
    opensMap: true,
    render: () => {
      const spotId = toPositiveId(new URLSearchParams(window.location.search).get('spotId') ?? undefined);
      return spotId === null ? <NotFoundPage /> : <MapPage mode="basecampOpen" openSpotId={spotId} />;
    },
  },
  {
    path: '/basecamps/:basecampId',
    opensMap: true,
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <MapPage mode="basecamps" basecampId={basecampId} />;
    },
  },
  {
    path: '/basecamps/:basecampId/manage',
    render: (params) => {
      const basecampId = toPositiveId(params.basecampId);
      return basecampId === null ? <NotFoundPage /> : <BasecampManagePage key={basecampId} basecampId={basecampId} />;
    },
  },
  { path: '/community', render: () => <CommunityListPage />, preload: CommunityListPage.preload },
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
