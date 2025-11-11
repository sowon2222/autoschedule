import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const DEFAULT_WS_URL = (() => {
  const configured = import.meta.env.VITE_WS_URL
  if (configured) return configured

  if (typeof window !== 'undefined' && window.location) {
    const { protocol, host } = window.location
    const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProtocol}//${host}/ws`
  }

  return 'http://localhost:8080/ws'
})()

export type TaskPayload = {
  id: number
  title: string
  durationMin: number
  dueAt?: string | null
  priority: number
  teamId?: number | null
  assigneeId?: number | null
  tags?: string | null
}

export type TaskEventMessage = {
  action: 'CREATED' | 'UPDATED' | 'DELETED'
  task: TaskPayload | null
  taskId: number | null
  teamId: number | null
}

export type CalendarEventPayload = {
  id: number
  teamId?: number | null
  title: string
  location?: string | null
  startsAt: string
  endsAt: string
  notes?: string | null
  ownerId?: number | null
  ownerName?: string | null
}

export type CalendarEventMessage = {
  teamId: number | null
  action: 'CREATED' | 'UPDATED' | 'DELETED'
  event: CalendarEventPayload | null
  eventId: number | null
}

export type ConflictAlertMessage = {
  teamId: number | null
  sourceType: string
  sourceId: number | null
  source: CalendarEventPayload | null
  conflicts: CalendarEventPayload[]
  message: string
}

export type CollaborationNotificationMessage = {
  teamId: number | null
  scope: 'TEAM' | 'USER' | 'BROADCAST' | string
  targetId: number | null
  category: string
  title: string
  content: string
  timestamp: string
}

export type ScheduleProgressMessage = {
  teamId: number | null
  status: 'PROGRESS' | 'COMPLETED' | 'FAILED' | string
  progress: number | null
  message: string | null
  schedule: {
    id: number
    teamId: number
    teamName?: string | null
    rangeStart: string
    rangeEnd: string
    score?: number | null
  } | null
}

type ClientConfig = ConstructorParameters<typeof Client>[0] extends undefined
  ? Record<string, unknown>
  : ConstructorParameters<typeof Client>[0]

export function createStompClient(config?: Partial<ClientConfig>) {
  const token = localStorage.getItem('accessToken')
  const url = DEFAULT_WS_URL

  const client = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 5000,
    debug: import.meta.env.DEV ? (str) => console.debug('[STOMP]', str) : undefined,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : undefined,
    ...config
  })

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers['message'], frame.body)
  }

  client.onWebSocketError = (event) => {
    console.error('WebSocket error', event)
  }

  return client
}

export function safeJsonParse<T>(body: string): T | null {
  try {
    return JSON.parse(body) as T
  } catch (error) {
    console.error('Failed to parse STOMP message', error, body)
    return null
  }
}
