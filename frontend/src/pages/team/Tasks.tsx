import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../../lib/api'

type Task = { id: number; title: string; dueAt?: string; priority: number }

export default function Tasks() {
  const { id } = useParams()
  const [list, setList] = useState<Task[]>([])
  useEffect(() => { if (id) api.get(`/api/tasks/team/${id}`).then(r => setList(r.data)) }, [id])
  return (
    <table className="w-full border">
      <thead><tr><th className="p-2 border">제목</th><th className="p-2 border">마감</th><th className="p-2 border">우선순위</th></tr></thead>
      <tbody>
      {list.map(t => (
        <tr key={t.id}>
          <td className="p-2 border">{t.title}</td>
          <td className="p-2 border">{t.dueAt?.slice(0,16) || ''}</td>
          <td className="p-2 border">{t.priority}</td>
        </tr>
      ))}
      </tbody>
    </table>
  )
}


