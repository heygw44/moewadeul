import { useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { interpretSpotQuery } from '../../api/aiSearch';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { AiInterpretResponse } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Icon } from '../../components/icons';
import { TextField } from '../../components/TextField';
import { useRetryCountdown } from '../../components/useRetryCountdown';
import { withNext } from '../../app/session/nextPath';
import { useSession } from '../../app/session/session';
import { RegionSearchError, normalizeRegionQuery, searchRegions } from '../../map/regionSearch';
import type { RegionCandidate } from '../../map/regionSearch';
import { formatRemaining } from './formatRemaining';

const MAX_QUERY_LENGTH = 50;
const AI_MIN_LENGTH = 2;
const AI_MAX_LENGTH = 100;

const AI_LENGTH_MESSAGE = `${AI_MIN_LENGTH}자 이상 ${AI_MAX_LENGTH}자 이하로 입력해 주세요.`;
const AI_PLACEHOLDER = '예: 강원도 계곡 옆 박지';
const AI_NOTICE = '입력한 문장은 해석을 위해 OpenAI로 보내져요. 하루 20번까지 쓸 수 있어요.';
const GUIDE_BOX_CLASS = 'rounded-sheet bg-surface px-4 py-3 text-sm text-ink shadow-raise';
const GUIDE_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-accent underline underline-offset-2 hover:text-accent-deep';
const MODE_BUTTON_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-ink underline underline-offset-2 hover:text-accent';
const AI_TOGGLE_ON = 'bg-brand text-white hover:bg-brand-strong';
const AI_TOGGLE_FLOATING = 'bg-fill text-ink hover:bg-fill-strong';
const AI_TOGGLE_PANEL = 'bg-surface text-ink hover:bg-fill-strong';
const PANEL_BOX_CLASS = 'rounded-sheet bg-fill px-4 py-3 text-sm text-ink';

type AiError = { kind: 'limited'; timed: boolean } | { kind: 'unavailable' } | { kind: 'message'; text: string };

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'candidates'; candidates: RegionCandidate[]; aiResult?: AiInterpretResponse }
  | { status: 'empty'; keyword: string }
  | { status: 'error'; message: string };

type MapSearchBarProps = {
  onSearch: () => void;
  onSelect: (candidate: RegionCandidate) => void;
  mapMoving: boolean;
  floating?: boolean;
  inPanel?: boolean;
  ai?: { onResult: (result: AiInterpretResponse, candidate: RegionCandidate | null) => void };
};

