import { FormEvent, useEffect, useRef, useState } from 'react'
import DeadlineCalendar from './components/DeadlineCalendar'
import Icon from './components/Icon'
import useAutoRefresh from './hooks/useAutoRefresh'

type LoadState = 'loading' | 'ready' | 'error'

type Deadline = {
  id: string
  eventId: string
  policyId: string
  title: string
  startsAt: string
  leadMinutes: number
  remindAt: string
  status: string
  scheduleStatus: string
  version: number
  eventVersion: number
  policyVersion: number
}

type DeadlineForm = {
  title: string
  startsAt: string
  leadMinutes: string
}

type PendingSubmission = {
  fingerprint: string
  key: string
}

type HistoryEntry = {
  id: string
  kind: 'DEADLINE' | 'SCHEDULE' | 'NOTIFICATION'
  action: string
  status: string
  occurredAt: string
  completedAt: string | null
  detail: string
  providerReference: string | null
  attempts: number
}

type DeadlineHistory = {
  reminderId: string
  deliveryMode: 'ACTIVE' | 'PARTIAL' | 'DISABLED'
  deliveryModeDetail: string
  entries: HistoryEntry[]
}

type HistoryState = {
  status: 'loading' | 'ready' | 'error'
  data?: DeadlineHistory
  message?: string
  refreshing?: boolean
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'
const SEOUL_TIMEZONE = 'Asia/Seoul'

const initialForm: DeadlineForm = {
  title: '',
  startsAt: '',
  leadMinutes: '60',
}

function App() {
  const [deadlines, setDeadlines] = useState<Deadline[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [loadError, setLoadError] = useState('')
  const [form, setForm] = useState<DeadlineForm>(initialForm)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [cancelConfirmId, setCancelConfirmId] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<{ id: string; message: string } | null>(null)
  const [openHistoryId, setOpenHistoryId] = useState<string | null>(null)
  const [historyState, setHistoryState] = useState<Record<string, HistoryState>>({})
  const [authToken, setAuthToken] = useState('')
  const [authRequired, setAuthRequired] = useState(false)
  const [tokenInput, setTokenInput] = useState('')
  const [authError, setAuthError] = useState('')
  const [authenticating, setAuthenticating] = useState(false)
  const [search, setSearch] = useState('')
  const [view, setView] = useState<'all' | 'scheduled' | 'attention' | 'cancelled'>('all')
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState('')
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const [online, setOnline] = useState(() => navigator.onLine)
  const titleInput = useRef<HTMLInputElement>(null)
  const pendingSubmission = useRef<PendingSubmission | null>(null)
  const pendingCancellations = useRef(new Map<string, string>())
  const hasLoaded = useRef(false)
  const listRequest = useRef<AbortController | null>(null)
  const historyRequest = useRef<AbortController | null>(null)
  const editingSnapshot = useRef<Deadline | null>(null)
  const activeHistory = useRef<string | null>(null)
  const mutationBusy = useRef(false)
  const automaticRefresh = useRef(false)

  function abortReads(): void {
    listRequest.current?.abort()
    historyRequest.current?.abort()
    setRefreshing(false)
  }

  async function loadDeadlines(signal?: AbortSignal, includeHistory = false): Promise<void> {
    listRequest.current?.abort()
    const controller = new AbortController()
    listRequest.current = controller
    const abort = () => controller.abort()
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) controller.abort()
    if (hasLoaded.current) setRefreshing(true)
    else setLoadState('loading')
    setLoadError('')
    try {
      const response = await fetch(`${API_BASE_URL}/deadlines`, {
        headers: authorizedHeaders(authToken, { Accept: 'application/json' }),
        signal: controller.signal,
      })
      if (controller.signal.aborted) return
      if (response.status === 401) {
        requireAuthentication('접근 키를 입력해 내 일정에 연결해 주세요.')
        return
      }
      if (!response.ok) throw new Error(await responseMessage(response, '일정을 불러오지 못했습니다.'))
      const body = (await response.json()) as Deadline[]
      if (controller.signal.aborted) return
      if (!Array.isArray(body)) throw new Error('서버가 올바른 일정 목록을 반환하지 않았습니다.')
      setDeadlines(body)
      hasLoaded.current = true
      setLoadState('ready')
      const historyId = activeHistory.current
      if (includeHistory && historyId && body.some((item) => item.id === historyId)) {
        const succeeded = await loadHistory(historyId, true, controller.signal)
        if (!succeeded || controller.signal.aborted) return
      } else if (historyId && !body.some((item) => item.id === historyId)) {
        historyRequest.current?.abort()
        activeHistory.current = null
        setOpenHistoryId(null)
      }
      if (controller.signal.aborted) return
      setLastUpdatedAt(new Date())
      setRefreshError('')
    } catch (error) {
      if (controller.signal.aborted) return
      const message = errorMessage(error, '일정을 불러오지 못했습니다.')
      if (hasLoaded.current) setRefreshError(message)
      else {
        setLoadState('error')
        setLoadError(message)
      }
    } finally {
      signal?.removeEventListener('abort', abort)
      if (listRequest.current === controller) {
        listRequest.current = null
        setRefreshing(false)
      }
    }
  }

  function requireAuthentication(message: string): void {
    abortReads()
    hasLoaded.current = false
    activeHistory.current = null
    setDeadlines([])
    setHistoryState({})
    setOpenHistoryId(null)
    setLastUpdatedAt(null)
    setRefreshError('')
    setAuthToken('')
    setAuthRequired(true)
    setAuthError(message)
    setLoadState('ready')
  }

  async function authenticate(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    const candidate = tokenInput.trim()
    if (!candidate) {
      setAuthError('접근 키를 입력해 주세요.')
      return
    }
    setAuthenticating(true)
    setAuthError('')
    const controller = new AbortController()
    listRequest.current?.abort()
    listRequest.current = controller
    try {
      const response = await fetch(`${API_BASE_URL}/deadlines`, {
        headers: authorizedHeaders(candidate, { Accept: 'application/json' }),
        signal: controller.signal,
      })
      if (!response.ok) {
        if (response.status === 401) throw new Error('접근 키가 올바르지 않습니다.')
        throw new Error(await responseMessage(response, '일정 서버에 연결하지 못했습니다.'))
      }
      const body = await response.json() as Deadline[]
      if (controller.signal.aborted) return
      if (!Array.isArray(body)) throw new Error('서버가 올바른 일정 목록을 반환하지 않았습니다.')
      hasLoaded.current = true
      setAuthToken(candidate)
      setTokenInput('')
      setDeadlines(Array.isArray(body) ? body : [])
      setLoadState('ready')
      setAuthRequired(false)
      setAuthError('')
      setLastUpdatedAt(new Date())
      setRefreshError('')
    } catch (error) {
      if (controller.signal.aborted) return
      setAuthError(errorMessage(error, '인증하지 못했습니다.'))
    } finally {
      if (listRequest.current === controller) listRequest.current = null
      setAuthenticating(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    const updateConnection = () => setOnline(navigator.onLine)
    window.addEventListener('online', updateConnection)
    window.addEventListener('offline', updateConnection)
    void loadDeadlines(controller.signal)
    return () => {
      controller.abort()
      listRequest.current?.abort()
      historyRequest.current?.abort()
      window.removeEventListener('online', updateConnection)
      window.removeEventListener('offline', updateConnection)
    }
  }, [])

  useAutoRefresh({
    enabled: hasLoaded.current && !authRequired && !refreshError,
    busy: submitting || cancellingId !== null || (refreshing && !automaticRefresh.current),
    onRefresh: async (signal) => {
      if (mutationBusy.current || listRequest.current) return
      automaticRefresh.current = true
      try {
        await loadDeadlines(signal, true)
      } finally {
        automaticRefresh.current = false
      }
    },
  })

  function updateField(field: keyof DeadlineForm, value: string): void {
    setForm((current) => ({ ...current, [field]: value }))
    setFormError('')
    setNotice('')
  }

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    if (mutationBusy.current) return
    const title = form.title.trim()
    const startsAt = parseSeoulLocalDateTime(form.startsAt)
    const leadMinutes = Number(form.leadMinutes)

    if (!title) {
      setFormError('일정 제목을 입력해 주세요.')
      return
    }
    if (!form.startsAt || Number.isNaN(startsAt.getTime()) || startsAt.getTime() <= Date.now()) {
      setFormError('현재보다 나중인 일정 시각을 선택해 주세요.')
      return
    }
    if (!Number.isInteger(leadMinutes) || leadMinutes < 0 || leadMinutes > 525_600) {
      setFormError('알림 시간은 0분에서 525,600분 사이여야 합니다.')
      return
    }
    if (startsAt.getTime() - leadMinutes * 60_000 <= Date.now()) {
      setFormError('알림 시각이 이미 지났습니다. 더 가까운 알림 시간을 선택해 주세요.')
      return
    }

    // Keep the versions seen when editing began. A background refresh must not
    // silently upgrade an old draft to the server's new optimistic-lock version.
    const editing = editingId ? editingSnapshot.current : undefined
    if (editingId && !editing) {
      setFormError('수정할 일정이 최신 목록에 없습니다. 목록을 다시 불러와 주세요.')
      return
    }
    const payload = editing
      ? {
          title,
          startsAt: startsAt.toISOString(),
          leadMinutes,
          expectedVersion: editing.version,
          expectedEventVersion: editing.eventVersion,
          expectedPolicyVersion: editing.policyVersion,
        }
      : { title, startsAt: startsAt.toISOString(), leadMinutes }
    const fingerprint = `${editing ? `update:${editing.id}` : 'create'}:${JSON.stringify(payload)}`
    const key = pendingSubmission.current?.fingerprint === fingerprint
      ? pendingSubmission.current.key
      : newIdempotencyKey()
    pendingSubmission.current = { fingerprint, key }
    mutationBusy.current = true
    abortReads()
    setSubmitting(true)
    setFormError('')
    setNotice('')

    try {
      const response = await fetch(
        editing ? `${API_BASE_URL}/deadlines/${editing.id}` : `${API_BASE_URL}/deadlines`, {
        method: editing ? 'PUT' : 'POST',
        headers: {
          ...authorizedHeaders(authToken, { Accept: 'application/json' }),
          'Content-Type': 'application/json',
          'Idempotency-Key': key,
        },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        if (response.status === 401) requireAuthentication('접근 키가 만료되었거나 변경되었습니다. 다시 입력해 주세요.')
        const message = await responseMessage(response, '일정을 저장하지 못했습니다.')
        if (response.status === 409) {
          pendingSubmission.current = null
          editingSnapshot.current = null
          setEditingId(null)
          setForm(initialForm)
          await loadDeadlines()
          throw new Error('다른 요청에서 일정이 먼저 변경되었습니다. 최신 내용을 불러왔으니 다시 수정해 주세요.')
        }
        throw new Error(message)
      }
      await response.json()
      pendingSubmission.current = null
      setForm(initialForm)
      setEditingId(null)
      editingSnapshot.current = null
      setNotice(editing ? '일정과 알림 예약을 변경했습니다.' : '일정과 알림을 저장했습니다.')
      await loadDeadlines(undefined, true)
    } catch (error) {
      setFormError(errorMessage(error, '일정을 저장하지 못했습니다.'))
    } finally {
      mutationBusy.current = false
      setSubmitting(false)
    }
  }

  function beginEdit(deadline: Deadline): void {
    if (mutationBusy.current || !canChange(deadline.status)) return
    editingSnapshot.current = { ...deadline }
    setEditingId(deadline.id)
    setForm({
      title: deadline.title,
      startsAt: toSeoulInput(deadline.startsAt),
      leadMinutes: String(deadline.leadMinutes),
    })
    pendingSubmission.current = null
    setCancelConfirmId(null)
    setActionError(null)
    setFormError('')
    setNotice('')
    focusForm()
  }

  function focusForm(): void {
    titleInput.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
    titleInput.current?.focus({ preventScroll: true })
  }

  function stopEditing(): void {
    editingSnapshot.current = null
    setEditingId(null)
    setForm(initialForm)
    pendingSubmission.current = null
    setFormError('')
  }

  async function cancelDeadline(deadline: Deadline): Promise<void> {
    if (mutationBusy.current) return
    const fingerprint = `${deadline.id}:${deadline.version}`
    const key = pendingCancellations.current.get(fingerprint) ?? newIdempotencyKey()
    pendingCancellations.current.set(fingerprint, key)
    mutationBusy.current = true
    abortReads()
    setCancellingId(deadline.id)
    setActionError(null)
    setNotice('')
    try {
      const response = await fetch(`${API_BASE_URL}/deadlines/${deadline.id}/cancel`, {
        method: 'POST',
        headers: {
          ...authorizedHeaders(authToken, { Accept: 'application/json' }),
          'Content-Type': 'application/json',
          'Idempotency-Key': key,
        },
        body: JSON.stringify({ expectedVersion: deadline.version }),
      })
      if (!response.ok) {
        if (response.status === 401) requireAuthentication('접근 키가 만료되었거나 변경되었습니다. 다시 입력해 주세요.')
        const message = await responseMessage(response, '일정을 취소하지 못했습니다.')
        if (response.status === 409) {
          pendingCancellations.current.delete(fingerprint)
          await loadDeadlines()
          throw new Error('일정이 이미 변경되었습니다. 최신 상태를 확인한 뒤 다시 시도해 주세요.')
        }
        throw new Error(message)
      }
      await response.json()
      pendingCancellations.current.delete(fingerprint)
      setCancelConfirmId(null)
      if (editingId === deadline.id) stopEditing()
      setNotice('일정을 취소했습니다. 이미 제공자에 접수된 메일은 회수되지 않을 수 있습니다.')
      await loadDeadlines()
      if (openHistoryId === deadline.id) {
        await toggleHistory(deadline, true)
      }
    } catch (error) {
      setActionError({ id: deadline.id, message: errorMessage(error, '일정을 취소하지 못했습니다.') })
    } finally {
      mutationBusy.current = false
      setCancellingId(null)
    }
  }

  async function toggleHistory(deadline: Deadline, forceReload = false): Promise<void> {
    if (mutationBusy.current && !forceReload) return
    if (!forceReload && openHistoryId === deadline.id) {
      historyRequest.current?.abort()
      activeHistory.current = null
      setOpenHistoryId(null)
      return
    }
    activeHistory.current = deadline.id
    setOpenHistoryId(deadline.id)
    await loadHistory(deadline.id)
  }

  async function loadHistory(id: string, quiet = false, signal?: AbortSignal): Promise<boolean> {
    historyRequest.current?.abort()
    const controller = new AbortController()
    historyRequest.current = controller
    const abort = () => controller.abort()
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) controller.abort()
    setHistoryState((current) => ({ ...current, [id]: quiet && current[id]?.data
      ? { ...current[id], status: 'ready', refreshing: true, message: undefined }
      : { status: 'loading' } }))
    try {
      const response = await fetch(`${API_BASE_URL}/deadlines/${id}/history`, {
        headers: authorizedHeaders(authToken, { Accept: 'application/json' }),
        signal: controller.signal,
      })
      if (controller.signal.aborted) return false
      if (response.status === 401) {
        requireAuthentication('접근 키가 만료되었거나 변경되었습니다. 다시 입력해 주세요.')
        return false
      }
      if (!response.ok) throw new Error(await responseMessage(response, '처리 이력을 불러오지 못했습니다.'))
      const data = await response.json() as DeadlineHistory
      if (controller.signal.aborted) return false
      setHistoryState((current) => ({ ...current, [id]: { status: 'ready', data } }))
      return true
    } catch (error) {
      if (controller.signal.aborted) return false
      const message = errorMessage(error, '처리 이력을 불러오지 못했습니다.')
      setHistoryState((current) => ({
        ...current,
        [id]: quiet && current[id]?.data
          ? { ...current[id], status: 'ready', refreshing: false, message }
          : { status: 'error', message },
      }))
      if (quiet) setRefreshError(`처리 이력 갱신 실패: ${message}`)
      return false
    } finally {
      signal?.removeEventListener('abort', abort)
      if (historyRequest.current === controller) {
        historyRequest.current = null
        setHistoryState((current) => current[id]
          ? { ...current, [id]: { ...current[id], refreshing: false } } : current)
      }
    }
  }

  const upcoming = deadlines.filter((deadline) => deadline.status !== 'CANCELLED' && new Date(deadline.startsAt).getTime() > Date.now())
  const activeCount = upcoming.length
  const scheduledCount = deadlines.filter((deadline) => deadline.status === 'SCHEDULED').length
  const attentionCount = deadlines.filter((deadline) => ['SCHEDULE_FAILED', 'DELIVERY_FAILED', 'DELIVERY_UNKNOWN'].includes(deadline.status)).length
  const waitingCount = deadlines.filter((deadline) => ['CREATED', 'SCHEDULE_PENDING', 'RETRYING'].includes(deadline.status)).length
  const filteredDeadlines = deadlines.filter((deadline) => {
    const matchesView = view === 'all' || (view === 'scheduled' && deadline.status === 'SCHEDULED')
      || (view === 'attention' && ['SCHEDULE_FAILED', 'DELIVERY_FAILED', 'DELIVERY_UNKNOWN'].includes(deadline.status))
      || (view === 'cancelled' && deadline.status === 'CANCELLED')
    return matchesView && deadline.title.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())
      && (!selectedDate || toSeoulInput(deadline.startsAt).slice(0, 10) === selectedDate)
  })
  const todayLabel = new Intl.DateTimeFormat('ko-KR', { timeZone: SEOUL_TIMEZONE, month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  const viewTitles = { all: '내 일정', scheduled: '예약된 일정', attention: '확인할 일정', cancelled: '취소한 일정' }

  function resetFilters(): void {
    setView('all')
    setSearch('')
    setSelectedDate(null)
  }

  if (authRequired) {
    return (
      <main className="auth-shell">
        <header className="hero auth-hero">
          <a className="brand" href="/" aria-label="Reminder 홈"><span className="brand-symbol"><Icon name="sparkle" /></span>remi<span className="brand-dot">.</span></a>
          <div className="auth-illustration"><Icon name="calendar" /></div>
          <div>
            <p className="eyebrow">A LITTLE HELP FOR YOUR DAY</p>
            <h1>소중한 순간을,<br />함께 챙겨요.</h1>
            <p className="hero-copy">기억할 일은 여기에 살짝 맡겨 두세요.<br />조금 더 가벼운 하루가 기다리고 있어요.</p>
          </div>
        </header>
        <section className="panel auth-panel" aria-labelledby="auth-heading">
          <div className="clay-icon clay-icon--lavender"><Icon name="check" /></div>
          <p className="section-kicker">WELCOME BACK</p>
          <h2 id="auth-heading">접근 키 입력</h2>
          <p className="auth-description">나만의 일정 공간으로 들어오세요.</p>
          <form onSubmit={authenticate} noValidate>
            <label>
              본인용 접근 키
              <input
                type="password"
                value={tokenInput}
                onChange={(event) => {
                  setTokenInput(event.target.value)
                  setAuthError('')
                }}
                autoComplete="current-password"
                autoFocus
              />
            </label>
            {authError && <p className="feedback feedback--error" role="alert">{authError}</p>}
            <button className="primary-button" type="submit" disabled={authenticating}>
              {authenticating ? '확인 중…' : '내 일정 열기'}<Icon name="arrow" />
            </button>
          </form>
          <p className="auth-footnote">키는 현재 탭에서만 사용해요.<br />새로고침하면 다시 입력해 주세요.</p>
        </section>
      </main>
    )
  }

  return (
    <div className="dashboard-layout">
      <header className="sidebar" aria-label="주 메뉴">
        <a className="brand" href="/" aria-label="Reminder 홈"><span className="brand-symbol"><Icon name="sparkle" /></span>remi<span className="brand-dot">.</span></a>
        <nav className="sidebar-nav" aria-label="일정 보기">
          <button className={view === 'all' ? 'is-active' : ''} onClick={resetFilters} aria-pressed={view === 'all'}><Icon name="grid" />대시보드</button>
          <button className={view === 'scheduled' ? 'is-active' : ''} onClick={() => { setView('scheduled'); setSelectedDate(null) }} aria-pressed={view === 'scheduled'}><Icon name="calendar" />예약된 일정<span className="nav-count">{scheduledCount}</span></button>
          <button className={view === 'attention' ? 'is-active' : ''} onClick={() => { setView('attention'); setSelectedDate(null) }} aria-pressed={view === 'attention'}><Icon name="bell" />확인할 일정{attentionCount > 0 && <span className="nav-count">{attentionCount}</span>}</button>
          <button className={view === 'cancelled' ? 'is-active' : ''} onClick={() => { setView('cancelled'); setSelectedDate(null) }} aria-pressed={view === 'cancelled'}><Icon name="archive" />취소한 일정</button>
        </nav>
        <button className="sidebar-add" onClick={() => { stopEditing(); focusForm() }}><Icon name="plus" />새 일정 만들기</button>
      </header>
    <main className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">{todayLabel} <span>· 나의 대시보드</span></p>
          <h1>중요한 순간을 위한,<br /><span className="hero-accent">조금 더 여유로운 하루.</span></h1>
          <p className="hero-copy">
            접수부터 예매, 제출까지. 일정을 한곳에 모으고<br className="desktop-break" /> 알림 준비 상황을 간편하게 확인하세요.
          </p>
        </div>
        <div className="header-tools">
          <div className="hero-calendar-mark" aria-hidden="true"><Icon name="calendar" /><span><Icon name="check" /></span></div>
          <p className="hero-tool-caption">계획은 간단하게.<br /><strong>하루는 더 가볍게.</strong></p>
          <button className="primary-button hero-cta" onClick={() => { stopEditing(); focusForm() }} aria-label="새 일정 등록으로 이동">새 일정 등록하기<Icon name="arrow" /></button>
        </div>
      </header>
      <div className="dashboard-status">
        <span><Icon name="grid" />나의 일정 한눈에 보기</span>
        <div className={`connection-pill connection-pill--${refreshError || !online ? 'error' : loadState}`} aria-live="polite">
          <span aria-hidden="true" />
          {!online ? '오프라인 · 이전 목록' : refreshError ? '최신 상태 확인 실패' : refreshing ? '최신 상태 확인 중' : loadState === 'loading' ? '서버 연결 중' : loadState === 'ready' ? '서버 연결됨' : '서버 연결 실패'}
        </div>
      </div>

      <section className="summary-grid" aria-label="일정 요약">
        <article className="summary-card summary-card--lavender">
          <div className="clay-icon clay-icon--lavender"><Icon name="check" /></div>
          <div className="summary-content"><span>다가오는 일정</span>
          <strong>{loadState === 'ready' ? activeCount : '—'}<em>개</em></strong>
          <small>앞으로 예정된 일정</small></div>
        </article>
        <article className="summary-card summary-card--pink">
          <div className="clay-icon clay-icon--pink"><Icon name="calendar" /></div>
          <div className="summary-content"><span>예약 준비</span>
          <strong>{loadState === 'ready' ? waitingCount : '—'}<em>개</em></strong>
          <small>예약 처리 대기 중</small></div>
        </article>
        <article className="summary-card summary-card--mint">
          <div className="clay-icon clay-icon--mint"><Icon name="bell" /></div>
          <div className="summary-content"><span>예약 완료</span>
          <strong>{loadState === 'ready' ? scheduledCount : '—'}<em>개</em></strong>
          <small>알림 예약이 확인됐어요</small></div>
        </article>
        <article className="summary-card summary-card--butter">
          <div className="clay-icon clay-icon--butter"><Icon name="clock" /></div>
          <div className="summary-content"><span>기준 시간대</span>
          <strong className="summary-time">KST</strong>
          <small>서울 · UTC +09:00</small></div>
        </article>
      </section>

      <div className="workspace-grid">
        <section className="panel create-panel" id="new-deadline" aria-labelledby="create-heading">
          <div className="panel-heading">
            <div>
              <p className="section-kicker">NEW DEADLINE</p>
              <h2 id="create-heading">{editingId ? '일정 수정' : '새 일정 등록'}</h2>
            </div>
            <span className="step-badge"><Icon name={editingId ? 'calendar' : 'plus'} /></span>
          </div>

          <form onSubmit={submit} noValidate>
            <label>
              일정 제목
              <input
                ref={titleInput}
                name="title"
                value={form.title}
                onChange={(event) => updateField('title', event.target.value)}
                maxLength={200}
                placeholder="예: 정보처리기사 접수 마감"
                autoComplete="off"
              />
            </label>

            <label>
              일정 시각
              <input
                name="startsAt"
                aria-label="일정 시각"
                type="datetime-local"
                value={form.startsAt}
                onChange={(event) => updateField('startsAt', event.target.value)}
                min={minimumLocalDateTime()}
              />
              <small>입력한 시각은 한국 시간 기준으로 저장됩니다.</small>
            </label>

            <label>
              언제 알려드릴까요?
              <select
                name="leadMinutes"
                value={form.leadMinutes}
                onChange={(event) => updateField('leadMinutes', event.target.value)}
              >
                <option value="10">10분 전</option>
                <option value="30">30분 전</option>
                <option value="60">1시간 전</option>
                <option value="180">3시간 전</option>
                <option value="1440">하루 전</option>
                <option value="10080">일주일 전</option>
              </select>
            </label>

            {formError && <p className="feedback feedback--error" role="alert">{formError}</p>}
            {notice && <p className="feedback feedback--success" role="status">{notice}</p>}

            <div className="form-actions">
              <button className="primary-button" type="submit" disabled={submitting || cancellingId !== null}>
                {submitting ? '저장 중…' : editingId ? '일정 변경 저장' : '일정과 알림 저장'}
                <Icon name="arrow" />
              </button>
              {editingId && (
                <button className="secondary-button" type="button" onClick={stopEditing} disabled={submitting}>
                  수정 닫기
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="panel list-panel" aria-labelledby="list-heading">
          <div className="panel-heading">
            <div>
              <p className="section-kicker">MY SCHEDULE</p>
              <h2 id="list-heading">{viewTitles[view]} <span className="heading-count">{loadState === 'ready' ? filteredDeadlines.length : '—'}</span></h2>
            </div>
            <button className="text-button" type="button"
              disabled={!online || loadState === 'loading' || refreshing || submitting || cancellingId !== null}
              onClick={() => void loadDeadlines(undefined, true)}>
              {refreshing ? '확인 중…' : loadState === 'error' ? '다시 불러오기' : '새로고침'}
            </button>
          </div>
          <label className="search-field"><Icon name="search" /><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="일정 제목으로 검색" aria-label="일정 검색" /></label>
          <div className="list-toolbar"><span>{selectedDate ? `${selectedDate.replaceAll('-', '. ')}의 일정` : '등록한 일정과 알림 상태를 확인하세요.'}</span>{(view !== 'all' || search || selectedDate) && <button className="text-button" onClick={resetFilters}>필터 초기화</button>}</div>
          <div className="refresh-status" aria-live="polite">
            <span>{lastUpdatedAt ? <>마지막 확인 <time dateTime={lastUpdatedAt.toISOString()}>{formatHistoryTime(lastUpdatedAt.toISOString())}</time></> : '아직 확인한 일정이 없습니다.'}</span>
            <span>{!online ? '온라인 복귀 후 다시 확인합니다.' : refreshError ? '자동 갱신 일시 중지' : '화면을 보고 있을 때 30초마다 확인'}</span>
          </div>
          {refreshError && <div className="refresh-warning" role="alert">
            <strong>최신 상태를 확인하지 못했습니다.</strong>
            <p>마지막으로 확인한 목록을 표시하고 있습니다. {refreshError}</p>
            <button className="text-button" type="button" disabled={!online || refreshing || submitting || cancellingId !== null}
              onClick={() => void loadDeadlines(undefined, true)}>다시 확인하고 자동 갱신 재개</button>
          </div>}

          {loadState === 'loading' && <DeadlineSkeleton />}
          {loadState === 'error' && (
            <div className="empty-state empty-state--error" role="alert">
              <strong>목록에 연결하지 못했습니다.</strong>
              <p>{loadError}</p>
            </div>
          )}
          {loadState === 'ready' && filteredDeadlines.length === 0 && (
            <div className="empty-state">
              <div className="empty-icon" aria-hidden="true"><Icon name="calendar" /></div>
              <strong>{deadlines.length === 0 ? '아직 등록한 일정이 없어요.' : '조건에 맞는 일정이 없어요.'}</strong>
              <p>{deadlines.length === 0 ? '기억하고 싶은 첫 번째 순간을 등록해 볼까요?' : '다른 검색어나 날짜를 선택해 주세요.'}</p>
              {deadlines.length === 0 && <button className="text-button" onClick={focusForm}>첫 일정 만들기 <Icon name="arrow" /></button>}
            </div>
          )}
          {loadState === 'ready' && filteredDeadlines.length > 0 && (
            <ol className="deadline-list">
              {filteredDeadlines.map((deadline) => (
                <DeadlineCard
                  key={deadline.id}
                  deadline={deadline}
                  editing={editingId === deadline.id}
                  confirmingCancel={cancelConfirmId === deadline.id}
                  cancelling={cancellingId === deadline.id}
                  actionError={actionError?.id === deadline.id ? actionError.message : ''}
                  historyOpen={openHistoryId === deadline.id}
                  history={historyState[deadline.id]}
                  onEdit={() => beginEdit(deadline)}
                  onAskCancel={() => {
                    setCancelConfirmId(deadline.id)
                    setActionError(null)
                  }}
                  onKeep={() => setCancelConfirmId(null)}
                  onCancel={() => void cancelDeadline(deadline)}
                  onToggleHistory={() => void toggleHistory(deadline)}
                  onRetryHistory={() => void toggleHistory(deadline, true)}
                />
              ))}
            </ol>
          )}
        </section>
        <aside className="dashboard-rail" aria-label="캘린더와 하루 안내">
          <DeadlineCalendar dates={deadlines.filter((deadline) => deadline.status !== 'CANCELLED').map((deadline) => deadline.startsAt)} selectedDate={selectedDate} onSelectDate={(date) => { setSelectedDate(date); setView('all') }} />
          <section className="panel gentle-reminder"><div className="gentle-title"><span className="mini-clay"><Icon name="bell" /></span><h2>잠깐, 기억해 주세요</h2></div><p>일정을 등록한 뒤 <strong>처리 이력</strong>에서<br />알림이 준비되었는지 확인할 수 있어요.</p><span className="gentle-footnote">예약과 발송 상태를 한눈에</span></section>
        </aside>
      </div>
      <footer className="page-footer"><span>remi. <span>Deadline Companion</span></span><span>중요한 순간에 집중할 수 있도록 · 모든 일정은 한국 시간 기준</span></footer>
    </main>
    </div>
  )
}

type DeadlineCardProps = {
  deadline: Deadline
  editing: boolean
  confirmingCancel: boolean
  cancelling: boolean
  actionError: string
  historyOpen: boolean
  history?: HistoryState
  onEdit: () => void
  onAskCancel: () => void
  onKeep: () => void
  onCancel: () => void
  onToggleHistory: () => void
  onRetryHistory: () => void
}

function DeadlineCard({
  deadline,
  editing,
  confirmingCancel,
  cancelling,
  actionError,
  historyOpen,
  history,
  onEdit,
  onAskCancel,
  onKeep,
  onCancel,
  onToggleHistory,
  onRetryHistory,
}: DeadlineCardProps) {
  const cancelled = deadline.status === 'CANCELLED'
  const mutable = canChange(deadline.status)
  return (
    <li className={`deadline-card${cancelled ? ' deadline-card--cancelled' : ''}${editing ? ' deadline-card--editing' : ''}`}>
      <div className="deadline-date" aria-hidden="true">
        <span>{formatMonth(deadline.startsAt)}</span>
        <strong>{formatDay(deadline.startsAt)}</strong>
      </div>
      <div className="deadline-content">
        <div className="deadline-title-row">
          <h3>{deadline.title}</h3>
          <span className={`status-badge status-badge--${statusTone(deadline)}`}>
            {statusLabel(deadline)}
          </span>
        </div>
        <p>{formatDateTime(deadline.startsAt)}</p>
        <div className="deadline-meta">
          <span>{dDayLabel(deadline.startsAt)}</span>
          <span>{leadLabel(deadline.leadMinutes)} 알림</span>
          <span>알림 {formatDateTime(deadline.remindAt)}</span>
        </div>
        {!confirmingCancel && (
          <div className="card-actions">
            <button type="button" onClick={onToggleHistory} aria-expanded={historyOpen}>
              {historyOpen ? '이력 닫기' : '처리 이력'}
            </button>
            {mutable && <button type="button" onClick={onEdit} aria-label={`${deadline.title} 수정`}>수정</button>}
            {mutable && <button className="danger-link" type="button" onClick={onAskCancel} aria-label={`${deadline.title} 취소`}>취소</button>}
          </div>
        )}
        {mutable && confirmingCancel && (
          <div className="cancel-confirm" role="group" aria-label={`${deadline.title} 취소 확인`}>
            <p>이 일정과 앞으로의 예약을 취소할까요?</p>
            <div>
              <button className="danger-button" type="button" onClick={onCancel} disabled={cancelling}>
                {cancelling ? '취소 중…' : '취소 확정'}
              </button>
              <button type="button" onClick={onKeep} disabled={cancelling}>돌아가기</button>
            </div>
          </div>
        )}
        {actionError && <p className="card-error" role="alert">{actionError}</p>}
        {historyOpen && <HistoryPanel state={history} onRetry={onRetryHistory} />}
      </div>
    </li>
  )
}

function HistoryPanel({ state, onRetry }: { state?: HistoryState; onRetry: () => void }) {
  if (!state || state.status === 'loading') {
    return <div className="history-panel" role="status">처리 이력을 불러오는 중…</div>
  }
  if (state.status === 'error') {
    return (
      <div className="history-panel history-panel--error" role="alert">
        <p>{state.message}</p>
        <button type="button" onClick={onRetry}>다시 불러오기</button>
      </div>
    )
  }
  if (!state.data) return null
  return (
    <div className="history-panel">
      {state.refreshing && <p role="status">처리 이력 확인 중…</p>}
      {state.message && <p className="card-error" role="alert">이전 이력을 표시하고 있습니다. {state.message}</p>}
      <div className={`delivery-mode delivery-mode--${state.data.deliveryMode.toLowerCase()}`}>
        <strong>{deliveryModeLabel(state.data.deliveryMode)}</strong>
        <p>{state.data.deliveryModeDetail}</p>
      </div>
      <ol className="history-list">
        {state.data.entries.map((entry) => (
          <li key={entry.id}>
            <span className={`history-dot history-dot--${historyTone(entry.status)}`} aria-hidden="true" />
            <div>
              <div className="history-title-row">
                <strong>{historyLabel(entry)}</strong>
                <time dateTime={entry.occurredAt}>{formatHistoryTime(entry.occurredAt)}</time>
              </div>
              <p>{entry.detail}</p>
              {entry.providerReference && <small>제공자 참조: {entry.providerReference}</small>}
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}

function DeadlineSkeleton() {
  return (
    <div className="skeleton-list" aria-label="일정 불러오는 중">
      {[0, 1, 2].map((item) => <div className="skeleton-row" key={item} />)}
    </div>
  )
}

async function responseMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json() as { detail?: string; message?: string; error?: string }
    return body.detail || body.message || body.error || fallback
  } catch {
    return fallback
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `deadline-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function authorizedHeaders(token: string, base: Record<string, string>): Record<string, string> {
  return token ? { ...base, Authorization: `Bearer ${token}` } : base
}

function minimumLocalDateTime(): string {
  return new Date(Date.now() + 9 * 60 * 60_000 + 60_000).toISOString().slice(0, 16)
}

function parseSeoulLocalDateTime(value: string): Date {
  return value ? new Date(`${value}:00+09:00`) : new Date(Number.NaN)
}

function toSeoulInput(value: string): string {
  return new Date(new Date(value).getTime() + 9 * 60 * 60_000).toISOString().slice(0, 16)
}

function canChange(status: string): boolean {
  return ['CREATED', 'SCHEDULE_PENDING', 'SCHEDULED', 'SCHEDULE_FAILED', 'DELIVERY_FAILED', 'RETRYING']
    .includes(status)
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL_TIMEZONE,
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatMonth(value: string): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: SEOUL_TIMEZONE, month: 'short' })
    .format(new Date(value)).toUpperCase()
}

function formatDay(value: string): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: SEOUL_TIMEZONE, day: '2-digit' }).format(new Date(value))
}

function dDayLabel(value: string): string {
  const days = Math.ceil((new Date(value).getTime() - Date.now()) / 86_400_000)
  if (days < 0) return '지난 일정'
  if (days === 0) return 'D-Day'
  return `D-${days}`
}

function leadLabel(minutes: number): string {
  if (minutes === 0) return '정시'
  if (minutes % 10_080 === 0) return `${minutes / 10_080}주 전`
  if (minutes % 1_440 === 0) return `${minutes / 1_440}일 전`
  if (minutes % 60 === 0) return `${minutes / 60}시간 전`
  return `${minutes}분 전`
}

function statusLabel(deadline: Deadline): string {
  if (deadline.status === 'CANCELLED') return '취소됨'
  if (deadline.status === 'DELIVERED' || deadline.status === 'ACKNOWLEDGED') return '발송 완료'
  if (deadline.status === 'DELIVERY_FAILED' || deadline.status === 'SCHEDULE_FAILED') return '확인 필요'
  if (deadline.status === 'DELIVERY_UNKNOWN') return '발송 결과 불명확'
  if (deadline.status === 'SCHEDULED') return '예약 완료'
  return deadline.scheduleStatus === 'PENDING' ? '예약 준비 중' : '처리 중'
}

function statusTone(deadline: Deadline): string {
  if (deadline.status === 'CANCELLED') return 'muted'
  if (deadline.status === 'DELIVERED' || deadline.status === 'ACKNOWLEDGED') return 'success'
  if (deadline.status === 'DELIVERY_FAILED' || deadline.status === 'SCHEDULE_FAILED' || deadline.status === 'DELIVERY_UNKNOWN') return 'danger'
  if (deadline.status === 'SCHEDULED') return 'success'
  return 'pending'
}

function deliveryModeLabel(mode: DeadlineHistory['deliveryMode']): string {
  if (mode === 'ACTIVE') return '실제 서버 알림 활성화'
  if (mode === 'PARTIAL') return '알림 구성 일부 활성화'
  return '외부 알림 비활성화'
}

function historyLabel(entry: HistoryEntry): string {
  if (entry.kind === 'DEADLINE') return `현재 상태 · ${entry.status}`
  if (entry.kind === 'SCHEDULE') {
    const action = entry.action === 'DELETE' ? '예약 취소' : '예약 생성·변경'
    return `${action} · ${entry.status}`
  }
  const labels: Record<string, string> = {
    PROVIDER_ACCEPTED: '발송 제공자 요청 수락',
    OUTCOME_UNKNOWN: '발송 결과 불명확',
    RETRYABLE_PROVIDER: '제공자 연결 재시도 대기',
    PROVIDER_FAILURE: '발송 요청 거부',
    DELIVERY_FAILED: '발송 실패',
    STARTED: '발송 처리 중',
  }
  return labels[entry.status] ?? `발송 · ${entry.status}`
}

function historyTone(status: string): string {
  if (['PROVIDER_ACCEPTED', 'SUCCEEDED', 'DELIVERED', 'ACKNOWLEDGED'].includes(status)) return 'success'
  if (['FAILED', 'PROVIDER_FAILURE', 'DELIVERY_FAILED', 'OUTCOME_UNKNOWN'].includes(status)) return 'danger'
  return 'pending'
}

function formatHistoryTime(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL_TIMEZONE,
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export default App
