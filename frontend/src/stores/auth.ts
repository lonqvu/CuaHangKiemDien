import { defineStore } from 'pinia'
import http from '../api/http'

// Luu tren may (localStorage) - boc try/catch: trinh duyet chan / che do an danh van dang nhap duoc
const ls = {
  get: (k: string) => { try { return localStorage.getItem(k) } catch { return null } },
  set: (k: string, v: string) => { try { localStorage.setItem(k, v) } catch { /* bo qua */ } },
  del: (k: string) => { try { localStorage.removeItem(k) } catch { /* bo qua */ } },
}
/** Ten dang nhap nho tren may (khi tich "Ghi nho dang nhap"); khong bao gio nho mat khau. */
export const rememberedUsername = () => ls.get('login.username') ?? ''
const trustKey = (u: string) => `login.trust.${u.trim().toLowerCase()}`

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
    /**
     * Tra ve { otpRequired: true } khi tai khoan bat xac thuc 2 lop ma chua gui ma (goi lai kem otp).
     * remember: phien 7 ngay + nho ten dang nhap; trustDevice: may nay khong hoi ma 2 lop 7 ngay (gui kem token tin cay da luu).
     */
    async login(username: string, password: string, otp?: string, opts: { remember?: boolean; trustDevice?: boolean } = {}): Promise<{ otpRequired: boolean }> {
      const { data } = await http.post('/auth/login', {
        username, password, otp: otp || undefined,
        remember: !!opts.remember, trustDevice: !!opts.trustDevice, trustToken: ls.get(trustKey(username)) || undefined,
      })
      if (data.otpRequired) return { otpRequired: true }
      if (data.trustToken) ls.set(trustKey(username), data.trustToken)
      else ls.del(trustKey(username)) // het han / bi thu hoi
      if (opts.remember) ls.set('login.username', data.username)
      else ls.del('login.username')
      localStorage.setItem('token', data.token)
      this.user = { username: data.username, fullName: data.fullName, role: data.role, twoFactor: !!data.twoFactor }
      localStorage.setItem('user', JSON.stringify(this.user))
      return { otpRequired: false }
    },
    /** Doi mat khau / dang xuat may khac: may dang dung nhan token moi (giu dang nhap). */
    replaceToken(token: string | null | undefined) {
      if (token) localStorage.setItem('token', token)
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
