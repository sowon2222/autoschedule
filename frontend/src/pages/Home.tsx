import { useEffect, useState, useCallback } from 'react'
import Header from '../components/Header'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import api from '../lib/api'
import { useAuth } from '../store/auth'

type CalendarEventItem = {
  id: string
  title: string
  start: string
  end: string
  backgroundColor?: string
  borderColor?: string
  location?: string
  teamName?: string
  source?: 'TASK' | 'EVENT' | 'BREAK'
  extendedProps?: {
    type?: 'task' | 'event'
    priority?: number
  }
}

export default function Home() {
  const { user, logout, setUser } = useAuth()
  const [events, setEvents] = useState<CalendarEventItem[]>([])

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

  // 우선순위에 따른 색상 진하기 조절 (1이 가장 높음, 가장 진함)
  const getColorByPriority = (baseColor: string, priority: number): { bg: string; border: string } => {
    // hex 색상을 RGB로 변환
    const hex = baseColor.replace('#', '')
    const r = parseInt(hex.substring(0, 2), 16)
    const g = parseInt(hex.substring(2, 4), 16)
    const b = parseInt(hex.substring(4, 6), 16)

    // 우선순위 1-5에 따라 진하기 조절 (1이 가장 진함)
    // priority 1: 100% (원본), 2: 85%, 3: 70%, 4: 55%, 5: 40%
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

  // 팀 ID로 색상 가져오기
  const getTeamColor = (teamId: number): string => {
    const index = teamId % teamColors.length
    return teamColors[index].base
  }

  const loadUserEvents = useCallback(async (userId: number) => {
    try {
      // 사용자가 속한 팀 목록 조회
      const teamsResponse = await api.get(`/api/teams/user/${userId}`).catch(() => ({ data: [] }))
      const teams = teamsResponse.data || []

      console.log('User teams:', teams)
      
      // 팀 ID -> 색상 매핑 생성
      const teamColorMap = new Map<number, string>()
      teams.forEach((team: any) => {
        teamColorMap.set(team.id, getTeamColor(team.id))
      })

      // 사용자의 CalendarEvent와 담당자 Task, 각 팀의 Task를 모두 조회
      const [eventsResponse, assigneeTasksResponse] = await Promise.all([
        api.get(`/api/events/user/${userId}`).catch(() => ({ data: [] })),
        api.get(`/api/tasks/assignee/${userId}`).catch(() => ({ data: [] }))
      ])

      // 각 팀의 Task 조회
      const taskPromises = teams.map((team: any) =>
        api.get(`/api/tasks/team/${team.id}`).catch(() => ({ data: [] }))
      )
      const tasksResponses = await Promise.all(taskPromises)
      
      console.log('Assignee tasks:', assigneeTasksResponse.data)
      console.log('Team tasks responses:', tasksResponses)

      const calendarEvents: any[] = []

      // CalendarEvent 변환
      if (eventsResponse.data && Array.isArray(eventsResponse.data)) {
        eventsResponse.data.forEach((event: any) => {
          calendarEvents.push({
            id: `event-${event.id}`,
            title: event.title,
            start: event.startsAt,
            end: event.endsAt,
            location: event.location,
            teamName: event.teamName,
            source: event.source as 'TASK' | 'EVENT' | 'BREAK' | undefined,
            backgroundColor: event.source === 'TASK' ? '#3b82f6' : event.source === 'EVENT' ? '#22c55e' : event.source === 'BREAK' ? '#f97316' : '#22c55e',
            borderColor: event.source === 'TASK' ? '#2563eb' : event.source === 'EVENT' ? '#16a34a' : event.source === 'BREAK' ? '#ea580c' : '#16a34a'
          })
        })
      }

      // 중복 제거를 위한 Set
      const taskIds = new Set<string>()
      
      // 담당자 Task 변환
      if (assigneeTasksResponse.data && Array.isArray(assigneeTasksResponse.data)) {
        assigneeTasksResponse.data.forEach((task: any) => {
          if (task.dueAt) {
            const taskId = `task-${task.id}`
            taskIds.add(taskId)
            
            const startDate = new Date(task.dueAt)
            if (isNaN(startDate.getTime())) {
              console.warn('Invalid date for task:', task)
              return
            }
            
            const endDate = new Date(startDate.getTime() + (task.durationMin || 60) * 60 * 1000)
            const priority = task.priority || 3
            const teamId = task.teamId
            
            // 팀 색상 가져오기 (없으면 기본 파란색)
            const teamBaseColor = teamColorMap.get(teamId) || teamColors[0].base
            const colors = getColorByPriority(teamBaseColor, priority)
            
            calendarEvents.push({
              id: taskId,
              title: `📋 ${task.title}`,
              start: startDate.toISOString(),
              end: endDate.toISOString(),
              backgroundColor: colors.bg,
              borderColor: colors.border,
              extendedProps: {
                type: 'task',
                priority: task.priority,
                teamId: teamId
              }
            })
          }
        })
      }

      // 각 팀의 Task 변환 (마감일이 있는 모든 Task 표시, 중복 제거)
      tasksResponses.forEach((tasksResponse: any, teamIndex: number) => {
        if (tasksResponse.data && Array.isArray(tasksResponse.data)) {
          // 현재 팀 정보 가져오기
          const currentTeam = teams[teamIndex]
          const teamId = currentTeam?.id
          
          tasksResponse.data.forEach((task: any) => {
            const taskId = `task-${task.id}`
            
            // 이미 추가한 Task는 제외 (담당자 Task와 중복 방지)
            if (taskIds.has(taskId)) {
              return
            }
            
            // 마감일이 있는 모든 Task 표시
            if (task.dueAt) {
              const startDate = new Date(task.dueAt)
              if (isNaN(startDate.getTime())) {
                console.warn('Invalid date for task:', task)
                return
              }
              
              const endDate = new Date(startDate.getTime() + (task.durationMin || 60) * 60 * 1000)
              const priority = task.priority || 3
              
              // 팀 색상 가져오기 (task.teamId 또는 현재 팀 ID 사용)
              const taskTeamId = task.teamId || teamId
              const teamBaseColor = teamColorMap.get(taskTeamId) || teamColors[0].base
              const colors = getColorByPriority(teamBaseColor, priority)
              
              taskIds.add(taskId)
              calendarEvents.push({
                id: taskId,
                title: `📋 ${task.title}`,
                start: startDate.toISOString(),
                end: endDate.toISOString(),
                backgroundColor: colors.bg,
                borderColor: colors.border,
                extendedProps: {
                  type: 'task',
                  priority: task.priority,
                  teamId: taskTeamId
                }
              })
            }
          })
        }
      })

      console.log('Loaded events:', calendarEvents.length, 'items')
      setEvents(calendarEvents)
    } catch (error) {
      console.error('이벤트를 불러오는 중 오류가 발생했습니다.', error)
    }
  }, [])

  // 로그인 상태 확인 및 사용자 정보 로드
  useEffect(() => {
    const email = localStorage.getItem('userEmail')
    const name = localStorage.getItem('userName')
    const token = localStorage.getItem('accessToken')
    
    if (token && email && name && !user) {
      // 사용자 ID를 가져오기 위해 이메일로 조회
      api.get(`/api/users/email/${email}`)
        .then(response => {
          const userData = response.data
          setUser({
            id: userData.id,
            email: userData.email,
            name: userData.name
          })
          try { localStorage.setItem('userId', String(userData.id)) } catch {}
          
          // 사용자 이벤트 로드
          loadUserEvents(userData.id)
        })
        .catch(() => {
          // 사용자 정보를 가져올 수 없으면 로그아웃
          logout()
        })
    } else if (user) {
      // 이미 사용자 정보가 있으면 이벤트만 로드
      loadUserEvents(user.id)
    }
  }, [user, setUser, logout, loadUserEvents])

  const handleDateClick = (_dateClickArg: any) => {
    if (!user) {
      alert('로그인이 필요합니다.')
      return
    }
    // 날짜 클릭 시 이벤트 생성 기능 추가 가능
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-indigo-50">
      <Header />
      {/* 메인: 캘린더만 노출 */}
      <main className="mx-auto max-w-7xl px-6 py-6">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-lg overflow-hidden">
            {/* 캘린더 헤더 */}
          <div className="px-8 pt-8 pb-6 border-b border-gray-100 bg-gradient-to-r from-blue-50 to-indigo-50">
            <h2 className="text-3xl font-bold text-gray-900 mb-2">
              {user ? `${user?.name}님의 일정` : '일정 캘린더'}
            </h2>
          </div>
          
          {/* 캘린더 */}
          <div className="p-8">
            <div className="mb-4 space-y-2">
              <div className="flex gap-4 text-sm flex-wrap">
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 rounded bg-green-500"></div>
                  <span>일정 (Event)</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 rounded" style={{ backgroundColor: 'rgb(59, 130, 246)' }}></div>
                  <span>작업 (Task) - 팀별 색상</span>
                </div>
              </div>
              <div className="text-xs text-gray-600">
                <span className="font-semibold">우선순위 색상 진하기:</span> 
                <span className="ml-2">1(가장 진함) → 5(가장 연함)</span>
              </div>
            </div>
            <style>{`
              .fc-header-toolbar {
                margin-bottom: 2rem !important;
                padding: 0 !important;
              }
              .fc-toolbar-title {
                font-size: 1.5rem !important;
                font-weight: 700 !important;
                color: #111827 !important;
              }
              .fc-button {
                background-color: #f3f4f6 !important;
                border-color: #e5e7eb !important;
                color: #374151 !important;
                font-weight: 600 !important;
                padding: 0.5rem 1rem !important;
                border-radius: 0.5rem !important;
                transition: all 0.2s !important;
              }
              .fc-button:hover {
                background-color: #e5e7eb !important;
                border-color: #d1d5db !important;
              }
              .fc-button-active {
                background-color: #3b82f6 !important;
                border-color: #2563eb !important;
                color: white !important;
              }
              .fc-button-active:hover {
                background-color: #2563eb !important;
              }
              .fc-daygrid-day-frame {
                min-height: 100px;
              }
              .fc-daygrid-day:hover {
                background-color: #f9fafb !important;
              }
              .fc-event {
                border-radius: 0.375rem !important;
                padding: 2px 4px !important;
              }
            `}</style>
            <FullCalendar
              plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
              initialView="dayGridMonth"
              headerToolbar={{
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,timeGridDay'
              }}
              events={events}
              dateClick={handleDateClick}
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
        </div>
      </main>
    </div>
  )
}
