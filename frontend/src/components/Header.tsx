import { Link, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../store/auth'
import api from '../lib/api'

type Props = Record<string, never>

type Team = { id: number; name: string }

export default function Header(_: Props) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [teams, setTeams] = useState<Team[]>([])

  useEffect(() => {
    const load = async () => {
      if (!user?.id) return
      const { data } = await api.get(`/api/teams/user/${user.id}`)
      setTeams(data)
    }
    load()
  }, [user?.id])
  return (
    <header className="sticky top-0 z-20 bg-white/95 backdrop-blur-md border-b border-gray-200 shadow-sm">
      <div className="mx-auto max-w-7xl px-6 py-3 grid grid-cols-[auto_1fr_auto] items-center gap-4">
        {/* Left: Logo */}
        <Link to="/" className="text-xl font-extrabold tracking-tight text-blue-600">
          AutoSchedule
        </Link>

        {/* Center: 팀 설정 드롭다운 */}
        <div className="justify-self-center relative">
          <button
            type="button"
            onClick={() => setOpen(v => !v)}
            className="px-3 py-2 rounded-md text-sm font-medium bg-gray-100 border border-gray-300 text-gray-700 hover:bg-gray-200"
          >
            팀 설정 ▾
          </button>
          {open && (
            <div className="absolute left-1/2 -translate-x-1/2 mt-2 w-64 rounded-md border bg-white shadow-lg p-1">
              <div className="max-h-72 overflow-auto">
                {teams.length === 0 && (
                  <div className="px-3 py-2 text-sm text-gray-500">소속된 팀이 없습니다</div>
                )}
                {teams.map((t) => (
                  <button
                    key={t.id}
                    className="w-full text-left px-3 py-2 rounded hover:bg-gray-50 text-sm"
                    onClick={() => { setOpen(false); navigate(`/team/${t.id}/settings`) }}
                  >
                    {t.name}
                  </button>
                ))}
              </div>
              <div className="border-t mt-1 pt-1 px-1">
                <button
                  className="w-full px-3 py-2 rounded bg-blue-600 text-white text-sm hover:bg-blue-700"
                  onClick={async () => {
                    const name = window.prompt('새 팀 이름을 입력하세요')
                    if (!name || !name.trim()) return
                    const { data } = await api.post('/api/teams', { name: name.trim() })
                    try { await api.post(`/api/teams/${data.id}/invite`, { userId: user?.id, role: 'OWNER' }) } catch {}
                    setOpen(false)
                    navigate(`/team/${data.id}/settings`)
                  }}
                >
                  + 팀 추가
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Right: 인증 액션 */}
        <div className="justify-self-end flex items-center gap-2">
          {user ? (
            <button
              type="button"
              onClick={logout}
              className="px-3 py-2 rounded-md text-sm font-medium bg-gray-100 border border-gray-300 text-gray-700 hover:bg-gray-200"
            >
              로그아웃
            </button>
          ) : (
            <>
              <Link
                to="/login"
                className="px-3 py-2 rounded-md text-sm font-medium bg-gray-100 border border-gray-300 text-gray-700 hover:bg-gray-200"
              >
                로그인
              </Link>
              <Link
                to="/register"
                className="px-3 py-2 rounded-md text-sm font-medium bg-gray-100 border border-gray-300 text-gray-700 hover:bg-gray-200"
              >
                회원가입
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}


