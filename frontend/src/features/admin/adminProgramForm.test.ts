import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import type { ProgramDetail } from '../../api/types';
import {
  emptyProgramForm,
  formValuesOf,
  kstInputToUtcIso,
  savedKindFrom,
  savedListPath,
  serverErrorsToFields,
  toCreateRequest,
  toRevision,
  utcIsoToKstInput,
  validate,
} from './adminProgramForm';
import type { ProgramFormValues } from './adminProgramForm';

const NOW = new Date('2026-11-05T00:00:00Z');

function filled(overrides: Partial<ProgramFormValues> = {}): ProgramFormValues {
  return {
    title: '가을 백패킹 데이',
    description: '함께 걷고 하룻밤 머무는 행사예요.',
    locationText: '북한산 백운대 입구',
    spotId: '',
    applyOpenAt: '2026-11-10T10:00',
    applyCloseAt: '2026-11-20T18:00',
    startAt: '2026-11-21T10:00',
    endAt: '2026-11-22T12:00',
    capacity: '200',
    fee: '30000',
    paymentDeadlineMinutes: '',
    overnight: true,
    ...overrides,
  };
}

function programDetail(overrides: Partial<ProgramDetail> = {}): ProgramDetail {
  return {
    programId: 7,
    title: '가을 백패킹 데이',
    description: '함께 걷고 하룻밤 머무는 행사예요.',
    locationText: '북한산 백운대 입구',
    spotId: 12,
    startAt: '2026-11-21T01:00:00Z',
    endAt: '2026-11-22T03:00:00Z',
    applyOpenAt: '2026-11-10T01:00:00Z',
    applyCloseAt: '2026-11-20T09:00:00Z',
    capacity: 200,
    remainingSeats: 150,
    fee: 30000,
    paymentDeadlineMinutes: 15,
    overnight: true,
    status: 'UPCOMING',
    ...overrides,
  };
}

describe('한국 시간과 UTC 변환', () => {
  it('한국 시간 입력을 9시간 앞선 UTC 시각으로 바꾼다', () => {
    expect(kstInputToUtcIso('2026-11-14T10:00')).toBe('2026-11-14T01:00:00Z');
  });

  it('한국 시간 자정 무렵은 전날 UTC 시각이 된다', () => {
    expect(kstInputToUtcIso('2026-11-14T00:30')).toBe('2026-11-13T15:30:00Z');
    expect(kstInputToUtcIso('2027-01-01T08:59')).toBe('2026-12-31T23:59:00Z');
  });

  it('UTC 시각을 한국 시간 입력값으로 바꾼다', () => {
    expect(utcIsoToKstInput('2026-11-14T01:00:00Z')).toBe('2026-11-14T10:00');
    expect(utcIsoToKstInput('2026-11-13T15:30:00Z')).toBe('2026-11-14T00:30');
    expect(utcIsoToKstInput('2026-12-31T15:00:00.000Z')).toBe('2027-01-01T00:00');
  });

  it('입력값을 바꿨다가 되돌려도 같은 값이 나온다', () => {
    for (const input of ['2026-11-14T10:00', '2026-01-01T00:00', '2026-12-31T23:59', '2028-02-29T09:00']) {
      const iso = kstInputToUtcIso(input);
      expect(iso).not.toBeNull();
      expect(utcIsoToKstInput(iso ?? '')).toBe(input);
    }
    for (const iso of ['2026-11-14T01:00:00Z', '2026-06-30T14:59:00Z']) {
      expect(kstInputToUtcIso(utcIsoToKstInput(iso))).toBe(iso);
    }
  });

  it('형식이 맞지 않거나 없는 날짜는 null이다', () => {
    expect(kstInputToUtcIso('')).toBeNull();
    expect(kstInputToUtcIso('2026-11-14')).toBeNull();
    expect(kstInputToUtcIso('2026-11-14 10:00')).toBeNull();
    expect(kstInputToUtcIso('2026-02-30T10:00')).toBeNull();
    expect(kstInputToUtcIso('2026-13-01T10:00')).toBeNull();
    expect(kstInputToUtcIso('2026-11-14T24:00')).toBeNull();
    expect(utcIsoToKstInput('아무 글자')).toBe('');
  });
});

