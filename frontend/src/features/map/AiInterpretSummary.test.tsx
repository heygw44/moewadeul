import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { AiInterpretSummary } from './AiInterpretSummary';

describe('AI 해석 요약', () => {
  it('[F-27][AI-08] AI가 문장을 해석했다는 표시를 붙인다', () => {
    const html = renderToStaticMarkup(<AiInterpretSummary summary={{ unparsed: [], empty: false }} />);

    expect(html).toContain('AI가 문장을 해석했어요');
  });

  it('[F-27][AI-05][AI-08] 반영하지 못한 말과 찾지 못한 지역, 뺀 입지를 함께 알린다', () => {
    const html = renderToStaticMarkup(
      <AiInterpretSummary
        summary={{ unparsed: ['다음 주말'], missingRegion: '없는곳', droppedSettings: ['VALLEY'], empty: false }}
      />,
    );

    expect(html).toContain('반영하지 못한 말: 다음 주말');
    expect(html).toContain('없는곳');
    expect(html).toContain('계곡');
  });
});
