import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchSpotReviews, requestReviewImageUpload } from '../../api/reviews';
import type { ImageUpload, Page, SpotReview } from '../../api/types';
import { putImage } from '../../api/uploads';
import { createThumbnail } from '../../components/createThumbnail';
import { reencodeImage } from '../../components/reencodeImage';
import { SpotReviews } from './SpotReviews';

vi.mock('../../api/reviews', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reviews')>()),
  fetchSpotReviews: vi.fn(),
  requestReviewImageUpload: vi.fn(),
}));
vi.mock('../../api/uploads', () => ({ putImage: vi.fn() }));
vi.mock('../../components/createThumbnail', () => ({ createThumbnail: vi.fn() }));
vi.mock('../../components/reencodeImage', () => ({ reencodeImage: vi.fn() }));
vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({
    status: 'authenticated',
    me: { memberId: 1, nickname: '산사람', status: 'ACTIVE', trustLevel: 1 },
  }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function review(overrides: Partial<SpotReview> = {}): SpotReview {
  return {
    reviewId: 7,
    author: { memberId: 2, nickname: '별바라기' },
    visitedDate: '2026-10-04',
    rating: 4,
    content: '물이 가까웠다',
    images: [],
    createdAt: '2026-10-05T09:00:00+09:00',
    ...overrides,
  };
}

function page(items: SpotReview[]): Page<SpotReview> {
  return { content: items, page: 0, size: 10, hasNext: false };
}

describe('장소 후기', () => {
  let container: HTMLDivElement;
  let root: Root;

  const render = () =>
    act(async () => root.render(<SpotReviews spotId={3} initialRating={{ average: 4, count: 1 }} />));

  beforeEach(() => {
    window.history.replaceState(null, '', '/');
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
    vi.mocked(fetchSpotReviews).mockReset();
    vi.mocked(requestReviewImageUpload).mockReset();
    vi.mocked(putImage).mockReset();
    vi.mocked(createThumbnail).mockReset();
    vi.mocked(reencodeImage).mockReset();
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('후기 사진은 API가 준 URL을 그대로 보여 준다', async () => {
    vi.mocked(fetchSpotReviews).mockResolvedValue(
      page([review({ images: [{ imageId: 1, url: 'https://view.test/review/2/a_thumb.jpg?signed=1' }] })]),
    );

    await render();

    const photo = container.querySelector<HTMLImageElement>('ul[aria-label="후기 사진"] img');
    expect(photo?.getAttribute('src')).toBe('https://view.test/review/2/a_thumb.jpg?signed=1');
  });

  it('후기에 사진을 올리면 썸네일을 만들어 업로드 URL을 받을 때 크기를 보내고, 썸네일과 원본을 모두 올린다', async () => {
    vi.mocked(fetchSpotReviews).mockResolvedValue(page([]));
    const original = new Blob(['original'], { type: 'image/jpeg' });
    const thumbnail = new Blob(['small'], { type: 'image/jpeg' });
    vi.mocked(reencodeImage).mockResolvedValue(original);
    vi.mocked(createThumbnail).mockResolvedValue(thumbnail);
    vi.mocked(requestReviewImageUpload).mockResolvedValue({
      imageId: 11,
      uploadUrl: 'https://upload.test/original',
      method: 'PUT',
      headers: {},
      expiresAt: '2026-11-08T00:00:00Z',
      thumbnailUpload: {
        uploadUrl: 'https://upload.test/thumb',
        method: 'PUT',
        headers: {},
        expiresAt: '2026-11-08T00:00:00Z',
      },
    } satisfies ImageUpload);
    await render();
    await act(async () => {
      [...container.querySelectorAll('button')].find((element) => element.textContent === '후기 쓰기')?.click();
    });
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['x'], 'p.jpg', { type: 'image/jpeg' });

    await act(async () => {
      Object.defineProperty(input, 'files', { value: [file], configurable: true });
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(requestReviewImageUpload).toHaveBeenCalledWith({
      contentType: 'image/jpeg',
      sizeBytes: original.size,
      thumbnailSizeBytes: thumbnail.size,
    });
    expect(vi.mocked(putImage).mock.calls.map(([target, blob]) => [target.uploadUrl, blob])).toEqual([
      ['https://upload.test/thumb', thumbnail],
      ['https://upload.test/original', original],
    ]);
  });
});
