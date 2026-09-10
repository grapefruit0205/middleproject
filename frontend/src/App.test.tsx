import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import App from './App'

const deadline = {
  id: 'd-1',
  eventId: 'e-1',
  policyId: 'p-1',
  title: '자격증 접수 마감',
  startsAt: '2035-06-12T18:00:00+09:00',
  leadMinutes: 1440,
  remindAt: '2035-06-11T18:00:00+09:00',
  status: 'CREATED',
  scheduleStatus: 'PENDING',
  version: 0,
  eventVersion: 0,
  policyVersion: 0,
}

function response(body: unknown, ok = true, status = ok ? 200 : 500): Response {
  return { ok, status, json: vi.fn().mockResolvedValue(body) } as unknown as Response
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([])))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('loads an empty dashboard and exposes the registration form', async () => {
    render(<App />)
    expect(screen.getByLabelText('일정 불러오는 중')).toBeInTheDocument()
    expect(await screen.findByText('아직 등록한 일정이 없어요.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(screen.getByLabelText('일정 제목')).toBeInTheDocument()
    expect(screen.getByText('서버 연결됨')).toBeInTheDocument()
    expect(screen.queryByLabelText('본인용 접근 키')).not.toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls[0][1]?.headers).not.toHaveProperty('Authorization')
  })

  it('renders persisted deadlines and their reminder metadata', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([deadline])))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '자격증 접수 마감' })).toBeInTheDocument()
    expect(screen.getByText('1일 전 알림')).toBeInTheDocument()
    expect(screen.getByText('예약 준비 중')).toBeInTheDocument()
    expect(screen.getByText('다가오는 일정').closest('article')).toHaveTextContent('1개')
  })

  it('submits one aggregate request and reloads the persisted list', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([]))
      .mockResolvedValueOnce(response(deadline))
      .mockResolvedValueOnce(response([deadline]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('아직 등록한 일정이 없어요.')

    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: '자격증 접수 마감' } })
    fireEvent.change(screen.getByLabelText('일정 시각'), { target: { value: '2035-06-12T18:00' } })
    fireEvent.change(screen.getByLabelText('언제 알려드릴까요?'), { target: { value: '1440' } })
    fireEvent.click(screen.getByRole('button', { name: /일정과 알림 저장/ }))

    expect(await screen.findByText('일정과 알림을 저장했습니다.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '자격증 접수 마감' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const [, options] = fetchMock.mock.calls[1]
    expect(options.method).toBe('POST')
    expect(options.headers['Idempotency-Key']).toBeTruthy()
    expect(JSON.parse(options.body)).toMatchObject({ title: '자격증 접수 마감', leadMinutes: 1440 })
  })

  it('keeps invalid reminder input local instead of calling create', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response([]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('아직 등록한 일정이 없어요.')

    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: '이미 지난 일정' } })
    fireEvent.change(screen.getByLabelText('일정 시각'), { target: { value: '2020-01-01T10:00' } })
    fireEvent.click(screen.getByRole('button', { name: /일정과 알림 저장/ }))

    expect(screen.getByRole('alert')).toHaveTextContent('현재보다 나중인 일정 시각')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows a real load failure and can retry', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ detail: 'database unavailable' }, false))
      .mockResolvedValueOnce(response([]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByText('database unavailable')).toBeInTheDocument()
    expect(screen.getByText('서버 연결 실패')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    expect(await screen.findByText('아직 등록한 일정이 없어요.')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  })

  it('edits an aggregate with all optimistic versions and reloads it', async () => {
    const updated = { ...deadline, title: '변경된 접수 마감', version: 1, eventVersion: 1, policyVersion: 1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response(updated))
      .mockResolvedValueOnce(response([updated]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.click(screen.getByRole('button', { name: `${deadline.title} 수정` }))
    expect(screen.getByRole('heading', { name: '일정 수정' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: updated.title } })
    fireEvent.click(screen.getByRole('button', { name: /일정 변경 저장/ }))

    expect(await screen.findByText('일정과 알림 예약을 변경했습니다.')).toBeInTheDocument()
    const [, options] = fetchMock.mock.calls[1]
    expect(options.method).toBe('PUT')
    expect(JSON.parse(options.body)).toMatchObject({
      title: updated.title,
      expectedVersion: 0,
      expectedEventVersion: 0,
      expectedPolicyVersion: 0,
    })
    expect(await screen.findByRole('heading', { name: updated.title })).toBeInTheDocument()
  })

  it('requires an explicit confirmation and then keeps a cancelled card', async () => {
    const cancelled = { ...deadline, status: 'CANCELLED', version: 1, scheduleStatus: 'PENDING' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response(cancelled))
      .mockResolvedValueOnce(response([cancelled]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.click(screen.getByRole('button', { name: `${deadline.title} 취소` }))
    expect(screen.getByText('이 일정과 앞으로의 예약을 취소할까요?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '취소 확정' }))

    expect(await screen.findByText(/일정을 취소했습니다/)).toBeInTheDocument()
    expect(await screen.findByText('취소됨')).toBeInTheDocument()
    const [, options] = fetchMock.mock.calls[1]
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ expectedVersion: 0 })
  })

  it('refreshes an open history panel after cancellation', async () => {
    const cancelled = { ...deadline, status: 'CANCELLED', version: 1, scheduleStatus: 'PENDING' }
    const pendingHistory = {
      reminderId: deadline.id,
      deliveryMode: 'DISABLED',
      deliveryModeDetail: '외부 알림이 비활성화되어 있습니다.',
      entries: [{
        id: 'deadline-before-cancel',
        kind: 'DEADLINE',
        action: 'STATE',
        status: 'CREATED',
        occurredAt: '2035-06-01T09:00:00Z',
        completedAt: null,
        detail: '현재 저장된 일정·알림 상태입니다.',
        providerReference: null,
        attempts: 0,
      }],
    }
    const cancelledHistory = {
      ...pendingHistory,
      entries: [{
        ...pendingHistory.entries[0],
        id: 'deadline-after-cancel',
        status: 'CANCELLED',
        occurredAt: '2035-06-01T09:01:00Z',
        detail: '일정이 취소되어 이후 예약 메시지는 현재 상태와 버전으로 차단됩니다.',
      }],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response(pendingHistory))
      .mockResolvedValueOnce(response(cancelled))
      .mockResolvedValueOnce(response([cancelled]))
      .mockResolvedValueOnce(response(cancelledHistory))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.click(screen.getByRole('button', { name: '처리 이력' }))
    expect(await screen.findByText('현재 상태 · CREATED')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: `${deadline.title} 취소` }))
    fireEvent.click(screen.getByRole('button', { name: '취소 확정' }))

    expect(await screen.findByText('현재 상태 · CANCELLED')).toBeInTheDocument()
    expect(screen.getByText(/이후 예약 메시지는 현재 상태와 버전으로 차단/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(5)
  })

  it('does not overwrite a conflict and reloads the newest server value', async () => {
    const newest = { ...deadline, title: '다른 곳에서 바뀐 일정', version: 2, eventVersion: 1, policyVersion: 1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response({ detail: 'conflict' }, false, 409))
      .mockResolvedValueOnce(response([newest]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.click(screen.getByRole('button', { name: `${deadline.title} 수정` }))
    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: '내가 덮어쓸 제목' } })
    fireEvent.click(screen.getByRole('button', { name: /일정 변경 저장/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('다른 요청에서 일정이 먼저 변경')
    expect(await screen.findByRole('heading', { name: newest.title })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '새 일정 등록' })).toBeInTheDocument()
  })

  it('shows persisted delivery history and keeps provider acceptance distinct from receipt', async () => {
    const history = {
      reminderId: deadline.id,
      deliveryMode: 'DISABLED',
      deliveryModeDetail: '외부 예약과 이메일 발송이 비활성화되어 있습니다.',
      entries: [
        {
          id: 'attempt-1',
          kind: 'NOTIFICATION',
          action: 'EMAIL',
          status: 'PROVIDER_ACCEPTED',
          occurredAt: '2035-06-11T09:00:00Z',
          completedAt: '2035-06-11T09:00:01Z',
          detail: '이메일 제공자가 발송 요청을 수락했습니다. 실제 수신이나 열람과는 다릅니다.',
          providerReference: 'provider-reference',
          attempts: 1,
        },
      ],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response(history))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.click(screen.getByRole('button', { name: '처리 이력' }))

    expect(await screen.findByText('외부 알림 비활성화')).toBeInTheDocument()
    expect(screen.getByText('발송 제공자 요청 수락')).toBeInTheDocument()
    expect(screen.getByText(/실제 수신이나 열람과는 다릅니다/)).toBeInTheDocument()
    expect(screen.getByText('제공자 참조: provider-reference')).toBeInTheDocument()
  })

  it('keeps the owner token in memory and retries the API with bearer authentication', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ detail: 'unauthorized' }, false, 401))
      .mockResolvedValueOnce(response([deadline]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByRole('heading', { name: '접근 키 입력' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('본인용 접근 키'), { target: { value: 'owner-secret-token' } })
    fireEvent.click(screen.getByRole('button', { name: /내 일정 열기/ }))

    expect(await screen.findByRole('heading', { name: deadline.title })).toBeInTheDocument()
    const [, options] = fetchMock.mock.calls[1]
    expect(options.headers.Authorization).toBe('Bearer owner-secret-token')
    expect(screen.queryByLabelText('본인용 접근 키')).not.toBeInTheDocument()
  })

  it('filters the saved list by search and sidebar state without changing data', async () => {
    const scheduled = { ...deadline, id: 'd-2', title: '콘서트 예매 시작', status: 'SCHEDULED' }
    const fetchMock = vi.fn().mockResolvedValue(response([deadline, scheduled]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })

    fireEvent.change(screen.getByLabelText('일정 검색'), { target: { value: '콘서트' } })
    expect(screen.queryByRole('heading', { name: deadline.title })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: scheduled.title })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('일정 검색'), { target: { value: '없는 검색어' } })
    expect(screen.getByText('조건에 맞는 일정이 없어요.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '필터 초기화' }))
    fireEvent.click(screen.getByRole('button', { name: /예약된 일정/ }))
    expect(screen.queryByRole('heading', { name: deadline.title })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: scheduled.title })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '대시보드' }))
    expect(screen.getByRole('heading', { name: deadline.title })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('refreshes quietly while keeping the current form draft and list visible', async () => {
    let finishRefresh!: (value: Response) => void
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { finishRefresh = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })
    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: '작성 중인 새 일정' } })
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(screen.getByRole('heading', { name: deadline.title })).toBeInTheDocument()
    expect(screen.queryByLabelText('일정 불러오는 중')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인 중…' })).toBeDisabled()
    finishRefresh(response([{ ...deadline, status: 'SCHEDULED', version: 1 }]))
    await waitFor(() => expect(screen.getByRole('heading', { name: deadline.title }).closest('li')).toHaveTextContent('예약 완료'))
    expect(screen.getByLabelText('일정 제목')).toHaveValue('작성 중인 새 일정')
    expect(screen.getByText(/마지막 확인/)).toBeInTheDocument()
  })

  it('retains stale data on refresh failure and resumes only after successful retry', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockRejectedValueOnce(new Error('연결 끊김'))
      .mockResolvedValueOnce(response([{ ...deadline, status: 'SCHEDULED' }]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('마지막으로 확인한 목록')
    expect(screen.getByRole('heading', { name: deadline.title })).toBeInTheDocument()
    expect(screen.getByText('자동 갱신 일시 중지')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 확인하고 자동 갱신 재개' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: deadline.title }).closest('li')).toHaveTextContent('예약 완료'))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps the edit baseline versions when a refresh observes someone else\'s changes', async () => {
    const newest = { ...deadline, title: '다른 사람이 수정한 일정', version: 2, eventVersion: 1, policyVersion: 1 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response([newest]))
      .mockResolvedValueOnce(response({ detail: 'conflict' }, false, 409))
      .mockResolvedValueOnce(response([newest]))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })
    fireEvent.click(screen.getByRole('button', { name: `${deadline.title} 수정` }))
    fireEvent.change(screen.getByLabelText('일정 제목'), { target: { value: '내 수정 초안' } })
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }))
    await screen.findByRole('heading', { name: newest.title })
    expect(screen.getByLabelText('일정 제목')).toHaveValue('내 수정 초안')
    fireEvent.click(screen.getByRole('button', { name: /일정 변경 저장/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('다른 요청에서 일정이 먼저 변경')
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toMatchObject({
      expectedVersion: 0, expectedEventVersion: 0, expectedPolicyVersion: 0,
    })
  })

  it('refreshes the expanded history together with the list', async () => {
    const history = { reminderId: deadline.id, deliveryMode: 'DISABLED', deliveryModeDetail: '외부 발송 꺼짐', entries: [] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response(history))
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response({ ...history, deliveryModeDetail: '갱신된 서버 설정' }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })
    fireEvent.click(screen.getByRole('button', { name: '처리 이력' }))
    await screen.findByText('외부 발송 꺼짐')
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByText('갱신된 서버 설정')).toBeInTheDocument()
    expect(fetchMock.mock.calls[3][0]).toBe('/api/deadlines/d-1/history')
  })

  it('clears previously loaded data when protected-server authentication expires', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([deadline]))
      .mockResolvedValueOnce(response({ detail: 'unauthorized' }, false, 401))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByRole('heading', { name: deadline.title })
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByRole('heading', { name: '접근 키 입력' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: deadline.title })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
