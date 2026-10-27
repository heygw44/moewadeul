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
