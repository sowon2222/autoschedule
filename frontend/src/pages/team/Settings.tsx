import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'

type Team = { id: number; name: string }
type Member = { userId: number; userName: string; userEmail: string; role: string }

export default function Settings(){
  const { id } = useParams()
  const [teamName, setTeamName] = useState('')
  const [currentTeam, setCurrentTeam] = useState<Team | null>(null)
  const [inviteEmail, setInviteEmail] = useState('')
  const [members, setMembers] = useState<Member[]>([])
  const [showCreateTeam, setShowCreateTeam] = useState(false)
  const userId = Number(localStorage.getItem('userId') || '0')

  const reload = async () => {
    if (id) {
      const m = await api.get(`/api/teams/${id}/members`)
      setMembers(m.data)
      const t = await api.get(`/api/teams/${id}`)
      setCurrentTeam(t.data)
    }
  }

  useEffect(() => { reload() }, [id])

  const createTeam = async () => {
    if (!teamName.trim()) return
    const { data } = await api.post('/api/teams', { name: teamName.trim() })
    setTeamName('')
    setShowCreateTeam(false)
    try { await api.post(`/api/teams/${data.id}/invite`, { userId, role: 'OWNER' }) } catch {}
    await reload()
    alert('팀이 생성되었습니다.')
    // 새로 생성된 팀의 설정 페이지로 이동
    window.location.href = `/team/${data.id}/settings`
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
    <div className="p-6">
      <div className="grid gap-6">
        {showCreateTeam && (
          <section className="bg-white rounded-xl shadow-lg border border-gray-200 p-6">
            <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
              <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              팀 생성
            </h3>
            <div className="flex gap-3 items-center">
              <input 
                className="border border-gray-300 rounded-lg px-4 py-2.5 flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
                placeholder="팀 이름을 입력하세요" 
                value={teamName} 
                onChange={e=>setTeamName(e.target.value)} 
              />
              <button 
                className="bg-gradient-to-r from-blue-600 to-purple-600 text-white px-6 py-2.5 rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium" 
                onClick={createTeam}
              >
                생성
              </button>
              <button 
                className="bg-gray-100 text-gray-700 px-6 py-2.5 rounded-lg hover:bg-gray-200 transition font-medium" 
                onClick={() => {
                  setShowCreateTeam(false)
                  setTeamName('')
                }}
              >
                취소
              </button>
            </div>
          </section>
        )}

        {id && currentTeam && (
          <section className="bg-white rounded-xl shadow-lg border border-gray-200 p-6">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-xl font-bold text-gray-800 mb-1">
                  {currentTeam.name}
                </h3>
                <p className="text-sm text-gray-600">현재 팀</p>
              </div>
              <button 
                className="bg-gradient-to-r from-blue-600 to-purple-600 text-white px-4 py-2 rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium text-sm flex items-center gap-2"
                onClick={() => setShowCreateTeam(true)}
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                팀 추가
              </button>
            </div>
          </section>
        )}

        <section className="bg-white rounded-xl shadow-lg border border-gray-200 p-6">
          <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            팀원 초대
          </h3>
          <div className="flex gap-3 items-center">
            <input 
              className="border border-gray-300 rounded-lg px-4 py-2.5 flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
              placeholder="이메일 주소를 입력하세요" 
              value={inviteEmail} 
              onChange={e=>setInviteEmail(e.target.value)} 
            />
            <button 
              className="bg-gradient-to-r from-blue-600 to-purple-600 text-white px-6 py-2.5 rounded-lg hover:from-blue-700 hover:to-purple-700 transition-all shadow-md hover:shadow-lg font-medium" 
              onClick={invite}
            >
              초대
            </button>
          </div>
        </section>

        <section className="bg-white rounded-xl shadow-lg border border-gray-200 p-6">
          <h3 className="text-xl font-bold mb-4 text-gray-800 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            팀 멤버
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
              <tr className="bg-gradient-to-r from-gray-50 to-gray-100">
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">이름</th>
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">이메일</th>
                <th className="p-3 text-left font-bold text-gray-700 border-b border-gray-200">역할</th>
              </tr>
              </thead>
              <tbody>
              {members.map(m => (
                <tr key={m.userId} className="hover:bg-blue-50 transition-colors">
                  <td className="p-3 border-b border-gray-100 font-medium text-gray-900">{m.userName}</td>
                  <td className="p-3 border-b border-gray-100 text-gray-700">{m.userEmail}</td>
                  <td className="p-3 border-b border-gray-100">
                    <span className="px-3 py-1 rounded-full bg-blue-100 text-blue-700 font-medium text-xs">
                      {m.role}
                    </span>
                  </td>
                </tr>
              ))}
              {members.length === 0 && (
                <tr>
                  <td colSpan={3} className="p-8 text-gray-500 text-center bg-gray-50">
                    멤버가 없습니다
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


