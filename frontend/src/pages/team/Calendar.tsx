import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventDropArg, EventClickArg } from '@fullcalendar/core'
import type { EventResizeDoneArg, DateClickArg } from '@fullcalendar/interaction'
import api from '../../lib/api'
import type { StompSubscription } from '@stomp/stompjs'
import {
  createStompClient,
  safeJsonParse
} from '../../lib/ws'
import type {
  CalendarEventMessage,
  ConflictAlertMessage,
  TaskEventMessage
} from '../../lib/ws'
import CalendarEventModal from '../../components/CalendarEventModal'
import CreateEventModal from '../../components/CreateEventModal'
import MeetingSuggestionModal from '../../components/MeetingSuggestionModal'

type CalendarEvent = {
  id: string
  title: string
  start: string
  end: string
  backgroundColor?: string
  borderColor?: string
  editable?: boolean // FullCalendar 드래그/수정 가능 여부
  durationEditable?: boolean // 하단 리사이즈 가능 여부 (시작 시간은 드래그로 변경)
  extendedProps?: {
    type: 'event' | 'task'
    location?: string
    priority?: number
    teamId?: number
  }
}

type Task = {
  id: number
  title: string
  dueAt?: string
  durationMin: number
  priority: number
}

type Event = {
  id: number
  title: string
  startsAt: string
  endsAt: string
  location?: string
}

