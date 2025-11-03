import { Outlet, useParams } from 'react-router-dom'

export default function TeamLayout() {
  const { id } = useParams()
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="sticky top-0 z-10 bg-white/80 backdrop-blur border-b">
        <div className="mx-auto max-w-6xl px-4 py-3 flex items-center justify-between">
          <a href="/dashboard" className="text-lg font-bold tracking-tight">TeamSpace</a>
          <nav className="flex items-center gap-2 text-sm">
            <a className="px-3 py-2 rounded-md hover:bg-gray-100" href={`/team/${id}/calendar`}>캘린더</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100" href={`/team/${id}/tasks`}>작업</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100" href={`/team/${id}/workhours`}>근무시간</a>
            <a className="px-3 py-2 rounded-md hover:bg-gray-100" href={`/team/${id}/settings`}>팀 설정</a>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}


