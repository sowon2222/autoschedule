import api from '../lib/api'

export default function Register() {
  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const f = new FormData(e.target as HTMLFormElement)
    const { data } = await api.post('/api/auth/signup', {
      name: f.get('name'),
      email: f.get('email'),
      password: f.get('password')
    })
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    location.href = '/dashboard'
  }
  return (
    <div className="p-6 max-w-sm mx-auto space-y-3">
      <h1 className="text-xl font-bold">회원가입</h1>
      <form onSubmit={submit} className="space-y-3">
        <input name="name" placeholder="name" className="w-full border p-2 rounded" />
        <input name="email" placeholder="email" className="w-full border p-2 rounded" />
        <input name="password" type="password" placeholder="password" className="w-full border p-2 rounded" />
        <button className="w-full bg-emerald-600 text-white p-2 rounded">Register</button>
      </form>
    </div>
  )
}


