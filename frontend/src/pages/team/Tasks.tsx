import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'

type Task = { id: number; title: string; dueAt?: string; priority: number; durationMin: number }

export default function Tasks() {
  const { id } = useParams()
  const [list, setList] = useState<Task[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [formData, setFormData] = useState({
    title: '',
    durationMin: 60,
    dueAt: '',
    priority: 3,
    assigneeId: undefined as number | undefined,
    splittable: true,
    tags: ''
  })

  const loadTasks = () => {
    if (id) {
      api.get(`/api/tasks/team/${id}`).then(r => setList(r.data)).catch(console.error)
    }
  }

  useEffect(() => {
    loadTasks()
  }, [id])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!id) return

    try {
      const payload = {
        teamId: Number(id),
        title: formData.title,
        durationMin: formData.durationMin,
        dueAt: formData.dueAt ? new Date(formData.dueAt).toISOString() : null,
        priority: formData.priority,
        assigneeId: formData.assigneeId || null,
        splittable: formData.splittable,
        tags: formData.tags || null
      }
      await api.post('/api/tasks', payload)
      setModalOpen(false)
      setFormData({
        title: '',
        durationMin: 60,
        dueAt: '',
        priority: 3,
        assigneeId: undefined,
        splittable: true,
        tags: ''
      })
      loadTasks()
    } catch (error: any) {
      alert(error.response?.data?.message || '작업 추가에 실패했습니다.')
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-bold">작업 목록</h2>
        <button
          onClick={() => setModalOpen(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          작업 추가
        </button>
      </div>

      <table className="w-full border">
        <thead>
          <tr>
            <th className="p-2 border">제목</th>
            <th className="p-2 border">소요시간(분)</th>
            <th className="p-2 border">마감</th>
            <th className="p-2 border">우선순위</th>
          </tr>
        </thead>
        <tbody>
          {list.map(t => (
            <tr key={t.id}>
              <td className="p-2 border">{t.title}</td>
              <td className="p-2 border">{t.durationMin}</td>
              <td className="p-2 border">{t.dueAt ? new Date(t.dueAt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : '-'}</td>
              <td className="p-2 border">{t.priority}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {modalOpen && (
        <div className="fixed inset-0 z-30 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/30" onClick={() => setModalOpen(false)} />
          <div className="relative z-10 w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl border">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">작업 추가</h3>
              <button
                onClick={() => setModalOpen(false)}
                className="h-8 w-8 rounded-full hover:bg-gray-100"
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleSubmit} className="grid grid-cols-1 gap-3">
              <input
                className="border rounded-lg px-3 py-2"
                placeholder="제목 *"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                required
              />
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1">소요시간(분) *</label>
                  <input
                    type="number"
                    className="border rounded-lg px-3 py-2 w-full"
                    min="1"
                    value={formData.durationMin}
                    onChange={(e) => setFormData({ ...formData, durationMin: Number(e.target.value) })}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">우선순위 (1-5)</label>
                  <input
                    type="number"
                    className="border rounded-lg px-3 py-2 w-full"
                    min="1"
                    max="5"
                    value={formData.priority}
                    onChange={(e) => setFormData({ ...formData, priority: Number(e.target.value) })}
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">마감일시</label>
                <input
                  type="datetime-local"
                  className="border rounded-lg px-3 py-2 w-full"
                  value={formData.dueAt}
                  onChange={(e) => setFormData({ ...formData, dueAt: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">담당자 ID (선택)</label>
                <input
                  type="number"
                  className="border rounded-lg px-3 py-2 w-full"
                  value={formData.assigneeId || ''}
                  onChange={(e) => setFormData({ ...formData, assigneeId: e.target.value ? Number(e.target.value) : undefined })}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">태그 (선택)</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  placeholder="태그"
                  value={formData.tags}
                  onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="splittable"
                  checked={formData.splittable}
                  onChange={(e) => setFormData({ ...formData, splittable: e.target.checked })}
                />
                <label htmlFor="splittable" className="text-sm">분할 가능</label>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-4 py-2 rounded-md border"
                >
                  취소
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-md text-white bg-blue-600 hover:bg-blue-700"
                >
                  저장
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}


