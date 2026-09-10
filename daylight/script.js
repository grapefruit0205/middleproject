// 주간 캘린더 애플리케이션 스크립트

// DOM 요소 참조
const detail = document.querySelector('#event-dialog');
const create = document.querySelector('#create-dialog');
const categoryDialog = document.querySelector('#category-dialog');
const search = document.querySelector('#event-search');
const toast = document.querySelector('.toast');
const categoryListEl = document.querySelector('.category-list');
const createCategorySelect = document.querySelector('#create-category-select');
const btnAddCategory = document.querySelector('#btn-add-category');
const closeCategoryDialog = document.querySelector('#close-category-dialog');
const categoryForm = document.querySelector('#category-form');
const deleteCategoryBtn = document.querySelector('#delete-category-btn');

let toastTimer;
let activeEvent = null;

// 메뉴 선택만 저장합니다. 일정/알림 샘플을 생성하거나 서버 데이터를 삭제하지 않습니다.
const app = document.querySelector('.calendar-app');
const sidebar = document.querySelector('#sidebar');
const sidebarToggle = document.querySelector('#sidebar-toggle');
const sidebarClose = document.querySelector('#sidebar-close');
const sidebarBackdrop = document.querySelector('#sidebar-backdrop');
const workspace = document.querySelector('.workspace');
const mobileMenu = window.matchMedia('(max-width: 700px)');
let sidebarOpen = !mobileMenu.matches;
try {
  const saved = localStorage.getItem('daylight_sidebar_open');
  if (saved !== null) sidebarOpen = saved === 'true';
} catch { /* 저장소가 제한되어도 메뉴는 동작합니다. */ }

function setSidebarOpen(open, persist = true) {
  const focusWasInSidebar = sidebar.contains(document.activeElement);
  sidebarOpen = open;
  sidebar.hidden = !open;
  app.classList.toggle('sidebar-collapsed', !open);
  sidebarToggle.setAttribute('aria-expanded', String(open));
  sidebarToggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
  sidebarBackdrop.hidden = !(open && mobileMenu.matches);
  workspace.inert = open && mobileMenu.matches;
  if (open && mobileMenu.matches) sidebarClose.focus();
  else if (!open && focusWasInSidebar) sidebarToggle.focus();
  if (persist) {
    try { localStorage.setItem('daylight_sidebar_open', String(open)); } catch { /* 선택은 현재 화면에 유지 */ }
  }
}
sidebarToggle.addEventListener('click', () => setSidebarOpen(!sidebarOpen));
sidebarClose.addEventListener('click', () => setSidebarOpen(false));
sidebarBackdrop.addEventListener('click', () => setSidebarOpen(false));
mobileMenu.addEventListener('change', () => setSidebarOpen(sidebarOpen, false));
setSidebarOpen(sidebarOpen, false);

// 기본 카테고리 데이터 (프로젝트 및 클라이언트 공유용)
const DEFAULT_CATEGORIES = [
  { id: 'meetings', label: '미팅 · 정기 보고', color: 'peach', checked: true },
  { id: 'branding', label: '브랜딩 · 아이덴티티', color: 'blue', checked: true },
  { id: 'wireframe', label: '화면 설계 (와이어프레임)', color: 'mint', checked: true },
  { id: 'research', label: '리서치 · 기획', color: 'yellow', checked: true },
  { id: 'mockup', label: '디자인 시안 (UI)', color: 'pink', checked: true },
  { id: 'prototype', label: '프로토타입 시연', color: 'aqua', checked: true },
  { id: 'review', label: '고객 검토 · 피드백', color: 'coral', checked: true }
];

// 로컬스토리지에서 카테고리 불러오기
function loadCategories() {
  const saved = localStorage.getItem('design_calendar_categories');
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch (e) {
      console.error('카테고리 파싱 실패, 기본값을 사용합니다.', e);
    }
  }
  return DEFAULT_CATEGORIES;
}

let categories = loadCategories();

function saveCategories() {
  localStorage.setItem('design_calendar_categories', JSON.stringify(categories));
}

// 토스트 메시지
function notify(message) {
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 3500);
}