describe('formValuesOf', () => {
  it('서버 값을 한국 시간 문자열 입력값으로 바꾼다', () => {
    expect(formValuesOf(programDetail())).toEqual({
      title: '가을 백패킹 데이',
      description: '함께 걷고 하룻밤 머무는 행사예요.',
      locationText: '북한산 백운대 입구',
      spotId: '12',
      applyOpenAt: '2026-11-10T10:00',
      applyCloseAt: '2026-11-20T18:00',
      startAt: '2026-11-21T10:00',
      endAt: '2026-11-22T12:00',
      capacity: '200',
      fee: '30000',
      paymentDeadlineMinutes: '15',
      overnight: true,
    });
  });

  it('연결한 장소가 없으면 지도 장소 번호는 빈 문자열이다', () => {
    expect(formValuesOf(programDetail({ spotId: null })).spotId).toBe('');
  });
});

describe('validate', () => {
  it('올바른 값이면 오류가 없다', () => {
    expect(validate(filled(), NOW, 'create')).toEqual({});
    expect(validate(filled({ spotId: '12', paymentDeadlineMinutes: '1440' }), NOW, 'create')).toEqual({});
  });

  it('비어 있는 칸마다 쉬운 문구를 돌려준다', () => {
    const errors = validate(emptyProgramForm(), NOW, 'create');

    expect(errors).toEqual({
      title: '제목을 입력해 주세요.',
      description: '설명을 입력해 주세요.',
      locationText: '장소 설명을 입력해 주세요.',
      applyOpenAt: '신청 시작 시각을 입력해 주세요.',
      applyCloseAt: '신청 마감 시각을 입력해 주세요.',
      startAt: '행사 시작 시각을 입력해 주세요.',
      endAt: '행사 종료 시각을 입력해 주세요.',
      capacity: '정원을 적어 주세요.',
      fee: '참가비를 적어 주세요. 무료면 0을 적어 주세요.',
    });
  });

  it('공백뿐인 글은 비어 있는 것으로 본다', () => {
    const errors = validate(filled({ title: '   ', description: '\n\n', locationText: ' ' }), NOW, 'create');

    expect(errors.title).toBe('제목을 입력해 주세요.');
    expect(errors.description).toBe('설명을 입력해 주세요.');
    expect(errors.locationText).toBe('장소 설명을 입력해 주세요.');
  });

  it('글자 수는 앞뒤 공백을 뺀 길이로 센다', () => {
    expect(validate(filled({ title: ` ${'가'.repeat(100)} ` }), NOW, 'create').title).toBeUndefined();
    expect(validate(filled({ title: '가'.repeat(101) }), NOW, 'create').title).toBe('제목은 100자 이하로 적어 주세요.');
    expect(validate(filled({ description: '가'.repeat(5000) }), NOW, 'create').description).toBeUndefined();
    expect(validate(filled({ description: '가'.repeat(5001) }), NOW, 'create').description).toBe(
      '설명은 5000자 이하로 적어 주세요.',
    );
    expect(validate(filled({ locationText: '가'.repeat(255) }), NOW, 'create').locationText).toBeUndefined();
    expect(validate(filled({ locationText: '가'.repeat(256) }), NOW, 'create').locationText).toBe(
      '장소 설명은 255자 이하로 적어 주세요.',
    );
  });

  it('정원은 1~1000이다', () => {
    const capacityError = (capacity: string) => validate(filled({ capacity }), NOW, 'create').capacity;

    expect(capacityError('1')).toBeUndefined();
    expect(capacityError('1000')).toBeUndefined();
    expect(capacityError('0')).toBe('정원은 1~1000명 사이의 숫자로 적어 주세요.');
    expect(capacityError('1001')).toBe('정원은 1~1000명 사이의 숫자로 적어 주세요.');
    expect(capacityError('-5')).toBe('정원은 1~1000명 사이의 숫자로 적어 주세요.');
    expect(capacityError('열 명')).toBe('정원은 1~1000명 사이의 숫자로 적어 주세요.');
    expect(capacityError('1.5')).toBe('정원은 1~1000명 사이의 숫자로 적어 주세요.');
  });

  it('참가비는 0~1,000,000원이고 0이면 무료다', () => {
    const feeError = (fee: string) => validate(filled({ fee }), NOW, 'create').fee;

    expect(feeError('0')).toBeUndefined();
    expect(feeError('1000000')).toBeUndefined();
    expect(feeError('1000001')).toBe('참가비는 0~1,000,000원 사이의 숫자로 적어 주세요.');
    expect(feeError('-1')).toBe('참가비는 0~1,000,000원 사이의 숫자로 적어 주세요.');
    expect(feeError('3만원')).toBe('참가비는 0~1,000,000원 사이의 숫자로 적어 주세요.');
  });

  it('결제 기한은 1~1440분이고 등록할 때는 비워 둘 수 있다', () => {
    const deadlineError = (value: string, mode: 'create' | 'edit') =>
      validate(filled({ paymentDeadlineMinutes: value }), NOW, mode).paymentDeadlineMinutes;

    expect(deadlineError('', 'create')).toBeUndefined();
    expect(deadlineError('1', 'create')).toBeUndefined();
    expect(deadlineError('1440', 'create')).toBeUndefined();
    expect(deadlineError('0', 'create')).toBe('결제 기한은 1~1440분 사이의 숫자로 적어 주세요.');
    expect(deadlineError('1441', 'create')).toBe('결제 기한은 1~1440분 사이의 숫자로 적어 주세요.');
    expect(deadlineError('', 'edit')).toBe('결제 기한을 적어 주세요.');
  });

  it('지도 장소 번호는 비워 두거나 양의 정수여야 한다', () => {
    const spotError = (spotId: string) => validate(filled({ spotId }), NOW, 'create').spotId;

    expect(spotError('')).toBeUndefined();
    expect(spotError('  ')).toBeUndefined();
    expect(spotError('12')).toBeUndefined();
    expect(spotError('0')).toBe('지도 장소 번호는 숫자로 적어 주세요.');
    expect(spotError('-3')).toBe('지도 장소 번호는 숫자로 적어 주세요.');
    expect(spotError('12번')).toBe('지도 장소 번호는 숫자로 적어 주세요.');
  });

  it('신청 마감은 신청 시작보다 늦어야 한다', () => {
    const same = validate(filled({ applyCloseAt: '2026-11-10T10:00' }), NOW, 'create');
    const earlier = validate(filled({ applyCloseAt: '2026-11-10T09:59' }), NOW, 'create');
    const later = validate(filled({ applyCloseAt: '2026-11-10T10:01' }), NOW, 'create');

    expect(same.applyCloseAt).toBe('신청 마감은 신청 시작보다 늦어야 해요.');
    expect(earlier.applyCloseAt).toBe('신청 마감은 신청 시작보다 늦어야 해요.');
    expect(later.applyCloseAt).toBeUndefined();
  });

  it('신청 마감은 행사 시작과 같을 수 있지만 더 늦을 수는 없다', () => {
    const same = validate(filled({ applyCloseAt: '2026-11-21T10:00' }), NOW, 'create');
    const later = validate(filled({ applyCloseAt: '2026-11-21T10:01' }), NOW, 'create');

    expect(same.applyCloseAt).toBeUndefined();
    expect(later.applyCloseAt).toBe('신청 마감은 행사 시작보다 늦을 수 없어요.');
  });

  it('행사 종료는 행사 시작보다 늦어야 한다', () => {
    const same = validate(filled({ endAt: '2026-11-21T10:00' }), NOW, 'create');
    const earlier = validate(filled({ endAt: '2026-11-21T09:00' }), NOW, 'create');

    expect(same.endAt).toBe('행사 종료는 행사 시작보다 늦어야 해요.');
    expect(earlier.endAt).toBe('행사 종료는 행사 시작보다 늦어야 해요.');
  });

  it('등록할 때 행사 시작은 지금보다 늦어야 하고 수정할 때는 따지지 않는다', () => {
    const past = filled({
      applyOpenAt: '2026-10-01T10:00',
      applyCloseAt: '2026-10-10T10:00',
      startAt: '2026-10-11T10:00',
      endAt: '2026-10-12T10:00',
    });
    const exactlyNow = filled({
      startAt: '2026-11-05T09:00',
      applyOpenAt: '2026-11-01T09:00',
      applyCloseAt: '2026-11-04T09:00',
    });

    expect(validate(past, NOW, 'create').startAt).toBe('행사 시작은 지금보다 늦어야 해요.');
    expect(validate(exactlyNow, NOW, 'create').startAt).toBe('행사 시작은 지금보다 늦어야 해요.');
    expect(validate({ ...past, paymentDeadlineMinutes: '15' }, NOW, 'edit')).toEqual({});
  });

  it('시각이 비어 있으면 순서는 따지지 않고 비어 있다는 문구만 보인다', () => {
    const errors = validate(filled({ applyOpenAt: '', endAt: '' }), NOW, 'create');

    expect(errors.applyOpenAt).toBe('신청 시작 시각을 입력해 주세요.');
    expect(errors.endAt).toBe('행사 종료 시각을 입력해 주세요.');
    expect(errors.applyCloseAt).toBeUndefined();
    expect(errors.startAt).toBeUndefined();
  });
});

