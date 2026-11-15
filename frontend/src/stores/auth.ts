import { defineStore } from 'pinia'
import http from '../api/http'

interface User { username: string; fullName: string; role: 'ADMIN' | 'STAFF' }

export const useAuth = defineStore('auth', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('user') || 'null') as User | null,
  }),
  getters: {
    isAdmin: (s) => s.user?.role === 'ADMIN',
    loggedIn: (s) => !!s.user,
  },
  actions: {
    async login(username: string, password: string) {
      const { data } = await http.post('/auth/login', { username, password })
      localStorage.setItem('token', data.token)
      this.user = { username: data.username, fullName: data.fullName, role: data.role }
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    logout() {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      this.user = null
    },
  },
})
