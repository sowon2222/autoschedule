import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import { useAuth } from '../store/auth'

export default function Register() {
  const { setUser } = useAuth()
  const [error, setError] = useState<string>('')
  const [isLoading, setIsLoading] = useState(false)
  
  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    
    try {
      const f = new FormData(e.target as HTMLFormElement)
      const password = f.get('password') as string
      const password2 = f.get('password2') as string
      
      // 비밀번호 확인
      if (password !== password2) {
        setError('비밀번호가 일치하지 않습니다.')
        setIsLoading(false)
        return
      }
      
      const { data } = await api.post('/api/auth/signup', {
        name: f.get('name'),
        email: f.get('email'),
        password: password
      })
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('userEmail', data.email)
      localStorage.setItem('userName', data.name)
      
      // 사용자 정보를 store에 설정
      setUser({
        id: data.userId,
        email: data.email,
        name: data.name
      })
      
      location.href = '/'
    } catch (err: any) {
      let errorMessage = '회원가입에 실패했습니다.'
      if (err.response?.data?.message) {
        errorMessage = err.response.data.message
      } else if (err.message) {
        errorMessage = err.message
      }
      setError(errorMessage)
    } finally {
      setIsLoading(false)
    }
  }
  
  return (
    <div className="min-h-screen bg-white flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* 로고 영역 */}
        <div className="text-center mb-12">
          <Link to="/" className="inline-block">
            <h1 className="text-4xl font-bold text-blue-600 mb-2">AutoSchedule</h1>
          </Link>
        </div>
        
        {/* 회원가입 폼 */}
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-8">
          <h2 className="text-2xl font-bold text-gray-900 mb-2">회원가입</h2>
          <p className="text-sm text-gray-500 mb-8">새로운 계정을 만들어 시작하세요</p>
          
          {/* 에러 메시지 표시 */}
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-600">
              {error}
            </div>
          )}
          
          <form onSubmit={submit} className="space-y-6">
            {/* 이름 입력 */}
            <div>
              <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-2">
                이름
              </label>
              <input
                id="name"
                name="name"
                type="text"
                placeholder="이름을 입력하세요"
                required
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
            </div>
            
            {/* 이메일 입력 */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                이메일
              </label>
              <input
                id="email"
                name="email"
                type="email"
                placeholder="이메일을 입력하세요"
                required
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
            </div>
            
            {/* 비밀번호 입력 */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
                비밀번호
              </label>
              <input
                id="password"
                name="password"
                type="password"
                placeholder="비밀번호를 입력하세요"
                required
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
            </div>
            
            {/* 비밀번호 확인 입력 */}
            <div>
              <label htmlFor="password2" className="block text-sm font-medium text-gray-700 mb-2">
                비밀번호 확인
              </label>
              <input
                id="password2"
                name="password2"
                type="password"
                placeholder="비밀번호를 다시 입력하세요"
                required
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
            </div>
            
            {/* 회원가입 버튼 */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 disabled:cursor-not-allowed text-white font-semibold py-4 rounded-lg transition duration-200 shadow-sm hover:shadow-md"
            >
              {isLoading ? '가입 중...' : '회원가입'}
            </button>
          </form>
          
          {/* 도움말 링크 */}
          <div className="mt-8 pt-6 border-t border-gray-200">
            <div className="flex items-center justify-center gap-4 text-sm">
              <Link to="/login" className="text-gray-600 hover:text-blue-600 transition">
                이미 계정이 있으신가요? 로그인
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
