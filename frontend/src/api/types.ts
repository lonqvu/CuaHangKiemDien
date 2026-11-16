export interface Product { id: number; name: string; categoryId: number | null; categoryName: string | null; unit: string | null; price: number }
export interface Category { id: number; name: string }
export interface Customer { id: number; name: string; address: string | null; phone: string | null; debt: number }
/** Mot dong so cong no, da dien giai san o backend (giong dong tren phieu thu / sao ke). */
export interface LedgerRow {
  id: number; type: 'ORDER' | 'PAYMENT' | 'ADJUST' | 'MANUAL' | 'VOID'; description: string
  increase: number | null; decrease: number | null; balanceAfter: number; createdAt: string; createdBy: string | null
  receiptNo: string | null; method: string | null; voided: boolean; canVoid: boolean
}
export interface PaymentResult { customer: Customer; ledgerId: number; receiptNo: string }
export interface OrderItem { id: number; productId: number; productName: string; unit: string | null; unitPrice: number; quantity: number; sheets: number; factor: number; totalQuantity: number; lineTotal: number }
export interface Order {
  id: number; code: string; status: 'DRAFT' | 'PAID' | 'CANCELLED'
  customerId: number | null; customerName: string | null; customerAddress: string | null; customerPhone: string | null; staffName: string | null
  itemsTotal: number; shippingFee: number; previousDebt: number; totalPayable: number; paidAmount: number; debtAfter: number; refundAmount: number
  note: string | null; createdAt: string; checkedOutAt: string | null; items: OrderItem[]
}
export interface CancelEffect { debtReversal: number; refund: number; debtBefore: number; debtAfter: number }
export interface DayRevenue { day: string; orders: number; revenue: number; collected: number }
export interface Period { current: number; previous: number }
export interface Overview {
  day: Period; week: Period; month: Period; year: Period
  ordersThisMonth: number; avgOrderThisMonth: number; newCustomersThisMonth: number; returningRate: number
  totalDebt: number; customersInDebt: number
}
export interface TopProduct { productId: number | null; productName: string; totalQuantity: number; revenue: number }
export interface TopCustomer { customerId: number; customerName: string; orders: number; totalAmount: number }
export interface CategoryStat { category: string; productCount: number; totalQuantity: number; revenue: number }
export interface CustomerStat { newCustomers: number; returningCustomers: number; totalCustomers: number; returningRate: number }
export interface MonthRevenue { month: number; revenue: number }
export interface UserAccount { id: number; username: string; fullName: string; role: 'ADMIN' | 'STAFF'; active: boolean }
