import { Link, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../store/auth'
import api from '../lib/api'

type Props = Record<string, never>

type Team = { id: number; name: string }

export default function Header(_: Props) {
  const { user, logout, setUser } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [teams, setTeams] = useState<Team[]>([])

  useEffect(() => {
    if (user) return
    const storedId = localStorage.getItem('userId')
    const storedEmail = localStorage.getItem('userEmail')
    const storedName = localStorage.getItem('userName')
    if (storedId && storedEmail && storedName) {
      setUser({
        id: Number(storedId),
        email: storedEmail,
        name: storedName
      })
    }
  }, [user, setUser])

  useEffect(() => {
    const load = async () => {
      if (!user?.id) return
      const { data } = await api.get(`/api/teams/user/${user.id}`)
      setTeams(data)
    }
    load()
  }, [user?.id])
  return (
    <header className="sticky top-0 z-20 bg-white/95 backdrop-blur-md border-b border-gray-200 shadow-md">
      <div className="mx-auto max-w-7xl px-6 py-4 grid grid-cols-[auto_1fr_auto] items-center gap-4">
        {/* Left: Logo */}
        <Link to="/" className="text-2xl font-extrabold tracking-tight bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent hover:from-blue-700 hover:to-purple-700 transition-all">
          AutoSchedule
        </Link>

        {/* Center: 팀 설정 드롭다운 */}
        <div className="justify-self-center relative">
          <button
            type="button"
            onClick={() => setOpen(v => !v)}
            className="px-4 py-2.5 rounded-lg text-sm font-semibold bg-gradient-to-r from-gray-50 to-gray-100 border-2 border-gray-300 text-gray-700 hover:from-blue-50 hover:to-purple-50 hover:border-blue-300 hover:text-blue-700 transition-all shadow-sm hover:shadow-md"
          >
            <span className="flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
              팀 목록
              <svg className={`w-4 h-4 transition-transform ${open ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </span>
          </button>
          {open && (
            <div className="absolute left-1/2 -translate-x-1/2 mt-2 w-72 rounded-xl border-2 border-gray-200 bg-white shadow-2xl p-2 backdrop-blur-sm">
              <div className="max-h-72 overflow-auto">
                {teams.length === 0 && (
                  <div className="px-4 py-3 text-sm text-gray-500 bg-gray-50 rounded-lg">소속된 팀이 없습니다</div>
                )}
                {teams.map((t) => (
                  <button
                    key={t.id}
                    className="w-full text-left px-4 py-3 rounded-lg hover:bg-gradient-to-r hover:from-blue-50 hover:to-purple-50 text-sm font-medium text-gray-700 hover:text-blue-700 transition-all"
                    onClick={() => { setOpen(false); navigate(`/team/${t.id}/settings`) }}
                  >
                    {t.name}
                  </button>
                ))}
              </div>
              <div className="border-t-2 border-gray-200 mt-2 pt-2">
                <button
                  className="w-full px-4 py-3 rounded-lg bg-gradient-to-r from-blue-600 to-purple-600 text-white text-sm font-semibold hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg flex items-center justify-center gap-2"
                  onClick={async () => {
                    const name = window.prompt('새 팀 이름을 입력하세요')
                    if (!name || !name.trim()) return
                    const { data } = await api.post('/api/teams', { name: name.trim() })
                    try { await api.post(`/api/teams/${data.id}/invite`, { userId: user?.id, role: 'OWNER' }) } catch {}
                    setOpen(false)
                    navigate(`/team/${data.id}/settings`)
                  }}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                  </svg>
                  팀 추가
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
              className="px-4 py-2.5 rounded-lg text-sm font-semibold bg-gray-100 border-2 border-gray-300 text-gray-700 hover:bg-gray-200 hover:shadow-md transition-all"
            >
              로그아웃
            </button>
          ) : (
            <>
              <Link
                to="/login"
                className="px-4 py-2.5 rounded-lg text-sm font-semibold bg-gray-100 border-2 border-gray-300 text-gray-700 hover:bg-gray-200 hover:shadow-md transition-all"
              >
                로그인
              </Link>
              <Link
                to="/register"
                className="px-4 py-2.5 rounded-lg text-sm font-semibold bg-gradient-to-r from-blue-600 to-purple-600 text-white hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg"
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


