import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'

type WorkHour = {
  id: number
  teamId: number
  userId?: number
  userName?: string
  dow: number  // 0=월요일, 1=화요일, ..., 6=일요일
  startMin: number  // 분 단위 (예: 540 = 09:00)
  endMin: number    // 분 단위 (예: 1080 = 18:00)
}

type Member = {
  userId: number
  userName: string
  userEmail: string
  role: string
}

const DAYS_OF_WEEK = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일']
const DEFAULT_START_MIN = 9 * 60  // 9시
const DEFAULT_END_MIN = 18 * 60   // 18시
const DEFAULT_DAYS = [0, 1, 2, 3, 4] // 월-금

export default function WorkHours() {
  const { id } = useParams()
  const [workHours, setWorkHours] = useState<WorkHour[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [loading, setLoading] = useState(false)
  const [editingUserId, setEditingUserId] = useState<number | null>(null)
  // 편집 중인 근무시간 임시 저장
  const [editingWorkHours, setEditingWorkHours] = useState<Map<number, Map<number, { startMin: number; endMin: number }>>>(new Map())

  const loadData = async () => {
    if (!id) return
    setLoading(true)
    try {
      const [workHoursRes, membersRes] = await Promise.all([
        api.get(`/api/teams/${id}/work-hours`),
        api.get(`/api/teams/${id}/members`)
      ])
      setWorkHours(workHoursRes.data)
      setMembers(membersRes.data)
    } catch (error: any) {
      console.error('데이터 로드 실패:', error)
      alert('데이터를 불러오는데 실패했습니다: ' + (error.response?.data?.message || error.message))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [id])

  const formatTime = (minutes: number): string => {
    const hours = Math.floor(minutes / 60)
    const mins = minutes % 60
    return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`
  }

  const minutesFromTime = (hour: number, minute: number): number => {
    return hour * 60 + minute
  }

  // 각 멤버별로 요일별 근무시간 가져오기
  const getWorkHourForMember = (userId: number, dow: number): WorkHour | null => {
    return workHours.find(wh => wh.userId === userId && wh.dow === dow) || null
  }

  // 멤버의 기본 근무시간 가져오기 (없으면 기본값)
  const getDefaultWorkHours = (userId: number): Map<number, { startMin: number; endMin: number }> => {
    // 편집 중인 경우 편집 중인 값 사용
    if (editingUserId === userId && editingWorkHours.has(userId)) {
      return editingWorkHours.get(userId)!
    }
    
    const result = new Map<number, { startMin: number; endMin: number }>()
    for (const dow of DEFAULT_DAYS) {
      const existing = getWorkHourForMember(userId, dow)
      if (existing) {
        result.set(dow, { startMin: existing.startMin, endMin: existing.endMin })
      } else {
        result.set(dow, { startMin: DEFAULT_START_MIN, endMin: DEFAULT_END_MIN })
      }
    }
    return result
  }

  // 멤버의 모든 요일 근무시간 저장/업데이트
  const handleSaveMemberWorkHours = async (userId: number, workHoursMap: Map<number, { startMin: number; endMin: number }>) => {
    if (!id) return

    setLoading(true)
    try {
      // 기존 근무시간 조회
      const existingWorkHours = workHours.filter(wh => wh.userId === userId)
      
      // 저장할 근무시간 목록 생성
      const requests: Array<{ teamId: number; userId: number; dow: number; startMin: number; endMin: number }> = []
      const toDelete: number[] = []

      // 기본 요일(월-금) 처리
      for (const dow of DEFAULT_DAYS) {
        const wh = workHoursMap.get(dow)
        if (wh) {
          const existing = existingWorkHours.find(e => e.dow === dow)
          if (existing) {
            // 기존 것 수정
            try {
              await api.put(`/api/teams/${id}/work-hours/${existing.id}`, {
                userId,
                dow,
                startMin: wh.startMin,
                endMin: wh.endMin
              })
            } catch (error: any) {
              if (error.response?.status === 409) {
                alert('다른 사용자가 편집 중입니다. 잠시 후 다시 시도해주세요.')
                return
              }
              throw error
            }
          } else {
            // 새로 생성
            requests.push({
              teamId: Number(id),
              userId,
              dow,
              startMin: wh.startMin,
              endMin: wh.endMin
            })
          }
        }
      }

      // 새로 생성할 것들 일괄 생성
      if (requests.length > 0) {
        await api.post(`/api/teams/${id}/work-hours`, requests)
      }

      // 기본 요일이 아닌 기존 근무시간 삭제 (사용자가 제거한 경우)
      for (const existing of existingWorkHours) {
        if (!DEFAULT_DAYS.includes(existing.dow)) {
          toDelete.push(existing.id)
        }
      }

      // 삭제
      for (const deleteId of toDelete) {
        try {
          await api.delete(`/api/teams/${id}/work-hours/${deleteId}`)
        } catch (error: any) {
          if (error.response?.status !== 404) {
            console.error('삭제 실패:', error)
          }
        }
      }

      await loadData()
      setEditingUserId(null)
      const newEditing = new Map(editingWorkHours)
      newEditing.delete(userId)
      setEditingWorkHours(newEditing)
      alert('근무시간이 저장되었습니다.')
    } catch (error: any) {
      console.error('저장 실패:', error)
      if (error.response?.status === 409) {
        alert('다른 사용자가 편집 중입니다. 잠시 후 다시 시도해주세요.')
      } else {
        alert('저장에 실패했습니다: ' + (error.response?.data?.message || error.message))
      }
    } finally {
      setLoading(false)
    }
  }

  // 멤버별 근무시간 초기화 (기본값으로 설정)
  const handleInitializeMember = async (userId: number) => {
    if (!id) return
    if (!confirm('기본 근무시간(월-금, 9시-18시)으로 초기화하시겠습니까?')) return

    setLoading(true)
    try {
      const requests = DEFAULT_DAYS.map(dow => ({
        teamId: Number(id),
        userId,
        dow,
        startMin: DEFAULT_START_MIN,
        endMin: DEFAULT_END_MIN
      }))

      await api.post(`/api/teams/${id}/work-hours`, requests)
      await loadData()
      alert('기본 근무시간으로 초기화되었습니다.')
    } catch (error: any) {
      console.error('초기화 실패:', error)
      if (error.response?.status === 409) {
        alert('다른 사용자가 편집 중입니다. 잠시 후 다시 시도해주세요.')
      } else {
        alert('초기화에 실패했습니다: ' + (error.response?.data?.message || error.message))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">근무시간 설정</h1>
        <p className="text-sm text-gray-600 mt-1">각 팀원의 근무시간을 설정하세요. 기본값은 월-금, 9시-18시입니다.</p>
      </div>

      {loading && !workHours.length && !members.length && (
        <div className="text-center py-12 text-gray-500">로딩 중...</div>
      )}

      <div className="space-y-6">
        {members.map((member) => {
          const memberWorkHours = getDefaultWorkHours(member.userId)
          const isEditing = editingUserId === member.userId

          return (
            <div key={member.userId} className="bg-white rounded-xl shadow-lg border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">{member.userName}</h3>
                  <p className="text-sm text-gray-500">{member.userEmail}</p>
                </div>
                <div className="flex gap-2">
                  {!isEditing && (
                    <>
                      <button
                        onClick={() => handleInitializeMember(member.userId)}
                        className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
                      >
                        기본값으로 초기화
                      </button>
                      <button
                        onClick={() => {
                          // 편집 시작 시 현재 값으로 초기화
                          const current = getDefaultWorkHours(member.userId)
                          const newEditing = new Map(editingWorkHours)
                          newEditing.set(member.userId, new Map(current))
                          setEditingWorkHours(newEditing)
                          setEditingUserId(member.userId)
                        }}
                        className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                      >
                        수정
                      </button>
                    </>
                  )}
                  {isEditing && (
                    <>
                      <button
                        onClick={() => {
                          setEditingUserId(null)
                          const newEditing = new Map(editingWorkHours)
                          newEditing.delete(member.userId)
                          setEditingWorkHours(newEditing)
                        }}
                        className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
                      >
                        취소
                      </button>
                      <button
                        onClick={() => handleSaveMemberWorkHours(member.userId, memberWorkHours)}
                        disabled={loading}
                        className="px-3 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {loading ? '저장 중...' : '저장'}
                      </button>
                    </>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-5 gap-4">
                {DEFAULT_DAYS.map((dow) => {
                  const wh = memberWorkHours.get(dow)!
                  const existing = getWorkHourForMember(member.userId, dow)
                  
                  return (
                    <div key={dow} className="border border-gray-200 rounded-lg p-4">
                      <div className="font-medium text-gray-900 mb-3">{DAYS_OF_WEEK[dow]}</div>
                      {isEditing ? (
                        <div className="space-y-2">
                          <div>
                            <label className="block text-xs text-gray-600 mb-1">시작</label>
                            <div className="flex gap-1">
                              <input
                                type="number"
                                min="0"
                                max="23"
                                value={Math.floor(wh.startMin / 60)}
                                onChange={(e) => {
                                  const hour = Number(e.target.value)
                                  const newStartMin = minutesFromTime(hour, wh.startMin % 60)
                                  const newEditing = new Map(editingWorkHours)
                                  const userMap = new Map(newEditing.get(member.userId) || memberWorkHours)
                                  userMap.set(dow, { startMin: newStartMin, endMin: wh.endMin })
                                  newEditing.set(member.userId, userMap)
                                  setEditingWorkHours(newEditing)
                                }}
                                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                placeholder="시"
                              />
                              <input
                                type="number"
                                min="0"
                                max="59"
                                value={wh.startMin % 60}
                                onChange={(e) => {
                                  const minute = Number(e.target.value)
                                  const newStartMin = minutesFromTime(Math.floor(wh.startMin / 60), minute)
                                  const newEditing = new Map(editingWorkHours)
                                  const userMap = new Map(newEditing.get(member.userId) || memberWorkHours)
                                  userMap.set(dow, { startMin: newStartMin, endMin: wh.endMin })
                                  newEditing.set(member.userId, userMap)
                                  setEditingWorkHours(newEditing)
                                }}
                                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                placeholder="분"
                              />
                            </div>
                          </div>
                          <div>
                            <label className="block text-xs text-gray-600 mb-1">종료</label>
                            <div className="flex gap-1">
                              <input
                                type="number"
                                min="0"
                                max="23"
                                value={Math.floor(wh.endMin / 60)}
                                onChange={(e) => {
                                  const hour = Number(e.target.value)
                                  const newEndMin = minutesFromTime(hour, wh.endMin % 60)
                                  const newEditing = new Map(editingWorkHours)
                                  const userMap = new Map(newEditing.get(member.userId) || memberWorkHours)
                                  userMap.set(dow, { startMin: wh.startMin, endMin: newEndMin })
                                  newEditing.set(member.userId, userMap)
                                  setEditingWorkHours(newEditing)
                                }}
                                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                placeholder="시"
                              />
                              <input
                                type="number"
                                min="0"
                                max="59"
                                value={wh.endMin % 60}
                                onChange={(e) => {
                                  const minute = Number(e.target.value)
                                  const newEndMin = minutesFromTime(Math.floor(wh.endMin / 60), minute)
                                  const newEditing = new Map(editingWorkHours)
                                  const userMap = new Map(newEditing.get(member.userId) || memberWorkHours)
                                  userMap.set(dow, { startMin: wh.startMin, endMin: newEndMin })
                                  newEditing.set(member.userId, userMap)
                                  setEditingWorkHours(newEditing)
                                }}
                                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                placeholder="분"
                              />
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="text-sm text-gray-700">
                          <div>{formatTime(wh.startMin)} ~ {formatTime(wh.endMin)}</div>
                          {existing && (
                            <div className="text-xs text-gray-500 mt-1">설정됨</div>
                          )}
                          {!existing && (
                            <div className="text-xs text-gray-400 mt-1">기본값</div>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )
        })}

        {members.length === 0 && (
          <div className="bg-gray-50 rounded-lg p-12 text-center text-gray-500">
            팀원이 없습니다.
          </div>
        )}
      </div>
    </div>
  )
}
