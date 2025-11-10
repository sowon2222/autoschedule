import { Link } from 'react-router-dom'
import api from '../lib/api'
import { useAuth } from '../store/auth'

export default function Login() {
  const { setUser } = useAuth()
  
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
    if (data.userId) {
      localStorage.setItem('userId', String(data.userId))
    }
    
    // 사용자 정보를 store에 설정
    setUser({
      id: data.userId,
      email: data.email,
      name: data.name
    })
    
    location.href = '/'
  }
  
  return (
    <div
      className="min-h-screen grid place-items-center bg-white px-4 py-12"
      style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', backgroundColor: '#fff' }}
    >
      <div className="w-full max-w-md">
        {/* 로고 영역 */}
        <div className="text-center mb-10">
          <Link to="/" className="inline-block">
            <h1 className="text-4xl font-extrabold tracking-tight text-blue-600">AutoSchedule</h1>
          </Link>
        </div>

        {/* 카드 */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-lg overflow-hidden">
          {/* 탭 (네이버 스타일, 파란색 테마) */}
          <div className="grid grid-cols-3 text-center text-sm font-semibold">
            <button className="py-3 bg-blue-50 text-blue-600">ID/전화번호</button>
            <button className="py-3 hover:bg-gray-50 text-gray-500">일회용 번호</button>
            <button className="py-3 hover:bg-gray-50 text-gray-500">QR코드</button>
          </div>

          <div className="p-8">
            <form onSubmit={submit} className="space-y-6">
              {/* 이메일 */}
              <div>
                <input
                  id="email"
                  name="email"
                  type="email"
                  placeholder="아이디 또는 전화번호"
                  required
                  className="w-full px-4 py-3 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              {/* 비밀번호 */}
              <div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  placeholder="비밀번호"
                  required
                  className="w-full px-4 py-3 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              {/* 옵션 라인 */}
              <div className="flex items-center justify-between text-sm text-gray-600">
                <label className="inline-flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" className="accent-blue-600" />
                  로그인 상태 유지
                </label>
                <div className="inline-flex items-center gap-2 select-none">
                  <span>IP보안</span>
                  <span className="relative inline-flex h-6 w-11 items-center rounded-full bg-blue-600">
                    <span className="ml-auto mr-1 text-[10px] font-bold text-white">ON</span>
                  </span>
                </div>
              </div>

              {/* 로그인 버튼 */}
              <button
                type="submit"
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 rounded-md transition"
              >
                로그인
              </button>

              {/* 패스키 로그인 (보조 버튼) */}
              <button
                type="button"
                className="w-full border border-blue-600 text-blue-600 hover:bg-blue-50 font-semibold py-3 rounded-md transition"
              >
                패스키 로그인
              </button>
            </form>

            {/* 하단 링크 */}
            <div className="mt-6 pt-6 border-t border-gray-200 flex items-center justify-center gap-3 text-sm text-gray-600">
              <a href="#" className="hover:text-blue-600">비밀번호 찾기</a>
              <span className="text-gray-300">|</span>
              <a href="#" className="hover:text-blue-600">아이디 찾기</a>
              <span className="text-gray-300">|</span>
              <Link to="/register" className="hover:text-blue-600">회원가입</Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
