// UI 데모 전용. 서버 요청과 영구 저장 없이 현재 페이지에서만 동작합니다.
const detail = document.querySelector('#event-dialog');
const create = document.querySelector('#create-dialog');
const search = document.querySelector('#event-search');
const toast = document.querySelector('.toast');
let toastTimer;
let activeEvent = document.querySelector('.featured-event');

function notify(message) {
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 3500);
}

function showEvent(event) {
  activeEvent = event;
  document.querySelector('#detail-title').textContent = event.querySelector('strong').textContent;
  document.querySelector('#detail-time').textContent = event.querySelector('.event-time').textContent.replace('ⓘ', '').trim();
  document.querySelector('#detail-day').textContent = event.dataset.day;
  document.querySelector('#detail-description').textContent = event.dataset.description || '팀과 함께 일정의 목표와 필요한 준비 사항을 확인합니다. 이 일정은 화면 구성을 보여주기 위한 샘플입니다.';
  if (detail.open) detail.close();
  detail.showModal();
}

document.querySelector('.schedule').addEventListener('click', (event) => {
  const card = event.target.closest('.event');
  if (card) showEvent(card);
});
document.querySelector('#close-detail').addEventListener('click', () => detail.close());
document.querySelector('#confirm-event').addEventListener('click', () => {
  detail.close();
  notify('일정을 확인했습니다.');
  activeEvent?.focus({ preventScroll: true });
});
document.querySelector('#new-event').addEventListener('click', () => {
  detail.close();
  create.showModal();
});
document.querySelector('#close-create').addEventListener('click', () => create.close());
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && detail.open) detail.close();
});
for (const dialog of [detail, create]) {
  dialog.addEventListener('click', (event) => {
    const bounds = dialog.getBoundingClientRect();
    if (event.target === dialog && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) dialog.close();
  });
}
function filterEvents() {
  const enabled = new Set([...document.querySelectorAll('.category-list input:checked')].map(input => input.value));
  const term = search.value.trim().toLocaleLowerCase();
  document.querySelectorAll('.event').forEach(card => {
    card.hidden = !enabled.has(card.dataset.category) || !card.querySelector('strong').textContent.toLocaleLowerCase().includes(term);
  });
  if (activeEvent?.hidden) detail.close();
}
document.querySelectorAll('.category-list input').forEach(input => input.addEventListener('change', filterEvents));
search.addEventListener('input', filterEvents);
document.querySelector('#search-button').addEventListener('click', () => {
  document.querySelector('.search-container').hidden = false;
  search.focus();
});
document.querySelector('#search-close').addEventListener('click', () => {
  search.value = '';
  filterEvents();
  document.querySelector('.search-container').hidden = true;
  document.querySelector('#search-button').focus();
});
document.querySelector('#calendar-button').addEventListener('click', () => {
  search.value = '';
  document.querySelectorAll('.category-list input').forEach(input => { input.checked = true; });
  filterEvents();
  detail.close();
});
document.querySelector('#inbox-button').addEventListener('click', () => notify('샘플 알림 2개: 킥오프 미팅 · 디자인 리뷰'));
document.querySelector('#notifications-button').addEventListener('click', () => notify('샘플 알림: 오전 11시 34분, 진행 중인 일정을 확인하세요.'));
document.querySelector('#messages-button').addEventListener('click', () => notify('디자인 미리보기에는 채팅이 연결되어 있지 않습니다.'));
document.querySelector('#create-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const title = String(data.get('title')).trim();
  if (!title) { form.elements.title.focus(); return; }
  const day = Number(data.get('day'));
  const hour = Number(data.get('hour'));
  const card = document.createElement('button');
  card.className = 'event peach';
  card.dataset.category = 'meetings';
  card.dataset.day = '7월 ' + (17 + day) + '일';
  card.style.setProperty('--start', hour);
  card.style.setProperty('--duration', 1);
  const time = document.createElement('span');
  time.className = 'event-time';
  time.textContent = String(8 + hour).padStart(2, '0') + ':00–' + String(9 + hour).padStart(2, '0') + ':00';
  const heading = document.createElement('strong');
  heading.textContent = title;
  card.append(time, heading);
  document.querySelectorAll('.day-column')[day].append(card);
  form.reset();
  create.close();
  filterEvents();
  notify('샘플 일정을 추가했습니다. 새로고침하면 초기화됩니다.');
});
