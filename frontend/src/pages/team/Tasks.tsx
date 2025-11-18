import { useEffect, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'
import type { StompSubscription } from '@stomp/stompjs'
import { createStompClient, safeJsonParse } from '../../lib/ws'
import type { TaskEventMessage } from '../../lib/ws'

type Task = { id: number; title: string; dueAt?: string; priority: number; durationMin: number }
type Member = { userId: number; userName: string; userEmail: string; role: string }

export default function Tasks() {
  const { id } = useParams()
  const [list, setList] = useState<Task[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [newTaskIds, setNewTaskIds] = useState<Set<number>>(new Set())
  const [formData, setFormData] = useState({
    title: '',
    startAt: '',
    dueAt: '',
    priority: 3,
    assigneeId: undefined as number | undefined,
    splittable: true,
    tags: '',
    recurrenceEnabled: false,
    recurrenceType: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY',
    recurrenceEndDate: ''
  })

  const loadTasks = () => {
    if (id) {
      api.get(`/api/tasks/team/${id}`).then(r => setList(r.data)).catch(console.error)
    }
  }

  const loadMembers = () => {
    if (id) {
      api.get(`/api/teams/${id}/members`).then(r => setMembers(r.data)).catch(console.error)
    }
  }

  useEffect(() => {
    loadTasks()
    loadMembers()
  }, [id])

  useEffect(() => {
    if (!id) return
    const teamId = Number(id)
    const client = createStompClient()
    const subscriptions: StompSubscription[] = []
    setWsStatus('connecting')

    // 연결 타임아웃 (10초)
    const connectTimeout = setTimeout(() => {
      if (wsStatusRef.current === 'connecting') {
        console.error('[Tasks] WebSocket connection timeout after 10 seconds')
        setWsStatus('disconnected')
        client.deactivate()
      }
    }, 10000)

    client.onConnect = () => {
      clearTimeout(connectTimeout)
      console.log('[Tasks] WebSocket connected, subscribing to /topic/tasks/' + teamId)
      setWsStatus('connected')
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0
      subscriptions.push(
        client.subscribe(`/topic/tasks/${teamId}`, (message) => {
          const payload = safeJsonParse<TaskEventMessage>(message.body)
          if (!payload) {
            console.warn('[Tasks] Failed to parse task event:', message.body)
            return
          }
          console.log('[Tasks] Received task event:', payload)
          setWsMessageCount(prev => prev + 1)
          setList((prev) => {
            if (payload.action === 'DELETED' && payload.taskId) {
              setNewTaskIds((ids) => {
                const next = new Set(ids)
                next.delete(payload.taskId!)
                return next
              })
              return prev.filter((task) => task.id !== payload.taskId)
            }
            if (!payload.task) return prev
            const nextTask: Task = {
              id: payload.task.id,
              title: payload.task.title,
              durationMin: payload.task.durationMin,
              dueAt: payload.task.dueAt ?? undefined,
              priority: payload.task.priority ?? 3
            }
            const exists = prev.some((task) => task.id === nextTask.id)
            if (exists) {
              return prev.map((task) => (task.id === nextTask.id ? nextTask : task))
            }
            // 새 작업이 추가될 때 하이라이트 효과를 위해 ID 저장
            if (payload.action === 'CREATED') {
              setNewTaskIds((ids) => new Set([...ids, nextTask.id]))
              // 3초 후 하이라이트 제거
              setTimeout(() => {
                setNewTaskIds((ids) => {
                  const next = new Set(ids)
                  next.delete(nextTask.id)
                  return next
                })
              }, 3000)
            }
            return [nextTask, ...prev]
          })
        })
      )
    }

    client.onStompError = (frame) => {
      clearTimeout(connectTimeout)
      console.error('[Tasks] STOMP error:', frame.headers['message'], frame.body)
      setWsStatus('disconnected')
    }

    client.onWebSocketError = (event) => {
      clearTimeout(connectTimeout)
      console.error('[Tasks] WebSocket error:', event)
      setWsStatus('disconnected')
    }

    client.onDisconnect = () => {
      clearTimeout(connectTimeout)
      console.log('[Tasks] WebSocket disconnected')
      setWsStatus('disconnected')
    }

    client.activate()

    return () => {
      clearTimeout(connectTimeout)
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
      setWsStatus('disconnected')
      setWsMessageCount(0)
    }
  }, [id])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!id) return

    try {
      // 시작 시간과 마감 시간을 기반으로 durationMin 계산
      let durationMin = 60 // 기본값
      if (formData.startAt && formData.dueAt) {
        const start = new Date(formData.startAt)
        const end = new Date(formData.dueAt)
        if (end > start) {
          durationMin = Math.round((end.getTime() - start.getTime()) / (1000 * 60))
        } else {
          alert('마감 시간은 시작 시간보다 늦어야 합니다.')
          return
        }
      } else if (formData.dueAt) {
        // 마감 시간만 있는 경우, 기본 소요 시간 사용
        durationMin = 60
      }

      const payload: any = {
        teamId: Number(id),
        title: formData.title,
        durationMin: durationMin,
        dueAt: formData.dueAt ? new Date(formData.dueAt).toISOString() : null,
        priority: formData.priority,
        assigneeId: formData.assigneeId || null,
        splittable: formData.splittable,
        tags: formData.tags || null
      }
      
      if (formData.recurrenceEnabled) {
        payload.recurrenceType = formData.recurrenceType
        payload.recurrenceEndDate = formData.recurrenceEndDate 
          ? new Date(formData.recurrenceEndDate).toISOString() 
          : null
      }
      const response = await api.post('/api/tasks', payload)
      console.log('[Tasks] Task created:', response.data)
      setModalOpen(false)
      setFormData({
        title: '',
        startAt: '',
        dueAt: '',
        priority: 3,
        assigneeId: undefined,
        splittable: true,
        tags: '',
        recurrenceEnabled: false,
        recurrenceType: 'WEEKLY',
        recurrenceEndDate: ''
      })
      // WebSocket으로 다른 팀원들에게는 자동으로 전송되지만,
      // 작업 생성자에게도 즉시 반영되도록 목록 새로고침
      loadTasks()
    } catch (error: any) {
      alert(error.response?.data?.message || '작업 추가에 실패했습니다.')
    }
  }

  const [wsStatus, setWsStatus] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const [wsMessageCount, setWsMessageCount] = useState(0)
  const wsStatusRef = useRef<'connecting' | 'connected' | 'disconnected'>('disconnected')

  useEffect(() => {
    wsStatusRef.current = wsStatus
  }, [wsStatus])

  return (
    <div className="p-6">
      <div className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2 text-sm text-gray-600">
            <div className={`w-2 h-2 rounded-full ${
              wsStatus === 'connected' ? 'bg-green-500' : 
              wsStatus === 'connecting' ? 'bg-yellow-500 animate-pulse' : 
              'bg-red-500'
            }`} />
            <span>
              {wsStatus === 'connected' ? '연결됨' : 
               wsStatus === 'connecting' ? '연결 중...' : 
               '연결 안됨'}
            </span>
            {wsStatus === 'connected' && wsMessageCount > 0 && (
              <span className="text-gray-500">({wsMessageCount}개 메시지 수신)</span>
            )}
          </div>
          <button
            onClick={() => setModalOpen(true)}
            className="px-4 py-2.5 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium"
          >
            작업 추가
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="bg-gradient-to-r from-gray-50 to-gray-100">
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">제목</th>
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">소요시간(분)</th>
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">마감</th>
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">우선순위</th>
              </tr>
            </thead>
            <tbody>
              {list.length === 0 ? (
                <tr>
                  <td colSpan={4} className="p-8 text-center text-gray-500 bg-gray-50">
                    작업이 없습니다
                  </td>
                </tr>
              ) : (
                list.map(t => (
                  <tr 
                    key={t.id}
                    className={`hover:bg-blue-50 transition-colors ${
                      newTaskIds.has(t.id) ? 'bg-green-50 animate-pulse' : ''
                    }`}
                  >
                    <td className="p-3 border-b border-gray-100">
                      {newTaskIds.has(t.id) && (
                        <span className="inline-block mr-2 px-2 py-0.5 text-xs bg-green-500 text-white rounded-full font-medium">
                          새 작업
                        </span>
                      )}
                      <span className="font-medium text-gray-900">{t.title}</span>
                    </td>
                    <td className="p-3 border-b border-gray-100 text-gray-700">{t.durationMin}분</td>
                    <td className="p-3 border-b border-gray-100 text-gray-700">
                      {t.dueAt ? new Date(t.dueAt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : '-'}
                    </td>
                    <td className="p-3 border-b border-gray-100">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                        t.priority <= 2 
                          ? 'bg-red-100 text-red-700' 
                          : t.priority === 3
                          ? 'bg-yellow-100 text-yellow-700'
                          : 'bg-gray-100 text-gray-700'
                      }`}>
                        {t.priority}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
          <div className="absolute inset-0" onClick={() => setModalOpen(false)} />
          <div className="relative z-10 w-full max-w-2xl bg-white rounded-xl shadow-2xl border border-gray-200 max-h-[90vh] overflow-y-auto mx-4">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-xl font-bold text-gray-900">작업 추가</h3>
              <button
                onClick={() => setModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 text-2xl font-bold"
              >
                ×
              </button>
            </div>
            <div className="p-6">
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">제목 *</label>
                <input
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="작업 제목을 입력하세요"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">시작 날짜/시간 *</label>
                  <input
                    type="datetime-local"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    value={formData.startAt}
                    onChange={(e) => setFormData({ ...formData, startAt: e.target.value })}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">마감 날짜/시간 *</label>
                  <input
                    type="datetime-local"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    value={formData.dueAt}
                    onChange={(e) => setFormData({ ...formData, dueAt: e.target.value })}
                    required
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">우선순위 (1-5)</label>
                  <input
                    type="number"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    min="1"
                    max="5"
                    value={formData.priority}
                    onChange={(e) => setFormData({ ...formData, priority: Number(e.target.value) })}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">담당자 (선택)</label>
                  <select
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    value={formData.assigneeId || ''}
                    onChange={(e) => setFormData({ ...formData, assigneeId: e.target.value ? Number(e.target.value) : undefined })}
                  >
                    <option value="">담당자 선택</option>
                    {members.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.userName} ({member.userEmail})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">태그 (선택)</label>
                <input
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="태그를 입력하세요"
                  value={formData.tags}
                  onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="splittable"
                  checked={formData.splittable}
                  onChange={(e) => setFormData({ ...formData, splittable: e.target.checked })}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <label htmlFor="splittable" className="text-sm text-gray-700">분할 가능</label>
              </div>
              
              {/* 반복 작업 옵션 */}
              <div className="border-t border-gray-200 pt-4">
                <label className="flex items-center gap-2 mb-3">
                  <input
                    type="checkbox"
                    checked={formData.recurrenceEnabled}
                    onChange={(e) => setFormData({ ...formData, recurrenceEnabled: e.target.checked })}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm font-medium text-gray-700">반복 작업</span>
                </label>
                
                {formData.recurrenceEnabled && (
                  <div className="ml-6 space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">반복 주기</label>
                      <select
                        value={formData.recurrenceType}
                        onChange={(e) => setFormData({ ...formData, recurrenceType: e.target.value as any })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      >
                        <option value="DAILY">매일</option>
                        <option value="WEEKLY">매주</option>
                        <option value="MONTHLY">매월</option>
                        <option value="YEARLY">매년</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">반복 종료일 (선택사항)</label>
                      <input
                        type="datetime-local"
                        value={formData.recurrenceEndDate}
                        onChange={(e) => setFormData({ ...formData, recurrenceEndDate: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      />
                      <p className="text-xs text-gray-500 mt-1">비워두면 1년 후까지 반복됩니다</p>
                    </div>
                  </div>
                )}
              </div>
              
              <div className="flex items-center justify-end gap-2 pt-4 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition"
                >
                  취소
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-white bg-gradient-to-r from-blue-600 to-purple-600 rounded-lg hover:from-blue-700 hover:to-purple-700 transition shadow-md hover:shadow-lg font-medium"
                >
                  저장
                </button>
              </div>
            </form>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}


