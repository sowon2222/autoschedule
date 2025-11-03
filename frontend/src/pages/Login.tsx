import api from '../lib/api'

export default function Login() {
  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const f = new FormData(e.target as HTMLFormElement)
    const { data } = await api.post('/api/auth/login', {
      email: f.get('email'),
      password: f.get('password')
    })
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('userEmail', data.email)
    localStorage.setItem('userName', data.name)
    location.href = '/dashboard'
  }
  return (
    <div className="min-h-screen bg-gradient-to-b from-white to-gray-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-xl border bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-bold mb-2 tracking-tight">로그인</h1>
        <p className="text-sm text-gray-500 mb-6">계정으로 계속 진행하세요</p>
        <form onSubmit={submit} className="space-y-3">
          <input name="email" placeholder="이메일" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <input name="password" type="password" placeholder="비밀번호" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          <button className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 rounded-lg font-medium">로그인</button>
        </form>
        <div className="mt-4 text-right">
          <a href="/register" className="text-blue-600 text-sm hover:underline">회원가입</a>
        </div>
      </div>
    </div>
  )
}


