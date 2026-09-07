import { useMemo, useState } from 'react';

type DeadlineCalendarProps = {
  dates: string[];
  selectedDate: string | null;
  onSelectDate: (date: string | null) => void;
};

const seoulDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

function toSeoulDate(date: Date): string {
  const parts = seoulDateFormatter.formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((entry) => entry.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export default function DeadlineCalendar({
  dates,
  selectedDate,
  onSelectDate,
}: DeadlineCalendarProps) {
  const today = toSeoulDate(new Date());
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const [year, month] = today.split('-').map(Number);
    return { year, month: month - 1 };
  });

  const eventCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const value of dates) {
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) continue;
      const key = toSeoulDate(date);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return counts;
  }, [dates]);

  const { year, month } = visibleMonth;
  const monthLabel = `${year}년 ${month + 1}월`;
  const firstWeekday = (new Date(Date.UTC(year, month, 1)).getUTCDay() + 6) % 7;
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const cellCount = Math.ceil((firstWeekday + daysInMonth) / 7) * 7;
  const visibleEventCount = Array.from(eventCounts.entries()).reduce(
    (total, [key, count]) =>
      key.startsWith(`${year}-${String(month + 1).padStart(2, '0')}-`) ? total + count : total,
    0,
  );

  function changeMonth(offset: number) {
    setVisibleMonth((current) => {
      const date = new Date(Date.UTC(current.year, current.month + offset, 1));
      return { year: date.getUTCFullYear(), month: date.getUTCMonth() };
    });
  }

  return (
    <section className="calendar-panel panel" aria-label="일정 달력">
      <div className="calendar-heading">
        <h2>나의 캘린더</h2>
        <div className="calendar-nav">
          <button type="button" onClick={() => changeMonth(-1)} aria-label="이전 달">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="m14 6-6 6 6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <span className="calendar-month" aria-live="polite">{monthLabel}</span>
          <button type="button" onClick={() => changeMonth(1)} aria-label="다음 달">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="m10 6 6 6-6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>
      </div>
      <div className="calendar-weekdays" aria-hidden="true">
        {['월', '화', '수', '목', '금', '토', '일'].map((day) => <span key={day}>{day}</span>)}
      </div>
      <div className="calendar-days" role="group" aria-label={`${monthLabel} 날짜 선택`}>
        {Array.from({ length: cellCount }, (_, index) => {
          const day = index - firstWeekday + 1;
          if (day < 1 || day > daysInMonth) {
            return <span className="calendar-day calendar-day-placeholder" key={`blank-${index}`} aria-hidden="true" />;
          }
          const key = dateKey(year, month, day);
          const count = eventCounts.get(key) ?? 0;
          const isToday = key === today;
          const isSelected = key === selectedDate;
          const className = [
            'calendar-day',
            isToday ? 'is-today' : '',
            isSelected ? 'is-selected' : '',
            count > 0 ? 'has-events' : '',
          ].filter(Boolean).join(' ');

          return (
            <button
              type="button"
              key={key}
              className={className}
              aria-label={`${year}년 ${month + 1}월 ${day}일, 일정 ${count}개`}
              aria-pressed={isSelected}
              aria-current={isToday ? 'date' : undefined}
              onClick={() => onSelectDate(isSelected ? null : key)}
            >
              {day}
              {count > 0 && <span className="calendar-dot" aria-hidden="true" />}
            </button>
          );
        })}
      </div>
      <div className="calendar-caption">
        <span>{selectedDate ? `${selectedDate} 선택됨` : `이번 표시 월의 일정 ${visibleEventCount}개`}</span>
        <button type="button" onClick={() => onSelectDate(null)}>전체 일정 보기</button>
      </div>
    </section>
  );
}
