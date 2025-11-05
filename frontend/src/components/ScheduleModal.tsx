import { useEffect } from 'react'

type Props = {
  open: boolean
  onClose: () => void
}

export default function ScheduleModal({ open, onClose }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  if (!open) return null
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <div className="relative z-10 w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl border">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">일정 만들기</h3>
          <button onClick={onClose} className="h-8 w-8 rounded-full hover:bg-gray-100">✕</button>
        </div>
        <div className="grid grid-cols-1 gap-3">
          <input className="border rounded-lg px-3 py-2" placeholder="제목" />
          <div className="grid grid-cols-2 gap-3">
            <input className="border rounded-lg px-3 py-2" type="datetime-local" />
            <input className="border rounded-lg px-3 py-2" type="datetime-local" />
          </div>
          <input className="border rounded-lg px-3 py-2" placeholder="참석자(쉼표로 구분)" />
          <textarea className="border rounded-lg px-3 py-2" placeholder="메모" rows={3} />
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={onClose} className="px-4 py-2 rounded-md border">취소</button>
            <button className="px-4 py-2 rounded-md text-white bg-blue-600 hover:bg-blue-700">저장</button>
          </div>
        </div>
      </div>
    </div>
  )
}


