import { useState } from 'react'
import api from '../lib/api'

type CreateEventModalProps = {
  isOpen: boolean
  onClose: () => void
  defaultDate?: Date
  teamId?: number
  onSuccess?: () => void
}

export default function CreateEventModal({ isOpen, onClose, defaultDate, teamId, onSuccess }: CreateEventModalProps) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>('')
  const [formData, setFormData] = useState({
    teamId: teamId || '',
    title: '',
    startsAt: defaultDate ? new Date(defaultDate).toISOString().slice(0, 16) : '',
    endsAt: defaultDate ? new Date(new Date(defaultDate).getTime() + 60 * 60 * 1000).toISOString().slice(0, 16) : '',
    location: '',
    attendees: '',
    notes: '',
    fixed: false,
    recurrenceEnabled: false,
    recurrenceType: 'WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY',
    recurrenceEndDate: ''
  })

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSaving(true)

    try {
      const payload: any = {
        teamId: Number(formData.teamId),
        title: formData.title,
        startsAt: new Date(formData.startsAt).toISOString(),
        endsAt: new Date(formData.endsAt).toISOString(),
        fixed: formData.fixed,
        location: formData.location || null,
        attendees: formData.attendees || null,
        notes: formData.notes || null
      }

      if (formData.recurrenceEnabled) {
        payload.recurrenceType = formData.recurrenceType
        payload.recurrenceEndDate = formData.recurrenceEndDate 
          ? new Date(formData.recurrenceEndDate).toISOString() 
          : null
      }

      await api.post('/api/events', payload)
      
      // 폼 초기화
      setFormData({
        teamId: teamId || '',
        title: '',
        startsAt: defaultDate ? new Date(defaultDate).toISOString().slice(0, 16) : '',
        endsAt: defaultDate ? new Date(new Date(defaultDate).getTime() + 60 * 60 * 1000).toISOString().slice(0, 16) : '',
        location: '',
        attendees: '',
        notes: '',
        fixed: false,
        recurrenceEnabled: false,
        recurrenceType: 'WEEKLY',
        recurrenceEndDate: ''
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
            <label className="block text-sm font-medium text-gray-700 mb-1">팀 ID *</label>
            <input
              type="number"
              value={formData.teamId}
              onChange={(e) => setFormData({ ...formData, teamId: e.target.value })}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
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

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">시작일시 *</label>
              <input
                type="datetime-local"
                value={formData.startsAt}
                onChange={(e) => setFormData({ ...formData, startsAt: e.target.value })}
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">종료일시 *</label>
              <input
                type="datetime-local"
                value={formData.endsAt}
                onChange={(e) => setFormData({ ...formData, endsAt: e.target.value })}
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
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

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">참석자 ID (쉼표로 구분)</label>
            <input
              type="text"
              value={formData.attendees}
              onChange={(e) => setFormData({ ...formData, attendees: e.target.value })}
              placeholder="예: 1,2,3"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
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
                    onChange={(e) => setFormData({ ...formData, recurrenceType: e.target.value as any })}
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

