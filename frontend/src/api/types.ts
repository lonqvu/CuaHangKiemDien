export interface Product { id: number; name: string; categoryId: number | null; categoryName: string | null; unit: string | null; price: number; minStock: number; onHand: number | null }
export interface Category { id: number; name: string; productCount: number }
export interface Customer { id: number; name: string; address: string | null; phone: string | null; debt: number }
/** Mot dong so cong no, da dien giai san o backend (giong dong tren phieu thu / sao ke). */
export interface LedgerRow {
  id: number; type: 'ORDER' | 'PAYMENT' | 'ADJUST' | 'MANUAL' | 'VOID'; description: string
  increase: number | null; decrease: number | null; balanceAfter: number; createdAt: string; createdBy: string | null
  receiptNo: string | null; method: string | null; voided: boolean; canVoid: boolean
  orderId: number | null; orderCode: string | null
}
export interface PaymentResult { customer: Customer; ledgerId: number; receiptNo: string }
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

// ================= KHO HANG =================
export interface ProductUnit { unit: string; ratio: number }
export interface StockRow { productId: number; name: string; categoryName: string | null; unit: string | null; onHand: number; avgCost: number; value: number; minStock: number; price: number }
export interface StockSummary { count: number; totalValue: number; lowCount: number; negativeCount: number }
export type MovementType = 'RECEIPT' | 'RECEIPT_CANCEL' | 'SALE' | 'SALE_CANCEL' | 'COUNT' | 'ADJUST'
export interface StockMovement {
  id: number; productId: number; type: MovementType; qty: number; unitCost: number; value: number; balanceAfter: number; avgCostAfter: number
  refType: 'ORDER' | 'RECEIPT' | 'COUNT' | 'ADJUST'; refId: number | null; refCode: string | null; note: string | null; createdBy: string | null; createdAt: string
}
export type ReceiptStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export interface ReceiptLine { id: number; productId: number; productName: string; unit: string | null; inputUnit: string | null; inputQty: number; inputPrice: number; ratio: number; qty: number; unitCost: number; lineTotal: number }
export interface Receipt {
  id: number; code: string | null; status: ReceiptStatus; supplierId: number | null; supplierName: string | null; note: string | null
  total: number; paidAmount: number; createdBy: string | null; createdAt: string; postedAt: string | null; cancelledAt: string | null; lines: ReceiptLine[]
}
export interface ReceiptRow { id: number; code: string | null; status: ReceiptStatus; supplierId: number | null; supplierName: string | null; total: number; paidAmount: number; lineCount: number; createdBy: string | null; createdAt: string; postedAt: string | null }
export interface ReceiptSummary { count: number; total: number; paid: number; countByStatus: Record<ReceiptStatus, number> }
export interface Supplier { id: number; name: string; phone: string | null; address: string | null; debt: number }
export interface SupplierSummary { count: number; totalDebt: number; suppliersInDebt: number }
export interface SupplierLedgerRow { id: number; type: 'RECEIPT' | 'PAYMENT' | 'CANCEL' | 'MANUAL'; amount: number; balanceAfter: number; receiptId: number | null; note: string | null; createdAt: string }
export interface StockCountLine { id: number; productId: number; productName: string; unit: string | null; systemQty: number | null; countedQty: number | null; diff: number | null; unitCost: number | null }
export interface StockCount { id: number; code: string | null; status: 'DRAFT' | 'POSTED'; note: string | null; createdBy: string | null; createdAt: string; postedAt: string | null; lines: StockCountLine[] }
export interface CountRow { id: number; code: string | null; status: 'DRAFT' | 'POSTED'; note: string | null; lineCount: number; countedLines: number; createdBy: string | null; createdAt: string; postedAt: string | null }
export interface Profit { revenue: number; revenueWithCost: number; cost: number; grossProfit: number; lines: number; linesWithCost: number }
