import { create } from 'zustand'

type User = { id: number; email: string; name: string } | null

type AuthState = {
  user: User
  setUser: (u: User) => void
  logout: () => void
}

export const useAuth = create<AuthState>((set) => ({
  user: null,
  setUser: (u) => set({ user: u }),
  logout: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userEmail')
    localStorage.removeItem('userName')
    set({ user: null })
    location.href = '/login'
  }
}))


