/**
 * Thong tin in tren hoa don / bien lai. Lay nguyen tu Bill.jrxml va ThanhToanNoReport.jrxml cua app cu.
 * Sua o day neu doi dia chi, so dien thoai, tai khoan ngan hang.
 */
export const SHOP = {
  invoice: {
    name: 'CÔNG TY TNHH SX VÀ TM KIỂM DIÊN',
    lines: [
      'CHUYÊN CUNG CẤP CÁC LOẠI SẮT HÌNH, HỘP U,V,C',
      'NHÔM, KẼM, TÔN LỢP, TRẦN NHỰA, CỬA XẾP, CỬA CUỐN',
      'Địa chỉ: Xóm 1 - Hồng Thắng - Yên Mạc - Ninh Bình',
      'ĐT: 0356.717.693 - 039.221.3986',
    ],
    notes: [
      '- Hóa dơn bán hàng có giá trị như giấy nợ đối với khách hàng thanh toán chậm.',
      '- Kiểm tra đối chiếu khi giao nhận hàng.',
      '- Tôn đã cắt không được trả lại.',
      '- Mọi thắc mắc xin quý khách vui lòng liên hệ ngay, quá hai ngày sau khi nhận hàng chúng tôi không chịu trách nhiệm',
    ],
    /** Logo (app cu: src/Image/download-2-photoroom_agfGo5ka.jpg) nam o /public/invoice/logo.jpg */
    /** Anh QR chuyen khoan (app cu: src/Image/1.jpg). Doi tai khoan thi thay file /public/invoice/qr.jpg */
    qrImage: '/invoice/qr.jpg' as string | null,
  },
  debtReceipt: {
    name: 'NHÀ MÁY CÁN TÔN KIỂM DIÊN',
    lines: [
      'CHUYÊN CUNG CẤP CÁC LOẠI SẮT HÌNH, HỘP U,V,C',
      'NHÔM, KẼM, TÔN LỢP, TRẦN NHỰA, CỬA XẾP, CỬA CUỐN',
      'Địa chỉ: Xóm 1 - Hồng Thắng - Yên Mạc - Yên Mô - Ninh Bình',
      'ĐT: 0356.717.693 - 039.221.3986',
      'STK: Vũ Xuân Kiểm: 108878791212 NH Viettin Bank Ninh Bình',
      'STK: Nguyễn Thị Diên: 3307205106828 NH Agribank',
    ],
    footnote: 'Chú ý: Tiền nợ hiện tại có thể thay đổi trong các giao dịch mới',
  },
}
