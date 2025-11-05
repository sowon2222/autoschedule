export default function Sidebar() {
  return (
    <aside className="hidden lg:block w-72 border-r h-[calc(100vh-64px)] sticky top-[64px] bg-gray-50/60">
      <div className="p-4">
        <h2 className="font-semibold text-gray-900 mb-3">내 팀</h2>
        <ul className="space-y-1 text-sm">
          <li><a className="block px-3 py-2 rounded hover:bg-white border">AI개발팀</a></li>
          <li><a className="block px-3 py-2 rounded hover:bg-white border">모바일팀</a></li>
        </ul>

        <h2 className="font-semibold text-gray-900 mt-8 mb-3">할 일(Task)</h2>
        <div className="space-y-2">
          {['PR 리뷰', 'API 명세 정리', '데일리 미팅'].map((t) => (
            <div key={t} className="rounded-lg bg-white border p-3 text-sm shadow-sm cursor-grab">
              {t}
            </div>
          ))}
        </div>
      </div>
    </aside>
  )
}


