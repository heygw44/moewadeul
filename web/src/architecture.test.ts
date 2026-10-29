/// <reference types="node" />
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = import.meta.dirname;

const IMPORT_PATTERN =
  /(?:import|export)\s[^'"]*?from\s*['"]([^'"]+)['"]|import\s*['"]([^'"]+)['"]|import\(\s*['"]([^'"]+)['"]\s*\)/g;
const BOTTOM_LAYERS = ['components', 'lib', 'api', 'map'];

function readSources(dir: string, prefix = ''): Record<string, string> {
  const result: Record<string, string> = {};
  for (const entry of fs.readdirSync(path.join(SRC, dir), { withFileTypes: true })) {
    const relative = `${prefix}${entry.name}`;
    if (entry.isDirectory()) Object.assign(result, readSources(path.join(dir, entry.name), `${relative}/`));
    else if (/\.tsx?$/.test(entry.name) && relative !== 'architecture.test.ts') {
      result[relative] = fs.readFileSync(path.join(SRC, dir, entry.name), 'utf8');
    }
  }
  return result;
}

const sources = readSources('');

function resolveImport(from: string, specifier: string): string | undefined {
  if (!specifier.startsWith('.')) return undefined;
  const segments = from.split('/').slice(0, -1);
  for (const part of specifier.split('/')) {
    if (part === '..') segments.pop();
    else if (part !== '.') segments.push(part);
  }
  const base = segments.join('/');
  return [base, `${base}.ts`, `${base}.tsx`, `${base}/index.ts`, `${base}/index.tsx`].find((c) => c in sources);
}

function unitOf(file: string): string {
  const [top = '', second = '', third] = file.split('/');
  if (top === 'features' && third !== undefined) return `features/${second}`;
  return second === '' ? '(root)' : top;
}

type UnitEdge = { from: string; to: string; imports: string[] };

function buildEdges(): UnitEdge[] {
  const edges = new Map<string, UnitEdge>();
  for (const [file, source] of Object.entries(sources)) {
    for (const match of source.matchAll(IMPORT_PATTERN)) {
      const target = resolveImport(file, match[1] ?? match[2] ?? match[3] ?? '');
      if (!target) continue;
      const from = unitOf(file);
      const to = unitOf(target);
      if (from === to) continue;
      const key = `${from} -> ${to}`;
      const edge = edges.get(key) ?? { from, to, imports: [] };
      edge.imports.push(`${file} -> ${target}`);
      edges.set(key, edge);
    }
  }
  return [...edges.values()];
}

function findCycles(adjacency: Map<string, string[]>): string[] {
  const cycles = new Set<string>();
  const visit = (node: string, stack: string[]) => {
    const at = stack.indexOf(node);
    if (at >= 0) {
      const cycle = stack.slice(at);
      const start = cycle.indexOf([...cycle].sort()[0] ?? '');
      const rotated = [...cycle.slice(start), ...cycle.slice(0, start)];
      cycles.add([...rotated, rotated[0]].join(' -> '));
      return;
    }
    for (const next of adjacency.get(node) ?? []) visit(next, [...stack, node]);
  };
  for (const node of adjacency.keys()) visit(node, []);
  return [...cycles].sort();
}

describe('모듈 의존 방향', () => {
  const edges = buildEdges();
  const isFeature = (unit: string) => unit.startsWith('features/');

  it('features/* 사이에 순환 import가 없다', () => {
    const adjacency = new Map<string, string[]>();
    for (const { from, to } of edges) {
      if (isFeature(from) && isFeature(to)) adjacency.set(from, [...(adjacency.get(from) ?? []), to]);
    }
    expect(findCycles(adjacency)).toEqual([]);
  });

  it(`${BOTTOM_LAYERS.join(', ')}는 features를 import하지 않는다`, () => {
    const violations = edges
      .filter(({ from, to }) => BOTTOM_LAYERS.includes(from) && isFeature(to))
      .flatMap(({ imports }) => imports);
    expect(violations).toEqual([]);
  });
});
