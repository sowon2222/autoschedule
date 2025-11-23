import { useState, useEffect } from 'react'
import api from '../lib/api'
import { useAuth } from '../store/auth'

type CreateEventModalProps = {
  isOpen: boolean
  onClose: () => void
  defaultDate?: Date
  defaultStartTime?: string  // ISO string
  defaultEndTime?: string    // ISO string
  defaultTitle?: string       // 미팅 제목
  defaultLocation?: string    // 미팅 장소
  teamId?: number
  onSuccess?: () => void
}

export default function CreateEventModal({ isOpen, onClose, defaultDate, defaultStartTime, defaultEndTime, defaultTitle, defaultLocation, teamId, onSuccess }: CreateEventModalProps) {
  const { user } = useAuth()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>('')
  const [userTeams, setUserTeams] = useState<Array<{ id: number; name: string }>>([])
  const [teamMembers, setTeamMembers] = useState<Array<{ userId: number; userName: string; userEmail: string }>>([])
  const [attendeeDropdownOpen, setAttendeeDropdownOpen] = useState(false)
  const getDefaultDate = () => {
    return defaultDate || new Date()
  }

  const getDefaultStartTime = () => {
    const date = getDefaultDate()
    const hours = date.getHours().toString().padStart(2, '0')
    const minutes = date.getMinutes().toString().padStart(2, '0')
    return `${hours}:${minutes}`
  }

  const getDefaultEndTime = () => {
    const date = getDefaultDate()
    const endDate = new Date(date.getTime() + 60 * 60 * 1000) // 1시간 후
    const hours = endDate.getHours().toString().padStart(2, '0')
    const minutes = endDate.getMinutes().toString().padStart(2, '0')
    return `${hours}:${minutes}`
  }

  const getInitialFormData = () => {
    let startDate = defaultDate ? new Date(defaultDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0]
    let startTime = getDefaultStartTime()
    let endDate = defaultDate ? new Date(defaultDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0]
    let endTime = getDefaultEndTime()

    // defaultStartTime과 defaultEndTime이 있으면 사용
    if (defaultStartTime) {
      const start = new Date(defaultStartTime)
      startDate = start.toISOString().split('T')[0]
      startTime = `${start.getHours().toString().padStart(2, '0')}:${start.getMinutes().toString().padStart(2, '0')}`
    }
    if (defaultEndTime) {
      const end = new Date(defaultEndTime)
      endDate = end.toISOString().split('T')[0]
      endTime = `${end.getHours().toString().padStart(2, '0')}:${end.getMinutes().toString().padStart(2, '0')}`
    }

    return {
      teamId: teamId || '',
      title: '',
      startDate,
      startTime,
      endDate,
      endTime,
      location: '',
      attendees: [] as number[],
      notes: '',
      fixed: false,
      recurrenceEnabled: false,
      recurrenceType: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY',
      recurrenceEndDate: '',
      recurrenceDaysOfWeek: [] as number[] // 0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토
    }
  }

  const [formData, setFormData] = useState(getInitialFormData())

  // 팀 목록 로드
  useEffect(() => {
    const loadTeams = async () => {
      if (!user?.id) return
      try {
        const response = await api.get(`/api/teams/user/${user.id}`)
        setUserTeams(response.data || [])
      } catch (err) {
        console.error('Failed to load teams:', err)
        setUserTeams([])
      }
    }
    if (isOpen) {
      loadTeams()
    }
  }, [user?.id, isOpen])

  // defaultStartTime, defaultEndTime, defaultTitle, defaultLocation이 변경되면 formData 업데이트
  useEffect(() => {
    if (isOpen) {
      const updates: any = {}
      
      if (defaultStartTime) {
        const start = new Date(defaultStartTime)
        updates.startDate = start.toISOString().split('T')[0]
        updates.startTime = `${start.getHours().toString().padStart(2, '0')}:${start.getMinutes().toString().padStart(2, '0')}`
      }
      if (defaultEndTime) {
        const end = new Date(defaultEndTime)
        updates.endDate = end.toISOString().split('T')[0]
        updates.endTime = `${end.getHours().toString().padStart(2, '0')}:${end.getMinutes().toString().padStart(2, '0')}`
      }
      if (defaultTitle) {
        updates.title = defaultTitle
      }
      if (defaultLocation !== undefined) {
        updates.location = defaultLocation
      }
      
      if (Object.keys(updates).length > 0) {
        setFormData(prev => ({ ...prev, ...updates }))
      }
    }
  }, [isOpen, defaultStartTime, defaultEndTime, defaultTitle, defaultLocation])

  // teamId prop이 변경되면 formData 업데이트
  useEffect(() => {
    if (teamId) {
      setFormData(prev => ({ ...prev, teamId: teamId.toString() }))
    }
  }, [teamId])

  // defaultDate가 변경되면 날짜와 시간 업데이트
  useEffect(() => {
    if (defaultDate) {
      const date = new Date(defaultDate)
      const dateStr = date.toISOString().split('T')[0]
      const hours = date.getHours().toString().padStart(2, '0')
      const minutes = date.getMinutes().toString().padStart(2, '0')
      const timeStr = `${hours}:${minutes}`
      
      const endDate = new Date(date.getTime() + 60 * 60 * 1000)
      const endHours = endDate.getHours().toString().padStart(2, '0')
      const endMinutes = endDate.getMinutes().toString().padStart(2, '0')
      const endTimeStr = `${endHours}:${endMinutes}`
      
      setFormData(prev => ({
        ...prev,
        startDate: dateStr,
        startTime: timeStr,
        endDate: dateStr,
        endTime: endTimeStr
      }))
    }
  }, [defaultDate])

  // 팀 선택 시 팀원 목록 로드
  useEffect(() => {
    const loadTeamMembers = async () => {
      if (!formData.teamId) {
        setTeamMembers([])
        setAttendeeDropdownOpen(false)
        return
      }
      try {
        const response = await api.get(`/api/teams/${formData.teamId}/members`)
        setTeamMembers(response.data || [])
      } catch (err) {
        console.error('Failed to load team members:', err)
        setTeamMembers([])
      }
    }
    if (isOpen && formData.teamId) {
      loadTeamMembers()
    }
  }, [formData.teamId, isOpen])

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as HTMLElement
      if (!target.closest('.attendee-dropdown-container')) {
        setAttendeeDropdownOpen(false)
      }
    }
    if (attendeeDropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      return () => document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [attendeeDropdownOpen])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSaving(true)

    try {
      // 날짜와 시간을 합쳐서 ISO 문자열 생성
      const startsAt = new Date(`${formData.startDate}T${formData.startTime}`).toISOString()
      const endsAt = new Date(`${formData.endDate}T${formData.endTime}`).toISOString()
      
      const payload: any = {
        teamId: Number(formData.teamId),
        title: formData.title,
        startsAt: startsAt,
        endsAt: endsAt,
        fixed: formData.fixed,
        location: formData.location || null,
        attendees: formData.attendees.length > 0 ? formData.attendees.join(',') : null,
        notes: formData.notes || null
      }

      if (formData.recurrenceEnabled) {
        payload.recurrenceType = formData.recurrenceType
        payload.recurrenceEndDate = formData.recurrenceEndDate 
          ? new Date(formData.recurrenceEndDate).toISOString() 
          : null
        // WEEKLY일 때 요일 정보 추가 (백엔드에서 처리할 수 있도록)
        if (formData.recurrenceType === 'WEEKLY' && formData.recurrenceDaysOfWeek.length > 0) {
          payload.recurrenceDaysOfWeek = formData.recurrenceDaysOfWeek.join(',')
        }
      }

      await api.post('/api/events', payload)
      
      // 폼 초기화
      const resetDate = defaultDate || new Date()
      const resetDateStr = resetDate.toISOString().split('T')[0]
      const resetHours = resetDate.getHours().toString().padStart(2, '0')
      const resetMinutes = resetDate.getMinutes().toString().padStart(2, '0')
      const resetTimeStr = `${resetHours}:${resetMinutes}`
      
      const resetEndDate = new Date(resetDate.getTime() + 60 * 60 * 1000)
      const resetEndHours = resetEndDate.getHours().toString().padStart(2, '0')
      const resetEndMinutes = resetEndDate.getMinutes().toString().padStart(2, '0')
      const resetEndTimeStr = `${resetEndHours}:${resetEndMinutes}`
      
      setFormData({
        teamId: teamId ? teamId.toString() : '',
        title: '',
        startDate: resetDateStr,
        startTime: resetTimeStr,
        endDate: resetDateStr,
        endTime: resetEndTimeStr,
        location: '',
        attendees: [],
        notes: '',
        fixed: false,
        recurrenceEnabled: false,
        recurrenceType: 'WEEKLY',
        recurrenceEndDate: '',
        recurrenceDaysOfWeek: []
      })
      
      if (onSuccess) onSuccess()
      onClose()
    } catch (err: any) {
      setError(err.response?.data?.message || '이벤트 생성에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50" onClick={onClose}>
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
        <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">새 일정 생성</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-2xl font-bold"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">팀 *</label>
            <select
              value={formData.teamId}
              onChange={(e) => {
                setFormData({ ...formData, teamId: e.target.value, attendees: [] })
                setAttendeeDropdownOpen(false)
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
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">시작 날짜 *</label>
            <input
              type="date"
              value={formData.startDate}
              onChange={(e) => {
                const newDate = e.target.value
                setFormData({ 
                  ...formData, 
                  startDate: newDate,
                  // 종료 날짜도 같이 변경 (선택적으로)
                  endDate: newDate
                })
              }}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 mb-2"
            />
            <label className="block text-sm font-medium text-gray-700 mb-1">시작 시간 *</label>
            <input
              type="time"
              value={formData.startTime}
              onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">종료 날짜 *</label>
            <input
              type="date"
              value={formData.endDate}
              onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 mb-2"
            />
            <label className="block text-sm font-medium text-gray-700 mb-1">종료 시간 *</label>
            <input
              type="time"
              value={formData.endTime}
              onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">장소</label>
            <input
              type="text"
              value={formData.location}
              onChange={(e) => setFormData({ ...formData, location: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="relative attendee-dropdown-container">
            <label className="block text-sm font-medium text-gray-700 mb-1">참석자</label>
            <div
              onClick={() => {
                if (formData.teamId && teamMembers.length > 0) {
                  setAttendeeDropdownOpen(!attendeeDropdownOpen)
                }
              }}
              className={`w-full min-h-[42px] px-3 py-2 border border-gray-300 rounded-md focus-within:outline-none focus-within:ring-2 focus-within:ring-blue-500 ${
                !formData.teamId ? 'bg-gray-100 cursor-not-allowed' : 'bg-white cursor-text'
              } flex flex-wrap gap-2 items-center`}
            >
              {formData.attendees.length === 0 ? (
                <span className="text-gray-400 text-sm">
                  {formData.teamId ? '참석자를 선택하세요' : '팀을 먼저 선택해주세요'}
                </span>
              ) : (
                formData.attendees.map((userId) => {
                  const member = teamMembers.find(m => m.userId === userId)
                  if (!member) return null
                  return (
                    <span
                      key={userId}
                      className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 rounded-md text-sm"
                    >
                      {member.userName}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation()
                          setFormData({
                            ...formData,
                            attendees: formData.attendees.filter(id => id !== userId)
                          })
                        }}
                        className="hover:text-blue-600 focus:outline-none"
                      >
                        ×
                      </button>
                    </span>
                  )
                })
              )}
            </div>
            {attendeeDropdownOpen && formData.teamId && teamMembers.length > 0 && (
              <div className="absolute z-10 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg max-h-60 overflow-y-auto">
                {teamMembers
                  .filter(member => !formData.attendees.includes(member.userId))
                  .map((member) => (
                    <button
                      key={member.userId}
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        setFormData({
                          ...formData,
                          attendees: [...formData.attendees, member.userId]
                        })
                        setAttendeeDropdownOpen(false)
                      }}
                      className="w-full text-left px-4 py-2 hover:bg-blue-50 text-sm"
                    >
                      {member.userName} ({member.userEmail})
                    </button>
                  ))}
                {teamMembers.filter(member => !formData.attendees.includes(member.userId)).length === 0 && (
                  <div className="px-4 py-2 text-sm text-gray-500">모든 팀원이 선택되었습니다</div>
                )}
              </div>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">메모</label>
            <textarea
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              rows={3}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={formData.fixed}
                onChange={(e) => setFormData({ ...formData, fixed: e.target.checked })}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span className="text-sm font-medium text-gray-700">고정 일정</span>
            </label>
          </div>

          {/* 반복 일정 옵션 */}
          <div className="border-t border-gray-200 pt-4">
            <label className="flex items-center gap-2 mb-3">
              <input
                type="checkbox"
                checked={formData.recurrenceEnabled}
                onChange={(e) => setFormData({ ...formData, recurrenceEnabled: e.target.checked })}
                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span className="text-sm font-medium text-gray-700">반복 일정</span>
            </label>
            
            {formData.recurrenceEnabled && (
              <div className="ml-6 space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">반복 주기</label>
                  <select
                    value={formData.recurrenceType}
                    onChange={(e) => {
                      const newType = e.target.value as any
                      setFormData({ 
                        ...formData, 
                        recurrenceType: newType,
                        recurrenceDaysOfWeek: newType === 'WEEKLY' ? formData.recurrenceDaysOfWeek : []
                      })
                    }}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="DAILY">일</option>
                    <option value="WEEKLY">매주</option>
                    <option value="MONTHLY">매월</option>
                    <option value="YEARLY">매년</option>
                  </select>
                </div>
                {formData.recurrenceType === 'WEEKLY' && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">반복 요일</label>
                    <div className="flex flex-wrap gap-2">
                      {[
                        { value: 0, label: '일' },
                        { value: 1, label: '월' },
                        { value: 2, label: '화' },
                        { value: 3, label: '수' },
                        { value: 4, label: '목' },
                        { value: 5, label: '금' },
                        { value: 6, label: '토' }
                      ].map((day) => (
                        <button
                          key={day.value}
                          type="button"
                          onClick={() => {
                            const current = formData.recurrenceDaysOfWeek
                            const newDays = current.includes(day.value)
                              ? current.filter(d => d !== day.value)
                              : [...current, day.value].sort()
                            setFormData({ ...formData, recurrenceDaysOfWeek: newDays })
                          }}
                          className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
                            formData.recurrenceDaysOfWeek.includes(day.value)
                              ? 'bg-blue-600 text-white'
                              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                          }`}
                        >
                          {day.label}
                        </button>
                      ))}
                    </div>
                    {formData.recurrenceDaysOfWeek.length === 0 && (
                      <p className="text-xs text-red-500 mt-1">최소 하나의 요일을 선택해주세요</p>
                    )}
                  </div>
                )}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">반복 종료일 (선택사항)</label>
                  <input
                    type="datetime-local"
                    value={formData.recurrenceEndDate}
                    onChange={(e) => setFormData({ ...formData, recurrenceEndDate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <p className="text-xs text-gray-500 mt-1">비워두면 1년 후까지 반복됩니다</p>
                </div>
              </div>
            )}
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-md p-3 text-red-600 text-sm">
              {error}
            </div>
          )}

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-gray-200">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:bg-blue-400 disabled:cursor-not-allowed transition"
            >
              {saving ? '생성 중...' : '생성'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

