import { useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { interpretSpotQuery } from '../../api/aiSearch';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { AiInterpretResponse } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Icon } from '../../components/icons';
import { TextField } from '../../components/TextField';
import { useRetryCountdown } from '../member/useRetryCountdown';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
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
// 검색 칸 옆의 AI 검색 버튼이다. 켜면 숲색 판으로 채워 지금 문장으로 찾는 중임을 알린다.
// AI 검색 버튼은 검색 칸 안 오른쪽의 작은 알약이다. 켜면 숲색, 끄면 검색 칸 바탕과 한 단계 다른 면이다.
const AI_TOGGLE_ON = 'bg-brand text-white hover:bg-brand-strong';
const AI_TOGGLE_FLOATING = 'bg-fill text-ink hover:bg-fill-strong';
const AI_TOGGLE_PANEL = 'bg-surface text-ink hover:bg-fill-strong';
const PANEL_BOX_CLASS = 'rounded-sheet bg-fill px-4 py-3 text-sm text-ink';

// AI 검색이 실패했을 때 입력란 아래에 띄울 안내의 종류다. 하루 횟수를 다 썼을 때는 남은 시간을 알 때만 시간을 적는다.
type AiError = { kind: 'limited'; timed: boolean } | { kind: 'unavailable' } | { kind: 'message'; text: string };

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  // aiResult가 있으면 AI가 읽은 조건을 쥐고 있다가, 후보를 고르면 그 후보와 함께 넘긴다.
  | { status: 'candidates'; candidates: RegionCandidate[]; aiResult?: AiInterpretResponse }
  | { status: 'empty'; keyword: string }
  | { status: 'error'; message: string };

type MapSearchBarProps = {
  // 새 검색을 시작할 때 부른다. 앞선 검색이 남긴 표시를 지운다.
  onSearch: () => void;
  // 고른 지역으로 지도를 옮긴다. 옮긴 뒤 영역 조회는 지도 idle 구독이 맡는다.
  onSelect: (candidate: RegionCandidate) => void;
  // 지도가 움직이기 시작하면 떠 있던 후보 목록과 안내 문구를 닫는다.
  mapMoving: boolean;
  // 지도 위에 띄울 때 켠다. 랜딩처럼 지도 밖 흰 바탕에 둘 때는 끈다.
  floating?: boolean;
  // 데스크톱 지도 화면의 왼쪽 패널 안에 둘 때 켠다. 지도 위가 아니라서 그림자 없이 테두리로 입력란을 알린다.
  inPanel?: boolean;
  // 넘기면 입력란에서 문장으로 찾는 AI 모드로 바꿀 수 있다. 읽어 낸 조건과, 그 조건의 지역으로 정한 후보(없으면 null)를 넘겨 준다.
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
  // 문장은 해석에 쓰고 버리는 값이라 화면 상태에만 둔다. 저장소나 콘솔에는 남기지 않는다.
  const [aiQuery, setAiQuery] = useState('');
  const [aiMode, setAiMode] = useState(false);
  // 결과를 받은 뒤에는 OpenAI 안내를 접어 지도를 덜 가린다. 문장을 고치거나 모드를 바꾸면 다시 보인다.
  const [aiResolved, setAiResolved] = useState(false);
  const [aiError, setAiError] = useState<AiError | null>(null);
  const [inputError, setInputError] = useState<string | undefined>();
  const [state, setState] = useState<SearchState>({ status: 'idle' });
  const [wasMoving, setWasMoving] = useState(mapMoving);

  // 렌더 중에 이전 값과 비교해서 닫으므로, 화면이 한 번 그려진 뒤 다시 그리는 일이 없다. 검색 중인 요청은 그대로 둔다.
  if (mapMoving !== wasMoving) {
    setWasMoving(mapMoving);
    if (mapMoving && state.status !== 'idle' && state.status !== 'loading') setState({ status: 'idle' });
  }
  const anonymous = session.status === 'anonymous' || (session.status === 'authenticated' && session.me === null);
  const unverified = session.status === 'authenticated' && session.me?.status === 'UNVERIFIED';
  // 세션을 아직 모를 때는 모드 토글을 그리지 않는다. 로그인 여부에 따라 보여 줄 내용이 달라서다.
  const aiAvailable = ai !== undefined && session.status !== 'loading';
  // 이 화면이 AI를 허용하지 않으면(ai를 안 넘기면) 모드가 켜져 있어도 지역 검색으로 그린다.
  const aiActive = aiMode && aiAvailable;
  const aiGuide = aiActive && (anonymous || unverified);
  const loading = state.status === 'loading';
  const waiting = aiActive && countdown.secondsLeft > 0;
  const limitedShown = aiError?.kind === 'limited' && (!aiError.timed || countdown.secondsLeft > 0);

  // 앞선 검색이 늦게 끝나도 마지막 검색 결과만 보여 주려고 요청마다 번호를 붙인다.
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

  // 문장을 조건으로 바꾼 뒤, 조건에 지역 이름이 있으면 지역 검색으로 좌표 후보를 찾는다.
  // 지역을 찾지 못하거나 지역 검색이 실패해도 읽어 낸 조건은 버리지 않고, 후보 없이 넘긴다.
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

  // 모드를 바꾸면 하던 검색과 안내를 모두 걷는다. 진행 중인 요청의 응답은 번호를 올려서 버린다.
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

  // 켜고 끄는 버튼이라 이름은 그대로 두고 눌림 상태(aria-pressed)로 지금 모드를 알린다.
  // 검색 칸 안 오른쪽에 두고, 패널 안에서는 "AI 검색"으로, 좁은 지도 위에서는 "AI"로 줄여 적는다.
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

  // 검색 칸은 알약 모양이다. 패널 안에서는 세이지 면, 지도 위에서는 지도에 묻히지 않게 흰 면과 그림자로 띄운다.
  // 오류일 때만 위험색 테두리를 보인다. 칸 안 오른쪽에 찾기 아이콘과 AI 검색 버튼이 들어가서 오른쪽 여백을 넓힌다.
  // 지도 밖(랜딩)에서는 첫 화면의 주 입력이라 52px로 키우고, 아이콘 대신 '찾기' 글자 버튼을 둔다.
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
          className="absolute inset-x-0 top-full z-20 mt-1 flex flex-col overflow-hidden rounded-sheet bg-surface py-1 shadow-raise"
        >
          {state.candidates.map((candidate, index) => (
            <li key={`${candidate.name}-${index}`} className="border-b border-line last:border-b-0">
              <button
                type="button"
                onClick={() => choose(candidate)}
                className="flex min-h-11 w-full flex-col items-start justify-center px-3 py-2 text-left hover:bg-fill focus-visible:-outline-offset-2"
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
