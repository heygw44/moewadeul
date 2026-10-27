// 날짜(예: 출발일 2026-11-07)는 이미 한국 날짜라서 바꾸지 않는다. new Date('2026-11-07')은 UTC 자정으로 읽히므로,
// 브라우저 시간대가 한국보다 늦으면 하루 앞 날짜가 나온다. 그래서 날짜는 문자열을 직접 나눠서 읽는다.

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const KST_DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

// 브라우저마다 한국어 날짜 사이의 구두점이 달라서, 조각을 받아 직접 이어 붙인다.
function kstParts(iso: string): Partial<Record<Intl.DateTimeFormatPartTypes, string>> {
  const parts: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {};
  for (const part of KST_DATE_TIME.formatToParts(new Date(iso))) {
    parts[part.type] = part.value;
  }
  return parts;
}

export function formatKstDateTime(iso: string): string {
  const { month, day, weekday, hour, minute } = kstParts(iso);
  return `${month}월 ${day}일 (${weekday}) ${hour}:${minute}`;
}

export function formatKstTime(iso: string): string {
  const { hour, minute } = kstParts(iso);
  return `${hour}:${minute}`;
}

export function formatLocalDate(date: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) {
    return date;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${month}월 ${day}일 (${weekday})`;
}

const KST_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function formatKstMonthDay(iso: string): string {
  return KST_DATE.format(new Date(iso)).slice(5);
}

export function todayKst(now: Date = new Date()): string {
  return KST_DATE.format(now);
}

export function addDaysToLocalDate(date: string, days: number): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) {
    return date;
  }
  const moved = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + days));
  return moved.toISOString().slice(0, 10);
}

export function departureInstant(startDate: string): Date {
  return new Date(`${startDate}T00:00:00+09:00`);
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export function daysUntilKst(iso: string, now: Date = new Date()): number {
  const target = Date.parse(`${KST_DATE.format(new Date(iso))}T00:00:00+09:00`);
  const today = Date.parse(`${todayKst(now)}T00:00:00+09:00`);
  return Math.round((target - today) / MS_PER_DAY);
}

export function formatRelativeKst(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffMinutes = Math.floor((now.getTime() - then.getTime()) / 60000);
  if (diffMinutes < 1) return '방금';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  const thenDate = KST_DATE.format(then);
  if (thenDate === todayKst(now)) return `${Math.floor(diffMinutes / 60)}시간 전`;
  const { month, day } = kstParts(iso);
  if (thenDate.slice(0, 4) === todayKst(now).slice(0, 4)) return `${month}월 ${day}일`;
  return `${thenDate.slice(0, 4)}년 ${month}월 ${day}일`;
}

export function kstDateBlock(iso: string): { month: string; day: string; weekday: string } {
  const { month, day, weekday } = kstParts(iso);
  return { month: `${month}월`, day: day ?? '', weekday: weekday ?? '' };
}
