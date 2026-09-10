// 로컬 팀 캘린더 어댑터. 팀원 선택은 인증이 아니며 외부 알림은 발송하지 않습니다.
const TEAM_STORE = 'daylight_team_calendar_v1';
const MEMBER_IDS = ['member-1', 'member-2', 'member-3', 'member-4'];
const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const pad = value => String(value).padStart(2, '0');
const dateKey = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
function parseDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day, 12);
  return dateKey(date) === value ? date : null;
}
function freshTeamState() {
  return { members: MEMBER_IDS.map((id, i) => ({id, name: `팀원 ${i + 1}`, role: '팀 멤버'})), events: [], notifications: [] };
}
function readTeamState() {
  const raw = localStorage.getItem(TEAM_STORE);
  if (!raw) return freshTeamState();
  const data = JSON.parse(raw);
  if (!Array.isArray(data.members) || data.members.length !== 4 ||
      !MEMBER_IDS.every(id => data.members.some(m => m.id === id && typeof m.name === 'string' && typeof m.role === 'string')) ||
      !Array.isArray(data.events) || !Array.isArray(data.notifications)) throw new Error('팀 데이터 형식 확인 필요');
  if (!data.events.every(e => typeof e.id === 'string' && typeof e.title === 'string' && parseDate(e.date) &&
      Number.isInteger(e.start) && e.start >= 0 && Number.isInteger(e.duration) && e.duration > 0 && e.start + e.duration <= 1440 &&
      Array.isArray(e.recipients) && e.recipients.every(id => MEMBER_IDS.includes(id)))) throw new Error('일정 데이터 형식 확인 필요');
  if (!data.notifications.every(n => typeof n.id === 'string' && typeof n.eventId === 'string' && MEMBER_IDS.includes(n.memberId) && typeof n.read === 'boolean')) throw new Error('알림 데이터 형식 확인 필요');
  return data;
}
let teamState;
try { teamState = readTeamState(); } catch { teamState = freshTeamState(); notify('로컬 데이터를 읽지 못했습니다. 기존 저장 내용을 덮어쓰지 않습니다.'); }
let currentMember = MEMBER_IDS[0];
try { const id = localStorage.getItem('daylight_current_member'); if (MEMBER_IDS.includes(id)) currentMember = id; } catch { /* 현재 화면에서 선택 가능 */ }
let selectedDate = parseDate(dateKey(new Date()));
let calendarView = mobileMenu.matches ? 'day' : 'week';
try {
  const saved = localStorage.getItem('daylight_calendar_view');
  if (['day', 'week', 'month'].includes(saved)) calendarView = saved;
} catch { /* View preference is optional, never calendar data. */ }
const yearSelect = document.querySelector('#calendar-year');
const monthSelect = document.querySelector('#calendar-month');
const daySelect = document.querySelector('#calendar-day');
const memberSelect = document.querySelector('#member-select');
const teamDialog = document.querySelector('#team-dialog');
const notificationDialog = document.querySelector('#notifications-dialog');

// 항상 최신 저장본에 변경을 반영합니다. 저장 실패를 성공으로 표시하지 않습니다.
function updateTeamState(change) {
  try {
    const next = readTeamState();
    change(next);
    localStorage.setItem(TEAM_STORE, JSON.stringify(next));
    teamState = next;
    return true;
  } catch {
    notify('저장하지 못했습니다. 브라우저 저장 공간·설정을 확인해 주세요.');
    return false;
  }
}
function option(value, label) { return new Option(label, String(value)); }
function syncDateSelectors() {
  const year = selectedDate.getFullYear();
  const currentYear = new Date().getFullYear();
  yearSelect.replaceChildren();
  for (let y = Math.min(currentYear - 10, year); y <= Math.max(currentYear + 10, year); y++) yearSelect.add(option(y, `${y}년`));
  yearSelect.value = String(year);
  monthSelect.replaceChildren();
  for (let m = 1; m <= 12; m++) monthSelect.add(option(m, `${m}월`));
  monthSelect.value = String(selectedDate.getMonth() + 1);
  daySelect.replaceChildren();
  const last = new Date(year, selectedDate.getMonth() + 1, 0).getDate();
  for (let d = 1; d <= last; d++) daySelect.add(option(d, `${d}일`));
  daySelect.value = String(selectedDate.getDate());
}
function selectDate(date) {
  selectedDate = date;
  detail.close();
  syncDateSelectors();
  renderTeamCalendar();
}
for (const select of [yearSelect, monthSelect, daySelect]) select.addEventListener('change', () => {
  const y = Number(yearSelect.value), m = Number(monthSelect.value);
  const d = Math.min(Number(daySelect.value), new Date(y, m, 0).getDate());
  selectDate(new Date(y, m - 1, d, 12));
});
document.querySelector('#today-button').addEventListener('click', () => selectDate(parseDate(dateKey(new Date()))));
for (const [id, direction] of [['previous-week', -1], ['next-week', 1]]) document.querySelector(`#${id}`).addEventListener('click', () => {
  const date = new Date(selectedDate);
  if (calendarView === 'month') {
    const day = date.getDate(); date.setDate(1); date.setMonth(date.getMonth()+direction);
    date.setDate(Math.min(day, new Date(date.getFullYear(),date.getMonth()+1,0).getDate()));
  } else date.setDate(date.getDate()+direction*(calendarView === 'week' ? 7 : 1));
  selectDate(date); document.querySelector('.week-scroll').scrollTop = 0;
});

