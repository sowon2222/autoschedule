import { useEffect, useState, useCallback } from 'react'
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
import type { CollaborationNotificationMessage, TaskEventMessage, CalendarEventMessage } from '../lib/ws'
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
  className?: string
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
  startsAt?: string  // Assignment의 시작 시간 (있으면 이걸 표시)
  endsAt?: string    // Assignment의 종료 시간
  priority: number
  teamName?: string
  durationMin: number
  hasAssignment?: boolean  // Assignment가 있는지 여부
}

export default function Home() {
  const { user, logout, setUser } = useAuth()
  const [events, setEvents] = useState<CalendarEventItem[]>([])
  const [notifications, setNotifications] = useState<ToastItem[]>([])
  const [userTeams, setUserTeams] = useState<Array<{ id: number; name: string }>>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedEventId, setSelectedEventId] = useState<string>('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createModalDate, setCreateModalDate] = useState<Date | undefined>()
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const [createTaskModalOpen, setCreateTaskModalOpen] = useState(false)
  const [taskFormData, setTaskFormData] = useState({
    teamId: '',
    title: '',
    dueDate: '',
    dueTime: '18:00',
    durationMin: 60,
    priority: 3,
    assigneeId: undefined as number | undefined,
    splittable: true,
    tags: '',
    recurrenceEnabled: false,
    recurrenceType: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY',
    recurrenceEndDate: ''
  })
  const [teamMembers, setTeamMembers] = useState<Array<{ userId: number; userName: string; userEmail: string }>>([])
  const [isGeneratingSchedule, setIsGeneratingSchedule] = useState(false)
  const [scheduleScores, setScheduleScores] = useState<Record<number, number | null>>({})
  const [unassignedTasks, setUnassignedTasks] = useState<Record<number, Array<{ taskId: number; reason: string }>>>({})
  const [calendarViewMode, setCalendarViewMode] = useState<'events' | 'schedule'>('events') // 'events': 일정 보기, 'schedule': 스케줄 보기
  const [scheduleEvents, setScheduleEvents] = useState<CalendarEventItem[]>([]) // 스케줄 보기용 이벤트 (Assignment 기반)

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
        setScheduleEvents([])
        return
      }
      
      // 날짜 범위 설정: 오늘부터 60일 후까지
      const today = new Date()
      const startDate = new Date(today)
      startDate.setHours(0, 0, 0, 0)
      const endDate = new Date(today)
      endDate.setDate(endDate.getDate() + 60)
      endDate.setHours(23, 59, 59, 999)
      
      const startParam = startDate.toISOString()
      const endParam = endDate.toISOString()
      
      // 각 팀의 Task와 Assignment를 모두 조회
      const [taskPromises, assignmentPromises] = await Promise.all([
        Promise.all(teams.map((team: any) =>
          api.get(`/api/tasks/team/${team.id}`).catch(() => ({ data: [] }))
        )),
        Promise.all(teams.map((team: any) =>
          api.get(`/api/assignments/team/${team.id}`, {
            params: {
              start: startParam,
              end: endParam
            }
          }).catch(() => ({ data: [] }))
        ))
      ])
      
      // 팀 ID -> 색상 매핑 생성
      const teamColorMap = new Map<number, string>()
      teams.forEach((team: any) => {
        teamColorMap.set(team.id, getTeamColor(team.id))
      })
      
      // Assignment를 수집 (중복 제거를 위해 Assignment ID 사용)
      const assignmentMap = new Map<number, any>() // Assignment ID -> Assignment
      const assignmentsByTaskIdForSidebar = new Map<number, Array<{ id: number; startsAt: string; endsAt: string; title?: string }>>()
      const duplicateCheck = new Set<string>() // 중복 체크용: "taskId-startsAt-endsAt"
      
      let totalAssignments = 0
      let duplicateCount = 0
      
      assignmentPromises.forEach((assignmentResponse: any, teamIndex: number) => {
        if (assignmentResponse.data && Array.isArray(assignmentResponse.data)) {
          assignmentResponse.data.forEach((assignment: any) => {
            totalAssignments++
            
            // Assignment ID로 중복 제거
            if (assignment.id && assignment.taskId && assignment.startsAt && assignment.endsAt) {
              // 추가 중복 체크: taskId + startsAt + endsAt 조합
              const duplicateKey = `${assignment.taskId}-${assignment.startsAt}-${assignment.endsAt}`
              
              if (assignmentMap.has(assignment.id)) {
                duplicateCount++
                console.warn(`[Schedule] 중복된 Assignment ID 발견: ${assignment.id}, 팀: ${teams[teamIndex]?.name}`)
                return
              }
              
              if (duplicateCheck.has(duplicateKey)) {
                duplicateCount++
                console.warn(`[Schedule] 중복된 Assignment 시간 조합 발견: taskId=${assignment.taskId}, startsAt=${assignment.startsAt}, endsAt=${assignment.endsAt}`)
                return
              }
              
              assignmentMap.set(assignment.id, assignment)
              duplicateCheck.add(duplicateKey)
              
              // Task ID로도 매핑 (사이드바용)
              if (!assignmentsByTaskIdForSidebar.has(assignment.taskId)) {
                assignmentsByTaskIdForSidebar.set(assignment.taskId, [])
              }
              assignmentsByTaskIdForSidebar.get(assignment.taskId)!.push({
                id: assignment.id,
                startsAt: assignment.startsAt,
                endsAt: assignment.endsAt,
                title: assignment.title
              })
            }
          })
        }
      })
      
      console.log(`[Schedule] Assignment 수집 완료: 총 ${totalAssignments}개, 중복 제거 후 ${assignmentMap.size}개, 중복 ${duplicateCount}개`)
      
      // Task ID별로 Assignment 그룹화 (분할된 작업들을 하나로 합치기)
      const assignmentsByTaskId = new Map<number, any[]>()
      assignmentMap.forEach((assignment) => {
        const taskId = assignment.taskId
        if (taskId) {
          if (!assignmentsByTaskId.has(taskId)) {
            assignmentsByTaskId.set(taskId, [])
          }
          assignmentsByTaskId.get(taskId)!.push(assignment)
        }
      })
      
      // 스케줄 이벤트 생성 (분할된 작업들을 하나의 이벤트로 합침)
      const scheduleEventList: CalendarEventItem[] = []
      const eventIdSet = new Set<string>() // FullCalendar 이벤트 ID 중복 체크
      
      assignmentsByTaskId.forEach((assignments, taskId) => {
        // Task 정보 찾기
        let taskInfo: any = null
        taskPromises.forEach((tasksResponse: any, teamIndex: number) => {
          if (tasksResponse.data && Array.isArray(tasksResponse.data)) {
            const task = tasksResponse.data.find((t: any) => t.id === taskId)
            if (task) {
              taskInfo = {
                ...task,
                teamName: teams[teamIndex]?.name || task.teamName
              }
            }
          }
        })
        
        if (!taskInfo) return
        
        const priority = taskInfo.priority || 3
        const teamId = taskInfo.teamId
        const teamBaseColor = teamColorMap.get(teamId) || teamColors[0].base
        const colors = getColorByPriority(teamBaseColor, priority)
        
        // 분할된 작업들을 시간 순서로 정렬
        const sortedAssignments = assignments.sort((a, b) => {
          const startA = new Date(a.startsAt).getTime()
          const startB = new Date(b.startsAt).getTime()
          return startA - startB
        })
        
        // 분할된 작업이 여러 개인 경우: 가장 빠른 시작 시간과 가장 늦은 종료 시간으로 하나의 이벤트 생성
        if (sortedAssignments.length > 1) {
          const firstAssignment = sortedAssignments[0]
          const lastAssignment = sortedAssignments[sortedAssignments.length - 1]
          
          const eventId = `schedule-task-${taskId}` // Task ID를 사용하여 분할된 작업들을 하나로 합침
          
          if (eventIdSet.has(eventId)) {
            console.warn(`[Schedule] 중복된 이벤트 ID 발견: ${eventId}`)
            return
          }
          eventIdSet.add(eventId)
          
          // 분할된 작업의 제목에서 "(부분 N)" 제거
          const baseTitle = taskInfo.title
          const title = `${baseTitle} (${sortedAssignments.length}개 부분)`
          
          scheduleEventList.push({
            id: eventId,
            title: title,
            start: firstAssignment.startsAt,
            end: lastAssignment.endsAt,
            backgroundColor: colors.bg,
            borderColor: colors.border,
            extendedProps: {
              type: 'task',
              priority: priority,
              teamId: teamId
            }
          })
        } else {
          // 분할되지 않은 작업: 단일 Assignment
          const assignment = sortedAssignments[0]
          const eventId = `schedule-${assignment.id}`
          
          if (eventIdSet.has(eventId)) {
            console.warn(`[Schedule] 중복된 이벤트 ID 발견: ${eventId}`)
            return
          }
          eventIdSet.add(eventId)
          
          scheduleEventList.push({
            id: eventId,
            title: assignment.title || taskInfo.title,
            start: assignment.startsAt,
            end: assignment.endsAt,
            backgroundColor: colors.bg,
            borderColor: colors.border,
            extendedProps: {
              type: 'task',
              priority: priority,
              teamId: teamId
            }
          })
        }
      })
      
      console.log(`[Schedule] 스케줄 이벤트 생성 완료: ${scheduleEventList.length}개 (분할된 작업 포함)`)
      setScheduleEvents(scheduleEventList)
      
      // 모든 팀의 작업을 하나의 배열로 합치기
      const allTasks: any[] = []
      taskPromises.forEach((tasksResponse: any, index: number) => {
        if (tasksResponse.data && Array.isArray(tasksResponse.data)) {
          tasksResponse.data.forEach((task: any) => {
            // Assignment 정보 가져오기
            const assignments = assignmentsByTaskIdForSidebar.get(task.id)
            const firstAssignment = assignments && assignments.length > 0 ? assignments[0] : null
            
            allTasks.push({
              ...task,
              teamName: teams[index]?.name || task.teamName,
              startsAt: firstAssignment?.startsAt,  // Assignment가 있으면 시작 시간
              endsAt: firstAssignment?.endsAt,        // Assignment가 있으면 종료 시간
              hasAssignment: !!firstAssignment        // Assignment 여부
            })
          })
        }
      })
      
      // 중복 제거 (같은 작업이 여러 팀에 있을 수 있으므로)
      const uniqueTasks = Array.from(
        new Map(allTasks.map(task => [task.id, task])).values()
      )
      
      // 마감일이 있거나 Assignment가 있는 작업만 필터링하고 정렬
      const todayDate = new Date()
      todayDate.setHours(0, 0, 0, 0)
      
      const sortedTasks = uniqueTasks
        .filter((task: any) => task.dueAt || task.hasAssignment) // 마감일이 있거나 Assignment가 있는 것만
        .sort((a: any, b: any) => {
          // Assignment가 있으면 Assignment 시간 기준, 없으면 마감일 기준
          const dateA = a.hasAssignment && a.startsAt 
            ? new Date(a.startsAt) 
            : a.dueAt ? new Date(a.dueAt) : new Date(0)
          const dateB = b.hasAssignment && b.startsAt 
            ? new Date(b.startsAt) 
            : b.dueAt ? new Date(b.dueAt) : new Date(0)
          
          const isTodayA = dateA.toDateString() === todayDate.toDateString()
          const isTodayB = dateB.toDateString() === todayDate.toDateString()
          
          // 오늘 날짜가 우선
          if (isTodayA && !isTodayB) return -1
          if (!isTodayA && isTodayB) return 1
          
          // 같은 날짜 그룹 내에서는 시간순 정렬
          return dateA.getTime() - dateB.getTime()
        })
        .slice(0, 10) // 최대 10개
        .map((task: any) => ({
          id: task.id,
          title: task.title,
          dueAt: task.dueAt,
          startsAt: task.startsAt,  // Assignment 시작 시간
          endsAt: task.endsAt,       // Assignment 종료 시간
          priority: task.priority,
          teamName: task.teamName,
          durationMin: task.durationMin,
          hasAssignment: task.hasAssignment
        }))
      
      setTasks(sortedTasks)
    } catch (error) {
      console.error('작업 목록을 불러오는 중 오류가 발생했습니다.', error)
    }
  }, [])

  const handleGenerateSchedule = async () => {
    if (userTeams.length === 0) {
      alert('속한 팀이 없습니다.')
      return
    }
    
    setIsGeneratingSchedule(true)
    const newScores: Record<number, number | null> = {}
    const newUnassignedTasks: Record<number, Array<{ taskId: number; reason: string }>> = {}
    
    try {
      // 오늘부터 30일 후까지 스케줄 생성
      const today = new Date()
      const rangeStart = today.toISOString().split('T')[0]
      const rangeEnd = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
      
      // 모든 팀에 대해 순차적으로 스케줄 생성
      for (const team of userTeams) {
        try {
          const response = await api.post('/api/schedules/generate', {
            teamId: team.id,
            rangeStart,
            rangeEnd
          })
          
          // 점수 저장
          if (response.data.score !== null && response.data.score !== undefined) {
            newScores[team.id] = response.data.score
          }
          
          // 배치되지 않은 작업 저장
          if (response.data.unassignedTasks && response.data.unassignedTasks.length > 0) {
            newUnassignedTasks[team.id] = response.data.unassignedTasks
          } else {
            newUnassignedTasks[team.id] = []
          }
        } catch (error: any) {
          console.error(`팀 ${team.name} 스케줄 생성 실패:`, error)
          if (error.response?.status === 423) {
            alert(`팀 "${team.name}"의 스케줄 생성이 차단되었습니다. 다른 사용자가 생성 중입니다.`)
          } else {
            alert(`팀 "${team.name}"의 스케줄 생성에 실패했습니다: ${error.response?.data?.message || error.message}`)
          }
          // 실패한 팀은 점수와 배치 실패 작업을 빈 값으로 설정
          newScores[team.id] = null
          newUnassignedTasks[team.id] = []
        }
      }
      
      // 모든 결과를 한 번에 업데이트
      setScheduleScores(newScores)
      setUnassignedTasks(newUnassignedTasks)
      
      // 작업 목록 새로고침
      if (user) {
        await loadUserTasks(user.id)
      }
    } catch (error: any) {
      console.error('스케줄 생성 중 오류:', error)
      alert('스케줄 생성 중 오류가 발생했습니다: ' + (error.message || '알 수 없는 오류'))
    } finally {
      setIsGeneratingSchedule(false)
    }
  }

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

      // 사용자의 CalendarEvent와 담당자 Task, 각 팀의 Task와 Event를 모두 조회
      const [eventsResponse, assigneeTasksResponse] = await Promise.all([
        api.get(`/api/events/user/${userId}`).catch(() => ({ data: [] })),
        api.get(`/api/tasks/assignee/${userId}`).catch(() => ({ data: [] }))
      ])

      // 각 팀의 Task와 Event 조회
      const taskPromises = teams.map((team: any) =>
        api.get(`/api/tasks/team/${team.id}`).catch(() => ({ data: [] }))
      )
      const eventPromises = teams.map((team: any) =>
        api.get(`/api/events/team/${team.id}`).catch(() => ({ data: [] }))
      )
      const [tasksResponses, eventsResponses] = await Promise.all([
        Promise.all(taskPromises),
        Promise.all(eventPromises)
      ])
      
      console.log('Assignee tasks:', assigneeTasksResponse.data)
      console.log('Team tasks responses:', tasksResponses)
      console.log('Team events responses:', eventsResponses)

      const calendarEvents: any[] = []
      const eventIds = new Set<string>() // 중복 제거용

      // 사용자 소유 CalendarEvent 변환
      if (eventsResponse.data && Array.isArray(eventsResponse.data)) {
        eventsResponse.data.forEach((event: any) => {
          const eventId = `event-${event.id}`
          if (eventIds.has(eventId)) return
          eventIds.add(eventId)
          
          calendarEvents.push({
            id: eventId,
            title: event.title,
            start: event.startsAt,
            end: event.endsAt,
            location: event.location,
            teamName: event.teamName,
            source: event.source as 'TASK' | 'EVENT' | 'BREAK' | undefined,
            backgroundColor: event.source === 'TASK' ? '#3b82f6' : event.source === 'EVENT' ? '#22c55e' : event.source === 'BREAK' ? '#f97316' : '#22c55e',
            borderColor: event.source === 'TASK' ? '#2563eb' : event.source === 'EVENT' ? '#16a34a' : event.source === 'BREAK' ? '#ea580c' : '#16a34a',
            extendedProps: {
              type: 'event',
              teamId: event.teamId
            }
          })
        })
      }

      // 각 팀의 CalendarEvent 변환
      eventsResponses.forEach((eventsResponse: any, teamIndex: number) => {
        if (eventsResponse.data && Array.isArray(eventsResponse.data)) {
          const currentTeam = teams[teamIndex]
          eventsResponse.data.forEach((event: any) => {
            const eventId = `event-${event.id}`
            // 중복 제거 (이미 추가된 일정은 제외)
            if (eventIds.has(eventId)) return
            eventIds.add(eventId)
            
            if (event.startsAt && event.endsAt) {
              calendarEvents.push({
                id: eventId,
                title: event.title,
                start: event.startsAt,
                end: event.endsAt,
                location: event.location,
                teamName: currentTeam?.name || event.teamName,
                backgroundColor: '#22c55e',
                borderColor: '#16a34a',
                extendedProps: {
                  type: 'event',
                  teamId: currentTeam?.id || event.teamId
                }
              })
            }
          })
        }
      })

      // 중복 제거를 위한 Set
      const taskIds = new Set<string>()
      
      // 담당자 Task 변환
      if (assigneeTasksResponse.data && Array.isArray(assigneeTasksResponse.data)) {
        assigneeTasksResponse.data.forEach((task: any) => {
          if (task.dueAt) {
            const taskId = `task-${task.id}`
            taskIds.add(taskId)
            
            const dueDate = new Date(task.dueAt)
            if (isNaN(dueDate.getTime())) {
              console.warn('Invalid date for task:', task)
              return
            }
            
            // 작업은 마감시간만 표시 (소요시간 없이, start = end = 마감일시)
            const startDate = dueDate
            const endDate = dueDate
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
              className: 'fc-task-event', // 작업 이벤트를 한 줄로 표시하기 위한 클래스
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
              const dueDate = new Date(task.dueAt)
              if (isNaN(dueDate.getTime())) {
                console.warn('Invalid date for task:', task)
                return
              }
              
              // 작업은 마감시간만 표시 (소요시간 없이, start = end = 마감일시)
              const startDate = dueDate
              const endDate = dueDate
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
                className: 'fc-task-event', // 작업 이벤트를 한 줄로 표시하기 위한 클래스
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

    const upsertCalendarEvent = (message: CalendarEventMessage) => {
      const eventId = message.event?.id ?? message.eventId
      if (!eventId) {
        console.warn('[Home] Calendar event missing eventId:', message)
        return
      }
      const calendarId = `event-${eventId}`

      // 삭제된 일정이거나 일정 정보가 없으면 캘린더에서 제거
      if (message.action === 'DELETED' || !message.event) {
        console.log('[Home] Removing calendar event from calendar:', eventId, message.action)
        setEvents((prev) => prev.filter((entry) => entry.id !== calendarId))
        return
      }

      const payload = message.event
      const teamId = payload.teamId || message.teamId

      const converted: CalendarEventItem = {
        id: calendarId,
        title: payload.title,
        start: payload.startsAt,
        end: payload.endsAt,
        location: payload.location ?? undefined,
        teamName: undefined, // 필요시 추가
        backgroundColor: '#22c55e',
        borderColor: '#16a34a',
        extendedProps: {
          type: 'event',
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

      // 작업은 마감시간만 표시 (소요시간 없이, start = end = 마감일시)
      // Assignment는 스케줄 화면에서 시작시간~끝나는시간으로 표시됨
      const startDate = dueDate // 마감일시
      const endDate = dueDate // 마감일시 (작업은 마감시간만 표시)
      
      const priority = message.task.priority ?? 3
      const teamId = message.task.teamId
      const teamBaseColor = teamId ? (teamColorMap.get(teamId) || teamColors[0].base) : teamColors[0].base
      const colors = getColorByPriority(teamBaseColor, priority)

      const converted: CalendarEventItem = {
        id: calendarId,
        title: `📋 ${message.task.title}`,
        start: startDate.toISOString(),
        end: endDate.toISOString(),
        backgroundColor: colors.bg,
        borderColor: colors.border,
        className: 'fc-task-event', // 작업 이벤트를 한 줄로 표시하기 위한 클래스
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

      // 각 팀의 일정 이벤트 구독 (캘린더 실시간 업데이트)
      userTeams.forEach((team) => {
        subscriptions.push(
          client.subscribe(`/topic/calendar/${team.id}`, (frame) => {
            const payload = safeJsonParse<CalendarEventMessage>(frame.body)
            if (!payload) {
              console.warn('[Home] Failed to parse calendar event:', frame.body)
              return
            }
            console.log('[Home] Received calendar event:', payload.action, 'for team:', team.id)
            upsertCalendarEvent(payload)
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
              <div className="flex items-center justify-between mb-3">
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
              {/* 모든 팀 스케줄 생성 버튼 */}
              {userTeams.length > 0 && (
                <div className="space-y-2">
                  <button
                    onClick={handleGenerateSchedule}
                    disabled={isGeneratingSchedule}
                    className="w-full px-3 py-2.5 text-sm bg-gradient-to-r from-green-600 to-emerald-600 text-white rounded-lg hover:from-green-700 hover:to-emerald-700 transition-all shadow-sm hover:shadow-md font-medium flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                    </svg>
                    {isGeneratingSchedule ? `스케줄 생성 중... (${userTeams.length}개 팀)` : `스케줄 배치하기`}
                  </button>
                  
                  {/* 팀별 점수 표시 */}
                  {Object.keys(scheduleScores).length > 0 && (
                    <div className="space-y-1">
                      {userTeams.map((team) => {
                        const score = scheduleScores[team.id]
                        if (score === null || score === undefined) return null
                        return (
                          <div key={team.id} className="flex items-center justify-between text-xs px-2 py-1 bg-purple-50 rounded">
                            <span className="text-gray-700 font-medium">{team.name}</span>
                            <span className="text-purple-700 font-semibold">{score.toLocaleString()}점</span>
                          </div>
                        )
                      })}
                    </div>
                  )}
                  
                  {/* 배치되지 않은 작업 표시 */}
                  {Object.entries(unassignedTasks).map(([teamId, tasks]) => {
                    if (tasks.length === 0) return null
                    const team = userTeams.find(t => t.id === Number(teamId))
                    return (
                      <div key={teamId} className="mt-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                        <div className="flex items-center gap-2 mb-1">
                          <svg className="w-4 h-4 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                          </svg>
                          <span className="text-xs font-semibold text-yellow-800">{team?.name}: 배치 실패 ({tasks.length}개)</span>
                        </div>
                        <ul className="text-xs text-yellow-700 space-y-0.5">
                          {tasks.slice(0, 3).map((task) => (
                            <li key={task.taskId} className="flex items-start gap-1">
                              <span className="text-yellow-600">•</span>
                              <span className="truncate">{task.reason}</span>
                            </li>
                          ))}
                          {tasks.length > 3 && (
                            <li className="text-yellow-600 text-xs">... 외 {tasks.length - 3}개</li>
                          )}
                        </ul>
                      </div>
                    )
                  })}
                </div>
              )}
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
                    // Assignment가 있으면 Assignment 시간 사용, 없으면 마감일 사용
                    const displayDate = task.hasAssignment && task.startsAt ? task.startsAt : task.dueAt
                    const today = isToday(displayDate)
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
                              {task.hasAssignment && (
                                <span className="ml-2 text-xs text-blue-600 font-normal">(스케줄됨)</span>
                              )}
                            </h4>
                            <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                              <span>
                                {task.hasAssignment && task.startsAt 
                                  ? formatDate(task.startsAt) + ' ~ ' + formatDate(task.endsAt)
                                  : formatDate(task.dueAt)}
                              </span>
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
                  {user ? `${user?.name}님의 전체 일정` : '일정 캘린더'}
                </h2>
              </div>
              {/* 뷰 모드 전환 탭 */}
              <div className="flex items-center gap-2 bg-white rounded-lg p-1 border border-gray-200 shadow-sm">
                <button
                  onClick={() => setCalendarViewMode('events')}
                  className={`px-4 py-2 rounded-md font-medium text-sm transition-all ${
                    calendarViewMode === 'events'
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  일정 / 작업 보기
                </button>
                <button
                  onClick={() => setCalendarViewMode('schedule')}
                  className={`px-4 py-2 rounded-md font-medium text-sm transition-all ${
                    calendarViewMode === 'schedule'
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  스케줄 보기
                </button>
              </div>
            </div>
          </div>
          
          {/* 캘린더 */}
          <div className="p-8">
            <div className="mb-4 space-y-2">
              <div className="flex gap-4 text-sm flex-wrap">
                {calendarViewMode === 'events' ? (
                  <>
                    <div className="flex items-center gap-2">
                      <div className="w-4 h-4 rounded bg-green-500"></div>
                      <span>일정 (Event)</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="w-4 h-4 rounded" style={{ backgroundColor: 'rgb(59, 130, 246)' }}></div>
                      <span>작업 (Task) - 팀별 색상</span>
                    </div>
                  </>
                ) : (
                  <div className="flex items-center gap-2">
                    <div className="w-4 h-4 rounded" style={{ backgroundColor: 'rgb(59, 130, 246)' }}></div>
                    <span>스케줄된 작업 (Assignment) - 팀별 색상</span>
                  </div>
                )}
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
              /* 작업 이벤트는 한 줄로 표시 */
              .fc-task-event {
                height: 20px !important;
                min-height: 20px !important;
                max-height: 20px !important;
                line-height: 16px !important;
                overflow: hidden !important;
                white-space: nowrap !important;
                text-overflow: ellipsis !important;
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
              events={calendarViewMode === 'events' ? events : scheduleEvents}
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
              if (!taskFormData.teamId || !taskFormData.title || !taskFormData.dueDate) {
                alert('팀, 제목, 마감일은 필수입니다.')
                return
              }
              
              try {
                // 날짜와 시간을 합쳐서 ISO 문자열 생성
                const dueAt = taskFormData.dueDate && taskFormData.dueTime
                  ? new Date(`${taskFormData.dueDate}T${taskFormData.dueTime}`).toISOString()
                  : null
                
                const payload: any = {
                  teamId: Number(taskFormData.teamId),
                  title: taskFormData.title,
                  durationMin: taskFormData.durationMin,
                  dueAt: dueAt,
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
                const tomorrow = new Date()
                tomorrow.setDate(tomorrow.getDate() + 1)
                setTaskFormData({
                  teamId: '',
                  title: '',
                  dueDate: '',
                  dueTime: '18:00',
                  durationMin: 60,
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
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">마감 날짜 *</label>
                <div className="flex gap-2 mb-2">
                  <button
                    type="button"
                    onClick={() => {
                      const today = new Date().toISOString().split('T')[0]
                      setTaskFormData({ ...taskFormData, dueDate: today })
                    }}
                    className="px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                  >
                    오늘
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const tomorrow = new Date()
                      tomorrow.setDate(tomorrow.getDate() + 1)
                      setTaskFormData({ ...taskFormData, dueDate: tomorrow.toISOString().split('T')[0] })
                    }}
                    className="px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                  >
                    내일
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const nextWeek = new Date()
                      nextWeek.setDate(nextWeek.getDate() + 7)
                      setTaskFormData({ ...taskFormData, dueDate: nextWeek.toISOString().split('T')[0] })
                    }}
                    className="px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                  >
                    1주일 후
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const nextMonth = new Date()
                      nextMonth.setMonth(nextMonth.getMonth() + 1)
                      setTaskFormData({ ...taskFormData, dueDate: nextMonth.toISOString().split('T')[0] })
                    }}
                    className="px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                  >
                    1개월 후
                  </button>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <input
                    type="date"
                    value={taskFormData.dueDate}
                    onChange={(e) => setTaskFormData({ ...taskFormData, dueDate: e.target.value })}
                    required
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <select
                    value={taskFormData.dueTime}
                    onChange={(e) => setTaskFormData({ ...taskFormData, dueTime: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {Array.from({ length: 24 }, (_, i) => {
                      const hour = i.toString().padStart(2, '0')
                      return (
                        <option key={i} value={`${hour}:00`}>{hour}:00</option>
                      )
                    })}
                  </select>
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">소요 시간 (분) *</label>
                <input
                  type="number"
                  min="1"
                  value={taskFormData.durationMin}
                  onChange={(e) => setTaskFormData({ ...taskFormData, durationMin: Number(e.target.value) })}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="60"
                />
                <p className="text-xs text-gray-500 mt-1">작업에 소요될 예상 시간을 분 단위로 입력하세요</p>
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
                      dueDate: '',
                      dueTime: '18:00',
                      durationMin: 60,
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
