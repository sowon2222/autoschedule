import { useEffect, useState, useCallback } from 'react'
import Header from '../components/Header'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import api from '../lib/api'
import { useAuth } from '../store/auth'

type CalendarEvent = {
  id: string
  title: string
  startsAt: string
  endsAt: string
  location?: string
  teamName?: string
  source?: 'TASK' | 'EVENT' | 'BREAK'
}

export default function Home() {
  const { user, logout, setUser } = useAuth()
  const [events, setEvents] = useState<CalendarEvent[]>([])

  const loadUserEvents = useCallback(async (userId: number) => {
    try {
      const response = await api.get(`/api/events/user/${userId}`)
      const eventData = response.data.map((event: any) => ({
        id: String(event.id),
        title: event.title,
        start: event.startsAt,
        end: event.endsAt,
        location: event.location,
        teamName: event.teamName,
        source: event.source as 'TASK' | 'EVENT' | 'BREAK' | undefined,
        backgroundColor: event.source === 'TASK' ? '#3b82f6' : event.source === 'EVENT' ? '#22c55e' : event.source === 'BREAK' ? '#f97316' : '#3b82f6',
        borderColor: '#2563eb'
      }))
      setEvents(eventData)
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
