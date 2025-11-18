import Header from '../../components/Header'
import { useEffect, useState } from 'react'
import { Outlet, useParams, useLocation, useNavigate } from 'react-router-dom'
import type { StompSubscription } from '@stomp/stompjs'
import {
  createStompClient,
  safeJsonParse
} from '../../lib/ws'
import type {
  CollaborationNotificationMessage,
  ScheduleProgressMessage
} from '../../lib/ws'
import { useAuth } from '../../store/auth'

type ToastItem = {
  id: number
  data: CollaborationNotificationMessage
}

export default function TeamLayout() {
  const { id } = useParams()
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<ToastItem[]>([])
  const [scheduleProgress, setScheduleProgress] = useState<ScheduleProgressMessage | null>(null)

  const teamId = id ? Number(id) : null
  const userId = user?.id ?? (() => {
    const stored = localStorage.getItem('userId')
    return stored ? Number(stored) : null
  })()

  useEffect(() => {
    if (!teamId) return
    const client = createStompClient()
    const subscriptions: StompSubscription[] = []

    const pushNotification = (message: CollaborationNotificationMessage) => {
      const toast: ToastItem = { id: Date.now(), data: message }
      setNotifications((prev) => [toast, ...prev].slice(0, 3))
      window.setTimeout(() => {
        setNotifications((prev) => prev.filter((item) => item.id !== toast.id))
      }, 10000)
    }

    const updateSchedule = (message: ScheduleProgressMessage) => {
      setScheduleProgress(message)
      if (message.status === 'COMPLETED' || message.status === 'FAILED') {
        window.setTimeout(() => {
          setScheduleProgress((current) => (current === message ? null : current))
        }, 8000)
      }
    }

    client.onConnect = () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0
      subscriptions.push(
        client.subscribe(`/topic/notifications/team/${teamId}`, (frame) => {
          const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
          if (!payload) {
            console.warn('[TeamLayout] Failed to parse notification:', frame.body)
            return
          }
          console.log('[TeamLayout] Received team notification:', payload.title, 'for team:', teamId)
          pushNotification(payload)
        })
      )
      if (userId) {
        subscriptions.push(
          client.subscribe(`/topic/notifications/user/${userId}`, (frame) => {
            const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
            if (!payload) return
            pushNotification(payload)
          })
        )
      }
      subscriptions.push(
        client.subscribe(`/topic/schedules/${teamId}`, (frame) => {
          const payload = safeJsonParse<ScheduleProgressMessage>(frame.body)
          if (!payload) return
          updateSchedule(payload)
        })
      )
    }

    client.activate()

    return () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
    }
  }, [teamId, userId])

  return (
    <>
      <Header />
      <div className="min-h-screen bg-gray-50">
      <div className="fixed top-20 right-6 z-50 flex w-80 flex-col gap-3">
        {scheduleProgress && (
          <div className="rounded-xl border-2 border-blue-300 bg-gradient-to-br from-blue-50 to-blue-100 p-5 text-sm text-blue-800 shadow-xl backdrop-blur-sm">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
              <div className="text-xs font-bold uppercase tracking-wider text-blue-600">{scheduleProgress.status}</div>
            </div>
            <div className="mt-1 font-bold text-blue-900">
              {scheduleProgress.message ?? '스케줄 최적화가 진행 중입니다.'}
            </div>
            {typeof scheduleProgress.progress === 'number' && (
              <div className="mt-4 h-2.5 rounded-full bg-blue-200 shadow-inner">
                <div
                  className="h-2.5 rounded-full bg-gradient-to-r from-blue-500 to-blue-600 transition-all duration-300 shadow-sm"
                  style={{ width: `${Math.min(Math.max(scheduleProgress.progress, 0), 100)}%` }}
                />
              </div>
            )}
          </div>
        )}
        {notifications.map((item) => (
          <div key={item.id} className="rounded-xl border border-gray-200 bg-white/95 backdrop-blur-sm p-5 text-sm shadow-xl hover:shadow-2xl transition-all duration-300 hover:-translate-y-1">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-1.5 h-1.5 rounded-full bg-blue-500"></div>
              <div className="text-[11px] font-bold uppercase tracking-wider text-gray-500">
                {item.data.category}
              </div>
            </div>
            <div className="mt-1 font-bold text-gray-900">{item.data.title}</div>
            <div className="mt-2 text-xs text-gray-600 leading-relaxed">{item.data.content}</div>
            <div className="mt-3 text-[11px] text-gray-400 border-t border-gray-100 pt-2">
              {item.data.timestamp ? new Date(item.data.timestamp).toLocaleString('ko-KR') : ''}
            </div>
          </div>
        ))}
      </div>

      <header className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-gray-200 shadow-sm">
        <div className="mx-auto max-w-6xl px-4 py-3 flex items-center justify-between">
          <div className="text-lg font-extrabold tracking-tight bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
            {location.pathname.includes('/calendar') || location.pathname === `/team/${id}` 
              ? '캘린더'
              : location.pathname.includes('/tasks')
              ? '작업'
              : location.pathname.includes('/workhours')
              ? '근무시간'
              : location.pathname.includes('/settings')
              ? '팀 설정'
              : 'TeamSpace'}
          </div>
          <nav className="flex items-center gap-1 text-sm">
            <button
              onClick={() => navigate(`/team/${id}/calendar`)}
              className={`px-4 py-2 rounded-lg font-medium transition-all ${
                location.pathname.includes('/calendar') || (location.pathname === `/team/${id}`)
                  ? 'bg-gradient-to-r from-blue-50 to-purple-50 text-blue-700' 
                  : 'text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700'
              }`}
            >
              캘린더
            </button>
            <button
              onClick={() => navigate(`/team/${id}/tasks`)}
              className={`px-4 py-2 rounded-lg font-medium transition-all ${
                location.pathname.includes('/tasks')
                  ? 'bg-gradient-to-r from-blue-50 to-purple-50 text-blue-700' 
                  : 'text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700'
              }`}
            >
              작업
            </button>
            <button
              onClick={() => navigate(`/team/${id}/workhours`)}
              className={`px-4 py-2 rounded-lg font-medium transition-all ${
                location.pathname.includes('/workhours')
                  ? 'bg-gradient-to-r from-blue-50 to-purple-50 text-blue-700' 
                  : 'text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700'
              }`}
            >
              근무시간
            </button>
            <button
              onClick={() => navigate(`/team/${id}/settings`)}
              className={`px-4 py-2 rounded-lg font-medium transition-all ${
                location.pathname.includes('/settings')
                  ? 'bg-gradient-to-r from-blue-50 to-purple-50 text-blue-700' 
                  : 'text-gray-700 hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 hover:text-blue-700'
              }`}
            >
              팀 설정
            </button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
      </div>
    </>
  )
}


