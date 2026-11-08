import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SyncJobRun, SyncJobType } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminSyncJobsPage } from './AdminSyncJobsPage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const ME = {
  memberId: 1,
  email: 'admin@b.c',
  nickname: '관리자',
  status: 'ACTIVE',
  role: 'ADMIN',
  selfAgeGroup: null,
  selfGender: null,
  identityVerified: true,
  trustLevel: 1,
};

const JOB_LABELS: Record<SyncJobType, string> = {
  GOCAMPING: '고캠핑',
  FOREST: '국립 자연휴양림',
  FOREST_STANDARD: '공립·사립 휴양림',
  PARK_BOUNDARY: '공원 경계',
  BAKJI_REJUDGE: '박지 경고 다시 확인',
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function pageOf(content: SyncJobRun[], page = 0, hasNext = false) {
  return { content, page, size: 20, hasNext };
}

function run(overrides: Partial<SyncJobRun> = {}): SyncJobRun {
  return {
    jobRunId: 1,
    jobType: 'GOCAMPING',
    status: 'COMPLETED',
    processedCount: 1234,
    skippedCount: 5,
    errorMessage: null,
    startedAt: '2026-11-01T03:00:00Z',
    finishedAt: new Date().toISOString(),
    ...overrides,
  };
}

interface Call {
  method: string;
  query: URLSearchParams;
  body: unknown;
}

describe('관리자 데이터 불러오기', () => {
  let container: HTMLDivElement;
  let root: Root;
  let calls: Call[];
  let me: typeof ME;
  let runs: (query: URLSearchParams) => SyncJobRun[];
  let history: (query: URLSearchParams) => Response;
  let start: () => Response;

  const text = (element: Element | null = container) => element?.textContent ?? '';
  const button = (label: string, scope: ParentNode = container) =>
    [...scope.querySelectorAll('button')].find((el) => el.textContent === label);
  const card = (jobType: SyncJobType) => {
    const found = [...container.querySelectorAll('section[aria-label="작업별 상태"] ul > li')].find(
      (el) => el.querySelector('h3')?.textContent === JOB_LABELS[jobType],
    );
    if (!found) throw new Error(`${JOB_LABELS[jobType]} 카드가 없다`);
    return found;
  };
  const historyRows = () => [...container.querySelectorAll('section[aria-label="실행 기록"] ul > li')];
  const gets = () => calls.filter((call) => call.method === 'GET');
  const posts = () => calls.filter((call) => call.method === 'POST');
  const historyGets = () => gets().filter((call) => call.query.get('size') === '20');
  const dialog = () => {
    const found = container.querySelector('dialog');
    if (!found) throw new Error('실행 확인 대화상자가 없다');
    return found;
  };

  async function render() {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          <AdminSyncJobsPage />
        </SessionProvider>,
      );
    });
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    calls = [];
    me = ME;
    runs = () => [];
    history = (query) => json(200, pageOf(runs(query)));
    start = () => json(202, { jobRunId: 99 });
    window.history.replaceState(null, '', '/admin/sync-jobs');
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const parsed = new URL(url, 'http://localhost');
        const method = init?.method ?? 'GET';
        if (parsed.pathname === '/api/me') return json(200, me);
        if (parsed.pathname !== '/api/admin/sync-jobs') return json(404, { code: 'NOT_FOUND', message: 'x' });
        calls.push({
          method,
          query: parsed.searchParams,
          body: typeof init?.body === 'string' ? JSON.parse(init.body) : null,
        });
        return method === 'POST' ? start() : history(parsed.searchParams);
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('작업마다 최근 실행의 상태, 시작과 끝, 처리·건너뜀 건수를 보이고, 실행한 적 없는 작업은 그렇게 알린다', async () => {
    runs = (query) =>
      query.get('jobType') === 'FOREST'
        ? [
            run({
              jobRunId: 2,
              jobType: 'FOREST',
              status: 'FAILED',
              processedCount: 7,
              skippedCount: 2,
              errorMessage: '파일을 읽지 못했어요',
              finishedAt: '2026-11-01T03:05:00Z',
            }),
          ]
        : query.get('jobType') === 'GOCAMPING'
          ? [run()]
          : [];

    await render();

    const gocamping = card('GOCAMPING');
    expect(text(gocamping)).toContain('완료');
    expect(text(gocamping)).toContain('11월 1일 (일) 12:00');
    expect(text(gocamping)).toContain('1,234건');
    expect(text(gocamping)).toContain('5건');
    const forest = card('FOREST');
    expect(text(forest)).toContain('실패');
    expect(text(forest)).toContain('11월 1일 (일) 12:05');
    expect(text(forest)).toContain('파일을 읽지 못했어요');
    expect(text(card('FOREST_STANDARD'))).toContain('아직 실행한 적이 없어요');
    expect(text(card('PARK_BOUNDARY'))).toContain('성공하면 박지 경고 다시 확인이 이어서 실행돼요');
  });

  it('카드 요청은 작업 종류마다 한 번씩, 고캠핑만 최근 50번까지 받는다', async () => {
    await render();

    const cardCalls = gets().filter((call) => call.query.get('size') !== '20');
    expect(cardCalls.map((call) => [call.query.get('jobType'), call.query.get('size')])).toEqual([
      ['GOCAMPING', '50'],
      ['FOREST', '1'],
      ['FOREST_STANDARD', '1'],
      ['PARK_BOUNDARY', '1'],
      ['BAKJI_REJUDGE', '1'],
    ]);
  });

  it('실행 기록은 작업 종류 조건 없이 처음 쪽부터 받아 작업 이름, 상태, 건수, 오류를 한 줄씩 보여 준다', async () => {
    runs = (query) =>
      query.get('size') === '20'
        ? [
            run({ jobRunId: 3, jobType: 'PARK_BOUNDARY', status: 'RUNNING', finishedAt: null }),
            run({ jobRunId: 2, jobType: 'FOREST', status: 'FAILED', errorMessage: '서버 파일이 없어요' }),
          ]
        : [];

    await render();

    expect(historyGets()).toHaveLength(1);
    expect(historyGets()[0]?.query.has('jobType')).toBe(false);
    expect(historyGets()[0]?.query.get('page')).toBe('0');
    expect(historyRows()).toHaveLength(2);
    expect(text(historyRows()[0] ?? null)).toContain('공원 경계');
    expect(text(historyRows()[0] ?? null)).toContain('실행 중');
    expect(text(historyRows()[1] ?? null)).toContain('국립 자연휴양림');
    expect(text(historyRows()[1] ?? null)).toContain('실패');
    expect(text(historyRows()[1] ?? null)).toContain('서버 파일이 없어요');
    expect(button('전체')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('실행 기록의 작업 종류를 고르면 그 종류만 다시 요청하고, 전체로 돌아가면 조건이 빠진다', async () => {
    runs = (query) =>
      query.get('size') !== '20'
        ? []
        : query.get('jobType') === 'FOREST'
          ? [run({ jobRunId: 8, jobType: 'FOREST', processedCount: 77 })]
          : [run({ jobRunId: 9, jobType: 'GOCAMPING', processedCount: 55 })];
    await render();
    expect(text(historyRows()[0] ?? null)).toContain('55건');

    const segment = [...container.querySelectorAll('[aria-label="작업 종류"] button')].find(
      (el) => el.textContent === '국립 자연휴양림',
    );
    await act(async () => (segment as HTMLButtonElement).click());

    expect(historyGets().at(-1)?.query.get('jobType')).toBe('FOREST');
    expect(historyGets().at(-1)?.query.get('page')).toBe('0');
    expect(segment?.getAttribute('aria-pressed')).toBe('true');
    expect(historyRows()).toHaveLength(1);
    expect(text(historyRows()[0] ?? null)).toContain('77건');

    await act(async () => button('전체')?.click());

    expect(historyGets().at(-1)?.query.has('jobType')).toBe(false);
    expect(text(historyRows()[0] ?? null)).toContain('55건');
  });

  it('실행 기록이 없으면 빈 상태 안내를 보여 준다', async () => {
    await render();

    expect(text()).toContain('실행 기록이 없어요');
    expect(text()).toContain('작업을 실행하면 여기에 기록이 쌓여요.');
    expect(historyRows()).toHaveLength(0);
  });

  it('더 보기를 누르면 다음 쪽을 이어 붙이고, 마지막 쪽이면 버튼이 사라진다', async () => {
    history = (query) => {
      if (query.get('size') !== '20') return json(200, pageOf([]));
      return query.get('page') === '1'
        ? json(200, pageOf([run({ jobRunId: 2, jobType: 'FOREST' })], 1, false))
        : json(200, pageOf([run({ jobRunId: 1, jobType: 'GOCAMPING' })], 0, true));
    };
    await render();
    expect(historyRows()).toHaveLength(1);

    await act(async () => button('더 보기')?.click());

    expect(historyGets().at(-1)?.query.get('page')).toBe('1');
    expect(historyRows()).toHaveLength(2);
    expect(text(historyRows()[1] ?? null)).toContain('국립 자연휴양림');
    expect(button('더 보기')).toBeUndefined();
  });

  it('실행을 누르면 설명이 든 확인 대화상자가 뜨고, 취소하면 작업을 시작하지 않는다', async () => {
    await render();

    await act(async () => button('실행', card('PARK_BOUNDARY'))?.click());

    expect(text(dialog())).toContain('공원 경계 작업을 실행할까요?');
    expect(text(dialog())).toContain('서버에 있는 공원 경계 파일을 다시 불러와요.');

    await act(async () => button('취소', dialog())?.click());

    expect(container.querySelector('dialog')).toBeNull();
    expect(posts()).toHaveLength(0);
  });

  it('대화상자에서 실행을 확인하면 그 작업 종류로 시작을 요청하고 카드와 기록을 다시 받아 온다', async () => {
    await render();
    const before = gets().length;
    runs = (query) =>
      query.get('jobType') === 'FOREST_STANDARD' || query.get('size') === '20'
        ? [run({ jobRunId: 99, jobType: 'FOREST_STANDARD', status: 'RUNNING', finishedAt: null })]
        : [];

    await act(async () => button('실행', card('FOREST_STANDARD'))?.click());
    await act(async () => button('실행', dialog())?.click());

    expect(posts()).toHaveLength(1);
    expect(posts()[0]?.body).toEqual({ jobType: 'FOREST_STANDARD' });
    expect(container.querySelector('dialog')).toBeNull();
    expect(gets().length).toBeGreaterThan(before);
    expect(text(card('FOREST_STANDARD'))).toContain('실행 중');
    expect(text(historyRows()[0] ?? null)).toContain('공립·사립 휴양림');
  });

  it('작업을 시작하지 못하면 그 카드에 오류를 보여 주고 대화상자를 닫는다', async () => {
    start = () =>
      json(409, { code: 'INVALID_STATE', message: '다른 작업이 먼저 실행 중이에요', traceId: 'trace-start' });
    await render();

    await act(async () => button('실행', card('GOCAMPING'))?.click());
    await act(async () => button('실행', dialog())?.click());

    expect(posts()).toHaveLength(1);
    expect(container.querySelector('dialog')).toBeNull();
    const alert = card('GOCAMPING').querySelector('[role="alert"]');
    expect(text(alert)).toContain('작업을 실행하지 못했어요');
    expect(text(alert)).toContain('다른 작업이 먼저 실행 중이에요');
    expect(card('FOREST').querySelector('[role="alert"]')).toBeNull();
  });

  it('이미 실행 중인 작업은 실행 버튼이 막히고 이유를 밝히며, 10초마다 새로고침한다고 알린다', async () => {
    runs = (query) =>
      query.get('jobType') === 'FOREST' ? [run({ jobRunId: 4, jobType: 'FOREST', status: 'RUNNING' })] : [];

    await render();

    const forestRun = button('실행', card('FOREST'));
    expect(forestRun?.disabled).toBe(true);
    expect(text(card('FOREST'))).toContain('이미 실행 중이에요');
    expect(button('실행', card('GOCAMPING'))?.disabled).toBe(false);
    expect(text()).toContain('실행 중인 작업이 있어서 10초마다 새로고침해요.');
  });

  it('실행 중인 작업이 있으면 10초 뒤 스스로 다시 받아 와서 끝난 결과로 바뀐다', async () => {
    let finished = false;
    runs = (query) =>
      query.get('jobType') === 'FOREST'
        ? [run({ jobRunId: 4, jobType: 'FOREST', status: finished ? 'COMPLETED' : 'RUNNING' })]
        : [];
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    await render();
    const before = gets().length;
    expect(text(card('FOREST'))).toContain('실행 중');

    finished = true;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    expect(gets().length).toBeGreaterThan(before);
    expect(text(card('FOREST'))).toContain('완료');
    expect(text()).not.toContain('10초마다 새로고침해요');
  });

  it('새로고침 버튼은 카드와 기록을 다시 받아 온다', async () => {
    await render();
    const before = gets().length;
    runs = (query) =>
      query.get('jobType') === 'BAKJI_REJUDGE' ? [run({ jobRunId: 6, jobType: 'BAKJI_REJUDGE' })] : [];

    await act(async () => button('새로고침')?.click());

    expect(gets().length).toBe(before * 2);
    expect(text(card('BAKJI_REJUDGE'))).toContain('완료');
  });

  it('고캠핑을 2일 넘게 불러오지 못했으면 마지막 성공 시각과 함께 경고한다', async () => {
    runs = (query) =>
      query.get('jobType') === 'GOCAMPING'
        ? [
            run({ jobRunId: 3, status: 'FAILED', finishedAt: new Date().toISOString() }),
            run({ jobRunId: 2, status: 'COMPLETED', finishedAt: '2026-10-01T03:00:00Z' }),
          ]
        : [];

    await render();

    expect(text()).toContain('고캠핑 데이터를 2일 넘게 불러오지 못했어요');
    expect(text()).toContain('마지막 성공은 10월 1일 (목) 12:00이에요.');
  });

  it('고캠핑이 최근 50번 안에 한 번도 성공하지 않았으면 그렇게 경고한다', async () => {
    runs = (query) => (query.get('jobType') === 'GOCAMPING' ? [run({ status: 'FAILED' })] : []);

    await render();

    expect(text()).toContain('고캠핑 데이터를 2일 넘게 불러오지 못했어요');
    expect(text()).toContain('최근 50번 실행 중에 성공한 기록이 없어요.');
  });

  it('고캠핑을 최근에 성공했으면 경고하지 않는다', async () => {
    runs = (query) => (query.get('jobType') === 'GOCAMPING' ? [run()] : []);

    await render();

    expect(text()).not.toContain('2일 넘게');
  });

  it('작업 상태를 못 불러오면 오류를 보이고, 다시 불러오기로 카드를 되찾는다', async () => {
    let failing = true;
    history = (query) =>
      failing && query.get('size') !== '20'
        ? json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-cards' })
        : json(200, pageOf(query.get('jobType') === 'GOCAMPING' ? [run()] : []));
    await render();

    const alert = container.querySelector('section[aria-label="작업별 상태"] [role="alert"]');
    expect(text(alert)).toContain('작업 상태를 불러오지 못했어요');
    expect(container.querySelectorAll('section[aria-label="작업별 상태"] ul > li')).toHaveLength(0);

    failing = false;
    await act(async () => button('다시 불러오기')?.click());

    expect(container.querySelector('section[aria-label="작업별 상태"] [role="alert"]')).toBeNull();
    expect(text(card('GOCAMPING'))).toContain('완료');
  });

  it('실행 기록을 못 불러오면 오류를 보이고, 다시 불러오기로 기록을 되찾는다', async () => {
    let failing = true;
    history = (query) =>
      failing && query.get('size') === '20'
        ? json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-history' })
        : json(200, pageOf(query.get('size') === '20' ? [run({ jobRunId: 5 })] : []));
    await render();

    const alert = container.querySelector('section[aria-label="실행 기록"] [role="alert"]');
    expect(text(alert)).toContain('실행 기록을 불러오지 못했어요');
    expect(text(alert)).toContain('trace-history');
    expect(text()).not.toContain('실행 기록이 없어요');

    failing = false;
    await act(async () => button('다시 불러오기')?.click());

    expect(container.querySelector('section[aria-label="실행 기록"] [role="alert"]')).toBeNull();
    expect(historyRows()).toHaveLength(1);
  });

  it('관리자가 아니면 작업 목록을 요청하지 않고 안내만 보여 준다', async () => {
    me = { ...ME, role: 'MEMBER' };

    await render();

    expect(text()).toContain('관리자만 볼 수 있어요');
    expect(calls).toHaveLength(0);
  });
});
