import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import api from '../../lib/api'

type CalendarEvent = {
  id: string
  title: string
  start: string
  end: string
  backgroundColor?: string
  borderColor?: string
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
  const teamId = id ? parseInt(id) : 0

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

  return (
    <div className="p-6">
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


