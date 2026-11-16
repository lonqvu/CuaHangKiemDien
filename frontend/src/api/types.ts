export interface Product { id: number; name: string; categoryId: number | null; categoryName: string | null; unit: string | null; price: number }
export interface Category { id: number; name: string; productCount: number }
export interface Customer { id: number; name: string; address: string | null; phone: string | null; debt: number }
export interface ReceiptRow { date: string; oldDebt: number; amount: number; remainingDebt: number }
export interface LedgerRow { id: number; type: 'ORDER' | 'PAYMENT' | 'ADJUST' | 'MANUAL'; amount: number; balanceAfter: number; orderId: number | null; orderCode: string | null; note: string | null; createdAt: string }
export interface OrderItem { id: number; productId: number; productName: string; unit: string | null; unitPrice: number; quantity: number; sheets: number; factor: number; totalQuantity: number; lineTotal: number }
export type OrderStatus = 'DRAFT' | 'PAID' | 'CANCELLED'

/** Ket qua danh sach co phan trang. summary tinh tren TOAN BO ket qua loc. */
export interface Page<T, S = Record<string, unknown>> { items: T[]; total: number; page: number; size: number; summary: S }

/** Dong trong danh sach hoa don (khong kem dong hang). */
export interface OrderRow {
  id: number; code: string | null; status: OrderStatus; customerId: number | null; customerName: string | null; customerPhone: string | null
  staffName: string | null; itemsTotal: number; totalPayable: number; paidAmount: number; debtAfter: number
  createdAt: string; checkedOutAt: string | null
}
export interface OrderSummary { count: number; itemsTotal: number; collected: number; debtAfter: number; countByStatus: Record<OrderStatus, number> }
export interface CustomerSummary { count: number; totalDebt: number; customersInDebt: number }

export interface Order {
  id: number; code: string | null; status: OrderStatus
  customerId: number | null; customerName: string | null; customerAddress: string | null; customerPhone: string | null; staffName: string | null
  itemsTotal: number; shippingFee: number; previousDebt: number; totalPayable: number; paidAmount: number; debtAfter: number
  note: string | null; createdAt: string; checkedOutAt: string | null; items: OrderItem[]
}
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

