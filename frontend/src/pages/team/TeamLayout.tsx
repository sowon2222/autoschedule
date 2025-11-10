import { useEffect, useState } from 'react'
import { Outlet, useParams } from 'react-router-dom'
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
          if (!payload) return
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
    <div className="min-h-screen bg-gray-50">
      <div className="fixed top-20 right-6 z-50 flex w-80 flex-col gap-3">
        {scheduleProgress && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800 shadow-lg">
            <div className="text-xs font-semibold uppercase text-blue-500">{scheduleProgress.status}</div>
            <div className="mt-1 font-semibold text-blue-900">
              {scheduleProgress.message ?? '스케줄 최적화가 진행 중입니다.'}
            </div>
            {typeof scheduleProgress.progress === 'number' && (
              <div className="mt-3 h-2 rounded-full bg-blue-100">
                <div
                  className="h-2 rounded-full bg-blue-500 transition-all"
                  style={{ width: `${Math.min(Math.max(scheduleProgress.progress, 0), 100)}%` }}
                />
              </div>
            )}
          </div>
        )}
        {notifications.map((item) => (
          <div key={item.id} className="rounded-lg border border-gray-200 bg-white p-4 text-sm shadow-lg">
            <div className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">
              {item.data.category}
            </div>
            <div className="mt-1 font-semibold text-gray-900">{item.data.title}</div>
            <div className="mt-1 text-xs text-gray-600">{item.data.content}</div>
            <div className="mt-2 text-[11px] text-gray-400">
              {item.data.timestamp ? new Date(item.data.timestamp).toLocaleString('ko-KR') : ''}
            </div>
          </div>
        ))}
      </div>

      <header className="sticky top-0 z-10 bg-white/80 backdrop-blur border-b">
        <div className="mx-auto max-w-6xl px-4 py-3 flex items-center justify-between">
          <a href="/dashboard" className="text-lg font-bold tracking-tight text-gray-900 hover:text-gray-900">TeamSpace</a>
          <nav className="flex items-center gap-2 text-sm">
            <a className="px-3 py-2 rounded-md hover:bg-gray-100 text-gray-700 hover:text-gray-700" href={`/team/${id}/calendar`}>캘린더</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100 text-gray-700 hover:text-gray-700" href={`/team/${id}/tasks`}>작업</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100 text-gray-700 hover:text-gray-700" href={`/team/${id}/workhours`}>근무시간</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100 text-gray-700 hover:text-gray-700" href={`/team/${id}/settings`}>팀 설정</a>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}


