import { Link } from '../../app/router';
import { AsideSection } from '../../components/HubLayout';

const TEXT_LINK_CLASS = 'font-semibold text-ink underline underline-offset-2';

// 공식 행사 목록의 오른쪽 안내 열이다. 신청 규칙만 둔다. 내 신청으로 가는 길은 머리 띠의 버튼이 맡는다.
export function ProgramAside() {
  return (
    <>
      <AsideSection title="신청 안내">
        <ul className="list-disc pl-5">
          <li>선착순으로 받아요. 한 사람당 한 번만 신청할 수 있어요.</li>
          <li>신청한 뒤 보통 15분 안에 결제해야 확정돼요.</li>
          <li>
            숙박 행사는{' '}
            <Link to="/identity-verification" className={TEXT_LINK_CLASS}>
              본인확인
            </Link>
            을 마친 성인만 신청할 수 있어요.
          </li>
          <li>행사 시작 72시간 전까지 취소하면 환불돼요.</li>
          <li>자리가 다 차면 행사 상세에서 빈자리 알림을 신청할 수 있어요.</li>
        </ul>
      </AsideSection>
    </>
  );
}