for (const button of document.querySelectorAll('[data-calendar-view]')) button.addEventListener('click', () => {
  calendarView = button.dataset.calendarView;
  try { localStorage.setItem('daylight_calendar_view',calendarView); } catch { /* optional preference */ }
  renderTeamCalendar();
  document.querySelector('.week-scroll').scrollTop = calendarView !== 'month' ? 8*hourHeight() : 0;
});
mobileMenu.addEventListener('change', () => { renderTeamCalendar(); document.querySelector('.week-scroll').scrollTop = 0; });
function memberName(id) { return teamState.members.find(m => m.id === id)?.name || '팀원'; }
function renderTeamProfile() {
  memberSelect.replaceChildren(...teamState.members.map(m => option(m.id, m.name)));
  memberSelect.value = currentMember;
  const member = teamState.members.find(m => m.id === currentMember);
  document.querySelector('#profile-role').textContent = member.role || '팀 멤버';
  document.querySelector('#profile-avatar').textContent = [...member.name][0];
  renderNotificationCount();
}
memberSelect.addEventListener('change', () => {
  currentMember = memberSelect.value;
  try { localStorage.setItem('daylight_current_member', currentMember); } catch { /* 선택은 현재 화면에 유지 */ }
  renderTeamProfile();
});
document.querySelector('#edit-team').addEventListener('click', () => {
  if (mobileMenu.matches) setSidebarOpen(false);
  const fields = document.querySelector('#team-fields');
  fields.replaceChildren();
  teamState.members.forEach((member, i) => {
    const row = document.createElement('fieldset');
    const legend = document.createElement('legend'); legend.textContent = `팀원 ${i + 1}`; row.append(legend);
    for (const [field, title, max] of [['name', '이름', 30], ['role', '역할', 40]]) {
      const label = document.createElement('label'); label.textContent = title;
      const input = document.createElement('input'); input.name = `${member.id}-${field}`; input.value = member[field]; input.maxLength = max; input.required = field === 'name';
      label.append(input); row.append(label);
    }
    fields.append(row);
  });
  teamDialog.showModal();
});
document.querySelector('#close-team').addEventListener('click', () => teamDialog.close());
document.querySelector('#team-form').addEventListener('submit', event => {
  event.preventDefault();
  const data = new FormData(event.currentTarget);
  const members = MEMBER_IDS.map(id => ({ id, name: String(data.get(`${id}-name`)).trim(), role: String(data.get(`${id}-role`)).trim() }));
  if (members.some(m => !m.name)) { notify('팀원 이름을 입력해 주세요.'); return; }
  if (!updateTeamState(next => { next.members = members; })) return;
  teamDialog.close(); renderTeamProfile(); renderTeamCalendar(); notify('팀원 정보를 이 브라우저에 저장했습니다.');
});
function prepareTeamEvent() {
  const form = document.querySelector('#create-form');
  form.elements.date.value = dateKey(selectedDate);
  const container = document.querySelector('#recipient-options'); container.replaceChildren();
  teamState.members.forEach(member => {
    const label = document.createElement('label');
    const input = document.createElement('input'); input.type = 'checkbox'; input.name = 'recipients'; input.value = member.id; input.checked = true;
    label.append(input, document.createTextNode(member.name)); container.append(label);
  });
}
function minutesText(minutes) { return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`; }
function submitTeamEvent(event) {
  event.preventDefault();
  const form = event.currentTarget, data = new FormData(form);
  const title = String(data.get('title')).trim();
  const date = parseDate(String(data.get('date')));
  const time = String(data.get('time'));
  if (!title || !date || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) { notify('제목·날짜·시간을 확인해 주세요.'); return; }
  const [h, m] = time.split(':').map(Number), start = h * 60 + m;
  const duration = Number(data.get('duration'));
  if (![30, 60, 90, 120].includes(duration) || start + duration > 1440) { notify('일정 종료 시각은 같은 날 자정 이내로 지정해 주세요.'); return; }
  const recipients = [...new Set(data.getAll('recipients').filter(id => MEMBER_IDS.includes(id)))];
  const entry = { id: crypto.randomUUID(), title, date: dateKey(date), start, duration, category: String(data.get('category')), creator: currentMember, recipients };
  if (!updateTeamState(next => {
    next.events.push(entry);
    recipients.forEach(memberId => next.notifications.push({ id: crypto.randomUUID(), eventId: entry.id, memberId, read: false, createdAt: new Date().toISOString() }));
  })) return;
  form.reset(); create.close(); selectDate(date); renderNotificationCount();
  document.querySelector('.week-scroll').scrollTop = calendarView !== 'month' ? Math.max(0, (start / 60 - 1) * hourHeight()) : 0;
  notify(`일정 저장 · ${recipients.length}명 대상 등록 알림 생성 (이 브라우저 미리보기)`);
}
function hourHeight() { return parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--hour')); }
function renderTeamCalendar() {
  const overview=calendarView==='month';
  document.querySelector('.week-canvas').classList.toggle('daily-canvas',calendarView==='day');
  document.querySelector('.week-scroll').dataset.view=calendarView;
  document.querySelector('.week-canvas').hidden=overview;
  document.querySelector('#calendar-overview').hidden=!overview;
  const labels={day:'일',week:'주',month:'달'};
  document.querySelector('#previous-week').setAttribute('aria-label','이전 '+labels[calendarView]);
  document.querySelector('#next-week').setAttribute('aria-label','다음 '+labels[calendarView]);
  for (const button of document.querySelectorAll('[data-calendar-view]')) button.setAttribute('aria-pressed',String(button.dataset.calendarView===calendarView));
  const week=weekDates();
  const period=calendarView==='month' ? `${selectedDate.getFullYear()}년 ${selectedDate.getMonth()+1}월` : calendarView==='day' ? `${selectedDate.getMonth()+1}월 ${selectedDate.getDate()}일 ${weekdays[selectedDate.getDay()]}요일` : `${week[0].getMonth()+1}월 ${week[0].getDate()}일 – ${week[6].getMonth()+1}월 ${week[6].getDate()}일`;
  document.querySelector('#calendar-period').textContent=period;
  document.querySelector('#week-range').textContent=period;
  document.querySelector('.week-scroll').setAttribute('aria-label',period+' 일정');
  if (overview) { renderCalendarOverview(); return; }
  const monday = new Date(selectedDate); monday.setDate(monday.getDate() - (monday.getDay() + 6) % 7);
  const dates = calendarView==='day' ? [new Date(selectedDate)] : Array.from({length: 7}, (_, i) => { const d = new Date(monday); d.setDate(d.getDate() + i); return d; });
  const header = document.querySelector('.week-header'); header.replaceChildren();
  const zone = document.createElement('div'); zone.className = 'timezone'; zone.textContent = '현지 시간'; zone.title = Intl.DateTimeFormat().resolvedOptions().timeZone; header.append(zone);
  const schedule = document.querySelector('.schedule');
  schedule.querySelectorAll('.day-column').forEach(el => el.remove());
  const today = dateKey(new Date());
  for (const date of dates) {
    const key = dateKey(date);
    const heading = document.createElement('button'); heading.type = 'button'; heading.className = 'day-heading';
    heading.classList.toggle('selected', key === dateKey(selectedDate)); heading.classList.toggle('is-today', key === today);
    heading.setAttribute('aria-label', `${key} ${weekdays[date.getDay()]}요일`); heading.setAttribute('aria-pressed', String(key === dateKey(selectedDate)));
    const number = document.createElement('strong'); number.textContent = String(date.getDate());
    const name = document.createElement('span'); name.textContent = `${date.getMonth() + 1}월 · ${weekdays[date.getDay()]}`;
    heading.append(number, name, document.createElement('i')); heading.addEventListener('click', () => selectDate(date)); header.append(heading);
    const column = document.createElement('div'); column.className = 'day-column'; column.dataset.date = key;
    const dayEvents = teamState.events.filter(e => e.date === key).sort((a, b) => a.start - b.start);
    // 겹치는 일정도 각각 클릭할 수 있도록 충돌 묶음 안에서 열을 나눕니다.
    const groups = [];
    for (const entry of dayEvents) {
      let group = groups.at(-1);
      if (!group || entry.start >= group.end) { group = {entries: [], end: 0}; groups.push(group); }
      group.entries.push(entry); group.end = Math.max(group.end, entry.start + entry.duration);
    }
    for (const group of groups) group.entries.forEach((entry, index) => {
      const category = categories.find(c => c.id === entry.category);
      const card = document.createElement('button'); card.type = 'button'; card.className = `event ${category?.color || 'peach'}`;
      card.dataset.category = entry.category; card.dataset.day = `${key} ${weekdays[date.getDay()]}요일`;
      card.dataset.description = `등록자: ${memberName(entry.creator)} · 등록 알림 대상: ${entry.recipients.map(memberName).join(', ') || '없음'} (로컬 미리보기)`;
      card.style.setProperty('--start', entry.start / 60); card.style.setProperty('--duration', entry.duration / 60);
      card.style.left = `calc(${index * 100 / group.entries.length}% + 3px)`; card.style.width = `calc(${100 / group.entries.length}% - 6px)`; card.style.right = 'auto';
      const time = document.createElement('span'); time.className = 'event-time'; time.textContent = `${minutesText(entry.start)}–${minutesText(entry.start + entry.duration)}`;
      const title = document.createElement('strong'); title.textContent = entry.title;
      card.setAttribute('aria-label', `${entry.title}, ${key}, ${time.textContent}`); card.append(time, title); column.append(card);
    });
    schedule.append(column);
  }
  const range = `${dateKey(dates[0])} ~ ${dateKey(dates.at(-1))}`;
  document.querySelector('#week-range').textContent = range;
  document.querySelector('.week-scroll').setAttribute('aria-label', `${range} ${calendarView==='day' ? '일간' : '주간'} 일정`);
  filterEvents();
}
function renderNotificationCount() {
  const count = teamState.notifications.filter(n => n.memberId === currentMember && !n.read).length;
  document.querySelector('.nav-counter').textContent = String(count);
  document.querySelector('#notifications-button').setAttribute('aria-label', `알림 ${count}개`);
}
function openTeamNotifications() {
  if (mobileMenu.matches) setSidebarOpen(false);
  renderTeamNotifications(); notificationDialog.showModal();
}
function renderTeamNotifications() {
  document.querySelector('#notifications-title').textContent = `${memberName(currentMember)} · 받은 알림`;
  const list = document.querySelector('#notification-list'); list.replaceChildren();
  const items = teamState.notifications.filter(n => n.memberId === currentMember).slice().reverse();
  document.querySelector('#mark-all-read').disabled = !items.some(n => !n.read);
  if (!items.length) { const empty = document.createElement('p'); empty.className = 'notification-empty'; empty.textContent = '받은 알림이 없습니다.'; list.append(empty); }
  for (const notification of items) {
    const entry = teamState.events.find(e => e.id === notification.eventId); if (!entry) continue;
    const item = document.createElement('button'); item.type = 'button'; item.className = 'notification-item'; item.classList.toggle('unread', !notification.read);
    const title = document.createElement('strong'); title.textContent = entry.title;
    const meta = document.createElement('span'); meta.textContent = `${entry.date} ${minutesText(entry.start)} · ${notification.read ? '읽음' : '새 등록 알림'}`;
    item.append(title, meta); item.addEventListener('click', () => {
      if (!updateTeamState(next => { const n = next.notifications.find(n => n.id === notification.id); if (n) n.read = true; })) return;
      notificationDialog.close(); selectDate(parseDate(entry.date)); renderNotificationCount();
      document.querySelector('.week-scroll').scrollTop = calendarView !== 'month' ? Math.max(0, (entry.start / 60 - 1) * hourHeight()) : 0;
    }); list.append(item);
  }
}
document.querySelector('#close-notifications').addEventListener('click', () => notificationDialog.close());
document.querySelector('#mark-all-read').addEventListener('click', () => {
  if (!updateTeamState(next => next.notifications.forEach(n => { if (n.memberId === currentMember) n.read = true; }))) return;
  renderNotificationCount(); renderTeamNotifications();
});
window.addEventListener('storage', event => {
  if (event.key !== TEAM_STORE) return;
  try { teamState = readTeamState(); renderTeamProfile(); renderTeamCalendar(); if (notificationDialog.open) renderTeamNotifications(); }
  catch { notify('다른 탭의 변경 내용을 읽지 못했습니다.'); }
});
const timeLabels = document.querySelector('.time-labels');
for (let hour = 0; hour < 24; hour++) { const span = document.createElement('span'); span.textContent = `${pad(hour)}:00`; timeLabels.append(span); }
syncDateSelectors(); renderTeamProfile(); renderTeamCalendar();
document.querySelector('.week-scroll').scrollTop = calendarView !== 'month' ? 8 * hourHeight() : 0;
