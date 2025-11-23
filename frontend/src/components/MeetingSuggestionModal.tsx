import { useState, useEffect } from 'react'
import api from '../lib/api'

type MeetingSuggestionModalProps = {
  isOpen: boolean
  onClose: () => void
  teamId: number
  onSelectTime: (startsAt: string, endsAt: string, title: string, location?: string) => void
}

type SuggestedTimeSlot = {
  startsAt: string
  endsAt: string
  availableParticipants: number
  totalParticipants: number
  preferenceScore: number
}

type MeetingSuggestionResponse = {
  suggestions: SuggestedTimeSlot[]
  events: any[]
  unassignedTasks: any[]
}

export default function MeetingSuggestionModal({
  isOpen,
  onClose,
  teamId,
  onSelectTime
}: MeetingSuggestionModalProps) {
  const [title, setTitle] = useState('')
  const [location, setLocation] = useState('')
  const [durationMin, setDurationMin] = useState(60)
  const [participantIds, setParticipantIds] = useState<number[]>([])
  const [teamMembers, setTeamMembers] = useState<Array<{ userId: number; userName: string; userEmail: string }>>([])
  const [suggestions, setSuggestions] = useState<SuggestedTimeSlot[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searchDays, setSearchDays] = useState(14) // 기본값: 오늘부터 14일 후까지

  useEffect(() => {
    if (isOpen && teamId) {
      // 팀 멤버 목록 로드
      api.get(`/api/teams/${teamId}/members`)
        .then(response => {
          const members = response.data.map((m: any) => ({
            userId: m.userId,
            userName: m.userName,
            userEmail: m.userEmail
          }))
          setTeamMembers(members)
        })
        .catch(console.error)
    }
  }, [isOpen, teamId])

  const handleSuggest = async () => {
    if (!teamId) {
      setError('팀 ID가 필요합니다')
      return
    }
    
    if (!title.trim()) {
      setError('미팅 제목을 입력해주세요')
      return
    }
    
    if (!durationMin || durationMin < 15) {
      setError('미팅 소요 시간은 최소 15분 이상이어야 합니다')
      return
    }

    setLoading(true)
    setError('')
    setSuggestions([])

    try {
      const response = await api.post<MeetingSuggestionResponse>('/api/events/suggest', {
        teamId,
        durationMin,
        participantIds: participantIds.length > 0 ? participantIds : undefined,
        searchDays
      })

      if (response.data.suggestions && response.data.suggestions.length > 0) {
        setSuggestions(response.data.suggestions)
      } else {
        setError('추천 가능한 시간대가 없습니다. 기간을 늘리거나 소요 시간을 조정해보세요.')
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '미팅 시간 추천에 실패했습니다.')
      console.error('Meeting suggestion error:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleSelectTime = (suggestion: SuggestedTimeSlot) => {
    onSelectTime(suggestion.startsAt, suggestion.endsAt, title, location || undefined)
    // 모달 닫기 전에 폼 초기화
    setTitle('')
    setLocation('')
    setSuggestions([])
    onClose()
  }

  const toggleParticipant = (userId: number) => {
    setParticipantIds(prev =>
      prev.includes(userId)
        ? prev.filter(id => id !== userId)
        : [...prev, userId]
    )
  }

  const formatDateTime = (isoString: string) => {
    const date = new Date(isoString)
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'short',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-800">미팅 시간 추천</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* 설정 섹션 */}
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                미팅 제목 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="예: 스프린트 회의, 프로젝트 리뷰"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                미팅 소요 시간 (분) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min="15"
                step="15"
                value={durationMin}
                onChange={(e) => setDurationMin(Number(e.target.value))}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                장소 (선택사항)
              </label>
              <input
                type="text"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="예: 회의실 A, 온라인"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                검색 기간 (일)
              </label>
              <input
                type="number"
                min="1"
                max="30"
                value={searchDays}
                onChange={(e) => setSearchDays(Number(e.target.value))}
                placeholder="오늘부터 며칠 후까지 검색할지 입력"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
              <p className="mt-1 text-xs text-gray-500">
                예: 7 = 오늘부터 7일 후까지, 14 = 오늘부터 14일 후까지
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                참석자 선택 (선택사항, 없으면 팀 전체)
              </label>
              <div className="max-h-40 overflow-y-auto border border-gray-300 rounded-lg p-2">
                {teamMembers.length === 0 ? (
                  <p className="text-sm text-gray-500">팀 멤버를 불러오는 중...</p>
                ) : (
                  <div className="space-y-2">
                    {teamMembers.map(member => (
                      <label key={member.userId} className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 p-2 rounded">
                        <input
                          type="checkbox"
                          checked={participantIds.includes(member.userId)}
                          onChange={() => toggleParticipant(member.userId)}
                          className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                        />
                        <span className="text-sm text-gray-700">{member.userName} ({member.userEmail})</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <button
              onClick={handleSuggest}
              disabled={loading}
              className="w-full px-4 py-3 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? '추천 중...' : '시간 추천 받기'}
            </button>
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
              {error}
            </div>
          )}

          {/* 추천 결과 */}
          {suggestions.length > 0 && (
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">
                추천된 시간대 ({suggestions.length}개)
              </h3>
              <div className="space-y-3">
                {suggestions.map((suggestion, index) => (
                  <div
                    key={index}
                    className="border border-gray-200 rounded-lg p-4 hover:border-blue-400 hover:shadow-md transition-all cursor-pointer"
                    onClick={() => handleSelectTime(suggestion)}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1">
                        <div className="font-medium text-gray-800 mb-1">
                          {formatDateTime(suggestion.startsAt)}
                        </div>
                        <div className="text-sm text-gray-600">
                          ~ {formatDateTime(suggestion.endsAt)}
                        </div>
                        <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
                          <span>참석자: {suggestion.availableParticipants}/{suggestion.totalParticipants}</span>
                          <span>선호도: {(suggestion.preferenceScore * 100).toFixed(0)}%</span>
                        </div>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          handleSelectTime(suggestion)
                        }}
                        className="ml-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
                      >
                        선택
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

