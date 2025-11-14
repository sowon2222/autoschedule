import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventDropArg } from '@fullcalendar/core'
import type { EventResizeDoneArg } from '@fullcalendar/interaction'
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

    const loadCalendarData = async () => {
      try {
        // 팀의 CalendarEvent와 Task를 모두 조회
        const [eventsResponse, tasksResponse] = await Promise.all([
          api.get(`/api/events/team/${id}`),
          api.get(`/api/tasks/team/${id}`)
        ])

        const calendarEvents: CalendarEvent[] = []
        
        // 팀 기본 색상 가져오기
        const baseColor = getTeamColor(teamId)
        setTeamBaseColor(baseColor)

        // CalendarEvent 변환
        eventsResponse.data.forEach((event: Event) => {
          calendarEvents.push({
            id: `event-${event.id}`,
            title: event.title,
            start: event.startsAt,
            end: event.endsAt,
            backgroundColor: '#22c55e',
            borderColor: '#16a34a',
            editable: true, // Event는 드래그/수정 가능
            durationEditable: true, // 하단 리사이즈 가능 (시작 시간은 드래그로 변경)
            extendedProps: {
              type: 'event',
              location: event.location
            }
          })
        })

        // Task 변환 (마감일이 있는 경우만)
        tasksResponse.data.forEach((task: Task) => {
          if (task.dueAt) {
            const startDate = new Date(task.dueAt)
            const endDate = new Date(startDate.getTime() + task.durationMin * 60 * 1000)
            
            const priority = task.priority || 3
            const colors = getColorByPriority(baseColor, priority)
            
            calendarEvents.push({
              id: `task-${task.id}`,
              title: `📋 ${task.title}`,
              start: startDate.toISOString(),
              end: endDate.toISOString(),
              backgroundColor: colors.bg,
              borderColor: colors.border,
              editable: true, // Task도 드래그/수정 가능
              durationEditable: true, // 하단 리사이즈 가능 (시작 시간은 드래그로 변경)
              extendedProps: {
                type: 'task',
                priority: task.priority
              }
            })
          }
        })

        setEvents(calendarEvents)
      } catch (error) {
        console.error('캘린더 데이터를 불러오는 중 오류가 발생했습니다.', error)
      }
    }

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
      if (!taskId) return
      const calendarId = `task-${taskId}`
      
      // 자신이 발생시킨 변경사항이면 무시 (중복 업데이트 방지)
      if (pendingUpdatesRef.current.has(calendarId)) {
        pendingUpdatesRef.current.delete(calendarId)
        return
      }
      
      if (message.action === 'DELETED' || !message.task || !message.task.dueAt) {
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      const dueDate = message.task.dueAt ? new Date(message.task.dueAt) : null
      if (!dueDate) {
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }
      const end = new Date(dueDate.getTime() + (message.task.durationMin ?? 0) * 60 * 1000)
      const priority = message.task.priority ?? 3
      const colors = getColorByPriority(teamColorRef.current, priority)
      const converted: CalendarEvent = {
        id: calendarId,
        title: `📋 ${message.task.title}`,
        start: dueDate.toISOString(),
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
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0
      subscriptions.push(
        client.subscribe(`/topic/calendar/${teamIdNum}`, (frame) => {
          const payload = safeJsonParse<CalendarEventMessage>(frame.body)
          if (!payload) return
          upsertCalendarEvent(payload)
        })
      )
      subscriptions.push(
        client.subscribe(`/topic/tasks/${teamIdNum}`, (frame) => {
          const payload = safeJsonParse<TaskEventMessage>(frame.body)
          if (!payload) return
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

    client.activate()

    return () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
    }
  }, [id])

  return (
    <div className="p-6">
      {conflictAlert && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-700 shadow-sm">
          <div className="font-semibold text-sm mb-1">일정 충돌 감지</div>
          <div className="text-sm">{conflictAlert.message}</div>
          {conflictAlert.conflicts?.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-red-600">
              {conflictAlert.conflicts.map((conflict) => (
                <li key={conflict.id}>
                  • {conflict.title}{' '}
                  <span className="text-[11px] text-red-500">
                    ({new Date(conflict.startsAt).toLocaleString()} ~ {new Date(conflict.endsAt).toLocaleString()})
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="mb-4">
        <h2 className="text-2xl font-bold mb-2">캘린더</h2>
        <div className="space-y-2">
          <div className="flex gap-4 text-sm flex-wrap">
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 rounded bg-green-500"></div>
              <span>일정 (Event)</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 rounded" style={{ backgroundColor: teamBaseColor }}></div>
              <span>작업 (Task) - 팀 색상</span>
            </div>
          </div>
          <div className="text-xs text-gray-600">
            <span className="font-semibold">우선순위 색상 진하기:</span> 
            <span className="ml-2">1(가장 진함) → 5(가장 연함)</span>
          </div>
        </div>
      </div>
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
        height="auto"
        locale="ko"
        buttonText={{
          today: '오늘',
          month: '월',
          week: '주',
          day: '일'
        }}
      />
    </div>
  )
}


