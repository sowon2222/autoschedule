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

  const section: React.CSSProperties = { border: '1px solid #ddd', borderRadius: 6, padding: 12, background: '#fff' }
  const row: React.CSSProperties = { display: 'flex', gap: 8, alignItems: 'center' }
  const input: React.CSSProperties = { border: '1px solid #ccc', borderRadius: 4, padding: '8px 10px', flex: 1 }
  const btn: React.CSSProperties = { border: '1px solid #444', background: '#444', color: '#fff', padding: '8px 12px', borderRadius: 4, cursor: 'pointer' }

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
    <div style={{ display: 'grid', gap: 16 }}>
      {!id && (
        <section style={section}>
          <h3 style={{ margin: '0 0 8px 0' }}>팀 생성</h3>
          <div style={row}>
            <input style={input} placeholder="팀 이름" value={teamName} onChange={e=>setTeamName(e.target.value)} />
            <button style={btn} onClick={createTeam}>생성</button>
          </div>
        </section>
      )}

      <section style={section}>
        <h3 style={{ margin: '0 0 8px 0' }}>내 팀 목록</h3>
        <ul style={{ paddingLeft: 16, margin: 0 }}>
          {teams.map(t => (
            <li key={t.id} style={{ margin: '6px 0' }}>
              <a href={`/team/${t.id}/settings`}>{t.name}</a>
            </li>
          ))}
          {teams.length === 0 && <li>소속된 팀이 없습니다.</li>}
        </ul>
      </section>

      <section style={section}>
        <h3 style={{ margin: '0 0 8px 0' }}>팀 초대</h3>
        <div style={row}>
          <input style={input} placeholder="이메일" value={inviteEmail} onChange={e=>setInviteEmail(e.target.value)} />
          <button style={btn} onClick={invite}>초대</button>
        </div>
      </section>

      <section style={section}>
        <h3 style={{ margin: '0 0 8px 0' }}>팀 멤버</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
          <thead>
          <tr>
            <th style={{ border: '1px solid #ddd', padding: 8, textAlign: 'left' }}>이름</th>
            <th style={{ border: '1px solid #ddd', padding: 8, textAlign: 'left' }}>이메일</th>
            <th style={{ border: '1px solid #ddd', padding: 8 }}>역할</th>
          </tr>
          </thead>
          <tbody>
          {members.map(m => (
            <tr key={m.userId}>
              <td style={{ border: '1px solid #ddd', padding: 8 }}>{m.userName}</td>
              <td style={{ border: '1px solid #ddd', padding: 8 }}>{m.userEmail}</td>
              <td style={{ border: '1px solid #ddd', padding: 8, textAlign: 'center' }}>{m.role}</td>
            </tr>
          ))}
          {members.length === 0 && (
            <tr><td colSpan={3} style={{ border: '1px solid #ddd', padding: 8 }}>멤버가 없습니다.</td></tr>
          )}
          </tbody>
        </table>
      </section>
    </div>
  )
}