export default function Calendar() {
  const { id } = useParams()
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [teamBaseColor, setTeamBaseColor] = useState<string>('#3b82f6')
  const [conflictAlert, setConflictAlert] = useState<ConflictAlertMessage | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedEventId, setSelectedEventId] = useState<string>('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createModalDate, setCreateModalDate] = useState<Date | undefined>()
  const [createModalStartTime, setCreateModalStartTime] = useState<string | undefined>()
  const [createModalEndTime, setCreateModalEndTime] = useState<string | undefined>()
  const [createModalTitle, setCreateModalTitle] = useState<string | undefined>()
  const [createModalLocation, setCreateModalLocation] = useState<string | undefined>()
  const [meetingSuggestionModalOpen, setMeetingSuggestionModalOpen] = useState(false)
  const teamId = id ? parseInt(id) : 0
  const teamColorRef = useRef<string>(teamBaseColor)
  // 자신이 발생시킨 변경사항 추적 (중복 업데이트 방지)
  const pendingUpdatesRef = useRef<Set<string>>(new Set())

  useEffect(() => {
    teamColorRef.current = teamBaseColor
  }, [teamBaseColor])

  // 일정/작업 드래그 핸들러 (이동)
  const handleEventDrop = async (dropInfo: EventDropArg) => {
    const event = dropInfo.event
    const calendarId = event.id
    
    const newStart = event.start
    const newEnd = event.end

    if (!newStart || !newEnd) {
      dropInfo.revert()
      return
    }

    // 낙관적 업데이트: UI는 이미 변경됨
    // 자신이 발생시킨 변경사항으로 표시
    pendingUpdatesRef.current.add(calendarId)

    try {
      if (calendarId.startsWith('event-')) {
        // Event 처리: startsAt, endsAt 변경
        const eventId = parseInt(calendarId.replace('event-', ''))
        if (isNaN(eventId)) {
          dropInfo.revert()
          pendingUpdatesRef.current.delete(calendarId)
          return
        }

        await api.put(`/api/events/${eventId}`, {
          startsAt: newStart.toISOString(),
          endsAt: newEnd.toISOString()
        })
      } else if (calendarId.startsWith('task-')) {
        // Task 처리: 드래그 시 마감일(dueAt) 변경
        // - 전체 드래그: 마감일 이동 (시작 시간만 변경, 소요 시간 유지)
        // - 상단 드래그: 시작 시간만 변경 (eventStartEditable=true로 인해 가능)
        const taskId = parseInt(calendarId.replace('task-', ''))
        if (isNaN(taskId)) {
          dropInfo.revert()
          pendingUpdatesRef.current.delete(calendarId)
          return
        }

        await api.put(`/api/tasks/${taskId}`, {
          dueAt: newStart.toISOString() // 드래그 시 마감일(시작 시간) 변경
        })
      } else {
        dropInfo.revert()
        pendingUpdatesRef.current.delete(calendarId)
        return
      }
      // 성공 시 pendingUpdates에서 제거는 WebSocket 메시지 수신 시 처리됨
    } catch (error) {
      console.error('이동 실패:', error)
      // 실패 시 롤백
      dropInfo.revert()
      pendingUpdatesRef.current.delete(calendarId)
      
      // 사용자에게 알림
      const itemType = calendarId.startsWith('event-') ? '일정' : '작업'
      alert(`${itemType} 이동에 실패했습니다. 다시 시도해주세요.`)
    }
  }

  // 일정/작업 리사이즈 핸들러 (종료 시간만 변경)
  // 하단 드래그만 가능 (시작 시간은 드래그로 변경)
  const handleEventResize = async (resizeInfo: EventResizeDoneArg) => {
    const event = resizeInfo.event
    const calendarId = event.id

    const newStart = event.start
    const newEnd = event.end

    if (!newStart || !newEnd) {
      resizeInfo.revert()
      return
    }

    // 낙관적 업데이트: UI는 이미 변경됨
    // 자신이 발생시킨 변경사항으로 표시
    pendingUpdatesRef.current.add(calendarId)

    try {
      if (calendarId.startsWith('event-')) {
        // Event 처리: endsAt만 변경 (시작 시간은 드래그로 변경)
        const eventId = parseInt(calendarId.replace('event-', ''))
        if (isNaN(eventId)) {
          resizeInfo.revert()
          pendingUpdatesRef.current.delete(calendarId)
          return
        }

        await api.put(`/api/events/${eventId}`, {
          endsAt: newEnd.toISOString() // 종료 시간만 변경
        })
      } else if (calendarId.startsWith('task-')) {
        // Task 처리: 소요 시간(durationMin)만 변경 (시작 시간은 드래그로 변경)
        const taskId = parseInt(calendarId.replace('task-', ''))
        if (isNaN(taskId)) {
          resizeInfo.revert()
          pendingUpdatesRef.current.delete(calendarId)
          return
        }

        // 새로운 소요 시간 계산 (분 단위)
        const durationMs = newEnd.getTime() - newStart.getTime()
        const durationMin = Math.round(durationMs / (1000 * 60))

        if (durationMin <= 0) {
          resizeInfo.revert()
          pendingUpdatesRef.current.delete(calendarId)
          alert('작업 소요 시간은 0보다 커야 합니다.')
          return
        }

        // 소요 시간(durationMin)만 업데이트 (시작 시간은 드래그로 변경)
        await api.put(`/api/tasks/${taskId}`, {
          durationMin: durationMin // 새로운 소요 시간만 변경
        })
      } else {
        resizeInfo.revert()
        pendingUpdatesRef.current.delete(calendarId)
        return
      }
      // 성공 시 pendingUpdates에서 제거는 WebSocket 메시지 수신 시 처리됨
    } catch (error) {
      console.error('시간 변경 실패:', error)
      // 실패 시 롤백
      resizeInfo.revert()
      pendingUpdatesRef.current.delete(calendarId)
      
      // 사용자에게 알림
      const itemType = calendarId.startsWith('event-') ? '일정' : '작업'
      alert(`${itemType} 시간 변경에 실패했습니다. 다시 시도해주세요.`)
    }
  }

  // 팀별 색상 팔레트 (기본 색상)
  const teamColors = [
    { base: '#3b82f6', name: 'blue' },      // 파란색
    { base: '#ef4444', name: 'red' },       // 빨간색
    { base: '#8b5cf6', name: 'purple' },    // 보라색
    { base: '#f59e0b', name: 'amber' },     // 주황색
    { base: '#10b981', name: 'green' },     // 초록색
    { base: '#ec4899', name: 'pink' },      // 분홍색
    { base: '#06b6d4', name: 'cyan' },      // 청록색
    { base: '#f97316', name: 'orange' },    // 오렌지색
  ]

  // 팀 ID로 색상 가져오기
  const getTeamColor = (teamId: number): string => {
    const index = teamId % teamColors.length
    return teamColors[index].base
  }

  // 우선순위에 따른 색상 진하기 조절 (1이 가장 높음, 가장 진함)
  const getColorByPriority = (baseColor: string, priority: number): { bg: string; border: string } => {
    // hex 색상을 RGB로 변환
    const hex = baseColor.replace('#', '')
    const r = parseInt(hex.substring(0, 2), 16)
    const g = parseInt(hex.substring(2, 4), 16)
    const b = parseInt(hex.substring(4, 6), 16)

    // 우선순위 1-5에 따라 진하기 조절 (1이 가장 진함)
    const opacityMap: { [key: number]: number } = {
      1: 1.0,   // 100% - 가장 진함
      2: 0.85,  // 85%
      3: 0.70,  // 70%
      4: 0.55,  // 55%
      5: 0.40   // 40% - 가장 연함
    }

    const opacity = opacityMap[priority] || 0.70

    // 배경색 (진하게)
    const bgR = Math.round(r * opacity)
    const bgG = Math.round(g * opacity)
    const bgB = Math.round(b * opacity)

    // 테두리색 (더 진하게, 약 20% 더)
    const borderOpacity = Math.min(opacity + 0.2, 1.0)
    const borderR = Math.round(r * borderOpacity)
    const borderG = Math.round(g * borderOpacity)
    const borderB = Math.round(b * borderOpacity)

    return {
      bg: `rgb(${bgR}, ${bgG}, ${bgB})`,
      border: `rgb(${borderR}, ${borderG}, ${borderB})`
    }
  }

  useEffect(() => {
    if (!id) return
    loadCalendarData()
  }, [id, teamId])

  useEffect(() => {
    if (!id) return
    const teamIdNum = Number(id)
    const client = createStompClient()
    const subscriptions: StompSubscription[] = []

    const upsertCalendarEvent = (message: CalendarEventMessage) => {
      if (message.eventId == null && !message.event) return
      const eventId = message.eventId ?? message.event?.id
      const calendarId = eventId != null ? `event-${eventId}` : undefined
      
      // 자신이 발생시킨 변경사항이면 무시 (중복 업데이트 방지)
      if (calendarId && pendingUpdatesRef.current.has(calendarId)) {
        pendingUpdatesRef.current.delete(calendarId)
        return
      }
      
      if (message.action === 'DELETED' || !message.event) {
        if (!calendarId) return
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      const payload = message.event
      const converted: CalendarEvent = {
        id: `event-${payload.id}`,
        title: payload.title,
        start: payload.startsAt,
        end: payload.endsAt,
        backgroundColor: '#22c55e',
        borderColor: '#16a34a',
        editable: true, // Event는 드래그/수정 가능
        durationEditable: true, // 하단 리사이즈 가능 (시작 시간은 드래그로 변경)
        extendedProps: {
          type: 'event',
          location: payload.location ?? undefined
        }
      }
      setEvents((prev) => {
        const index = prev.findIndex((entry) => entry.id === converted.id)
        if (index >= 0) {
          const copy = [...prev]
          copy[index] = converted
          return copy
        }
        return [...prev, converted]
      })
    }

    const upsertTaskEvent = (message: TaskEventMessage) => {
      const taskId = message.task?.id ?? message.taskId
      if (!taskId) {
        console.warn('[Calendar] Task event missing taskId:', message)
        return
      }
      const calendarId = `task-${taskId}`
      
      // 자신이 발생시킨 변경사항이면 무시 (중복 업데이트 방지)
      if (pendingUpdatesRef.current.has(calendarId)) {
        console.log('[Calendar] Ignoring own update for task:', taskId)
        pendingUpdatesRef.current.delete(calendarId)
        return
      }
      
      // 삭제된 작업이거나 작업 정보가 없으면 캘린더에서 제거
      if (message.action === 'DELETED' || !message.task) {
        console.log('[Calendar] Removing task from calendar:', taskId, message.action)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      
      // 마감일시가 없으면 캘린더에 표시하지 않음 (작업 목록에는 표시됨)
      if (!message.task.dueAt) {
        console.log('[Calendar] Task has no dueAt, skipping calendar display:', taskId)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      
      const dueDate = new Date(message.task.dueAt)
      if (isNaN(dueDate.getTime())) {
        console.warn('[Calendar] Invalid dueAt date:', message.task.dueAt)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      
      // 작업은 마감시간만 표시 (소요시간 없이, start = end = 마감일시)
      // Assignment는 스케줄 화면에서 시작시간~끝나는시간으로 표시됨
      const start = dueDate // 마감일시
      const end = dueDate // 마감일시 (작업은 마감시간만 표시)
      
      const priority = message.task.priority ?? 3
      const colors = getColorByPriority(teamColorRef.current, priority)
      const converted: CalendarEvent = {
        id: calendarId,
        title: `📋 ${message.task.title}`,
        start: start.toISOString(),
        end: end.toISOString(),
        backgroundColor: colors.bg,
        borderColor: colors.border,
        editable: true, // Task도 드래그/수정 가능
        durationEditable: true, // 하단 리사이즈 가능 (시작 시간은 드래그로 변경)
        extendedProps: {
          type: 'task',
          priority
        }
      }
      setEvents((prev) => {
        const index = prev.findIndex((entry) => entry.id === converted.id)
        if (index >= 0) {
          const copy = [...prev]
          copy[index] = converted
          return copy
        }
        return [...prev, converted]
      })
    }

    const showConflictAlert = (message: ConflictAlertMessage) => {
      setConflictAlert(message)
      window.setTimeout(() => {
        setConflictAlert((current) => (current === message ? null : current))
      }, 8000)
    }

    client.onConnect = () => {
      console.log('[Calendar] WebSocket connected, subscribing to topics')
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0
      subscriptions.push(
        client.subscribe(`/topic/calendar/${teamIdNum}`, (frame) => {
          const payload = safeJsonParse<CalendarEventMessage>(frame.body)
          if (!payload) return
          console.log('[Calendar] Received calendar event:', payload)
          upsertCalendarEvent(payload)
        })
      )
      subscriptions.push(
        client.subscribe(`/topic/tasks/${teamIdNum}`, (frame) => {
          const payload = safeJsonParse<TaskEventMessage>(frame.body)
          if (!payload) {
            console.warn('[Calendar] Failed to parse task event:', frame.body)
            return
          }
          console.log('[Calendar] Received task event:', payload)
          upsertTaskEvent(payload)
        })
      )
      subscriptions.push(
        client.subscribe(`/topic/conflicts/${teamIdNum}`, (frame) => {
          const payload = safeJsonParse<ConflictAlertMessage>(frame.body)
          if (!payload) return
          showConflictAlert(payload)
        })
      )
    }

    client.onStompError = (frame) => {
      console.error('[Calendar] STOMP error:', frame.headers['message'], frame.body)
    }

    client.onWebSocketError = (event) => {
      console.error('[Calendar] WebSocket error:', event)
    }

    client.activate()

    return () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
    }
  }, [id])

  const handleEventClick = (clickInfo: EventClickArg) => {
    setSelectedEventId(clickInfo.event.id)
    setModalOpen(true)
  }

  const handleDateClick = (dateClickArg: DateClickArg) => {
    setCreateModalDate(dateClickArg.date)
    setCreateModalOpen(true)
  }


  const loadCalendarData = async () => {
    if (!id) return
    try {
      // 팀의 CalendarEvent와 Task만 조회 (Assignment는 조회하지 않음)
      const [eventsResponse, tasksResponse] = await Promise.all([
        api.get(`/api/events/team/${id}`).catch((error) => {
          console.error('[Calendar] Failed to load events:', error)
          return { data: [] }
        }),
        api.get(`/api/tasks/team/${id}`).catch((error) => {
          console.error('[Calendar] Failed to load tasks:', error)
          return { data: [] }
        })
      ])

      console.log('[Calendar] Loaded events:', eventsResponse.data)
      console.log('[Calendar] Loaded tasks:', tasksResponse.data)

      const calendarEvents: CalendarEvent[] = []
      
      // 팀 기본 색상 가져오기
      const baseColor = getTeamColor(teamId)
      setTeamBaseColor(baseColor)

      // CalendarEvent 변환
      if (eventsResponse.data && Array.isArray(eventsResponse.data)) {
        eventsResponse.data.forEach((event: Event) => {
          if (event && event.startsAt && event.endsAt) {
            // 날짜 유효성 검사
            const startDate = new Date(event.startsAt)
            const endDate = new Date(event.endsAt)
            
            if (isNaN(startDate.getTime()) || isNaN(endDate.getTime())) {
              console.warn('[Calendar] Invalid date for event:', event)
              return
            }
            
            calendarEvents.push({
              id: `event-${event.id}`,
              title: event.title,
              start: startDate.toISOString(),
              end: endDate.toISOString(),
              backgroundColor: '#22c55e',
              borderColor: '#16a34a',
              editable: true,
              durationEditable: true,
              extendedProps: {
                type: 'event',
                location: event.location
              }
            })
          } else {
            console.warn('[Calendar] Event missing required fields:', event)
          }
        })
      } else {
        console.warn('[Calendar] Events response is not an array:', eventsResponse.data)
      }
      
      console.log('[Calendar] Converted calendar events:', calendarEvents.length, 'items')

      // Task 변환 (마감일 기준으로 역산하여 표시)
      if (tasksResponse.data && Array.isArray(tasksResponse.data)) {
        tasksResponse.data.forEach((task: Task) => {
          // 마감일이 있는 경우만 표시
          if (task.dueAt) {
            const dueDate = new Date(task.dueAt)
            if (!isNaN(dueDate.getTime())) {
              const durationMin = task.durationMin || 60
              const startDate = new Date(dueDate.getTime() - durationMin * 60 * 1000) // 마감일시 - 소요시간
              const endDate = dueDate // 마감일시가 종료 시간
              
              const priority = task.priority || 3
              const colors = getColorByPriority(baseColor, priority)
              
              calendarEvents.push({
                id: `task-${task.id}`,
                title: `📋 ${task.title}`,
                start: startDate.toISOString(),
                end: endDate.toISOString(),
                backgroundColor: colors.bg,
                borderColor: colors.border,
                editable: true,
                durationEditable: true,
                extendedProps: {
                  type: 'task',
                  priority: task.priority
                }
              })
            }
          }
        })
      }

      setEvents(calendarEvents)
    } catch (error) {
      console.error('캘린더 데이터를 불러오는 중 오류가 발생했습니다.', error)
    }
  }

  return (
    <div className="p-6 bg-gradient-to-br from-gray-50 to-white min-h-screen">
      {conflictAlert && (
        <div className="mb-6 rounded-xl border-2 border-red-300 bg-gradient-to-r from-red-50 to-red-100 p-5 text-red-800 shadow-lg animate-pulse">
          <div className="flex items-center gap-2 mb-2">
            <svg className="w-5 h-5 text-red-600" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <div className="font-bold text-base">일정 충돌 감지</div>
          </div>
          <div className="text-sm font-medium">{conflictAlert.message}</div>
          {conflictAlert.conflicts?.length > 0 && (
            <ul className="mt-3 space-y-2 text-xs text-red-700 bg-white/50 rounded-lg p-3">
              {conflictAlert.conflicts.map((conflict) => (
                <li key={conflict.id} className="flex items-start gap-2">
                  <span className="text-red-500 mt-0.5">•</span>
                  <div>
                    <span className="font-semibold">{conflict.title}</span>
                    <span className="ml-2 text-red-600">
                      ({new Date(conflict.startsAt).toLocaleString()} ~ {new Date(conflict.endsAt).toLocaleString()})
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="mb-6">
        <div className="bg-white rounded-xl shadow-lg p-5 border border-gray-200">
          <div className="flex items-center justify-between mb-4">
            <div className="flex gap-6 text-sm flex-wrap">
              <div className="flex items-center gap-2.5 px-3 py-2 bg-green-50 rounded-lg border border-green-200">
                <div className="w-4 h-4 rounded-full bg-gradient-to-br from-green-500 to-green-600 shadow-sm"></div>
                <span className="font-medium text-gray-700">일정 (Event)</span>
              </div>
              <div className="flex items-center gap-2.5 px-3 py-2 bg-blue-50 rounded-lg border border-blue-200">
                <div className="w-4 h-4 rounded-full shadow-sm" style={{ backgroundColor: teamBaseColor }}></div>
                <span className="font-medium text-gray-700">작업 (Task) - 팀 색상</span>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setMeetingSuggestionModalOpen(true)}
                className="px-4 py-2.5 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium flex items-center gap-2"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                미팅 추가
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden">
        <style>{`
          .fc-header-toolbar {
            margin-bottom: 2rem !important;
            padding: 1.5rem !important;
            background: linear-gradient(to right, #f8fafc, #ffffff) !important;
            border-bottom: 1px solid #e5e7eb !important;
          }
          .fc-toolbar-title {
            font-size: 1.75rem !important;
            font-weight: 800 !important;
            background: linear-gradient(to right, #3b82f6, #8b5cf6) !important;
            -webkit-background-clip: text !important;
            -webkit-text-fill-color: transparent !important;
            background-clip: text !important;
          }
          .fc-button {
            background: #f3f4f6 !important;
            border: 1px solid #d1d5db !important;
            color: #374151 !important;
            font-weight: 500 !important;
            padding: 0.5rem 1rem !important;
            border-radius: 0.5rem !important;
            transition: all 0.2s !important;
          }
          .fc-button:hover {
            background: #e5e7eb !important;
            transform: translateY(-1px) !important;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1) !important;
          }
          .fc-button-active {
            background: linear-gradient(to right, #3b82f6, #8b5cf6) !important;
            border-color: #3b82f6 !important;
            color: white !important;
          }
          .fc-daygrid-day {
            border-color: #e5e7eb !important;
          }
          .fc-day-today {
            background: #fef3c7 !important;
          }
          .fc-timegrid-col {
            width: 14.2857% !important;
            min-width: 0 !important;
          }
          .fc-timegrid-slot {
            min-height: 2.5em !important;
          }
          .fc-timegrid-event {
            width: 100% !important;
            max-width: 100% !important;
            margin-left: 0 !important;
            margin-right: 0 !important;
            left: 0 !important;
            right: 0 !important;
          }
          .fc-timegrid-event-harness {
            width: 100% !important;
            left: 0 !important;
            right: 0 !important;
          }
          .fc-timegrid-event-harness-inset {
            left: 0 !important;
            right: 0 !important;
          }
          .fc-timegrid-col-events {
            margin: 0 !important;
          }
          .fc-timegrid-event-seg {
            left: 0 !important;
            right: 0 !important;
            width: 100% !important;
          }
          .fc-daygrid-day-frame {
            width: 100% !important;
          }
          .fc-daygrid-day-events {
            width: 100% !important;
          }
          .fc-event {
            border-radius: 0.375rem !important;
            border: none !important;
            padding: 4px 6px !important;
            font-weight: 500 !important;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1) !important;
            transition: all 0.2s !important;
            min-height: 24px !important;
            font-size: 0.875rem !important;
            width: 100% !important;
            max-width: 100% !important;
            margin-left: 0 !important;
            margin-right: 0 !important;
          }
          .fc-event:hover {
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.15) !important;
            transform: translateY(-1px) !important;
          }
          .fc-timegrid-event {
            min-height: 24px !important;
            padding: 4px 6px !important;
            width: 100% !important;
            max-width: 100% !important;
            margin-left: 0 !important;
            margin-right: 0 !important;
          }
          .fc-daygrid-event {
            min-height: 24px !important;
            padding: 4px 6px !important;
            white-space: nowrap !important;
            overflow: hidden !important;
            text-overflow: ellipsis !important;
            width: 100% !important;
            max-width: 100% !important;
            margin-left: 0 !important;
            margin-right: 0 !important;
          }
          .fc-col-header-cell {
            background: #f9fafb !important;
            border-color: #e5e7eb !important;
            font-weight: 600 !important;
            color: #374151 !important;
            padding: 0.75rem !important;
          }
          .fc-event-time {
            display: inline !important;
          }
          .fc-daygrid-block-event .fc-event-time {
            font-weight: 500 !important;
          }
          .fc-daygrid-event-harness {
            position: relative !important;
          }
          .fc-daygrid-event-harness + .fc-daygrid-event-harness {
            margin-top: 2px !important;
          }
        `}</style>
        <FullCalendar
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="timeGridWeek"
          headerToolbar={{
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek,timeGridDay'
          }}
          events={events}
          editable={true}
          eventStartEditable={false}
          eventDurationEditable={true}
          eventDrop={handleEventDrop}
          eventResize={handleEventResize}
          eventClick={handleEventClick}
          dateClick={handleDateClick}
          height="auto"
          locale="ko"
          buttonText={{
            today: '오늘',
            month: '월',
            week: '주',
            day: '일'
          }}
          displayEventEnd={true}
          eventDisplay="block"
          moreLinkClick="popover"
          eventOverlap={true}
          slotEventOverlap={true}
        />
      </div>
      <CalendarEventModal
        isOpen={modalOpen}
        onClose={() => {
          setModalOpen(false)
          setSelectedEventId('')
        }}
        eventId={selectedEventId}
        onUpdate={() => {
          // 모달에서 업데이트 후 이벤트 목록 새로고침
          loadCalendarData()
        }}
      />
      <CreateEventModal
        isOpen={createModalOpen}
        onClose={() => {
          setCreateModalOpen(false)
          setCreateModalDate(undefined)
          setCreateModalStartTime(undefined)
          setCreateModalEndTime(undefined)
          setCreateModalTitle(undefined)
          setCreateModalLocation(undefined)
        }}
        defaultDate={createModalDate}
        defaultStartTime={createModalStartTime}
        defaultEndTime={createModalEndTime}
        defaultTitle={createModalTitle}
        defaultLocation={createModalLocation}
        teamId={teamId}
        onSuccess={() => {
          // 이벤트 생성 후 목록 새로고침
          loadCalendarData()
        }}
      />
      <MeetingSuggestionModal
        isOpen={meetingSuggestionModalOpen}
        onClose={() => {
          setMeetingSuggestionModalOpen(false)
        }}
        teamId={teamId}
        onSelectTime={(startsAt, endsAt, title, location) => {
          // 선택된 시간으로 이벤트 생성 모달 열기
          setCreateModalDate(new Date(startsAt))
          setCreateModalStartTime(startsAt)
          setCreateModalEndTime(endsAt)
          setCreateModalTitle(title)
          setCreateModalLocation(location)
          setCreateModalOpen(true)
        }}
      />
    </div>
  )
}


