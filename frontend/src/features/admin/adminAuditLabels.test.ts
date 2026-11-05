import { describe, expect, it } from 'vitest';
import { AUDIT_ACTION_LABELS, AUDIT_TARGET_TYPES, auditTargetPath, summarizeAuditDetail } from './adminAuditLabels';

const texts = (entries: ReturnType<typeof summarizeAuditDetail>) =>
  entries.map((entry) => `${entry.label}: ${entry.text}`);

describe('감사 기록 detail 요약', () => {
  it('detail이 없으면 빈 목록이다', () => {
    expect(summarizeAuditDetail(null, 'SPOT')).toEqual([]);
    expect(summarizeAuditDetail({}, 'SPOT')).toEqual([]);
  });

  it('조치 14개와 대상 8종에 이름이 모두 있다', () => {
    expect(Object.keys(AUDIT_ACTION_LABELS)).toHaveLength(14);
    expect(AUDIT_TARGET_TYPES).toHaveLength(8);
  });

  it('이전과 이후 상태는 한 줄로 합치고 대상에 맞는 이름으로 보인다', () => {
    expect(texts(summarizeAuditDetail({ fromStatus: 'ACTIVE', toStatus: 'ENDED_EARLY' }, 'SANCTION'))).toEqual([
      '상태: 적용 중 → 일찍 해제',
    ]);
    expect(
      texts(summarizeAuditDetail({ spotType: 'BAKJI', fromStatus: 'ACTIVE', toStatus: 'HIDDEN' }, 'SPOT')),
    ).toEqual(['장소 종류: 박지', '상태: 공개 중 → 숨김']);
    expect(texts(summarizeAuditDetail({ fromStatus: 'RECEIVED', toStatus: 'IN_REVIEW' }, 'MEMBER_REPORT'))).toEqual([
      '상태: 접수 → 검토 중',
    ]);
  });

  it('한쪽 상태만 있으면 따로 보인다', () => {
    expect(texts(summarizeAuditDetail({ toStatus: 'HIDDEN' }, 'MEMORY'))).toEqual(['이후 상태: 숨김']);
  });

  it('신고 조치의 제재 정보를 보인다', () => {
    expect(
      texts(
        summarizeAuditDetail(
          {
            fromStatus: 'IN_REVIEW',
            toStatus: 'ACTIONED',
            hideReview: true,
            sanctionId: 31,
            sanctionType: 'SUSPEND_7D',
            level: 2,
          },
          'MEMBER_REPORT',
        ),
      ),
    ).toEqual([
      '상태: 검토 중 → 조치 완료',
      '신고된 후기 숨김: 함께 숨김',
      '제재 번호: 31',
      '제재 종류: 7일 정지',
      '제재 단계: 2단계',
    ]);
  });

  it('번호에는 천 단위 쉼표를 붙이지 않고 개수에는 붙인다', () => {
    expect(
      texts(
        summarizeAuditDetail(
          { sanctionId: 1234, liftedSanctionId: 12345, memberId: 1234567, reviewedReportCount: 1234 },
          'SANCTION',
        ),
      ),
    ).toEqual([
      '제재 번호: 1234',
      '함께 해제한 제재 번호: 12345',
      '대상 회원 번호: 1234567',
      '함께 검토 처리된 신고: 1,234건',
    ]);
  });

  it('행사의 바뀐 항목 이름은 등록 폼과 같은 말을 쓴다', () => {
    expect(texts(summarizeAuditDetail({ changedFields: ['startAt', 'endAt', 'applyOpenAt'] }, 'PROGRAM'))).toEqual([
      '바뀐 항목: 행사 시작, 행사 종료, 신청 시작',
    ]);
  });

  it('행사 수정과 취소의 값을 보인다', () => {
    expect(
      texts(
        summarizeAuditDetail(
          { changedFields: ['title', 'capacity'], capacityBefore: 10, capacityAfter: 12 },
          'PROGRAM',
        ),
      ),
    ).toEqual(['바뀐 항목: 제목, 정원', '정원: 10명 → 12명']);
    expect(
      texts(
        summarizeAuditDetail(
          { fromStatus: 'SCHEDULED', toStatus: 'CANCELED', canceledApplicationCount: 3, refundedPaymentCount: 1 },
          'PROGRAM',
        ),
      ),
    ).toEqual(['상태: 진행 예정 → 취소', '취소된 신청: 3건', '환불된 결제: 1건']);
  });

  it('데이터 불러오기 작업 이름을 보인다', () => {
    expect(texts(summarizeAuditDetail({ jobType: 'PARK_BOUNDARY' }, 'SYNC_JOB_RUN'))).toEqual(['작업: 공원 경계']);
  });

  it('모르는 키는 키 이름 그대로, 객체와 배열은 한 줄 JSON으로 보인다', () => {
    expect(
      texts(summarizeAuditDetail({ newKey: 'x', nested: { a: 1, b: [2] }, list: [1, 'a'], none: null }, 'SPOT')),
    ).toEqual(['newKey: x', 'nested: {"a":1,"b":[2]}', 'list: [1,"a"]', 'none: 없음']);
  });

  it('아는 키라도 값 모양이 다르면 꾸미지 않고 그대로 보인다', () => {
    expect(
      texts(
        summarizeAuditDetail({ capacity: 'many', hideReview: 'yes', changedFields: 'title', level: null }, 'PROGRAM'),
      ),
    ).toEqual(['정원: many', '신고된 후기 숨김: yes', '바뀐 항목: title', '제재 단계: 없음']);
  });

  it('모르는 코드값은 코드 그대로 보인다', () => {
    expect(
      texts(summarizeAuditDetail({ sanctionType: 'NEW_TYPE', fromStatus: 'X', toStatus: 'Y' }, 'SANCTION')),
    ).toEqual(['제재 종류: NEW_TYPE', '상태: X → Y']);
  });

  it('prototype 이름과 같은 키도 일반 키로 다룬다', () => {
    expect(texts(summarizeAuditDetail({ constructor: 1, toString: 'a' }, 'SPOT'))).toEqual([
      'constructor: 1',
      'toString: a',
    ]);
  });

  it('신고와 행사만 화면 주소가 있다', () => {
    expect(auditTargetPath('MEMBER_REPORT', 5)).toBe('/admin/reports/5');
    expect(auditTargetPath('PROGRAM', 5)).toBe('/admin/programs/5/applications');
    expect(auditTargetPath('SPOT', 5)).toBeNull();
  });
});
