// Amplify 정적 미리보기 전용 날짜별 목록. 서버 API를 호출하지 않습니다.
function textNode(tag, value, className) {
  const element=document.createElement(tag); element.textContent=value;
  if(className) element.className=className;
  return element;
}
function weekDates() {
  const monday = new Date(selectedDate); monday.setDate(monday.getDate()-(monday.getDay()+6)%7);
  return Array.from({length:7},(_,i)=>{ const date=new Date(monday); date.setDate(date.getDate()+i); return date; });
}
function visibleDayEvents(date) {
  const term=search.value.trim().toLocaleLowerCase();
  return teamState.events.filter(entry=>entry.date===dateKey(date) && entry.title.toLocaleLowerCase().includes(term) && categories.find(c=>c.id===entry.category)?.checked !== false)
    .sort((a,b)=>a.start-b.start || a.title.localeCompare(b.title));
}
function openMonthEvent(entry,card) {
  activeEvent=card;
  document.querySelector('#detail-title').textContent=entry.title;
  document.querySelector('#detail-time').textContent=minutesText(entry.start)+'–'+minutesText(entry.start+entry.duration);
  document.querySelector('#detail-day').textContent=entry.date;
  document.querySelector('#detail-description').textContent='등록자: '+memberName(entry.creator)+' · 등록 알림 대상: '+(entry.recipients.map(memberName).join(', ') || '없음')+' (이 브라우저 미리보기)';
  if(detail.open) detail.close();
  detail.showModal();
}

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
