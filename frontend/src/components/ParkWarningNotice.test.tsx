import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ParkWarningNotice } from './ParkWarningNotice';

describe('공원 경계 경고', () => {
  it('[F-05][SF-05] 경고 박지에는 공원 이름, 야영 금지 안내, 출처와 기준일을 보여 주고 흔적 남기지 않기 안내는 쓰지 않는다', () => {
    const html = renderToStaticMarkup(
      <ParkWarningNotice
        warning={{
          warned: true,
          areaName: '설악산국립공원',
          source: 'KDPA',
          sourceDate: '2025-01-01',
          notice: '공원 경계는 참고 자료예요.',
          guide: '이곳에서는 야영하지 마세요.',
        }}
      />,
    );

    expect(html).toContain('설악산국립공원');
    expect(html).toContain('이곳에서는 야영하지 마세요.');
    expect(html).toContain('공원 경계는 참고 자료예요.');
    expect(html).toContain('출처');
    expect(html).toContain('한국보호지역통합DB(KDPA)');
    expect(html).toContain('기준일');
    expect(html).toContain('2025-01-01');
    expect(html).not.toContain('흔적');
  });

  it('[SF-05] 서버가 안내 문구를 주지 않아도 야영 금지와 과태료 안내를 보여 준다', () => {
    const html = renderToStaticMarkup(<ParkWarningNotice warning={{ warned: true }} />);

    expect(html).toContain('어기면 과태료 대상이에요');
    expect(html).toContain('이곳에서는 야영하지 말고');
  });

  it('[SF-05] 경고 대상이 아니면 아무것도 그리지 않는다', () => {
    expect(renderToStaticMarkup(<ParkWarningNotice warning={{ warned: false }} />)).toBe('');
  });
});
