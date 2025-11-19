import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const DEFAULT_WS_URL = (() => {
  // 환경 변수로 명시적으로 설정된 경우 사용
  const configured = import.meta.env.VITE_WS_URL
  if (configured) return configured

  // API 베이스 URL이 설정되어 있으면 해당 호스트 사용
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
  if (apiBaseUrl && apiBaseUrl.trim() !== '') {
    try {
      // 절대 URL인 경우
      const url = new URL(apiBaseUrl)
      const wsProtocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
      return `${wsProtocol}//${url.host}/ws`
    } catch {
      // 상대 경로인 경우 (빈 문자열 등) - 현재 페이지 호스트 사용
      // 개발 환경에서는 백엔드 포트 사용
      if (import.meta.env.DEV) {
        return 'http://localhost:8080/ws'
      }
    }
  }

  // 개발 환경: 백엔드가 8080 포트에서 실행
  if (import.meta.env.DEV) {
    return 'http://localhost:8080/ws'
  }

  // 프로덕션: 현재 페이지의 호스트 사용 (EC2에서는 프론트엔드가 8080에 포함됨)
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

  // 디버깅: WebSocket URL과 토큰 상태 로그
  console.log('[WebSocket] Connecting to:', url)
  console.log('[WebSocket] Token present:', !!token)

  // debug 함수 정의
  const debugFn = (str: string) => {
    // 프로덕션에서도 중요한 로그는 출력
    if (str.includes('CONNECTED') || str.includes('ERROR') || str.includes('SUBSCRIBE')) {
      console.log('[STOMP]', str)
    } else if (import.meta.env.DEV) {
      console.debug('[STOMP]', str)
    }
  }

  const client = new Client({
    ...config,
    webSocketFactory: () => {
      console.log('[WebSocket] Creating SockJS connection to:', url)
      const sock = new SockJS(url, null, {
        transports: ['websocket', 'xhr-streaming']
      })
      
      // 로컬 개발 환경에서만 상세 로그
      if (import.meta.env.DEV) {
        sock.onopen = () => {
          console.log('[SockJS] Connection opened')
        }
        sock.onclose = (event) => {
          console.warn('[SockJS] Connection closed', { code: event.code, reason: event.reason, wasClean: event.wasClean })
        }
        sock.onerror = (error) => {
          console.error('[SockJS] Connection error', error)
        }
        sock.onmessage = (event) => {
          // 메시지가 너무 많으므로 중요한 것만 로그
          if (event.data && typeof event.data === 'string' && (event.data.includes('o') || event.data.includes('a'))) {
            console.debug('[SockJS] Message received', event.data.substring(0, 50))
          }
        }
      }
      
      return sock
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000, // 10초마다 heartbeat 수신 대기
    heartbeatOutgoing: 10000, // 10초마다 heartbeat 전송
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : undefined,
    // config에 debug가 없으면 우리가 정의한 것 사용
    debug: config?.debug ?? debugFn
  })

  client.onStompError = (frame) => {
    console.error('[STOMP] Error:', frame.headers['message'], frame.body)
  }

  client.onWebSocketError = (event) => {
    console.error('[WebSocket] Error:', event)
  }

  // 로컬 개발 환경에서만 연결 상태 상세 로그
  if (import.meta.env.DEV) {
    client.onConnect = (frame) => {
      console.log('[STOMP] Connected successfully', frame)
    }
    
    client.onDisconnect = () => {
      console.warn('[STOMP] Disconnected')
    }
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