export function MapSearchBar({
  onSearch,
  onSelect,
  mapMoving,
  floating = true,
  inPanel = false,
  ai,
}: MapSearchBarProps) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const countdown = useRetryCountdown();
  const [query, setQuery] = useState('');
  const [aiQuery, setAiQuery] = useState('');
  const [aiMode, setAiMode] = useState(false);
  const [aiResolved, setAiResolved] = useState(false);
  const [aiError, setAiError] = useState<AiError | null>(null);
  const [inputError, setInputError] = useState<string | undefined>();
  const [state, setState] = useState<SearchState>({ status: 'idle' });
  const [wasMoving, setWasMoving] = useState(mapMoving);

  if (mapMoving !== wasMoving) {
    setWasMoving(mapMoving);
    if (mapMoving && state.status !== 'idle' && state.status !== 'loading') setState({ status: 'idle' });
  }
  const anonymous = session.status === 'anonymous' || (session.status === 'authenticated' && session.me === null);
  const unverified = session.status === 'authenticated' && session.me?.status === 'UNVERIFIED';
  const aiAvailable = ai !== undefined && session.status !== 'loading';
  const aiActive = aiMode && aiAvailable;
  const aiGuide = aiActive && (anonymous || unverified);
  const loading = state.status === 'loading';
  const waiting = aiActive && countdown.secondsLeft > 0;
  const limitedShown = aiError?.kind === 'limited' && (!aiError.timed || countdown.secondsLeft > 0);

  const requestRef = useRef(0);

  async function handleRegionSubmit() {
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

  async function handleAiSubmit() {
    if (!ai) return;
    const trimmed = aiQuery.trim();
    if (trimmed.length < AI_MIN_LENGTH || trimmed.length > AI_MAX_LENGTH) {
      setInputError(AI_LENGTH_MESSAGE);
      setState({ status: 'idle' });
      return;
    }
    setInputError(undefined);
    setAiError(null);
    onSearch();

    const request = ++requestRef.current;
    setState({ status: 'loading' });
    try {
      const result = await interpretSpotQuery(trimmed);
      if (request !== requestRef.current) return;
      let candidates: RegionCandidate[] = [];
      if (result.region) {
        try {
          candidates = await searchRegions(result.region);
        } catch {
          candidates = [];
        }
        if (request !== requestRef.current) return;
      }
      if (candidates.length > 1) {
        setState({ status: 'candidates', candidates, aiResult: result });
        return;
      }
      setState({ status: 'idle' });
      setAiResolved(true);
      ai.onResult(result, candidates[0] ?? null);
    } catch (error: unknown) {
      if (request !== requestRef.current) return;
      setState({ status: 'idle' });
      if (error instanceof ApiError && error.code === 'AI_SEARCH_LIMITED') {
        const wait = error.retryAfterSeconds;
        countdown.start(wait ?? 0);
        setAiError({ kind: 'limited', timed: wait !== undefined && wait > 0 });
      } else if (error instanceof ApiError && error.code === 'AI_SEARCH_UNAVAILABLE') {
        setAiError({ kind: 'unavailable' });
      } else if (error instanceof ApiError && error.code === 'INVALID_INPUT') {
        setInputError(AI_LENGTH_MESSAGE);
      } else {
        setAiError({ kind: 'message', text: toUserMessage(error) });
      }
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (aiActive) void handleAiSubmit();
    else void handleRegionSubmit();
  }

  function toggleMode() {
    requestRef.current++;
    setAiMode((value) => !value);
    setAiResolved(false);
    setInputError(undefined);
    setAiError(null);
    setState({ status: 'idle' });
  }

  function choose(candidate: RegionCandidate) {
    const pending = state.status === 'candidates' ? state.aiResult : undefined;
    setState({ status: 'idle' });
    if (pending && ai) {
      setAiResolved(true);
      ai.onResult(pending, candidate);
    } else onSelect(candidate);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === 'Escape' && state.status !== 'idle' && state.status !== 'loading') {
      event.stopPropagation();
      setState({ status: 'idle' });
    }
  }

  const boxClass = inPanel ? PANEL_BOX_CLASS : GUIDE_BOX_CLASS;
  const floatingField = floating && !inPanel;

  const modeButton = aiAvailable && (
    <button
      type="button"
      aria-pressed={aiActive}
      aria-label="AI 검색"
      onClick={toggleMode}
      className={`inline-flex h-10 min-w-11 shrink-0 items-center justify-center rounded-control px-3.5 text-sm font-bold whitespace-nowrap transition-colors ${
        aiActive ? AI_TOGGLE_ON : floatingField ? AI_TOGGLE_FLOATING : AI_TOGGLE_PANEL
      }`}
    >
      {inPanel ? 'AI 검색' : 'AI'}
    </button>
  );

  return (
    <form
      role="search"
      noValidate
      onSubmit={handleSubmit}
      onKeyDown={handleKeyDown}
      className={
        inPanel
          ? `relative min-w-0 flex-1 [&_input]:min-h-13 [&_input]:rounded-control [&_input]:bg-fill [&_input]:pl-5 [&_input:not([aria-invalid])]:border-transparent ${aiAvailable ? '[&_input]:pr-36' : ''}`
          : floating
            ? `pointer-events-auto relative min-w-0 flex-1 [&_input]:rounded-control [&_input]:bg-surface [&_input]:pl-5 [&_input]:shadow-raise [&_input:not([aria-invalid])]:border-transparent ${aiAvailable ? '[&_input]:pr-28' : ''}`
            : 'relative min-w-0 flex-1 [&_input]:min-h-13 [&_input]:pr-24'
      }
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          {aiGuide ? (
            <div className={`${boxClass} flex flex-col items-start gap-1`}>
              <p>{anonymous ? 'AI 검색은 로그인한 회원이 쓸 수 있어요.' : '이메일 인증을 마친 뒤에 쓸 수 있어요.'}</p>
              <Link
                to={anonymous ? withNext('/login', pathname + search) : '/verify-email'}
                className={GUIDE_LINK_CLASS}
              >
                {anonymous ? '로그인' : '이메일 인증하기'}
              </Link>
              <button type="button" onClick={toggleMode} className={MODE_BUTTON_CLASS}>
                지역 검색으로 바꾸기
              </button>
            </div>
          ) : (
            <TextField
              label={aiActive ? 'AI 검색' : '지역 검색'}
              hideLabel
              type="text"
              enterKeyHint="search"
              autoComplete="off"
              placeholder={aiActive ? AI_PLACEHOLDER : '지역이나 주소 (예: 안동)'}
              value={aiActive ? aiQuery : query}
              error={inputError}
              onChange={(event) => {
                if (aiActive) {
                  setAiQuery(event.target.value);
                  setAiResolved(false);
                } else setQuery(event.target.value);
              }}
              trailing={
                <div className="flex items-center gap-1 pr-1.5">
                  <button
                    type="submit"
                    aria-label={aiActive ? 'AI 검색' : '지역 검색'}
                    aria-busy={loading || undefined}
                    disabled={loading || waiting}
                    className={
                      floating || inPanel
                        ? 'inline-flex size-11 items-center justify-center text-ink hover:text-accent disabled:text-ink-muted'
                        : 'mr-1.5 inline-flex h-10 items-center justify-center rounded-control bg-brand px-4 font-bold text-white hover:bg-brand-strong disabled:bg-ink-muted'
                    }
                  >
                    {floating || inPanel ? <Icon name="search" size={20} /> : '찾기'}
                  </button>
                  {modeButton}
                </div>
              }
            />
          )}
        </div>
      </div>

      {aiActive && !aiGuide && !aiResolved && (
        <p
          className={
            inPanel
              ? 'mt-2 text-[0.8125rem] text-ink-muted'
              : 'mt-1 rounded-sheet bg-surface px-4 py-2 text-sm text-ink-muted shadow-raise'
          }
        >
          {AI_NOTICE}
        </p>
      )}

      {aiActive && limitedShown && (
        <p role="alert" className={`${boxClass} mt-1`}>
          오늘 AI 검색 20번을 모두 썼어요.{' '}
          {aiError.timed
            ? `${formatRemaining(countdown.secondsLeft)} 뒤에 다시 쓸 수 있어요.`
            : '내일 다시 쓸 수 있어요.'}{' '}
          지역 이름 검색은 지금도 쓸 수 있어요.
        </p>
      )}

      {aiActive && aiError?.kind === 'unavailable' && (
        <div role="alert" className={`${boxClass} mt-1 flex flex-col items-start gap-1`}>
          <p>지금은 AI 검색을 쓸 수 없어요. 지역 이름으로 검색해 주세요.</p>
          <button type="button" onClick={toggleMode} className={MODE_BUTTON_CLASS}>
            지역 검색으로 바꾸기
          </button>
        </div>
      )}

      {aiActive && aiError?.kind === 'message' && (
        <p role="alert" className={`${boxClass} mt-1`}>
          {aiError.text}
        </p>
      )}

      {state.status === 'candidates' && (
        <ul
          aria-label="지역 후보"
          className="absolute inset-x-0 top-full z-20 mt-2 flex flex-col gap-0.5 overflow-hidden rounded-sheet bg-surface p-1.5 shadow-raise"
        >
          {state.candidates.map((candidate, index) => (
            <li key={`${candidate.name}-${index}`}>
              <button
                type="button"
                onClick={() => choose(candidate)}
                className="flex min-h-11 w-full flex-col items-start justify-center rounded-row px-3.5 py-2 text-left hover:bg-fill focus-visible:-outline-offset-2"
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
          className="absolute inset-x-0 top-full z-20 mt-1 rounded-sheet bg-surface px-4 py-3 text-sm text-ink shadow-raise"
        >
          {state.status === 'empty'
            ? `'${state.keyword}' 검색 결과가 없어요. 다른 지역 이름이나 주소로 찾아 주세요.`
            : state.message}
        </p>
      )}
    </form>
  );
}