describe('toCreateRequest', () => {
  it('한국 시간 입력을 UTC 시각으로, 숫자 칸을 숫자로 바꾼다', () => {
    expect(toCreateRequest(filled({ spotId: '12', paymentDeadlineMinutes: '30' }))).toEqual({
      title: '가을 백패킹 데이',
      description: '함께 걷고 하룻밤 머무는 행사예요.',
      locationText: '북한산 백운대 입구',
      spotId: 12,
      applyOpenAt: '2026-11-10T01:00:00Z',
      applyCloseAt: '2026-11-20T09:00:00Z',
      startAt: '2026-11-21T01:00:00Z',
      endAt: '2026-11-22T03:00:00Z',
      capacity: 200,
      fee: 30000,
      paymentDeadlineMinutes: 30,
      overnight: true,
    });
  });

  it('결제 기한과 지도 장소 번호를 비우면 필드를 보내지 않는다', () => {
    const request = toCreateRequest(filled({ spotId: ' ', paymentDeadlineMinutes: '' }));

    expect(Object.keys(request)).not.toContain('paymentDeadlineMinutes');
    expect(Object.keys(request)).not.toContain('spotId');
  });

  it('글의 앞뒤 공백을 뺀다', () => {
    const request = toCreateRequest(filled({ title: '  제목  ', description: '\n설명\n', locationText: ' 장소 ' }));

    expect(request.title).toBe('제목');
    expect(request.description).toBe('설명');
    expect(request.locationText).toBe('장소');
  });

  it('참가비 0은 그대로 0으로 보낸다', () => {
    expect(toCreateRequest(filled({ fee: '0' })).fee).toBe(0);
  });

  it('검증을 통과하지 못한 시각으로는 요청을 만들 수 없다', () => {
    expect(() => toCreateRequest(filled({ startAt: '' }))).toThrow();
  });
});

