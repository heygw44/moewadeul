import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it } from 'vitest';
import { Icon } from './icons';
import { ICON_PATHS } from './iconPaths';
import type { IconName } from './iconPaths';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function render(element: React.ReactElement): HTMLDivElement {
  const container = document.createElement('div');
  act(() => createRoot(container).render(element));
  return container;
}

function describeShape(element: Element): string {
  const attrs = [...element.attributes]
    .map((attr) => `${attr.name}=${attr.value}`)
    .sort()
    .join(' ');
  return `${element.tagName.toLowerCase()} ${attrs}`;
}

describe('Icon', () => {
  it.each(Object.keys(ICON_PATHS) as IconName[])('%s는 모양 문자열이 HTML로 그려질 때와 같은 요소를 그린다', (name) => {
    const expected = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    expected.innerHTML = ICON_PATHS[name];

    const container = render(<Icon name={name} />);

    const actual = [...(container.querySelector('svg')?.children ?? [])].map(describeShape);
    expect(actual).toEqual([...expected.children].map(describeShape));
    expect(actual.length).toBeGreaterThan(0);
  });

  it('title은 글자로만 들어가고 태그로 읽히지 않는다', () => {
    const container = render(<Icon name="info" title={'<img src=x onerror=alert(1)>'} />);

    const svg = container.querySelector('svg');
    expect(svg?.querySelector('img')).toBeNull();
    expect(svg?.querySelector('title')?.textContent).toBe('<img src=x onerror=alert(1)>');
    expect(svg?.getAttribute('role')).toBe('img');
  });

  it('title이 없으면 장식으로 보고 스크린 리더가 건너뛰게 한다', () => {
    const container = render(<Icon name="info" />);

    expect(container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(container.querySelector('title')).toBeNull();
  });
});
