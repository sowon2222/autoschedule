import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'

type Team = { id: number; name: string }
type Member = { userId: number; userName: string; userEmail: string; role: string }

export default function Settings(){
  const { id } = useParams()
  const [teamName, setTeamName] = useState('')
  const [teams, setTeams] = useState<Team[]>([])
  const [inviteEmail, setInviteEmail] = useState('')
  const [members, setMembers] = useState<Member[]>([])
  const userId = Number(localStorage.getItem('userId') || '0')

  const reload = async () => {
    if (id) {
      const m = await api.get(`/api/teams/${id}/members`)
      setMembers(m.data)
    }
    if (userId) {
      const t = await api.get(`/api/teams/user/${userId}`)
      setTeams(t.data)
    }
  }

  useEffect(() => { reload() }, [id])

  const createTeam = async () => {
    if (!teamName.trim()) return
    const { data } = await api.post('/api/teams', { name: teamName.trim() })
    setTeamName('')
    try { await api.post(`/api/teams/${data.id}/invite`, { userId, role: 'OWNER' }) } catch {}
    await reload()
    alert('팀이 생성되었습니다.')
  }

  const invite = async () => {
    if (!id) return
    if (!inviteEmail.trim()) return
    await api.post(`/api/teams/${id}/invite`, { email: inviteEmail.trim(), role: 'MEMBER' })
    setInviteEmail('')
    await reload()
    alert('초대되었습니다.')
  }

  return (
    <div className="p-6 bg-gradient-to-br from-gray-50 to-white min-h-screen">
      <div className="mb-6">
        <h2 className="text-3xl font-extrabold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent mb-2">
          팀 설정
        </h2>
        <p className="text-gray-600">팀을 생성하고 멤버를 관리하세요</p>
      </div>
      <div className="grid gap-6">
        {!id && (
          <section className="border-2 border-gray-200 rounded-xl p-6 bg-white shadow-lg hover:shadow-xl transition-shadow">
            <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
              <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              팀 생성
            </h3>
            <div className="flex gap-3 items-center">
              <input 
                className="border-2 border-gray-300 rounded-lg px-4 py-2.5 flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
                placeholder="팀 이름을 입력하세요" 
                value={teamName} 
                onChange={e=>setTeamName(e.target.value)} 
              />
              <button 
                className="bg-gradient-to-r from-blue-600 to-purple-600 text-white px-6 py-2.5 rounded-lg cursor-pointer hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium" 
                onClick={createTeam}
              >
                생성
              </button>
            </div>
          </section>
        )}

        <section className="border-2 border-gray-200 rounded-xl p-6 bg-white shadow-lg hover:shadow-xl transition-shadow">
          <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
            내 팀 목록
          </h3>
          <ul className="space-y-2">
            {teams.map(t => (
              <li key={t.id}>
                <a 
                  href={`/team/${t.id}/settings`} 
                  className="block px-4 py-3 rounded-lg bg-gradient-to-r from-blue-50 to-purple-50 border border-blue-200 text-blue-700 hover:from-blue-100 hover:to-purple-100 hover:shadow-md transition-all font-medium"
                >
                  {t.name}
                </a>
              </li>
            ))}
            {teams.length === 0 && (
              <li className="text-gray-500 px-4 py-3 bg-gray-50 rounded-lg border border-gray-200">
                소속된 팀이 없습니다.
              </li>
            )}
          </ul>
        </section>

        <section className="border-2 border-gray-200 rounded-xl p-6 bg-white shadow-lg hover:shadow-xl transition-shadow">
          <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            팀 초대
          </h3>
          <div className="flex gap-3 items-center">
            <input 
              className="border-2 border-gray-300 rounded-lg px-4 py-2.5 flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
              placeholder="이메일 주소를 입력하세요" 
              value={inviteEmail} 
              onChange={e=>setInviteEmail(e.target.value)} 
            />
            <button 
              className="bg-gradient-to-r from-blue-600 to-purple-600 text-white px-6 py-2.5 rounded-lg cursor-pointer hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium" 
              onClick={invite}
            >
              초대
            </button>
          </div>
        </section>

        <section className="border-2 border-gray-200 rounded-xl p-6 bg-white shadow-lg hover:shadow-xl transition-shadow">
          <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            팀 멤버
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
              <tr className="bg-gradient-to-r from-gray-50 to-gray-100">
                <th className="border-2 border-gray-300 p-3 text-left font-bold text-gray-700">이름</th>
                <th className="border-2 border-gray-300 p-3 text-left font-bold text-gray-700">이메일</th>
                <th className="border-2 border-gray-300 p-3 font-bold text-gray-700">역할</th>
              </tr>
              </thead>
              <tbody>
              {members.map(m => (
                <tr key={m.userId} className="hover:bg-blue-50 transition-colors">
                  <td className="border-2 border-gray-300 p-3 font-medium">{m.userName}</td>
                  <td className="border-2 border-gray-300 p-3">{m.userEmail}</td>
                  <td className="border-2 border-gray-300 p-3 text-center">
                    <span className="px-3 py-1 rounded-full bg-blue-100 text-blue-700 font-medium text-xs">
                      {m.role}
                    </span>
                  </td>
                </tr>
              ))}
              {members.length === 0 && (
                <tr>
                  <td colSpan={3} className="border-2 border-gray-300 p-4 text-gray-500 text-center bg-gray-50">
                    멤버가 없습니다.
                  </td>
                </tr>
              )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  )
}