describe('toRevision', () => {
  const original = formValuesOf(programDetail());

  it('바뀐 필드만 담는다', () => {
    expect(toRevision(original, { ...original, title: '새 제목', capacity: '250' })).toEqual({
      title: '새 제목',
      capacity: 250,
    });
  });

  it('바뀐 게 없으면 빈 객체다', () => {
    expect(toRevision(original, { ...original })).toEqual({});
  });

  it('시각은 한국 시간 입력값이 바뀐 것만 UTC로 보낸다', () => {
    expect(toRevision(original, { ...original, startAt: '2026-11-21T09:00', endAt: '2026-11-22T13:00' })).toEqual({
      startAt: '2026-11-21T00:00:00Z',
      endAt: '2026-11-22T04:00:00Z',
    });
  });

  it('서버 시각에 초가 있어도 건드리지 않은 시각은 보내지 않는다', () => {
    const withSeconds = formValuesOf(programDetail({ startAt: '2026-11-21T01:00:30Z' }));

    expect(toRevision(withSeconds, { ...withSeconds, title: '새 제목' })).toEqual({ title: '새 제목' });
  });

  it('지도 장소를 비우면 spotId를 null로 보내 연결을 끊는다', () => {
    expect(toRevision(original, { ...original, spotId: '' })).toEqual({ spotId: null });
    expect(toRevision(original, { ...original, spotId: '   ' })).toEqual({ spotId: null });
  });

  it('지도 장소 번호를 바꾸면 새 번호를 보낸다', () => {
    expect(toRevision(original, { ...original, spotId: '30' })).toEqual({ spotId: 30 });
  });

  it('원래 연결이 없고 계속 비어 있으면 spotId 필드를 보내지 않는다', () => {
    const unlinked = formValuesOf(programDetail({ spotId: null }));

    expect(toRevision(unlinked, { ...unlinked, title: '새 제목' })).toEqual({ title: '새 제목' });
  });

  it('원래 연결이 없던 행사에 장소를 연결하면 번호를 보낸다', () => {
    const unlinked = formValuesOf(programDetail({ spotId: null }));

    expect(toRevision(unlinked, { ...unlinked, spotId: '12' })).toEqual({ spotId: 12 });
  });

  it('글은 앞뒤 공백을 뺀 값이 같으면 바뀐 것으로 보지 않는다', () => {
    expect(toRevision(original, { ...original, title: `  ${original.title} ` })).toEqual({});
  });

  it('숫자는 값이 같으면 바뀐 것으로 보지 않는다', () => {
    expect(toRevision(original, { ...original, capacity: '0200', fee: ' 30000 ' })).toEqual({});
  });

  it('숙박 여부와 참가비를 0으로 바꾼 것도 담는다', () => {
    expect(toRevision(original, { ...original, overnight: false, fee: '0' })).toEqual({ overnight: false, fee: 0 });
  });

  it('결제 기한은 바뀐 값만 보낸다', () => {
    expect(toRevision(original, { ...original, paymentDeadlineMinutes: '30' })).toEqual({
      paymentDeadlineMinutes: 30,
    });
  });
});

