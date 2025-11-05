type Props = { open: boolean }

export default function RightPanel({ open }: Props) {
  if (!open) return null
  return (
    <aside className="hidden xl:block w-80 border-l h-[calc(100vh-64px)] sticky top-[64px] bg-white">
      <div className="p-4">
        <h2 className="font-semibold text-gray-900 mb-3">이번 주 요약</h2>
        <div className="rounded-xl border p-4 bg-gray-50">
          <div className="text-sm text-gray-600">총 작업 시간</div>
          <div className="text-2xl font-bold">16h</div>
        </div>
        <div className="mt-4 rounded-xl border p-4 bg-gray-50">
          <div className="text-sm text-gray-600">미할당 Task</div>
          <div className="text-2xl font-bold">3</div>
        </div>
        <button className="mt-6 w-full px-4 py-2 rounded-lg text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700">
          AI 재배치
        </button>
      </div>
    </aside>
  )
}


