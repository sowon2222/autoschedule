import Header from '../components/Header'
import { useEffect, useState } from 'react'
import api from '../lib/api'
import { useAuth } from '../store/auth'
import type { StompSubscription } from '@stomp/stompjs'
import { createStompClient, safeJsonParse } from '../lib/ws'
import type { CollaborationNotificationMessage } from '../lib/ws'

type Team = { id: number; name: string; createdAt?: string }

type ToastItem = {
  id: number
  data: CollaborationNotificationMessage
}

export default function Dashboard() {
  const [teams, setTeams] = useState<Team[]>([])
  const [notifications, setNotifications] = useState<ToastItem[]>([])
  const { user } = useAuth()
  
  const userId = user?.id ?? (() => {
    const stored = localStorage.getItem('userId')
    return stored ? Number(stored) : null
  })()

  // 팀 목록 로드
  useEffect(() => { 
    api.get('/api/teams').then(r => setTeams(r.data)).catch(() => {}) 
  }, [])

  // 사용자가 속한 모든 팀의 알림 구독
  useEffect(() => {
    if (!teams.length || !userId) return

    const client = createStompClient()
    const subscriptions: StompSubscription[] = []

    const pushNotification = (message: CollaborationNotificationMessage) => {
      const toast: ToastItem = { id: Date.now(), data: message }
      setNotifications((prev) => [toast, ...prev].slice(0, 3))
      console.log('[Dashboard] Received notification:', message.title)
      window.setTimeout(() => {
        setNotifications((prev) => prev.filter((item) => item.id !== toast.id))
      }, 10000)
    }

    client.onConnect = () => {
      console.log('[Dashboard] WebSocket connected, subscribing to notifications for teams:', teams.map(t => t.id))
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0

      // 각 팀의 알림 구독
      teams.forEach((team) => {
        subscriptions.push(
          client.subscribe(`/topic/notifications/team/${team.id}`, (frame) => {
            const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
            if (!payload) {
              console.warn('[Dashboard] Failed to parse notification:', frame.body)
              return
            }
            console.log('[Dashboard] Received team notification:', payload.title, 'for team:', team.id)
            pushNotification(payload)
          })
        )
      })

      // 사용자 개인 알림 구독
      subscriptions.push(
        client.subscribe(`/topic/notifications/user/${userId}`, (frame) => {
          const payload = safeJsonParse<CollaborationNotificationMessage>(frame.body)
          if (!payload) return
          console.log('[Dashboard] Received user notification:', payload.title)
          pushNotification(payload)
        })
      )
    }

    client.activate()

    return () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      client.deactivate()
    }
  }, [teams, userId])
  return (
    <>
      <Header />
      <div className="mx-auto max-w-6xl px-4 py-6">
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

        <h1 className="text-2xl font-bold mb-4 tracking-tight">대시보드</h1>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {teams.map(t => (
            <a
              key={t.id}
              href={`/team/${t.id}/calendar`}
              className="rounded-xl border bg-white p-5 shadow-sm transition hover:shadow-md hover:-translate-y-0.5 text-gray-900 hover:text-gray-900"
            >
              <div className="font-semibold text-gray-900">{t.name}</div>
              <div className="text-sm text-gray-500">{t.createdAt?.slice(0,10)}</div>
            </a>
          ))}
        </div>
      </div>
    </>
  )
}


