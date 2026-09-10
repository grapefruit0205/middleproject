// 동일 출처의 Deadline Companion API 사용. 일정/알림은 로컬 저장으로 대체하지 않습니다.
const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const pad = value => String(value).padStart(2, '0');
const dateKey = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
function parseDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day, 12);
  return dateKey(date) === value ? date : null;
}

let selectedDate = parseDate(dateKey(new Date()));
let calendarView = mobileMenu.matches ? 'day' : 'week';
try {
  const saved = localStorage.getItem('daylight_calendar_view');
  if (['day', 'week', 'month'].includes(saved)) calendarView = saved;
} catch { /* View preference is optional, never calendar data. */ }
const yearSelect = document.querySelector('#calendar-year');
const monthSelect = document.querySelector('#calendar-month');
const daySelect = document.querySelector('#calendar-day');
const notificationDialog = document.querySelector('#notifications-dialog');
const teamState = { events: [] };
const apiBanner = document.querySelector('#api-status');
let loaded = false, refreshing = false, mutating = false;
let editing = null, detailId = null, detailGeneration = 0, pendingWrite = null;
const changeable = value => ['CREATED', 'SCHEDULE_PENDING', 'SCHEDULED', 'SCHEDULE_FAILED', 'DELIVERY_FAILED', 'RETRYING'].includes(value);
const timeFormat = value => new Date(value).toLocaleString('ko-KR');
function deadlineStatus(row) {
  return ({ CANCELLED:'취소됨', SCHEDULED:'예약 완료', SCHEDULE_FAILED:'예약 실패',
    DELIVERY_FAILED:'발송 실패', DELIVERY_UNKNOWN:'발송 결과 불명확',
    DELIVERED:'발송 요청 수락', ACKNOWLEDGED:'발송 요청 수락',
    RETRYING:'재시도 중', SCHEDULE_PENDING:'예약 준비 중', CREATED:'저장 완료' })[row.status] || row.status;
}
function banner(message, error = false) {
  apiBanner.textContent = message;
  apiBanner.classList.toggle('api-error', error);
}
async function api(path, options = {}) {
  let response;
  try {
    response = await fetch('/api/deadlines' + path, {
      ...options, cache:'no-store', credentials:'same-origin',
      headers:{ Accept:'application/json', ...(options.body ? {'Content-Type':'application/json'} : {}), ...options.headers },
      signal: AbortSignal.timeout(15000)
    });
  } catch {
    throw Object.assign(new Error('서버 응답을 확인하지 못했습니다. 네트워크를 확인해 주세요.'), {uncertain: true});
  }
  if (!response.ok) {
    const messages = {400:'입력값을 확인해 주세요. 일정과 알림 시각 모두 미래여야 합니다.',
      401:'인증이 필요합니다. 현재 연결은 로컬 단일 소유자용입니다.',
      403:'이 요청에 대한 접근 권한이 없습니다.',
      404:'일정을 찾을 수 없습니다.',
      409:'다른 변경과 충돌했습니다. 최신 일정을 다시 열어 확인해 주세요.'};
    throw Object.assign(new Error(messages[response.status] || '서버에서 요청을 처리하지 못했습니다.'),
      {status:response.status, uncertain:response.status >= 500});
  }
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw Object.assign(new Error('API가 아닌 정적 페이지에 접속했습니다. WEB 서버의 Daylight 주소를 사용하세요.'), {uncertain:true});
  try { return await response.json(); }
  catch { throw Object.assign(new Error('서버 응답 형식이 올바르지 않습니다.'), {uncertain:true}); }
}
function toEvent(row) {
  const date = new Date(row.startsAt);
  if (!row.id || !row.title || Number.isNaN(date.getTime())) throw new Error('일정 데이터 형식이 올바르지 않습니다.');
  return {...row, date:dateKey(date), start:date.getHours()*60+date.getMinutes(),
    duration:Math.min(60, 1440-date.getHours()*60-date.getMinutes()), category:'server'};
}
function setControls() {
  for (const id of ['new-event', 'empty-new-event'])
    document.getElementById(id).disabled = !loaded || mutating || !!pendingWrite;
  document.querySelector('#create-form button[type=submit]').disabled = mutating || !!pendingWrite;
  document.querySelector('#edit-event').disabled = mutating || !!pendingWrite || !loaded;
  document.querySelector('#cancel-event').disabled = mutating || !!pendingWrite || !loaded;
  document.querySelector('#retry-write').hidden = !pendingWrite;
  document.querySelector('#retry-write').disabled = mutating;
  document.querySelector('#retry-form-write').hidden = !pendingWrite;
  document.querySelector('#retry-form-write').disabled = mutating;
}
async function refreshDeadlines() {
  if (refreshing || mutating || document.hidden || !navigator.onLine) return;
  refreshing = true;
  try {
    const rows = await api('');
    if (!Array.isArray(rows)) throw new Error('일정 목록 응답 형식이 올바르지 않습니다.');
    teamState.events = rows.map(toEvent);
    loaded = true;
    renderTeamCalendar();
    if (notificationDialog.open) renderTeamNotifications();
    if (detail.open && detailId) await loadDetail(detailId);
    if (!pendingWrite) banner('서버 DB 연결 · '+new Date().toLocaleTimeString('ko-KR')+' 갱신 · 팀 공유는 아직 미연결');
  } catch (error) {
    loaded = false;
    banner(error.message + ' 마지막 조회 내용을 유지합니다. 로컬 저장으로 대체하지 않습니다.', true);
    if (!teamState.events.length) {
      document.querySelector('#calendar-empty h1').textContent = '서버 연결을 확인해 주세요';
      document.querySelector('#calendar-empty p').textContent = '연결 실패를 빈 일정 목록으로 처리하지 않습니다.';
    }
  } finally { refreshing = false; setControls(); }
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

function weekDates() {
  const monday = new Date(selectedDate); monday.setDate(monday.getDate()-(monday.getDay()+6)%7);
  return Array.from({length:7},(_,i)=>{ const date=new Date(monday); date.setDate(date.getDate()+i); return date; });
}
function visibleDayEvents(date) {
  const term=search.value.trim().toLocaleLowerCase();
  return teamState.events.filter(entry=>entry.date===dateKey(date) && entry.title.toLocaleLowerCase().includes(term))
    .sort((a,b)=>a.start-b.start || a.title.localeCompare(b.title));
}
function openMonthEvent(entry,card) { activeEvent=card; detail.showModal(); loadDetail(entry.id); }

function renderCalendarOverview() {
  const container=document.querySelector('#calendar-overview');
  if (container.hidden) return;
  const focused=document.activeElement;
  const focusDate=container.contains(focused) ? focused.dataset.date : null;
  const focusId=container.contains(focused) ? focused.dataset.id : null;
  container.replaceChildren();
  const grid=textNode('div','','month-grid');
  grid.setAttribute('aria-label','월간 일정 달력');
  for (const day of ['월','화','수','목','금','토','일']) grid.append(textNode('span',day,'month-weekday'));
  const first=new Date(selectedDate.getFullYear(),selectedDate.getMonth(),1,12);
  const start=new Date(first); start.setDate(1-(first.getDay()+6)%7);
  const days=new Date(first.getFullYear(),first.getMonth()+1,0).getDate();
  const slots=Math.ceil(((first.getDay()+6)%7+days)/7)*7;
  for (let i=0;i<slots;i++) {
    const date=new Date(start); date.setDate(start.getDate()+i);
    const key=dateKey(date), entries=visibleDayEvents(date);
    const cell=textNode('section','','month-cell');
    cell.classList.toggle('outside-month',date.getMonth()!==first.getMonth());
    cell.classList.toggle('selected',key===dateKey(selectedDate));
    const day=textNode('button',String(date.getDate()),'month-date');
    day.type='button'; day.dataset.date=key;
    day.setAttribute('aria-label',key+' '+weekdays[date.getDay()]+'요일, 일정 '+entries.length+'개');
    day.setAttribute('aria-pressed',String(key===dateKey(selectedDate)));
    if(key===dateKey(new Date())) day.setAttribute('aria-current','date');
    day.onclick=()=>selectDate(date);
    cell.append(day);
    for(const entry of entries.slice(0,3)) {
      const category=categories.find(c=>c.id===entry.category);
      const card=textNode('button','','event month-event '+(category?.color || 'peach'));
      card.type='button'; card.dataset.id=entry.id;
      card.classList.toggle('cancelled',entry.status==='CANCELLED');
      card.append(textNode('span',minutesText(entry.start),'event-time'),textNode('strong',entry.title));
      card.setAttribute('aria-label',entry.title+', '+key+', '+minutesText(entry.start));
      card.title=entry.title+' · '+minutesText(entry.start);
      card.onclick=()=>openMonthEvent(entry,card);
      cell.append(card);
    }
    if(entries.length>3) {
      const more=textNode('button','+'+(entries.length-3)+'개 더 보기','month-more'); more.type='button';
      more.setAttribute('aria-label',key+' 전체 일정 '+entries.length+'개 보기');
      more.onclick=()=>{selectDate(date); document.querySelector('[data-calendar-view="day"]').click();};
      cell.append(more);
    }
    grid.append(cell);
  }
  container.append(grid);
  if(focusDate) [...container.querySelectorAll('.month-date')].find(el=>el.dataset.date===focusDate)?.focus({preventScroll:true});
  if(focusId) [...container.querySelectorAll('.month-event')].find(el=>el.dataset.id===focusId)?.focus({preventScroll:true});
}

function minutesText(minutes) { return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`; }
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
      card.dataset.id = entry.id;
      card.dataset.category = entry.category; card.dataset.day = `${key} ${weekdays[date.getDay()]}요일`;
      card.dataset.description = `알림: ${entry.leadMinutes}분 전 · ${deadlineStatus(entry)} · 카드 높이는 표시용이며 종료 시각은 저장하지 않습니다.`;
      card.style.setProperty('--start', entry.start / 60); card.style.setProperty('--duration', entry.duration / 60);
      card.style.left = `calc(${index * 100 / group.entries.length}% + 3px)`; card.style.width = `calc(${100 / group.entries.length}% - 6px)`; card.style.right = 'auto';
      const time = document.createElement('span'); time.className = 'event-time'; time.textContent = `${minutesText(entry.start)} · ${deadlineStatus(entry)}`;
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

function prepareTeamEvent() {
  const form = document.querySelector('#create-form');
  form.reset(); editing = null;
  form.elements.date.value = dateKey(selectedDate);
  document.querySelector('#create-title').textContent = '새 일정';
  document.querySelector('#form-error').textContent = '';
}
async function submitTeamEvent(event) {
  event.preventDefault();
  if (mutating || pendingWrite || !loaded) return;
  const form = event.currentTarget;
  const date = new Date(form.elements.date.value + 'T' + form.elements.time.value);
  const title = form.elements.title.value.trim();
  const leadMinutes = Number(form.elements.leadMinutes.value);
  if (!title || Number.isNaN(date.getTime()) || !Number.isInteger(leadMinutes) || leadMinutes < 0 ||
      leadMinutes > 525600 || date.getTime() - leadMinutes*60000 <= Date.now()) {
    document.querySelector('#form-error').textContent = '제목과 미래의 일정·알림 시각을 확인해 주세요.'; return;
  }
  const body = {title, startsAt:date.toISOString(), leadMinutes};
  if (editing) Object.assign(body, { expectedVersion:editing.version,
    expectedEventVersion:editing.eventVersion, expectedPolicyVersion:editing.policyVersion });
  pendingWrite = {path:editing ? '/'+editing.id : '', method:editing ? 'PUT' : 'POST',
    body:JSON.stringify(body), key:crypto.randomUUID(), date:dateKey(date)};
  await sendPendingWrite();
}
async function sendPendingWrite() {
  if (mutating || !pendingWrite) return;
  const operation = pendingWrite;
  mutating = true; setControls();
  document.querySelector('#form-error').textContent = '';
  try {
    const saved = await api(operation.path, {method:operation.method, body:operation.body,
      headers:{'Idempotency-Key':operation.key}});
    // Identical retries may receive the first committed response.
    toEvent(saved); pendingWrite = null;
    create.close(); detail.close();
    if (operation.date) selectDate(parseDate(operation.date));
    notify('서버에 반영했습니다. 예약·발송 완료 여부는 처리 이력에서 확인하세요.');
  } catch (error) {
    if (!error.uncertain) pendingWrite = null;
    const message = error.message + (error.uncertain ?
      ' 저장 여부가 불명확합니다. “같은 요청 다시 확인”으로 동일 요청 키를 재사용하세요. 새로고침 전 상태를 확인하세요.' : '');
    banner(message, true);
    document.querySelector('#form-error').textContent = message;
    if (error.status === 409) { create.close(); detail.close(); }
    notify(message);
  } finally {
    mutating = false; setControls();
    if (!pendingWrite) await refreshDeadlines();
  }
}
function textNode(tag, value, className) {
  const node = document.createElement(tag); node.textContent = value;
  if (className) node.className = className;
  return node;
}
async function loadDetail(id) {
  detailId = id;
  const generation = ++detailGeneration;
  const list = document.querySelector('#detail-history');
  list.replaceChildren(textNode('p', '처리 이력을 불러오는 중입니다.'));
  document.querySelector('#edit-event').hidden = true;
  document.querySelector('#cancel-event').hidden = true;
  try {
    const [row, history] = await Promise.all([api('/'+id), api('/'+id+'/history')]);
    if (generation !== detailGeneration || !detail.open) return;
    document.querySelector('#detail-title').textContent = row.title;
    document.querySelector('#detail-day').textContent = timeFormat(row.startsAt);
    document.querySelector('#detail-time').textContent = deadlineStatus(row);
    document.querySelector('#detail-description').textContent =
      '예약 알림: '+timeFormat(row.remindAt)+' · '+history.deliveryModeDetail+
      ' 발송 요청 수락은 이메일 도착·열람을 뜻하지 않습니다.';
    list.replaceChildren(...history.entries.map(entry => {
      const item = textNode('li', '');
      item.append(textNode('strong', entry.kind+' · '+entry.status),
        textNode('p', entry.detail), textNode('small', timeFormat(entry.occurredAt)));
      return item;
    }));
    document.querySelector('#edit-event').hidden = !changeable(row.status);
    document.querySelector('#cancel-event').hidden = !changeable(row.status);
    document.querySelector('#edit-event').onclick = () => {
      prepareTeamEvent(); editing = row;
      const date = new Date(row.startsAt), form = document.querySelector('#create-form');
      form.elements.title.value = row.title;
      form.elements.date.value = dateKey(date);
      form.elements.time.value = minutesText(date.getHours()*60+date.getMinutes());
      form.elements.leadMinutes.value = row.leadMinutes;
      document.querySelector('#create-title').textContent = '일정 수정';
      detail.close(); create.showModal();
    };
    document.querySelector('#cancel-event').onclick = async () => {
      if (mutating || pendingWrite || !confirm('이 일정과 예약 알림을 취소할까요? 이력은 남습니다.')) return;
      pendingWrite = {path:'/'+row.id+'/cancel', method:'POST',
        body:JSON.stringify({expectedVersion:row.version}), key:crypto.randomUUID()};
      await sendPendingWrite();
    };
    setControls();
  } catch (error) {
    if (generation === detailGeneration) list.replaceChildren(textNode('p', error.message, 'api-error'));
  }
}
function openTeamNotifications() {
  if (mobileMenu.matches) setSidebarOpen(false);
  renderTeamNotifications(); notificationDialog.showModal();
}
function renderTeamNotifications() {
  const list = document.querySelector('#notification-list'); list.replaceChildren();
  if (!loaded) list.append(textNode('p', '서버 연결이 끊겼습니다. 아래 내용은 마지막 조회 결과입니다.'));
  if (!teamState.events.length && loaded) list.append(textNode('p', '등록된 일정이 없습니다.'));
  for (const entry of teamState.events) {
    const item = textNode('button', '', 'notification-item'); item.type = 'button';
    item.append(textNode('strong', entry.title), textNode('span', timeFormat(entry.startsAt)+' · '+deadlineStatus(entry)));
    item.onclick = () => { notificationDialog.close(); detail.showModal(); loadDetail(entry.id); };
    list.append(item);
  }
}
document.querySelector('#close-notifications').onclick = () => notificationDialog.close();
document.querySelector('#refresh-deadlines').onclick = refreshDeadlines;
document.querySelector('#retry-write').onclick = sendPendingWrite;
document.querySelector('#retry-form-write').onclick = sendPendingWrite;
detail.addEventListener('close', () => { detailId = null; detailGeneration++; });
document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshDeadlines(); });
window.addEventListener('online', refreshDeadlines);
window.addEventListener('offline', () => { loaded = false; banner('오프라인입니다. 마지막 조회 결과를 표시합니다.', true); setControls(); });
window.addEventListener('beforeunload', event => { if (pendingWrite) { event.preventDefault(); event.returnValue = ''; } });
setInterval(refreshDeadlines, 30000);
const timeLabels = document.querySelector('.time-labels');
for (let hour = 0; hour < 24; hour++) timeLabels.append(textNode('span', `${pad(hour)}:00`));
syncDateSelectors(); renderTeamCalendar(); setControls(); refreshDeadlines();
document.querySelector('.week-scroll').scrollTop = calendarView !== 'month' ? 8 * hourHeight() : 0;
