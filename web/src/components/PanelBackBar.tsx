import { buttonClassName } from './buttonClass';
import { Icon } from './icons';

// 지도 패널 안 상세·폼 맨 위의 돌아가기 줄이다. 스크롤해도 위에 붙어 있고, 선 없이 패널 바탕색으로 내용과 나눈다.
export function PanelBackBar({ label, onBack }: { label: string; onBack: () => void }) {
  return (
    <div className="sticky top-0 z-10 flex items-center bg-canvas px-4 pt-1 pb-2 lg:px-5 lg:pt-5">
      <button type="button" onClick={onBack} className={buttonClassName('secondary', 'sm')}>
        <Icon name="chevronLeft" size={18} />
        {label}
      </button>
    </div>
  );
}