describe('serverErrorsToFields', () => {
  const body = (extra: object) => ({ code: 'INVALID_INPUT', message: 'x', traceId: 't', ...extra });

  it('fieldErrors를 입력란에 하나씩 짝지어 준다', () => {
    const error = new ApiError(
      400,
      'INVALID_INPUT',
      body({
        fieldErrors: [
          { field: 'title', reason: '제목은 100자 이하여야 해요.' },
          { field: 'capacity', reason: '정원은 1 이상이어야 해요.' },
        ],
      }),
    );

    expect(serverErrorsToFields(error)).toEqual({
      fields: { title: '제목은 100자 이하여야 해요.', capacity: '정원은 1 이상이어야 해요.' },
      rest: [],
    });
  });

  it('폼에 없는 필드의 오류는 따로 모은다', () => {
    const error = new ApiError(
      400,
      'INVALID_INPUT',
      body({ fieldErrors: [{ field: 'anyFieldPresent', reason: '수정할 항목을 하나 이상 입력해 주세요.' }] }),
    );

    expect(serverErrorsToFields(error)).toEqual({ fields: {}, rest: ['수정할 항목을 하나 이상 입력해 주세요.'] });
  });

  it('"필드: 문구" 모양의 메시지는 그 필드에 짝지어 준다', () => {
    const error = new ApiError(
      400,
      'INVALID_INPUT',
      body({ message: 'spotId: 지도에 보이는 장소만 연결할 수 있어요.' }),
    );

    expect(serverErrorsToFields(error)).toEqual({
      fields: { spotId: '지도에 보이는 장소만 연결할 수 있어요.' },
      rest: [],
    });
  });

  it('필드를 가리키지 않는 오류는 그대로 모은다', () => {
    const error = new ApiError(
      400,
      'INVALID_INPUT',
      body({ message: '신청 마감 시각은 신청 시작 시각보다 늦어야 합니다.' }),
    );

    expect(serverErrorsToFields(error)).toEqual({
      fields: {},
      rest: ['신청 마감 시각은 신청 시작 시각보다 늦어야 합니다.'],
    });
  });

  it('정원을 줄이려다 막힌 409는 정원 칸에 보여 준다', () => {
    const error = new ApiError(409, 'PROGRAM_CAPACITY_DECREASE', {
      code: 'PROGRAM_CAPACITY_DECREASE',
      message: 'x',
      traceId: 't',
    });

    const { fields, rest } = serverErrorsToFields(error);

    expect(fields.capacity).toBe(error.message);
    expect(rest).toEqual([]);
  });
});

describe('저장 뒤 목록 안내', () => {
  it('저장 종류를 쿼리로 실어 보내고 다시 읽는다', () => {
    expect(savedListPath('created')).toBe('/admin/programs?saved=created');
    expect(savedKindFrom('?saved=created')).toBe('created');
    expect(savedKindFrom('?saved=revised')).toBe('revised');
  });

  it('알 수 없는 값이나 쿼리가 없으면 null이다', () => {
    expect(savedKindFrom('')).toBeNull();
    expect(savedKindFrom('?saved=deleted')).toBeNull();
    expect(savedKindFrom('?other=created')).toBeNull();
  });
});
