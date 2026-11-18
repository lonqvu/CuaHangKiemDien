import { defineStore } from 'pinia'
import http from '../api/http'

interface User { username: string; fullName: string; role: 'ADMIN' | 'STAFF'; twoFactor?: boolean }

export const useAuth = defineStore('auth', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('user') || 'null') as User | null,
  }),
  getters: {
    isAdmin: (s) => s.user?.role === 'ADMIN',
    loggedIn: (s) => !!s.user,
  },
  actions: {
    /** Tra ve { otpRequired: true } khi tai khoan bat xac thuc 2 lop ma chua gui ma (goi lai kem otp). */
    async login(username: string, password: string, otp?: string): Promise<{ otpRequired: boolean }> {
      const { data } = await http.post('/auth/login', { username, password, otp: otp || undefined })
      if (data.otpRequired) return { otpRequired: true }
      localStorage.setItem('token', data.token)
      this.user = { username: data.username, fullName: data.fullName, role: data.role, twoFactor: !!data.twoFactor }
      localStorage.setItem('user', JSON.stringify(this.user))
      return { otpRequired: false }
    },
    setTwoFactor(on: boolean) {
      if (!this.user) return
      this.user = { ...this.user, twoFactor: on }
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    logout() {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      this.user = null
    },
  },
})
