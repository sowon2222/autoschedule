import { useState, useEffect } from 'react'
import api from '../lib/api'

type TaskResponse = {
  id: number
  teamId: number
  teamName?: string
  assigneeId?: number
  assigneeName?: string
  title: string
  durationMin: number
  dueAt?: string
  priority: number
  splittable: boolean
  tags?: string
  createdAt: string
  updatedAt: string
}

type CalendarEventResponse = {
  id: number
  teamId: number
  teamName?: string
  ownerId?: number
  ownerName?: string
  title: string
  startsAt: string
  endsAt: string
  fixed: boolean
  location?: string
  attendees?: string
  notes?: string
  recurrenceType?: string
  recurrenceEndDate?: string
  createdAt: string
  updatedAt: string
}

type AssignmentResponse = {
  id: number
  scheduleId: number
  taskId: number
  taskTitle?: string
  title: string
  startsAt: string
  endsAt: string
  source: string
  slotIndex?: number
  meta?: string
}

type CalendarEventModalProps = {
  isOpen: boolean
  onClose: () => void
  eventId: string // "task-123" or "event-456" or "schedule-task-123" or "schedule-456"
  onUpdate?: () => void
}

export default function CalendarEventModal({ isOpen, onClose, eventId, onUpdate }: CalendarEventModalProps) {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>('')
  const [isEditMode, setIsEditMode] = useState(false)
  
  const isTask = eventId.startsWith('task-')
  const isEvent = eventId.startsWith('event-')
  const isScheduleTask = eventId.startsWith('schedule-task-')
  const isScheduleAssignment = eventId.startsWith('schedule-') && !isScheduleTask
  
  const id = isTask 
    ? parseInt(eventId.replace('task-', '')) 
    : isEvent 
    ? parseInt(eventId.replace('event-', '')) 
    : isScheduleTask
    ? parseInt(eventId.replace('schedule-task-', ''))
    : isScheduleAssignment
    ? parseInt(eventId.replace('schedule-', ''))
    : null
  
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]) // 스케줄 작업의 Assignment 목록
  const [assignmentData, setAssignmentData] = useState<AssignmentResponse | null>(null) // 개별 Assignment 데이터

  // Task form state
  const [taskData, setTaskData] = useState<TaskResponse | null>(null)
  const [taskForm, setTaskForm] = useState({
    title: '',
    durationMin: 60,
    dueAt: '',
    priority: 3,
    splittable: true,
    tags: '',
    assigneeId: undefined as number | undefined
  })

  // Event form state
  const [eventData, setEventData] = useState<CalendarEventResponse | null>(null)
  const [eventForm, setEventForm] = useState({
    title: '',
    startsAt: '',
    endsAt: '',
    fixed: false,
    location: '',
    attendees: '',
    notes: '',
    recurrenceEnabled: false,
    recurrenceType: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY',
    recurrenceEndDate: ''
  })

  // Load data
  useEffect(() => {
    if (!isOpen || !id) return
    
    setLoading(true)
    setError('')
    setAssignments([])
    setAssignmentData(null)
    
    const loadData = async () => {
      try {
        if (isScheduleTask) {
          // 스케줄 작업: Task 정보와 Assignment 목록 가져오기
          const [taskResponse, assignmentsResponse] = await Promise.all([
            api.get(`/api/tasks/${id}`),
            api.get(`/api/assignments/task/${id}`)
          ])
          const taskData = taskResponse.data as TaskResponse
          const assignmentsData = assignmentsResponse.data as AssignmentResponse[]
          
          // 시간 순서로 정렬
          const sortedAssignments = assignmentsData.sort((a, b) => {
            return new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime()
          })
          
          setTaskData(taskData)
          setAssignments(sortedAssignments)
          setTaskForm({
            title: taskData.title,
            durationMin: taskData.durationMin,
            dueAt: taskData.dueAt ? new Date(taskData.dueAt).toISOString().slice(0, 16) : '',
            priority: taskData.priority,
            splittable: taskData.splittable,
            tags: taskData.tags || '',
            assigneeId: taskData.assigneeId
          })
        } else if (isScheduleAssignment) {
          // 개별 Assignment: Assignment 정보와 관련 Task 정보 가져오기
          const assignmentResponse = await api.get(`/api/assignments/${id}`)
          const assignment = assignmentResponse.data as AssignmentResponse
          setAssignmentData(assignment)
          
          // Assignment에 taskId가 있으면 Task 정보도 가져오기
          if (assignment.taskId) {
            try {
              const taskResponse = await api.get(`/api/tasks/${assignment.taskId}`)
              const taskData = taskResponse.data as TaskResponse
              setTaskData(taskData)
              setTaskForm({
                title: taskData.title,
                durationMin: taskData.durationMin,
                dueAt: taskData.dueAt ? new Date(taskData.dueAt).toISOString().slice(0, 16) : '',
                priority: taskData.priority,
                splittable: taskData.splittable,
                tags: taskData.tags || '',
                assigneeId: taskData.assigneeId
              })
            } catch (taskErr: any) {
              console.warn('Task 정보를 불러오는데 실패했습니다:', taskErr)
              // Task 정보가 없어도 Assignment 정보는 표시 가능
            }
          }
          
          // 개별 Assignment를 배열로 설정 (표시를 위해)
          setAssignments([assignment])
        } else if (isTask) {
          const response = await api.get(`/api/tasks/${id}`)
          const data = response.data as TaskResponse
          setTaskData(data)
          setTaskForm({
            title: data.title,
            durationMin: data.durationMin,
            dueAt: data.dueAt ? new Date(data.dueAt).toISOString().slice(0, 16) : '',
            priority: data.priority,
            splittable: data.splittable,
            tags: data.tags || '',
            assigneeId: data.assigneeId
          })
        } else if (isEvent) {
          const response = await api.get(`/api/events/${id}`)
          const data = response.data as CalendarEventResponse
          setEventData(data)
          setEventForm({
            title: data.title,
            startsAt: new Date(data.startsAt).toISOString().slice(0, 16),
            endsAt: new Date(data.endsAt).toISOString().slice(0, 16),
            fixed: data.fixed,
            location: data.location || '',
            attendees: data.attendees || '',
            notes: data.notes || '',
            recurrenceEnabled: !!data.recurrenceType,
            recurrenceType: (data.recurrenceType as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY') || 'WEEKLY',
            recurrenceEndDate: data.recurrenceEndDate ? new Date(data.recurrenceEndDate).toISOString().slice(0, 16) : ''
          })
        }
      } catch (err: any) {
        setError(err.response?.data?.message || '데이터를 불러오는데 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }
    
    loadData()
  }, [isOpen, id, isTask, isEvent, isScheduleTask, isScheduleAssignment])

  const handleSave = async () => {
    if (!id) return
    
    setSaving(true)
    setError('')
    
    try {
      if (isTask) {
        await api.put(`/api/tasks/${id}`, {
          title: taskForm.title,
          durationMin: taskForm.durationMin,
          dueAt: taskForm.dueAt ? new Date(taskForm.dueAt).toISOString() : null,
          priority: taskForm.priority,
          splittable: taskForm.splittable,
          tags: taskForm.tags || null,
          assigneeId: taskForm.assigneeId || null
        })
      } else if (isEvent) {
        await api.put(`/api/events/${id}`, {
          title: eventForm.title,
          startsAt: new Date(eventForm.startsAt).toISOString(),
          endsAt: new Date(eventForm.endsAt).toISOString(),
          fixed: eventForm.fixed,
          location: eventForm.location || null,
          attendees: eventForm.attendees || null,
          notes: eventForm.notes || null,
          recurrenceType: eventForm.recurrenceEnabled ? eventForm.recurrenceType : null,
          recurrenceEndDate: eventForm.recurrenceEnabled && eventForm.recurrenceEndDate 
            ? new Date(eventForm.recurrenceEndDate).toISOString() 
            : null
        })
      }
      
      setIsEditMode(false)
      if (onUpdate) onUpdate()
      // 모달은 닫지 않고 데이터만 새로고침
      if (isTask) {
        const response = await api.get(`/api/tasks/${id}`)
        setTaskData(response.data)
      } else if (isEvent) {
        const response = await api.get(`/api/events/${id}`)
        setEventData(response.data)
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50" onClick={onClose}>
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">
            {isTask ? '📋 작업 상세' : '📅 일정 상세'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-2xl font-bold"
          >
            ×
          </button>
        </div>

        {/* Content */}
        <div className="px-6 py-4">
          {loading ? (
            <div className="text-center py-8">
              <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
              <p className="mt-2 text-gray-600">로딩 중...</p>
            </div>
          ) : error && !isEditMode ? (
            <div className="bg-red-50 border border-red-200 rounded-md p-4 text-red-600">
              {error}
            </div>
          ) : (isTask || isScheduleTask || (isScheduleAssignment && taskData)) && taskData ? (
            <div className="space-y-4">
              {isEditMode ? (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">제목</label>
                    <input
                      type="text"
                      value={taskForm.title}
                      onChange={(e) => setTaskForm({ ...taskForm, title: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">마감일시</label>
                    <input
                      type="datetime-local"
                      value={taskForm.dueAt}
                      onChange={(e) => setTaskForm({ ...taskForm, dueAt: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">소요 시간 (분)</label>
                    <input
                      type="number"
                      min="1"
                      value={taskForm.durationMin}
                      onChange={(e) => setTaskForm({ ...taskForm, durationMin: parseInt(e.target.value) || 60 })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">우선순위 (1-5)</label>
                    <input
                      type="number"
                      min="1"
                      max="5"
                      value={taskForm.priority}
                      onChange={(e) => setTaskForm({ ...taskForm, priority: parseInt(e.target.value) || 3 })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={taskForm.splittable}
                        onChange={(e) => setTaskForm({ ...taskForm, splittable: e.target.checked })}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">분할 가능</span>
                    </label>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">태그</label>
                    <input
                      type="text"
                      value={taskForm.tags}
                      onChange={(e) => setTaskForm({ ...taskForm, tags: e.target.value })}
                      placeholder="쉼표로 구분"
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  {error && (
                    <div className="bg-red-50 border border-red-200 rounded-md p-3 text-red-600 text-sm">
                      {error}
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div>
                    <span className="text-sm font-medium text-gray-500">제목</span>
                    <p className="text-lg font-semibold text-gray-900 mt-1">{taskData.title}</p>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <span className="text-sm font-medium text-gray-500">팀</span>
                      <p className="text-gray-900 mt-1">{taskData.teamName || `팀 ID: ${taskData.teamId}`}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">담당자</span>
                      <p className="text-gray-900 mt-1">{taskData.assigneeName || '미지정'}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">마감일시</span>
                      <p className="text-gray-900 mt-1">
                        {taskData.dueAt ? new Date(taskData.dueAt).toLocaleString('ko-KR') : '미설정'}
                      </p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">우선순위</span>
                      <p className="text-gray-900 mt-1">{taskData.priority}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">분할 가능</span>
                      <p className="text-gray-900 mt-1">{taskData.splittable ? '예' : '아니오'}</p>
                    </div>
                  </div>
                  {taskData.tags && (
                    <div>
                      <span className="text-sm font-medium text-gray-500">태그</span>
                      <p className="text-gray-900 mt-1">{taskData.tags}</p>
                    </div>
                  )}
                  
                  {/* 스케줄된 작업의 경우 Assignment 목록 표시 */}
                  {(isScheduleTask || isScheduleAssignment) && assignments.length > 0 && (
                    <div className="mt-6 pt-6 border-t border-gray-200">
                      <h3 className="text-lg font-semibold text-gray-900 mb-3">
                        스케줄 시간표 {assignments.length > 1 && `(${assignments.length}개 부분)`}
                      </h3>
                      <div className="space-y-2 max-h-64 overflow-y-auto">
                        {assignments.map((assignment, index) => {
                          let startTime = new Date(assignment.startsAt)
                          let endTime = new Date(assignment.endsAt)
                          
                          // startsAt > endsAt인 경우 교체 (데이터 오류 보정)
                          if (startTime.getTime() > endTime.getTime()) {
                            console.warn(`[Schedule] Assignment 시간 오류 보정: id=${assignment.id}, startsAt=${assignment.startsAt}, endsAt=${assignment.endsAt}`)
                            const temp = startTime
                            startTime = endTime
                            endTime = temp
                          }
                          
                          const duration = Math.round((endTime.getTime() - startTime.getTime()) / (1000 * 60))
                          
                          return (
                            <div
                              key={assignment.id}
                              className="bg-blue-50 border border-blue-200 rounded-lg p-3"
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex-1">
                                  <div className="flex items-center gap-2 mb-1">
                                    {assignments.length > 1 && (
                                      <span className="px-2 py-0.5 bg-blue-600 text-white text-xs font-semibold rounded">
                                        부분 {index + 1}
                                      </span>
                                    )}
                                    <span className="text-sm font-medium text-gray-700">
                                      {assignment.title || taskData.title}
                                    </span>
                                  </div>
                                  <div className="text-sm text-gray-600">
                                    <span className="font-medium">
                                      {startTime.toLocaleString('ko-KR', {
                                        month: 'long',
                                        day: 'numeric',
                                        hour: '2-digit',
                                        minute: '2-digit',
                                        hour12: false
                                      })}
                                    </span>
                                    {' ~ '}
                                    <span className="font-medium">
                                      {endTime.toLocaleString('ko-KR', {
                                        month: 'long',
                                        day: 'numeric',
                                        hour: '2-digit',
                                        minute: '2-digit',
                                        hour12: false
                                      })}
                                    </span>
                                    <span className="ml-2 text-gray-500">({duration}분)</span>
                                  </div>
                                </div>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          ) : isScheduleAssignment && assignmentData && !taskData ? (
            // Assignment만 있고 Task 정보가 없는 경우
            <div className="space-y-4">
              <div>
                <span className="text-sm font-medium text-gray-500">제목</span>
                <p className="text-lg font-semibold text-gray-900 mt-1">{assignmentData.title || assignmentData.taskTitle || 'Assignment'}</p>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-sm font-medium text-gray-500">시작 시간</span>
                  <p className="text-gray-900 mt-1">
                    {assignmentData.startsAt ? new Date(assignmentData.startsAt).toLocaleString('ko-KR') : '미설정'}
                  </p>
                </div>
                <div>
                  <span className="text-sm font-medium text-gray-500">종료 시간</span>
                  <p className="text-gray-900 mt-1">
                    {assignmentData.endsAt ? new Date(assignmentData.endsAt).toLocaleString('ko-KR') : '미설정'}
                  </p>
                </div>
                {assignmentData.taskId && (
                  <div>
                    <span className="text-sm font-medium text-gray-500">작업 ID</span>
                    <p className="text-gray-900 mt-1">{assignmentData.taskId}</p>
                  </div>
                )}
                {assignmentData.scheduleId && (
                  <div>
                    <span className="text-sm font-medium text-gray-500">스케줄 ID</span>
                    <p className="text-gray-900 mt-1">{assignmentData.scheduleId}</p>
                  </div>
                )}
              </div>
            </div>
          ) : isEvent && eventData ? (
            <div className="space-y-4">
              {isEditMode ? (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">제목</label>
                    <input
                      type="text"
                      value={eventForm.title}
                      onChange={(e) => setEventForm({ ...eventForm, title: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">시작일시</label>
                      <input
                        type="datetime-local"
                        value={eventForm.startsAt}
                        onChange={(e) => setEventForm({ ...eventForm, startsAt: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">종료일시</label>
                      <input
                        type="datetime-local"
                        value={eventForm.endsAt}
                        onChange={(e) => setEventForm({ ...eventForm, endsAt: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">장소</label>
                    <input
                      type="text"
                      value={eventForm.location}
                      onChange={(e) => setEventForm({ ...eventForm, location: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">참석자 ID (쉼표로 구분)</label>
                    <input
                      type="text"
                      value={eventForm.attendees}
                      onChange={(e) => setEventForm({ ...eventForm, attendees: e.target.value })}
                      placeholder="예: 1,2,3"
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">메모</label>
                    <textarea
                      value={eventForm.notes}
                      onChange={(e) => setEventForm({ ...eventForm, notes: e.target.value })}
                      rows={3}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={eventForm.fixed}
                        onChange={(e) => setEventForm({ ...eventForm, fixed: e.target.checked })}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">고정 일정</span>
                    </label>
                  </div>
                  
                  {/* 반복 일정 옵션 */}
                  <div className="border-t border-gray-200 pt-4 mt-4">
                    <label className="flex items-center gap-2 mb-3">
                      <input
                        type="checkbox"
                        checked={eventForm.recurrenceEnabled}
                        onChange={(e) => setEventForm({ ...eventForm, recurrenceEnabled: e.target.checked })}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">반복 일정</span>
                    </label>
                    
                    {eventForm.recurrenceEnabled && (
                      <div className="ml-6 space-y-3">
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">반복 주기</label>
                          <select
                            value={eventForm.recurrenceType}
                            onChange={(e) => setEventForm({ ...eventForm, recurrenceType: e.target.value as any })}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                            value={eventForm.recurrenceEndDate}
                            onChange={(e) => setEventForm({ ...eventForm, recurrenceEndDate: e.target.value })}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                          <p className="text-xs text-gray-500 mt-1">비워두면 1년 후까지 반복됩니다</p>
                        </div>
                      </div>
                    )}
                  </div>
                  
                  {error && (
                    <div className="bg-red-50 border border-red-200 rounded-md p-3 text-red-600 text-sm">
                      {error}
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div>
                    <span className="text-sm font-medium text-gray-500">제목</span>
                    <p className="text-lg font-semibold text-gray-900 mt-1">{eventData.title}</p>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <span className="text-sm font-medium text-gray-500">팀</span>
                      <p className="text-gray-900 mt-1">{eventData.teamName || `팀 ID: ${eventData.teamId}`}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">소유자</span>
                      <p className="text-gray-900 mt-1">{eventData.ownerName || '미지정'}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">시작일시</span>
                      <p className="text-gray-900 mt-1">{new Date(eventData.startsAt).toLocaleString('ko-KR')}</p>
                    </div>
                    <div>
                      <span className="text-sm font-medium text-gray-500">종료일시</span>
                      <p className="text-gray-900 mt-1">{new Date(eventData.endsAt).toLocaleString('ko-KR')}</p>
                    </div>
                    {eventData.location && (
                      <div>
                        <span className="text-sm font-medium text-gray-500">장소</span>
                        <p className="text-gray-900 mt-1">{eventData.location}</p>
                      </div>
                    )}
                    <div>
                      <span className="text-sm font-medium text-gray-500">고정 일정</span>
                      <p className="text-gray-900 mt-1">{eventData.fixed ? '예' : '아니오'}</p>
                    </div>
                    {eventData.recurrenceType && (
                      <>
                        <div>
                          <span className="text-sm font-medium text-gray-500">반복 주기</span>
                          <p className="text-gray-900 mt-1">
                            {eventData.recurrenceType === 'DAILY' ? '매일' :
                             eventData.recurrenceType === 'WEEKLY' ? '매주' :
                             eventData.recurrenceType === 'MONTHLY' ? '매월' :
                             eventData.recurrenceType === 'YEARLY' ? '매년' : eventData.recurrenceType}
                          </p>
                        </div>
                        {eventData.recurrenceEndDate && (
                          <div>
                            <span className="text-sm font-medium text-gray-500">반복 종료일</span>
                            <p className="text-gray-900 mt-1">{new Date(eventData.recurrenceEndDate).toLocaleDateString('ko-KR')}</p>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                  {eventData.attendees && (
                    <div>
                      <span className="text-sm font-medium text-gray-500">참석자 ID</span>
                      <p className="text-gray-900 mt-1">{eventData.attendees}</p>
                    </div>
                  )}
                  {eventData.notes && (
                    <div>
                      <span className="text-sm font-medium text-gray-500">메모</span>
                      <p className="text-gray-900 mt-1 whitespace-pre-wrap">{eventData.notes}</p>
                    </div>
                  )}
                </>
              )}
            </div>
          ) : null}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-end gap-2">
          {isEditMode ? (
            <>
              <button
                onClick={() => {
                  setIsEditMode(false)
                  setError('')
                }}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:bg-blue-400 disabled:cursor-not-allowed transition"
              >
                {saving ? '저장 중...' : '저장'}
              </button>
            </>
          ) : (
            <>
              <button
                onClick={onClose}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition"
              >
                닫기
              </button>
              {isTask && id && (
                <button
                  onClick={async () => {
                    if (confirm(`작업 "${taskData?.title}"을(를) 삭제하시겠습니까?`)) {
                      try {
                        setSaving(true)
                        setError('')
                        await api.delete(`/api/tasks/${id}`)
                        // 삭제 성공: 상태 초기화 후 모달 닫기
                        setSaving(false)
                        // WebSocket으로 자동 업데이트되므로 onUpdate 호출 불필요
                        onClose()
                      } catch (err: any) {
                        setError(err.response?.data?.message || '작업 삭제에 실패했습니다.')
                        setSaving(false)
                      }
                    }
                  }}
                  disabled={saving}
                  className="px-4 py-2 text-white bg-red-600 rounded-md hover:bg-red-700 disabled:bg-red-400 disabled:cursor-not-allowed transition"
                >
                  {saving ? '삭제 중...' : '삭제'}
                </button>
              )}
              {isEvent && id && (
                <button
                  onClick={async () => {
                    if (confirm(`일정 "${eventData?.title}"을(를) 삭제하시겠습니까?`)) {
                      try {
                        setSaving(true)
                        setError('')
                        await api.delete(`/api/events/${id}`)
                        // 삭제 성공: 상태 초기화 후 모달 닫기
                        setSaving(false)
                        // WebSocket으로 자동 업데이트되므로 onUpdate 호출 불필요
                        onClose()
                      } catch (err: any) {
                        setError(err.response?.data?.message || '일정 삭제에 실패했습니다.')
                        setSaving(false)
                      }
                    }
                  }}
                  disabled={saving}
                  className="px-4 py-2 text-white bg-red-600 rounded-md hover:bg-red-700 disabled:bg-red-400 disabled:cursor-not-allowed transition"
                >
                  {saving ? '삭제 중...' : '삭제'}
                </button>
              )}
              <button
                onClick={() => setIsEditMode(true)}
                className="px-4 py-2 text-white bg-blue-600 rounded-md hover:bg-blue-700 transition"
              >
                수정
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

