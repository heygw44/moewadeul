// 해 뜨고 지는 시각을 브라우저에서 계산한다(NOAA 간이식, 오차 1~2분). 외부 API를 부르지 않는다.
// 랜딩처럼 장소 상세를 열지 않는 곳에서 "오늘 일몰"을 보여 줄 때 쓴다. 장소 상세의 일몰은 한국천문연구원 값을 쓴다.

const RAD = Math.PI / 180;

function dayOfYear(day: Date): number {
  const year = day.getUTCFullYear();
  return Math.floor((Date.UTC(year, day.getUTCMonth(), day.getUTCDate()) - Date.UTC(year, 0, 0)) / 86_400_000);
}

// 그날(UTC 날짜 기준) 해가 뜨거나 지는 순간을 돌려준다. 해가 지지 않는 극지방은 다루지 않는다.
export function sunEventAt(lat: number, lng: number, day: Date, kind: 'rise' | 'set'): Date {
  const g = ((2 * Math.PI) / 365) * (dayOfYear(day) - 1);
  const equationOfTime =
    229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
  const declination =
    0.006918 -
    0.399912 * Math.cos(g) +
    0.070257 * Math.sin(g) -
    0.006758 * Math.cos(2 * g) +
    0.000907 * Math.sin(2 * g) -
    0.002697 * Math.cos(3 * g) +
    0.00148 * Math.sin(3 * g);
  const hourAngle =
    Math.acos(Math.cos(90.833 * RAD) / (Math.cos(lat * RAD) * Math.cos(declination)) - Math.tan(lat * RAD) * Math.tan(declination)) / RAD;
  const minutesUtc = 720 - 4 * (lng + (kind === 'rise' ? hourAngle : -hourAngle)) - equationOfTime;
  return new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate()) + minutesUtc * 60_000);
}

// 한국 시간으로 "17시 48분"처럼 적는다.
export function formatKstHourMinute(at: Date): string {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Seoul', hour: 'numeric', minute: 'numeric', hour12: false }).formatToParts(at);
  const hour = Number(parts.find((part) => part.type === 'hour')?.value ?? 0);
  const minute = Number(parts.find((part) => part.type === 'minute')?.value ?? 0);
  return `${hour}시 ${minute}분`;
}
