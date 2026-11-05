import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProgramDetail } from '../../api/types';
import { SessionProvider } from '../../app/session/session';
import { AdminProgramFormPage } from './AdminProgramFormPage';

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

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function programDetail(overrides: Partial<ProgramDetail> = {}): ProgramDetail {
  return {
    programId: 7,
    title: '가을 백패킹 데이',
    description: '함께 걷고 하룻밤 머무는 행사예요.',
    locationText: '북한산 백운대 입구',
    spotId: 12,
    startAt: '2099-11-21T01:00:00Z',
    endAt: '2099-11-22T03:00:00Z',
    applyOpenAt: '2099-11-10T01:00:00Z',
    applyCloseAt: '2099-11-20T09:00:00Z',
    capacity: 200,
    remainingSeats: 150,
    fee: 30000,
    paymentDeadlineMinutes: 15,
    overnight: true,
    status: 'UPCOMING',
    ...overrides,
  };
}

const FIELD_LABELS =
  '제목|설명|장소 설명|지도 장소 번호 (선택)|신청 시작|신청 마감|행사 시작|행사 종료|정원 (명)|참가비 (원)|결제 기한 (분)|숙박 행사예요'.split(
    '|',
  );

type Call = { method: string; url: string; body: unknown };
type Reply = () => Promise<Response>;