// 카테고리 동적 렌더링
function renderCategories() {
  categoryListEl.innerHTML = '';

  categories.forEach(cat => {
    const label = document.createElement('label');
    label.className = 'category-item';
    label.dataset.id = cat.id;

    // 체크박스
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.value = cat.id;
    checkbox.checked = cat.checked;
    checkbox.addEventListener('change', () => {
      cat.checked = checkbox.checked;
      saveCategories();
      filterEvents();
    });

    // 스와치
    const swatch = document.createElement('span');
    swatch.className = `swatch ${cat.color}`;

    // 카테고리 텍스트 (더블클릭 시 빠른 인라인 수정)
    const nameSpan = document.createElement('span');
    nameSpan.className = 'category-name';
    nameSpan.textContent = cat.label;
    nameSpan.title = '더블클릭하여 이름 수정';
    nameSpan.addEventListener('dblclick', (e) => {
      e.preventDefault();
      startInlineRename(cat, nameSpan);
    });

    // 편집 버튼
    const actions = document.createElement('div');
    actions.className = 'category-actions';

    const editBtn = document.createElement('button');
    editBtn.type = 'button';
    editBtn.className = 'cat-icon-btn';
    editBtn.title = '카테고리 수정';
    editBtn.setAttribute('aria-label', `${cat.label} 수정`);
    editBtn.innerHTML = '<svg class="icon"><use href="#edit"/></svg>';
    editBtn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      openCategoryDialog(cat.id);
    });

    actions.append(editBtn);
    label.append(checkbox, swatch, nameSpan, actions);
    categoryListEl.appendChild(label);
  });

  updateCategorySelectOptions();
}

// 빠른 인라인 이름 수정 핸들러
function startInlineRename(cat, element) {
  const currentText = cat.label;
  const input = document.createElement('input');
  input.type = 'text';
  input.className = 'inline-category-input';
  input.value = currentText;

  element.replaceWith(input);
  input.focus();
  input.select();

  let isSaved = false;
  function finish() {
    if (isSaved) return;
    isSaved = true;
    const newText = input.value.trim();
    if (newText && newText !== currentText) {
      cat.label = newText;
      saveCategories();
      notify(`카테고리가 '${newText}'(으)로 변경되었습니다.`);
    }
    renderCategories();
  }

  input.addEventListener('blur', finish);
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      finish();
    } else if (e.key === 'Escape') {
      isSaved = true;
      renderCategories();
    }
  });
}

// 새 일정 생성 시 카테고리 셀렉트박스 동기화
function updateCategorySelectOptions() {
  if (!createCategorySelect) return;
  const currentVal = createCategorySelect.value;
  createCategorySelect.innerHTML = '';

  categories.forEach(cat => {
    const opt = document.createElement('option');
    opt.value = cat.id;
    opt.textContent = cat.label;
    createCategorySelect.appendChild(opt);
  });

  if (currentVal && categories.some(c => c.id === currentVal)) {
    createCategorySelect.value = currentVal;
  }
}

// 카테고리 모달 열기 (추가 또는 수정 모드)
function openCategoryDialog(categoryId = null) {
  const titleEl = document.querySelector('#category-dialog-title');
  const idInput = document.querySelector('#category-id');
  const nameInput = document.querySelector('#category-name-input');
  const colorRadios = document.querySelectorAll('input[name="color"]');

  if (categoryId) {
    const cat = categories.find(c => c.id === categoryId);
    if (!cat) return;
    titleEl.textContent = '카테고리 편집';
    idInput.value = cat.id;
    nameInput.value = cat.label;
    colorRadios.forEach(r => { r.checked = (r.value === cat.color); });
    deleteCategoryBtn.style.display = 'inline-block';
  } else {
    titleEl.textContent = '새 카테고리 추가';
    idInput.value = '';
    nameInput.value = '';
    colorRadios.forEach(r => { r.checked = (r.value === 'peach'); });
    deleteCategoryBtn.style.display = 'none';
  }

  categoryDialog.showModal();
  nameInput.focus();
}

// 카테고리 폼 저장 핸들러
categoryForm.addEventListener('submit', (e) => {
  e.preventDefault();
  const id = document.querySelector('#category-id').value;
  const label = document.querySelector('#category-name-input').value.trim();
  const color = document.querySelector('input[name="color"]:checked')?.value || 'peach';

  if (!label) return;

  if (id) {
    // 수정 모드
    const cat = categories.find(c => c.id === id);
    if (cat) {
      const oldColor = cat.color;
      cat.label = label;
      cat.color = color;

      // 해당 카테고리를 사용하는 기존 캘린더 이벤트의 색상 클래스도 함께 갱신
      document.querySelectorAll(`.event[data-category="${id}"]`).forEach(card => {
        card.classList.remove(oldColor);
        card.classList.add(color);
      });
      notify(`카테고리 '${label}'이(가) 수정되었습니다.`);
    }
  } else {
    // 추가 모드
    const newId = 'cat_' + Date.now();
    categories.push({
      id: newId,
      label: label,
      color: color,
      checked: true
    });
    notify(`새 카테고리 '${label}'이(가) 추가되었습니다.`);
  }

  saveCategories();
  renderCategories();
  filterEvents();
  categoryDialog.close();
});

