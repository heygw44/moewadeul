import { useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { Icon } from '../../components/icons';
import { TextField } from '../../components/TextField';
import { RegionSearchError, normalizeRegionQuery, searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';

const MAX_QUERY_LENGTH = 50;

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'candidates'; candidates: RegionCandidate[] }
  | { status: 'empty'; keyword: string }
  | { status: 'error'; message: string };

type MapSearchBarProps = {
  // 새 검색을 시작할 때 부른다. 앞선 검색이 남긴 표시를 지운다.
  onSearch: () => void;
  // 고른 지역으로 지도를 옮긴다. 옮긴 뒤 영역 조회는 지도 idle 구독이 맡는다.
  onSelect: (candidate: RegionCandidate) => void;
  // 지도가 움직이기 시작하면 떠 있던 후보 목록과 안내 문구를 닫는다.
  mapMoving: boolean;
};

export function MapSearchBar({ onSearch, onSelect, mapMoving }: MapSearchBarProps) {
  const [query, setQuery] = useState('');
  const [inputError, setInputError] = useState<string | undefined>();
  const [state, setState] = useState<SearchState>({ status: 'idle' });
  const [wasMoving, setWasMoving] = useState(mapMoving);

  // 렌더 중에 이전 값과 비교해서 닫으므로, 화면이 한 번 그려진 뒤 다시 그리는 일이 없다. 검색 중인 요청은 그대로 둔다.
  if (mapMoving !== wasMoving) {
    setWasMoving(mapMoving);
    if (mapMoving && state.status !== 'idle' && state.status !== 'loading') setState({ status: 'idle' });
  }
  // 앞선 검색이 늦게 끝나도 마지막 검색 결과만 보여 주려고 요청마다 번호를 붙인다.
  const requestRef = useRef(0);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = query.trim();
    if (normalizeRegionQuery(trimmed) === '') {
      setInputError('찾을 지역 이름이나 주소를 입력해 주세요.');
      setState({ status: 'idle' });
      return;
    }
    if (trimmed.length > MAX_QUERY_LENGTH) {
      setInputError(`${MAX_QUERY_LENGTH}자 이하로 입력해 주세요.`);
      setState({ status: 'idle' });
      return;
    }
    setInputError(undefined);
    onSearch();

    const request = ++requestRef.current;
    setState({ status: 'loading' });
    try {
      const candidates = await searchRegions(trimmed);
      if (request !== requestRef.current) return;
      const [only] = candidates;
      if (only === undefined) {
        setState({ status: 'empty', keyword: normalizeRegionQuery(trimmed) });
      } else if (candidates.length === 1) {
        // 후보가 하나면 고르는 단계 없이 바로 옮긴다.
        setState({ status: 'idle' });
        onSelect(only);
      } else {
        setState({ status: 'candidates', candidates });
      }
    } catch (error: unknown) {
      if (request !== requestRef.current) return;
      const message = error instanceof RegionSearchError ? error.message : new RegionSearchError().message;
      setState({ status: 'error', message });
    }
  }

  function choose(candidate: RegionCandidate) {
    setState({ status: 'idle' });
    onSelect(candidate);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === 'Escape' && state.status !== 'idle' && state.status !== 'loading') {
      event.stopPropagation();
      setState({ status: 'idle' });
    }
  }

  return (
    <form
      role="search"
      noValidate
      onSubmit={handleSubmit}
      onKeyDown={handleKeyDown}
      className="pointer-events-auto relative min-w-0 flex-1"
    >
      <TextField
        label="지역 검색"
        hideLabel
        type="text"
        enterKeyHint="search"
        autoComplete="off"
        placeholder="지역 이름이나 주소 (예: 안동, 원주)"
        value={query}
        error={inputError}
        onChange={(event) => setQuery(event.target.value)}
        trailing={
          <button
            type="submit"
            aria-label="지역 검색"
            aria-busy={state.status === 'loading' || undefined}
            disabled={state.status === 'loading'}
            className="inline-flex size-11 items-center justify-center text-ink hover:text-forest disabled:text-ink-muted"
          >
            <Icon name="search" size={20} />
          </button>
        }
      />

      {state.status === 'candidates' && (
        <ul
          aria-label="지역 후보"
          className="absolute inset-x-0 top-full z-20 mt-1 flex flex-col rounded-control border border-contour bg-card shadow-raise"
        >
          {state.candidates.map((candidate, index) => (
            <li key={`${candidate.name}-${index}`} className="border-b border-contour last:border-b-0">
              <button
                type="button"
                onClick={() => choose(candidate)}
                className="flex min-h-11 w-full flex-col items-start justify-center px-3 py-2 text-left hover:bg-paper-deep"
              >
                <span className="text-base text-ink">{candidate.name}</span>
                {candidate.detail && <span className="text-sm text-ink-muted">{candidate.detail}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}

      {(state.status === 'empty' || state.status === 'error') && (
        <p
          role="status"
          className="absolute inset-x-0 top-full z-20 mt-1 rounded-control border border-contour bg-card px-3 py-2 text-sm text-ink shadow-raise"
        >
          {state.status === 'empty'
            ? `'${state.keyword}' 검색 결과가 없어요. 다른 지역 이름이나 주소로 찾아 주세요.`
            : state.message}
        </p>
      )}
    </form>
  );
}
