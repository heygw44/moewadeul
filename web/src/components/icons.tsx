import { createElement } from 'react';
import type { ReactElement, SVGProps } from 'react';
import { ICON_PATHS } from './iconPaths';
import type { IconName } from './iconPaths';

type IconProps = Omit<SVGProps<SVGSVGElement>, 'children' | 'dangerouslySetInnerHTML'> & {
  name: IconName;
  size?: number;
  title?: string;
};

type Shape = { tag: string; attrs: Record<string, string> };

// 모양 문자열은 지도 마커가 HTML로 쓰려고 문자열로 둔 상수다. 화면에서는 HTML로 넣지 않고,
// 태그와 속성을 읽어 React 요소로 만든다. 모양 문자열은 `<태그 이름="값" …/>` 꼴만 쓴다.
const SHAPE_PATTERN = /<(\w+)((?:\s+[\w-]+="[^"]*")*)\s*\/>/g;
const ATTR_PATTERN = /([\w-]+)="([^"]*)"/g;

const shapeCache = new Map<IconName, Shape[]>();

function shapesOf(name: IconName): Shape[] {
  const cached = shapeCache.get(name);
  if (cached) return cached;
  const shapes = Array.from(ICON_PATHS[name].matchAll(SHAPE_PATTERN), ([, tag, attrText]): Shape => {
    const attrs: Record<string, string> = {};
    for (const [, key, value] of (attrText ?? '').matchAll(ATTR_PATTERN)) {
      if (key !== undefined && value !== undefined) attrs[key] = value;
    }
    return { tag: tag ?? '', attrs };
  });
  shapeCache.set(name, shapes);
  return shapes;
}

// title이 없으면 장식으로 보고 스크린 리더가 건너뛰게 한다. 뜻을 전하는 아이콘에만 title을 넘긴다.
export function Icon({ name, size = 20, title, ...svgProps }: IconProps) {
  const children: ReactElement[] = shapesOf(name).map(({ tag, attrs }, index) =>
    createElement(tag, { ...attrs, key: index }),
  );
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      focusable="false"
      {...(title ? { role: 'img' } : { 'aria-hidden': true })}
      {...svgProps}
    >
      {title && <title>{title}</title>}
      {children}
    </svg>
  );
}
