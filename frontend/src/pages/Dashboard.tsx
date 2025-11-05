import { useEffect, useState } from 'react'
import api from '../lib/api'

type Team = { id: number; name: string; createdAt?: string }

export default function Dashboard() {
  const [teams, setTeams] = useState<Team[]>([])
  useEffect(() => { api.get('/api/teams').then(r => setTeams(r.data)).catch(() => {}) }, [])
  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <h1 className="text-2xl font-bold mb-4 tracking-tight">대시보드</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {teams.map(t => (
          <a
            key={t.id}
            href={`/team/${t.id}/calendar`}
            className="rounded-xl border bg-white p-5 shadow-sm transition hover:shadow-md hover:-translate-y-0.5 text-gray-900 hover:text-gray-900"
          >
            <div className="font-semibold text-gray-900">{t.name}</div>
            <div className="text-sm text-gray-500">{t.createdAt?.slice(0,10)}</div>
          </a>
        ))}
      </div>
    </div>
  )
}