// 카테고리 삭제 버튼 핸들러
deleteCategoryBtn.addEventListener('click', () => {
  const id = document.querySelector('#category-id').value;
  if (!id) return;

  const cat = categories.find(c => c.id === id);
  if (!cat) return;

  if (confirm(`'${cat.label}' 카테고리를 삭제하시겠습니까?`)) {
    categories = categories.filter(c => c.id !== id);
    saveCategories();
    renderCategories();
    filterEvents();
    categoryDialog.close();
    notify(`카테고리가 삭제되었습니다.`);
  }
});

btnAddCategory.addEventListener('click', () => openCategoryDialog(null));
closeCategoryDialog.addEventListener('click', () => categoryDialog.close());

// 일정 상세 정보 모달
function showEvent(event) {
  activeEvent = event;
  document.querySelector('#detail-title').textContent = event.querySelector('strong').textContent;
  document.querySelector('#detail-time').textContent = event.querySelector('.event-time').textContent.replace('ⓘ', '').trim();
  document.querySelector('#detail-day').textContent = event.dataset.day;
  document.querySelector('#detail-description').textContent = event.dataset.description || '추가 설명이 없습니다.';
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

// 새 일정 만들기 모달
function openCreateDialog() {
  if (mobileMenu.matches) setSidebarOpen(false);
  detail.close();
  prepareTeamEvent();
  create.showModal();
}
document.querySelector('#new-event').addEventListener('click', openCreateDialog);
document.querySelector('#empty-new-event').addEventListener('click', openCreateDialog);
document.querySelector('#close-create').addEventListener('click', () => create.close());

// Esc 키 닫기 핸들러
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    const hadDialog = Boolean(document.querySelector('dialog[open]'));
    if (detail.open) detail.close();
    if (create.open) create.close();
    if (categoryDialog.open) categoryDialog.close();
    if (!hadDialog && sidebarOpen) setSidebarOpen(false);
  }
});

// 모달 바깥 배경 클릭 시 닫기
for (const dialog of [detail, create, categoryDialog]) {
  dialog.addEventListener('click', (event) => {
    const bounds = dialog.getBoundingClientRect();
    if (event.target === dialog && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) {
      dialog.close();
    }
  });
}

// 이벤트 필터링 (카테고리 및 검색어)
function filterEvents() {
  const enabled = new Set(categories.filter(c => c.checked).map(c => c.id));
  const term = search.value.trim().toLocaleLowerCase();

  document.querySelectorAll('.event').forEach(card => {
    const isCategoryMatched = enabled.has(card.dataset.category) || !categories.some(c => c.id === card.dataset.category);
    const isSearchMatched = card.querySelector('strong').textContent.toLocaleLowerCase().includes(term);
    card.hidden = !isCategoryMatched || !isSearchMatched;
  });

  if (activeEvent?.hidden && detail.open) detail.close();
  const empty = document.querySelector('#calendar-empty');
  const cards = document.querySelectorAll('.event');
  empty.hidden = [...cards].some(card => !card.hidden);
  empty.querySelector('h1').textContent = cards.length ? '조건에 맞는 일정이 없어요' : '이번 주는 여유롭게';
  empty.querySelector('p').textContent = cards.length ? '검색어나 카테고리 선택을 바꿔 보세요.' : '선택한 주에 등록된 일정이 없습니다. 팀의 첫 일정을 추가해 보세요.';
}

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

// 캘린더 네비게이션 버튼 (전체 카테고리 체크 활성화 및 검색 초기화)
document.querySelector('#calendar-button').addEventListener('click', () => {
  search.value = '';
  categories.forEach(c => { c.checked = true; });
  saveCategories();
  renderCategories();
  filterEvents();
  detail.close();
  notify('모든 카테고리 일정을 표시합니다.');
});

document.querySelector('#inbox-button').addEventListener('click', () => openTeamNotifications());
document.querySelector('#notifications-button').addEventListener('click', () => openTeamNotifications());

// 팀 일정 저장은 날짜·수신자 모델을 사용하는 별도 어댑터가 담당합니다.
document.querySelector("#create-form").addEventListener("submit", (event) => submitTeamEvent(event));

// 초기 실행
renderCategories();
filterEvents();
