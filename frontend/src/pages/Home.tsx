export default function Home() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-indigo-50 via-white to-white">
      <header className="sticky top-0 z-10 bg-white/80 backdrop-blur border-b">
        <div className="mx-auto max-w-6xl px-4 py-3 flex items-center justify-between">
          <a href="/" className="text-xl font-bold tracking-tight">AutoSchdule</a>
          <nav className="flex items-center gap-2 text-sm">
            <a className="px-3 py-2 rounded-md hover:bg-gray-100" href="/login">로그인</a>
            <a className="px-3 py-2 rounded-md bg-indigo-600 text-white hover:bg-indigo-700" href="/register">회원가입</a>
          </nav>
        </div>
      </header>

      <main>
        {/* Hero */}
        <section className="mx-auto max-w-6xl px-4 py-20 text-center">
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-gray-900">
            팀 협업을 더 빠르고, 더 똑똑하게
          </h1>
          <p className="mt-4 text-lg text-gray-600 max-w-2xl mx-auto">
            캘린더, 작업, 근무시간을 하나로. 실시간 알림과 함께 팀의 업무를 한 곳에서 관리하세요.
          </p>
          <div className="mt-8 flex items-center justify-center gap-3">
            <a href="/register" className="px-5 py-3 rounded-lg bg-indigo-600 text-white font-medium hover:bg-indigo-700">무료로 시작하기</a>
            <a href="/login" className="px-5 py-3 rounded-lg border font-medium hover:bg-gray-50">이미 계정이 있어요</a>
          </div>
        </section>

        {/* Features */}
        <section className="mx-auto max-w-6xl px-4 pb-16">
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <div className="rounded-2xl border bg-white p-6 shadow-sm">
              <div className="text-indigo-600 font-semibold">캘린더</div>
              <div className="mt-2 text-gray-900 font-semibold">스케줄 한눈에</div>
              <p className="mt-1 text-sm text-gray-600">팀 일정을 공유하고 주/월 보기로 빠르게 파악하세요.</p>
            </div>
            <div className="rounded-2xl border bg-white p-6 shadow-sm">
              <div className="text-indigo-600 font-semibold">작업</div>
              <div className="mt-2 text-gray-900 font-semibold">할 일 관리</div>
              <p className="mt-1 text-sm text-gray-600">담당자, 마감일로 정리하고 진행 상황을 쉽게 추적합니다.</p>
            </div>
            <div className="rounded-2xl border bg-white p-6 shadow-sm">
              <div className="text-indigo-600 font-semibold">근무시간</div>
              <div className="mt-2 text-gray-900 font-semibold">출퇴근 기록</div>
              <p className="mt-1 text-sm text-gray-600">근무시간을 기록하고 팀 통계를 확인하세요.</p>
            </div>
          </div>
        </section>

        {/* CTA Banner */}
        <section className="bg-indigo-600">
          <div className="mx-auto max-w-6xl px-4 py-10 text-center text-white">
            <h2 className="text-2xl md:text-3xl font-bold tracking-tight">지금 바로 팀을 시작해 보세요</h2>
            <p className="mt-2 text-indigo-100">3분이면 설정이 끝나요. 무료로 시작하고 언제든 업그레이드할 수 있어요.</p>
            <a href="/register" className="inline-block mt-6 px-6 py-3 rounded-lg bg-white text-indigo-700 font-semibold hover:bg-indigo-50">무료 시작</a>
          </div>
        </section>
      </main>

      <footer className="border-t">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm text-gray-500 flex items-center justify-between">
          <span>© {new Date().getFullYear()} AutoSchdule</span>
          <div className="flex gap-4">
            <a className="hover:text-gray-700" href="#">이용약관</a>
            <a className="hover:text-gray-700" href="#">개인정보처리방침</a>
          </div>
        </div>
      </footer>
    </div>
  )
}