describe('관리자 행사 등록·수정 화면', () => {
  let container: HTMLDivElement;
  let root: Root;
  let me: typeof ME;
  let calls: Call[];
  let detailReply: Reply;
  let saveReply: Reply;

  const text = (element: Element | null) => element?.textContent ?? '';
  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const link = (label: string) => [...container.querySelectorAll('a')].find((element) => element.textContent === label);
  const writes = () => calls.filter((call) => call.method !== 'GET');
  const location = () => window.location.pathname + window.location.search;

  function field(label: string): HTMLInputElement | HTMLTextAreaElement {
    const control = [...container.querySelectorAll('label')].find((element) => element.textContent === label)?.control;
    if (control instanceof HTMLInputElement || control instanceof HTMLTextAreaElement) return control;
    throw new Error(`${label} 입력란이 없다`);
  }

  function messageUnder(label: string): string {
    return text(field(label).closest('div')?.querySelector('p.text-danger') ?? null);
  }

  async function type(label: string, value: string) {
    const target = field(label);
    const prototype =
      target instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    await act(async () => {
      Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(target, value);
      target.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function click(element: Element | undefined) {
    await act(async () => element instanceof HTMLElement && element.click());
  }

  async function mount(programId?: number) {
    root = createRoot(container);
    await act(async () => {
      root.render(
        <SessionProvider>
          {programId === undefined ? (
            <AdminProgramFormPage mode="create" />
          ) : (
            <AdminProgramFormPage mode="edit" programId={programId} />
          )}
        </SessionProvider>,
      );
    });
  }

  async function fillRequiredFields() {
    await type('제목', '  겨울 백패킹  ');
    await type('설명', '눈 쌓인 길을 걸어요.');
    await type('장소 설명', '설악산 소공원');
    await type('신청 시작', '2099-12-01T10:00');
    await type('신청 마감', '2099-12-10T18:00');
    await type('행사 시작', '2099-12-11T10:00');
    await type('행사 종료', '2099-12-12T12:00');
    await type('정원 (명)', '120');
    await type('참가비 (원)', '0');
  }

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    me = ME;
    calls = [];
    detailReply = async () => json(200, programDetail());
    saveReply = async () => json(201, { programId: 31 });
    window.history.replaceState(null, '', '/admin/programs/new');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/me') return json(200, me);
        const method = init?.method ?? 'GET';
        calls.push({ method, url, body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined });
        if (url === '/api/programs/7' && method === 'GET') return detailReply();
        if (url === '/api/admin/programs' && method === 'POST') return saveReply();
        if (url === '/api/admin/programs/7' && method === 'PATCH') return saveReply();
        return json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      }),
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
  });

  describe('등록', () => {
    it('입력란마다 라벨과 쉬운 안내를 보여 준다', async () => {
      await mount();

      for (const label of FIELD_LABELS) expect(field(label)).toBeInstanceOf(HTMLElement);
      expect(field('신청 시작').type).toBe('datetime-local');
      expect(text(container)).toContain('시각은 모두 한국 시간이에요.');
      expect(text(container)).toContain('지도에서 장소를 열면 주소 끝에 있는 숫자예요. 비워 두면 연결하지 않아요.');
      expect(text(container)).not.toContain('정원을 줄일 수 없어요');
      expect(text(container)).toContain('1~1440분이고, 비우면 15분이에요.');
      expect(button('등록하기')).toBeDefined();
      expect(link('목록으로')?.getAttribute('href')).toBe('/admin/programs');
      expect(calls).toEqual([]);
    });

    it('비워 둔 채 등록하면 필드 아래에 문구를 보이고 요청을 보내지 않고 첫 오류로 포커스를 옮긴다', async () => {
      await mount();

      await click(button('등록하기'));

      expect(messageUnder('제목')).toBe('제목을 입력해 주세요.');
      expect(messageUnder('설명')).toBe('설명을 입력해 주세요.');
      expect(messageUnder('장소 설명')).toBe('장소 설명을 입력해 주세요.');
      expect(messageUnder('신청 시작')).toBe('신청 시작 시각을 입력해 주세요.');
      expect(messageUnder('행사 종료')).toBe('행사 종료 시각을 입력해 주세요.');
      expect(messageUnder('정원 (명)')).toBe('정원을 적어 주세요.');
      expect(messageUnder('참가비 (원)')).toContain('참가비를 적어 주세요.');
      expect(messageUnder('결제 기한 (분)')).toBe('');
      expect(field('제목').getAttribute('aria-invalid')).toBe('true');
      expect(document.activeElement).toBe(field('제목'));
      expect(writes()).toEqual([]);
    });

    it('시각 순서가 맞지 않으면 어느 칸을 고쳐야 하는지 알리고 요청을 보내지 않는다', async () => {
      await mount();
      await fillRequiredFields();
      await type('신청 마감', '2099-11-30T10:00');
      await type('행사 종료', '2099-12-11T09:00');

      await click(button('등록하기'));

      expect(messageUnder('신청 마감')).toBe('신청 마감은 신청 시작보다 늦어야 해요.');
      expect(messageUnder('행사 종료')).toBe('행사 종료는 행사 시작보다 늦어야 해요.');
      expect(writes()).toEqual([]);
    });

    it('행사 시작이 지금보다 이르면 등록할 수 없다', async () => {
      await mount();
      await fillRequiredFields();
      await type('신청 시작', '2020-01-01T10:00');
      await type('신청 마감', '2020-01-05T10:00');
      await type('행사 시작', '2020-01-10T10:00');
      await type('행사 종료', '2020-01-11T10:00');

      await click(button('등록하기'));

      expect(messageUnder('행사 시작')).toBe('행사 시작은 지금보다 늦어야 해요.');
      expect(writes()).toEqual([]);
    });

    it('입력을 고치면 그 칸의 오류 문구가 사라진다', async () => {
      await mount();
      await click(button('등록하기'));
      expect(messageUnder('제목')).not.toBe('');

      await type('제목', '새 제목');

      expect(messageUnder('제목')).toBe('');
    });

    it('결제 기한과 지도 장소 번호를 비우면 필드 없이, 한국 시간을 UTC로 바꿔 보내고 목록으로 돌아간다', async () => {
      await mount();
      await fillRequiredFields();

      await click(button('등록하기'));

      expect(writes()).toEqual([
        {
          method: 'POST',
          url: '/api/admin/programs',
          body: {
            title: '겨울 백패킹',
            description: '눈 쌓인 길을 걸어요.',
            locationText: '설악산 소공원',
            applyOpenAt: '2099-12-01T01:00:00Z',
            applyCloseAt: '2099-12-10T09:00:00Z',
            startAt: '2099-12-11T01:00:00Z',
            endAt: '2099-12-12T03:00:00Z',
            capacity: 120,
            fee: 0,
            overnight: false,
          },
        },
      ]);
      expect(location()).toBe('/admin/programs?saved=created');
    });

    it('결제 기한, 지도 장소 번호, 숙박 여부를 채우면 함께 보낸다', async () => {
      await mount();
      await fillRequiredFields();
      await type('지도 장소 번호 (선택)', '12');
      await type('결제 기한 (분)', '30');
      await click(field('숙박 행사예요'));

      await click(button('등록하기'));

      expect(writes()).toHaveLength(1);
      expect(writes()[0]?.body).toMatchObject({ spotId: 12, paymentDeadlineMinutes: 30, overnight: true });
    });

    it('처리 중에 다시 눌러도 요청은 한 번만 나가고 버튼이 잠긴다', async () => {
      let release: () => void = () => undefined;
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      saveReply = async () => {
        await gate;
        return json(201, { programId: 31 });
      };
      await mount();
      await fillRequiredFields();

      const submit = button('등록하기');
      await click(submit);
      await click(submit);

      expect(writes()).toHaveLength(1);
      expect(submit?.disabled).toBe(true);
      expect(field('제목').readOnly).toBe(true);

      await act(async () => release());

      expect(writes()).toHaveLength(1);
    });

    it('서버가 필드 오류(400)를 돌려주면 그 필드 아래에 보이고 입력은 그대로 남는다', async () => {
      saveReply = async () =>
        json(400, {
          code: 'INVALID_INPUT',
          message: 'x',
          traceId: 't',
          fieldErrors: [{ field: 'title', reason: '제목은 100자 이하여야 해요.' }],
        });
      await mount();
      await fillRequiredFields();

      await click(button('등록하기'));

      expect(messageUnder('제목')).toBe('제목은 100자 이하여야 해요.');
      expect(field('설명').value).toBe('눈 쌓인 길을 걸어요.');
      expect(location()).toBe('/admin/programs/new');
      expect(button('등록하기')?.disabled).toBe(false);
    });

    it('지도에 보이지 않는 장소라는 서버 안내는 지도 장소 번호 아래에 보인다', async () => {
      saveReply = async () =>
        json(400, { code: 'INVALID_INPUT', message: 'spotId: 지도에 보이는 장소만 연결할 수 있어요.', traceId: 't' });
      await mount();
      await fillRequiredFields();
      await type('지도 장소 번호 (선택)', '99');

      await click(button('등록하기'));

      expect(messageUnder('지도 장소 번호 (선택)')).toBe('지도에 보이는 장소만 연결할 수 있어요.');
      expect(container.querySelector('[role="alert"]')).toBeNull();
    });

    it('서버 오류(500)면 문의 번호와 함께 오류를 보여 주고 다시 등록할 수 있다', async () => {
      saveReply = async () => json(500, { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-5' });
      await mount();
      await fillRequiredFields();

      await click(button('등록하기'));

      const alert = container.querySelector('[role="alert"]');
      expect(text(alert)).toContain('행사를 등록하지 못했어요');
      expect(text(alert)).toContain('trace-5');
      expect(location()).toBe('/admin/programs/new');
      expect(button('등록하기')?.disabled).toBe(false);
    });

    it('응답을 받지 못하면 등록 여부를 확인하지 못했다고 알리고 행사 목록을 먼저 보라고 안내한다', async () => {
      saveReply = async () => {
        throw new TypeError('Failed to fetch');
      };
      await mount();
      await fillRequiredFields();

      await click(button('등록하기'));

      const alert = container.querySelector('[role="alert"]');
      expect(text(alert)).toContain('처리 결과를 확인하지 못했어요');
      expect(text(alert)).toContain(
        '행사가 등록됐는지 확인하지 못했어요. 다시 누르기 전에 행사 목록에서 먼저 확인해 주세요.',
      );
      expect(text(alert)).not.toContain('행사를 등록하지 못했어요');
      expect(alert?.querySelector('a')?.getAttribute('href')).toBe('/admin/programs');
      expect(location()).toBe('/admin/programs/new');
    });
  });

  describe('수정', () => {
    it('공개 상세로 값을 채우고 시각은 한국 시간으로 보여 준다', async () => {
      await mount(7);

      expect(field('제목').value).toBe('가을 백패킹 데이');
      expect(field('설명').value).toBe('함께 걷고 하룻밤 머무는 행사예요.');
      expect(field('장소 설명').value).toBe('북한산 백운대 입구');
      expect(field('지도 장소 번호 (선택)').value).toBe('12');
      expect(field('신청 시작').value).toBe('2099-11-10T10:00');
      expect(field('신청 마감').value).toBe('2099-11-20T18:00');
      expect(field('행사 시작').value).toBe('2099-11-21T10:00');
      expect(field('행사 종료').value).toBe('2099-11-22T12:00');
      expect(field('정원 (명)').value).toBe('200');
      expect(text(container)).toContain(
        '신청이 시작됐거나 신청이 들어온 행사는 정원을 줄일 수 없어요. 늘리는 건 괜찮아요.',
      );
      expect(field('참가비 (원)').value).toBe('30000');
      expect(field('결제 기한 (분)').value).toBe('15');
      expect(field('숙박 행사예요')).toHaveProperty('checked', true);
      expect(text(container.querySelector('h1'))).toBe('행사 수정');
      expect(calls).toEqual([{ method: 'GET', url: '/api/programs/7', body: undefined }]);
    });

    it('바뀐 필드만 PATCH로 보내고, 지도 장소를 비우면 spotId를 null로 보낸 뒤 목록으로 돌아간다', async () => {
      saveReply = async () => json(200, programDetail());
      await mount(7);

      await type('제목', '새 제목');
      await type('지도 장소 번호 (선택)', '');
      await click(button('저장하기'));

      expect(writes()).toEqual([
        { method: 'PATCH', url: '/api/admin/programs/7', body: { title: '새 제목', spotId: null } },
      ]);
      expect(location()).toBe('/admin/programs?saved=revised');
    });

    it('시각과 정원을 고치면 그 필드만 UTC와 숫자로 보낸다', async () => {
      saveReply = async () => json(200, programDetail());
      await mount(7);

      await type('행사 시작', '2099-11-21T09:00');
      await type('정원 (명)', '250');
      await click(button('저장하기'));

      expect(writes()).toEqual([
        {
          method: 'PATCH',
          url: '/api/admin/programs/7',
          body: { startAt: '2099-11-21T00:00:00Z', capacity: 250 },
        },
      ]);
    });

    it('원래 장소가 없고 계속 비어 있으면 spotId는 보내지 않는다', async () => {
      detailReply = async () => json(200, programDetail({ spotId: null }));
      saveReply = async () => json(200, programDetail());
      await mount(7);

      await type('참가비 (원)', '0');
      await click(button('저장하기'));

      expect(writes()[0]?.body).toEqual({ fee: 0 });
    });

    it('바뀐 게 없으면 요청을 보내지 않고 알려 준다', async () => {
      await mount(7);

      await click(button('저장하기'));

      expect(writes()).toEqual([]);
      expect(text(container.querySelector('[role="status"]'))).toContain('바뀐 내용이 없어요');
      expect(location()).toBe('/admin/programs/new');
    });

    it('수정할 때 결제 기한을 비우면 적어 달라고 알린다', async () => {
      await mount(7);

      await type('결제 기한 (분)', '');
      await click(button('저장하기'));

      expect(messageUnder('결제 기한 (분)')).toBe('결제 기한을 적어 주세요.');
      expect(writes()).toEqual([]);
    });

    it('이미 시작한 행사라도 행사 시작이 지난 값을 그대로 두고 다른 곳을 고칠 수 있다', async () => {
      detailReply = async () =>
        json(
          200,
          programDetail({
            applyOpenAt: '2020-01-01T01:00:00Z',
            applyCloseAt: '2020-01-05T01:00:00Z',
            startAt: '2020-01-10T01:00:00Z',
            endAt: '2020-01-11T01:00:00Z',
            status: 'CLOSED',
          }),
        );
      saveReply = async () => json(200, programDetail());
      await mount(7);

      await type('설명', '내용을 고쳤어요.');
      await click(button('저장하기'));

      expect(writes()).toEqual([
        { method: 'PATCH', url: '/api/admin/programs/7', body: { description: '내용을 고쳤어요.' } },
      ]);
    });

    it('신청이 시작된 뒤 정원을 줄이려다 막히면(409) 정원 칸 아래에 알린다', async () => {
      saveReply = async () => json(409, { code: 'PROGRAM_CAPACITY_DECREASE', message: 'x', traceId: 't' });
      await mount(7);

      await type('정원 (명)', '100');
      await click(button('저장하기'));

      expect(messageUnder('정원 (명)')).toContain('정원을 줄일 수 없어요.');
      expect(location()).toBe('/admin/programs/new');
      expect(document.activeElement).toBe(field('정원 (명)'));
    });

    it('응답을 받지 못하면 저장 여부를 확인하지 못했다고 알리고 행사 목록 링크를 보여 준다', async () => {
      saveReply = async () => {
        throw new TypeError('Failed to fetch');
      };
      await mount(7);

      await type('제목', '새 제목');
      await click(button('저장하기'));

      const alert = container.querySelector('[role="alert"]');
      expect(text(alert)).toContain('처리 결과를 확인하지 못했어요');
      expect(text(alert)).toContain('수정한 내용이 저장됐는지 확인하지 못했어요.');
      expect(text(alert)).not.toContain('행사를 수정하지 못했어요');
      expect(alert?.querySelector('a')?.getAttribute('href')).toBe('/admin/programs');
    });

    it('그사이 행사가 취소돼 저장이 막히면(409) 오류를 보여 준다', async () => {
      saveReply = async () => json(409, { code: 'PROGRAM_INVALID_STATE', message: 'x', traceId: 't' });
      await mount(7);

      await type('제목', '새 제목');
      await click(button('저장하기'));

      const alert = container.querySelector('[role="alert"]');
      expect(text(alert)).toContain('행사를 수정하지 못했어요');
      expect(text(alert)).toContain('상태가 바뀌어 지금은 할 수 없어요.');
    });

    it('취소한 행사를 열면 폼 대신 안내와 목록 링크를 보여 준다', async () => {
      detailReply = async () => json(200, programDetail({ status: 'CANCELED' }));

      await mount(7);

      expect(text(container)).toContain('취소한 행사는 수정할 수 없어요');
      expect(container.querySelector('form')).toBeNull();
      expect(link('행사 목록')?.getAttribute('href')).toBe('/admin/programs');
    });

    it('행사를 찾을 수 없으면(404) 다시 불러오기와 목록 링크를 보여 주고, 다시 불러오면 폼이 뜬다', async () => {
      detailReply = async () => json(404, { code: 'NOT_FOUND', message: 'x', traceId: 't' });
      await mount(7);

      expect(text(container.querySelector('[role="alert"]'))).toContain('행사를 불러오지 못했어요');
      expect(text(container.querySelector('[role="alert"]'))).toContain('찾는 내용이 없거나 삭제됐어요.');
      expect(link('행사 목록')?.getAttribute('href')).toBe('/admin/programs');
      expect(container.querySelector('form')).toBeNull();

      detailReply = async () => json(200, programDetail());
      await click(button('다시 불러오기'));

      expect(field('제목').value).toBe('가을 백패킹 데이');
    });
  });

  it('관리자가 아니면 폼을 보이지 않고 행사도 불러오지 않는다', async () => {
    me = { ...ME, role: 'USER' };

    await mount(7);

    expect(text(container)).toContain('관리자만 볼 수 있어요');
    expect(container.querySelector('form')).toBeNull();
    expect(calls).toEqual([]);
  });
});
