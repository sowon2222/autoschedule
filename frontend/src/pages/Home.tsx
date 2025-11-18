import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import Header from '../components/Header'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventClickArg } from '@fullcalendar/core'
import api from '../lib/api'
import { useAuth } from '../store/auth'
import type { StompSubscription } from '@stomp/stompjs'
import { createStompClient, safeJsonParse } from '../lib/ws'
import type { CollaborationNotificationMessage, TaskEventMessage } from '../lib/ws'
import CalendarEventModal from '../components/CalendarEventModal'
import CreateEventModal from '../components/CreateEventModal'

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
    teamId?: number
  }
}

type ToastItem = {
  id: number
  data: CollaborationNotificationMessage
}

type TaskItem = {
  id: number
  title: string
  dueAt?: string
  priority: number
  teamName?: string
  durationMin: number
}

export default function Home() {
  const { user, logout, setUser } = useAuth()
  const navigate = useNavigate()
  const [events, setEvents] = useState<CalendarEventItem[]>([])
  const [notifications, setNotifications] = useState<ToastItem[]>([])
  const [userTeams, setUserTeams] = useState<Array<{ id: number; name: string }>>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedEventId, setSelectedEventId] = useState<string>('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createModalDate, setCreateModalDate] = useState<Date | undefined>()
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const [createTaskModalOpen, setCreateTaskModalOpen] = useState(false)
  const [openNavDropdown, setOpenNavDropdown] = useState<string | null>(null)
  const [taskFormData, setTaskFormData] = useState({
    teamId: '',
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
  const [teamMembers, setTeamMembers] = useState<Array<{ userId: number; userName: string; userEmail: string }>>([])

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

  const loadUserTasks = useCallback(async (userId: number) => {
    try {
      // 사용자가 속한 팀 목록 조회
      const teamsResponse = await api.get(`/api/teams/user/${userId}`).catch(() => ({ data: [] }))
      const teams = teamsResponse.data || []
      
      if (teams.length === 0) {
        setTasks([])
        return
      }
      
      // 각 팀의 모든 작업 조회 (담당자 여부와 상관없이)
      const taskPromises = teams.map((team: any) =>
        api.get(`/api/tasks/team/${team.id}`).catch(() => ({ data: [] }))
      )
      const tasksResponses = await Promise.all(taskPromises)
      
      // 모든 팀의 작업을 하나의 배열로 합치기
      const allTasks: any[] = []
      tasksResponses.forEach((tasksResponse: any, index: number) => {
        if (tasksResponse.data && Array.isArray(tasksResponse.data)) {
          tasksResponse.data.forEach((task: any) => {
            allTasks.push({
              ...task,
              teamName: teams[index]?.name || task.teamName
            })
          })
        }
      })
      
      // 중복 제거 (같은 작업이 여러 팀에 있을 수 있으므로)
      const uniqueTasks = Array.from(
        new Map(allTasks.map(task => [task.id, task])).values()
      )
      
      // 마감일이 있는 작업만 필터링하고 정렬 (오늘 날짜 우선, 그 다음 날짜순)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      
      const sortedTasks = uniqueTasks
        .filter((task: any) => task.dueAt) // 마감일이 있는 것만
        .sort((a: any, b: any) => {
          const dateA = new Date(a.dueAt)
          const dateB = new Date(b.dueAt)
          const isTodayA = dateA.toDateString() === today.toDateString()
          const isTodayB = dateB.toDateString() === today.toDateString()
          
          // 오늘 날짜가 우선
          if (isTodayA && !isTodayB) return -1
          if (!isTodayA && isTodayB) return 1
          
          // 같은 날짜 그룹 내에서는 날짜순 정렬
          return dateA.getTime() - dateB.getTime()
        })
        .slice(0, 10) // 최대 10개
        .map((task: any) => ({
          id: task.id,
          title: task.title,
          dueAt: task.dueAt,
          priority: task.priority,
          teamName: task.teamName,
          durationMin: task.durationMin
        }))
      
      setTasks(sortedTasks)
    } catch (error) {
      console.error('작업 목록을 불러오는 중 오류가 발생했습니다.', error)
    }
  }, [])

  const loadUserEvents = useCallback(async (userId: number) => {
    try {
      // 사용자가 속한 팀 목록 조회
      const teamsResponse = await api.get(`/api/teams/user/${userId}`).catch(() => ({ data: [] }))
      const teams = teamsResponse.data || []
      setUserTeams(teams)

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
          
          // 사용자 이벤트와 작업 로드
          loadUserEvents(userData.id)
          loadUserTasks(userData.id)
        })
        .catch(() => {
          // 사용자 정보를 가져올 수 없으면 로그아웃
          logout()
        })
    } else if (user) {
      // 이미 사용자 정보가 있으면 이벤트와 작업 로드
      loadUserEvents(user.id)
      loadUserTasks(user.id)
    }
  }, [user, setUser, logout, loadUserEvents, loadUserTasks])

  // 사용자가 속한 모든 팀의 알림 및 작업 이벤트 구독
  useEffect(() => {
    if (!userTeams.length || !user?.id) return

    const client = createStompClient()
    const subscriptions: StompSubscription[] = []

    // 팀 ID -> 색상 매핑 생성
    const teamColorMap = new Map<number, string>()
    userTeams.forEach((team: any) => {
      teamColorMap.set(team.id, getTeamColor(team.id))
    })

    const pushNotification = (message: CollaborationNotificationMessage) => {
      const toast: ToastItem = { id: Date.now(), data: message }
      setNotifications((prev) => [toast, ...prev].slice(0, 3))
      console.log('[Home] Received notification:', message.title)
      window.setTimeout(() => {
        setNotifications((prev) => prev.filter((item) => item.id !== toast.id))
      }, 10000)
    }

    const upsertTaskEvent = (message: TaskEventMessage) => {
      const taskId = message.task?.id ?? message.taskId
      if (!taskId) {
        console.warn('[Home] Task event missing taskId:', message)
        return
      }
      const calendarId = `task-${taskId}`

      // 삭제된 작업이거나 작업 정보가 없으면 캘린더에서 제거
      if (message.action === 'DELETED' || !message.task) {
        console.log('[Home] Removing task from calendar:', taskId, message.action)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }

      // 마감일시가 없으면 캘린더에 표시하지 않음
      if (!message.task.dueAt) {
        console.log('[Home] Task has no dueAt, skipping calendar display:', taskId)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }

      const dueDate = new Date(message.task.dueAt)
      if (isNaN(dueDate.getTime())) {
        console.warn('[Home] Invalid dueAt date:', message.task.dueAt)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }

      const endDate = new Date(dueDate.getTime() + (message.task.durationMin ?? 0) * 60 * 1000)
      const priority = message.task.priority ?? 3
      const teamId = message.task.teamId
      const teamBaseColor = teamId ? (teamColorMap.get(teamId) || teamColors[0].base) : teamColors[0].base
      const colors = getColorByPriority(teamBaseColor, priority)

      const converted: CalendarEventItem = {
        id: calendarId,
        title: `📋 ${message.task.title}`,
        start: dueDate.toISOString(),
        end: endDate.toISOString(),
        backgroundColor: colors.bg,
        borderColor: colors.border,
        extendedProps: {
          type: 'task',
          priority,
          teamId: teamId ?? undefined
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
      
      // 작업 목록도 새로고침 (사용자가 담당자인 경우)
      if (user?.id && message.task?.assigneeId === user.id) {
        loadUserTasks(user.id)
      }
    }

    client.onConnect = () => {
      console.log('[Home] WebSocket connected, subscribing to notifications and tasks for teams:', userTeams.map(t => t.id))
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0

      // 각 팀의 알림 구독
      userTeams.forEach((team) => {
        subscriptions.push(
          client.subscribe(`/topic/notifications/team/${team.id}`, (frame) => {
            const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
            if (!payload) {
              console.warn('[Home] Failed to parse notification:', frame.body)
              return
            }
            console.log('[Home] Received team notification:', payload.title, 'for team:', team.id)
            pushNotification(payload)
          })
        )
      })

      // 각 팀의 작업 이벤트 구독 (캘린더 실시간 업데이트)
      userTeams.forEach((team) => {
        subscriptions.push(
          client.subscribe(`/topic/tasks/${team.id}`, (frame) => {
            const payload = safeJsonParse<TaskEventMessage>(frame.body)
            if (!payload) {
              console.warn('[Home] Failed to parse task event:', frame.body)
              return
            }
            console.log('[Home] Received task event:', payload.action, 'for team:', team.id)
            upsertTaskEvent(payload)
          })
        )
      })

      // 사용자 개인 알림 구독
      subscriptions.push(
        client.subscribe(`/topic/notifications/user/${user.id}`, (frame) => {
          const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
          if (!payload) return
          console.log('[Home] Received user notification:', payload.title)
          pushNotification(payload)
        })
      )
    }

    client.activate()

    return () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
    }
  }, [userTeams, user?.id])

  const handleDateClick = (dateClickArg: any) => {
    if (!user) {
      alert('로그인이 필요합니다.')
      return
    }
    // 날짜 클릭 시 이벤트 생성 모달 열기
    setCreateModalDate(dateClickArg.date)
    setCreateModalOpen(true)
  }

  const handleEventClick = (clickInfo: EventClickArg) => {
    setSelectedEventId(clickInfo.event.id)
    setModalOpen(true)
  }

  const handleTaskClick = (taskId: number) => {
    setSelectedEventId(`task-${taskId}`)
    setModalOpen(true)
  }

  const isToday = (dateString?: string) => {
    if (!dateString) return false
    const date = new Date(dateString)
    const today = new Date()
    return date.toDateString() === today.toDateString()
  }

  const formatDate = (dateString?: string) => {
    if (!dateString) return ''
    const date = new Date(dateString)
    const today = new Date()
    const tomorrow = new Date(today)
    tomorrow.setDate(tomorrow.getDate() + 1)
    
    if (date.toDateString() === today.toDateString()) {
      return `오늘 ${date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`
    } else if (date.toDateString() === tomorrow.toDateString()) {
      return `내일 ${date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`
    } else {
      return date.toLocaleString('ko-KR', { 
        month: 'short', 
        day: 'numeric',
        hour: '2-digit', 
        minute: '2-digit' 
      })
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-indigo-50">
      <Header />
      {/* 알림 표시 */}
      <div className="fixed top-20 right-4 z-50 space-y-2">
        {notifications.map((toast) => (
          <div
            key={toast.id}
            className="bg-white border border-gray-200 rounded-lg shadow-lg p-4 min-w-[300px] max-w-md animate-in slide-in-from-right"
          >
            <div className="font-semibold text-gray-900">{toast.data.title}</div>
            <div className="text-sm text-gray-600 mt-1">{toast.data.content}</div>
          </div>
        ))}
      </div>
      {/* 메인: 사이드바 + 캘린더 */}
      <main className="flex gap-6 w-full">
        {/* 왼쪽 사이드바: 해야할 일 목록 */}
        <aside className="w-80 flex-shrink-0 pl-6 pr-0">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-lg overflow-hidden h-full">
            {/* 헤더 */}
            <div className="px-6 py-4 border-b border-gray-200 bg-gradient-to-r from-blue-50 to-indigo-50">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-xl font-bold text-gray-900">해야할 일</h3>
                  <p className="text-sm text-gray-600 mt-1">{tasks.length}개의 작업</p>
                </div>
                <button
                  onClick={() => setCreateTaskModalOpen(true)}
                  className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
                >
                  작업 추가
                </button>
              </div>
            </div>
            
            {/* 작업 목록 */}
            <div className="max-h-[calc(100vh-12rem)] overflow-y-auto">
              {tasks.length === 0 ? (
                <div className="px-6 py-8 text-center text-gray-500">
                  <p className="text-sm">할 일이 없습니다</p>
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {tasks.map((task) => {
                    const today = isToday(task.dueAt)
                    return (
                      <div
                        key={task.id}
                        onClick={() => handleTaskClick(task.id)}
                        className={`px-6 py-4 cursor-pointer transition-all hover:bg-gray-50 ${
                          today ? 'bg-blue-50 border-l-4 border-blue-500' : ''
                        }`}
                      >
                        <div className={`flex items-start gap-3 ${today ? '' : 'opacity-60'}`}>
                          <div className={`flex-shrink-0 w-2 h-2 rounded-full mt-2 ${
                            today ? 'bg-blue-500' : 'bg-gray-300'
                          }`}></div>
                          <div className="flex-1 min-w-0">
                            <h4 className={`font-medium text-gray-900 truncate ${
                              today ? 'font-semibold' : ''
                            }`}>
                              {task.title}
                            </h4>
                            <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                              <span>{formatDate(task.dueAt)}</span>
                              {task.teamName && (
                                <>
                                  <span>•</span>
                                  <span className="truncate">{task.teamName}</span>
                                </>
                              )}
                            </div>
                            <div className="mt-1 flex items-center gap-2">
                              <span className={`text-xs px-2 py-0.5 rounded ${
                                task.priority <= 2 
                                  ? 'bg-red-100 text-red-700' 
                                  : task.priority === 3
                                  ? 'bg-yellow-100 text-yellow-700'
                                  : 'bg-gray-100 text-gray-700'
                              }`}>
                                우선순위 {task.priority}
                              </span>
                              <span className="text-xs text-gray-500">
                                {task.durationMin}분
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </aside>

        {/* 오른쪽: 캘린더 */}
        <div className="flex-1 min-w-0 pl-0 pr-6">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-lg overflow-hidden h-full w-[80%]">
            {/* 캘린더 헤더 */}
          <div className="px-6 py-4 border-b border-gray-100 bg-gradient-to-r from-blue-50 to-indigo-50">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold text-gray-900">
                  {user ? `${user?.name}님의 일정` : '일정 캘린더'}
                </h2>
              </div>
              {/* TeamSpace 네비게이션 */}
              <nav className="flex items-center gap-1 text-sm">
                <div className="relative">
                  <button
                    onClick={() => setOpenNavDropdown(openNavDropdown === 'calendar' ? null : 'calendar')}
                    className="px-4 py-2 rounded-lg font-medium text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700 transition-all"
                  >
                    캘린더
                  </button>
                  {openNavDropdown === 'calendar' && (
                    <div className="absolute right-0 mt-2 w-64 rounded-xl border-2 border-gray-200 bg-white shadow-2xl p-2 z-50">
                      <div className="max-h-72 overflow-auto">
                        {userTeams.length === 0 ? (
                          <div className="px-4 py-3 text-sm text-gray-500 bg-gray-50 rounded-lg">소속된 팀이 없습니다</div>
                        ) : (
                          userTeams.map((team) => (
                            <button
                              key={team.id}
                              className="w-full text-left px-4 py-3 rounded-lg hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 text-sm font-medium text-gray-700 hover:text-blue-700 transition-all"
                              onClick={() => {
                                setOpenNavDropdown(null)
                                navigate(`/team/${team.id}/calendar`)
                              }}
                            >
                              {team.name}
                            </button>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
                <div className="relative">
                  <button
                    onClick={() => setOpenNavDropdown(openNavDropdown === 'tasks' ? null : 'tasks')}
                    className="px-4 py-2 rounded-lg font-medium text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700 transition-all"
                  >
                    작업
                  </button>
                  {openNavDropdown === 'tasks' && (
                    <div className="absolute right-0 mt-2 w-64 rounded-xl border-2 border-gray-200 bg-white shadow-2xl p-2 z-50">
                      <div className="max-h-72 overflow-auto">
                        {userTeams.length === 0 ? (
                          <div className="px-4 py-3 text-sm text-gray-500 bg-gray-50 rounded-lg">소속된 팀이 없습니다</div>
                        ) : (
                          userTeams.map((team) => (
                            <button
                              key={team.id}
                              className="w-full text-left px-4 py-3 rounded-lg hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 text-sm font-medium text-gray-700 hover:text-blue-700 transition-all"
                              onClick={() => {
                                setOpenNavDropdown(null)
                                navigate(`/team/${team.id}/tasks`)
                              }}
                            >
                              {team.name}
                            </button>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
                <div className="relative">
                  <button
                    onClick={() => setOpenNavDropdown(openNavDropdown === 'workhours' ? null : 'workhours')}
                    className="px-4 py-2 rounded-lg font-medium text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700 transition-all"
                  >
                    근무시간
                  </button>
                  {openNavDropdown === 'workhours' && (
                    <div className="absolute right-0 mt-2 w-64 rounded-xl border-2 border-gray-200 bg-white shadow-2xl p-2 z-50">
                      <div className="max-h-72 overflow-auto">
                        {userTeams.length === 0 ? (
                          <div className="px-4 py-3 text-sm text-gray-500 bg-gray-50 rounded-lg">소속된 팀이 없습니다</div>
                        ) : (
                          userTeams.map((team) => (
                            <button
                              key={team.id}
                              className="w-full text-left px-4 py-3 rounded-lg hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 text-sm font-medium text-gray-700 hover:text-blue-700 transition-all"
                              onClick={() => {
                                setOpenNavDropdown(null)
                                navigate(`/team/${team.id}/workhours`)
                              }}
                            >
                              {team.name}
                            </button>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
                <div className="relative">
                  <button
                    onClick={() => setOpenNavDropdown(openNavDropdown === 'settings' ? null : 'settings')}
                    className="px-4 py-2 rounded-lg font-medium text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700 transition-all"
                  >
                    팀 설정
                  </button>
                  {openNavDropdown === 'settings' && (
                    <div className="absolute right-0 mt-2 w-64 rounded-xl border-2 border-gray-200 bg-white shadow-2xl p-2 z-50">
                      <div className="max-h-72 overflow-auto">
                        {userTeams.length === 0 ? (
                          <div className="px-4 py-3 text-sm text-gray-500 bg-gray-50 rounded-lg">소속된 팀이 없습니다</div>
                        ) : (
                          userTeams.map((team) => (
                            <button
                              key={team.id}
                              className="w-full text-left px-4 py-3 rounded-lg hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 text-sm font-medium text-gray-700 hover:text-blue-700 transition-all"
                              onClick={() => {
                                setOpenNavDropdown(null)
                                navigate(`/team/${team.id}/settings`)
                              }}
                            >
                              {team.name}
                            </button>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </nav>
            </div>
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
              eventClick={handleEventClick}
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
        </div>
      </main>
      <CalendarEventModal
        isOpen={modalOpen}
        onClose={() => {
          setModalOpen(false)
          setSelectedEventId('')
        }}
        eventId={selectedEventId}
        onUpdate={() => {
          // 모달에서 업데이트 후 이벤트 목록 새로고침
          if (user) {
            loadUserEvents(user.id)
            loadUserTasks(user.id)
          }
        }}
      />
      <CreateEventModal
        isOpen={createModalOpen}
        onClose={() => {
          setCreateModalOpen(false)
          setCreateModalDate(undefined)
        }}
        defaultDate={createModalDate}
        onSuccess={() => {
          // 이벤트 생성 후 목록 새로고침
          if (user) {
            loadUserEvents(user.id)
            loadUserTasks(user.id)
          }
        }}
      />
      
      {/* 작업 추가 모달 */}
      {createTaskModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50" onClick={() => setCreateTaskModalOpen(false)}>
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
              <h2 className="text-xl font-bold text-gray-900">작업 추가</h2>
              <button
                onClick={() => setCreateTaskModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 text-2xl font-bold"
              >
                ×
              </button>
            </div>
            
            <form onSubmit={async (e) => {
              e.preventDefault()
              if (!taskFormData.teamId || !taskFormData.title || !taskFormData.dueAt) {
                alert('팀, 제목, 마감일은 필수입니다.')
                return
              }
              
              try {
                // 시작 시간과 마감 시간을 기반으로 durationMin 계산
                let durationMin = 60 // 기본값
                if (taskFormData.startAt && taskFormData.dueAt) {
                  const start = new Date(taskFormData.startAt)
                  const end = new Date(taskFormData.dueAt)
                  if (end > start) {
                    durationMin = Math.round((end.getTime() - start.getTime()) / (1000 * 60))
                  } else {
                    alert('마감 시간은 시작 시간보다 늦어야 합니다.')
                    return
                  }
                } else if (taskFormData.dueAt) {
                  durationMin = 60
                }
                
                const payload: any = {
                  teamId: Number(taskFormData.teamId),
                  title: taskFormData.title,
                  durationMin: durationMin,
                  dueAt: taskFormData.dueAt ? new Date(taskFormData.dueAt).toISOString() : null,
                  priority: taskFormData.priority,
                  assigneeId: taskFormData.assigneeId || null,
                  splittable: taskFormData.splittable,
                  tags: taskFormData.tags || null
                }
                
                if (taskFormData.recurrenceEnabled) {
                  payload.recurrenceType = taskFormData.recurrenceType
                  payload.recurrenceEndDate = taskFormData.recurrenceEndDate 
                    ? new Date(taskFormData.recurrenceEndDate).toISOString() 
                    : null
                }
                
                await api.post('/api/tasks', payload)
                setCreateTaskModalOpen(false)
                setTaskFormData({
                  teamId: '',
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
                setTeamMembers([])
                if (user) {
                  loadUserTasks(user.id)
                  loadUserEvents(user.id)
                }
              } catch (error: any) {
                alert(error.response?.data?.message || '작업 추가에 실패했습니다.')
              }
            }} className="px-6 py-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">팀 *</label>
                <select
                  value={taskFormData.teamId}
                  onChange={async (e) => {
                    const teamId = e.target.value
                    setTaskFormData({ ...taskFormData, teamId, assigneeId: undefined })
                    if (teamId) {
                      try {
                        const response = await api.get(`/api/teams/${teamId}/members`)
                        setTeamMembers(response.data || [])
                      } catch (err) {
                        console.error('Failed to load team members:', err)
                        setTeamMembers([])
                      }
                    } else {
                      setTeamMembers([])
                    }
                  }}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">팀 선택</option>
                  {userTeams.map((team) => (
                    <option key={team.id} value={team.id}>
                      {team.name}
                    </option>
                  ))}
                </select>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">제목 *</label>
                <input
                  type="text"
                  value={taskFormData.title}
                  onChange={(e) => setTaskFormData({ ...taskFormData, title: e.target.value })}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">시작 날짜/시간</label>
                  <input
                    type="datetime-local"
                    value={taskFormData.startAt}
                    onChange={(e) => setTaskFormData({ ...taskFormData, startAt: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">마감 날짜/시간 *</label>
                  <input
                    type="datetime-local"
                    value={taskFormData.dueAt}
                    onChange={(e) => setTaskFormData({ ...taskFormData, dueAt: e.target.value })}
                    required
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">우선순위 (1-5)</label>
                  <input
                    type="number"
                    min="1"
                    max="5"
                    value={taskFormData.priority}
                    onChange={(e) => setTaskFormData({ ...taskFormData, priority: Number(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">담당자 (선택)</label>
                  <select
                    value={taskFormData.assigneeId || ''}
                    onChange={(e) => setTaskFormData({ ...taskFormData, assigneeId: e.target.value ? Number(e.target.value) : undefined })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">담당자 선택</option>
                    {teamMembers.map((member) => (
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
                  type="text"
                  value={taskFormData.tags}
                  onChange={(e) => setTaskFormData({ ...taskFormData, tags: e.target.value })}
                  placeholder="태그"
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="splittable-task"
                  checked={taskFormData.splittable}
                  onChange={(e) => setTaskFormData({ ...taskFormData, splittable: e.target.checked })}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <label htmlFor="splittable-task" className="text-sm text-gray-700">분할 가능</label>
              </div>
              
              {/* 반복 작업 옵션 */}
              <div className="border-t border-gray-200 pt-4">
                <label className="flex items-center gap-2 mb-3">
                  <input
                    type="checkbox"
                    checked={taskFormData.recurrenceEnabled}
                    onChange={(e) => setTaskFormData({ ...taskFormData, recurrenceEnabled: e.target.checked })}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm font-medium text-gray-700">반복 작업</span>
                </label>
                
                {taskFormData.recurrenceEnabled && (
                  <div className="ml-6 space-y-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">반복 주기</label>
                      <select
                        value={taskFormData.recurrenceType}
                        onChange={(e) => setTaskFormData({ ...taskFormData, recurrenceType: e.target.value as any })}
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
                        value={taskFormData.recurrenceEndDate}
                        onChange={(e) => setTaskFormData({ ...taskFormData, recurrenceEndDate: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <p className="text-xs text-gray-500 mt-1">비워두면 1년 후까지 반복됩니다</p>
                    </div>
                  </div>
                )}
              </div>
              
              <div className="flex items-center justify-end gap-2 pt-4 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => {
                    setCreateTaskModalOpen(false)
                    setTaskFormData({
                      teamId: '',
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
                    setTeamMembers([])
                  }}
                  className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition"
                >
                  취소
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-white bg-blue-600 rounded-md hover:bg-blue-700 transition"
                >
                  저장
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
