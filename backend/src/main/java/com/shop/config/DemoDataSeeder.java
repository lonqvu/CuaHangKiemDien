package com.shop.config;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtService;
import com.shop.order.Order;
import com.shop.order.OrderRepository;
import com.shop.order.OrderService;
import com.shop.order.OrderService.CheckoutRequest;
import com.shop.order.OrderService.ItemRequest;
import com.shop.product.Category;
import com.shop.product.CategoryRepository;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.stock.ReceiptService;
import com.shop.stock.StockBalanceRepository;
import com.shop.stock.StockService;
import com.shop.supplier.Supplier;
import com.shop.supplier.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Du lieu mau de chay thu / demo. CHI chay khi bat SEED_DEMO=true va DB chua co san pham, hoa don.
 * Tao qua dung service that (OrderService, DebtService) nen cong no, so cai, tong tien khop nhu thao tac tren man hinh;
 * sau do lui ngay tao (created_at, checked_out_at) rai deu 14 thang de bao cao co so lieu so sanh.
 * Ket qua co dinh (Random seed 42): xoa DB tao lai se ra y het.
 */
@Component
@org.springframework.core.annotation.Order(2) // sau DataSeeder (tao admin truoc)
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int DAYS_BACK = 420;

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final AppUserRepository users;
    private final OrderService orderService;
    private final DebtService debtService;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final ReceiptService receipts;
    private final StockService stock;
    private final StockBalanceRepository balances;
    private final SupplierRepository suppliers;
    private final Random rnd = new Random(42);
    /** productId -> ten loai (de chon nha cung cap khi nhap hang). */
    private final java.util.Map<Long, String> categoryOf = new java.util.HashMap<>();
    private final java.util.Map<String, Supplier> supplierOf = new java.util.HashMap<>();

    public DemoDataSeeder(CategoryRepository categories, ProductRepository products, CustomerRepository customers,
                          OrderRepository orders, AppUserRepository users, OrderService orderService,
                          DebtService debtService, PasswordEncoder encoder, JdbcTemplate jdbc,
                          ReceiptService receipts, StockService stock, StockBalanceRepository balances, SupplierRepository suppliers) {
        this.categories = categories; this.products = products; this.customers = customers; this.orders = orders;
        this.users = users; this.orderService = orderService; this.debtService = debtService;
        this.encoder = encoder; this.jdbc = jdbc;
        this.receipts = receipts; this.stock = stock; this.balances = balances; this.suppliers = suppliers;
    }

    /** Ban theo tam (ton, nhom tam): SL = chieu dai 1 tam (m), nhap so tam, he so 1.08. */
    private record P(String name, String unit, long price, boolean bySheet) {}

    @Override
    public void run(String... args) {
        Long realOrders = jdbc.queryForObject("select count(*) from orders where status <> 'DRAFT'", Long.class);
        if (products.count() > 0 || (realOrders != null && realOrders > 0)) {
            log.info("SEED_DEMO: DB da co du lieu, bo qua.");
            return;
        }
        long t0 = System.currentTimeMillis();
        LocalDateTime start = LocalDate.now().minusDays(DAYS_BACK).atTime(7, 0);

        String staffName = seedStaff();
        List<Product> sheetProducts = new ArrayList<>();
        List<Product> unitProducts = new ArrayList<>();
        seedProducts(sheetProducts, unitProducts);
        List<Customer> custs = seedCustomers(start);
        List<Product> all = new ArrayList<>(sheetProducts);
        all.addAll(unitProducts);
        seedSuppliers();
        restock(all, start.minusDays(1), true); // nhap kho dau ky

        int orderCount = 0, payments = 0, cancelled = 0, receiptCount = 1;
        for (LocalDate day = start.toLocalDate(); !day.isAfter(LocalDate.now()); day = day.plusDays(1)) {
            // Thu Hai hang tuan: nhap bu cac mat hang sap het (nhu cua hang dat hang dinh ky)
            if (day.getDayOfWeek() == DayOfWeek.MONDAY && day.atTime(6, 30).isBefore(LocalDateTime.now()))
                receiptCount += restock(all, day.atTime(6, 30), false);
            int n = day.getDayOfWeek() == DayOfWeek.SUNDAY ? rnd.nextInt(2) : 1 + rnd.nextInt(4);
            List<LocalDateTime> times = new ArrayList<>();
            for (int i = 0; i < n; i++) times.add(day.atTime(7 + rnd.nextInt(11), rnd.nextInt(60)));
            times.sort(null);
            for (LocalDateTime at : times) {
                if (at.isAfter(LocalDateTime.now())) continue;
                Long id = createOrder(at, rnd.nextInt(10) < 7 ? custs.get(rnd.nextInt(custs.size())) : null,
                        rnd.nextBoolean() ? "Quản trị" : staffName, sheetProducts, unitProducts);
                orderCount++;
                if (rnd.nextInt(60) == 0) { // ~1.5% hoa don bi huy (ADMIN huy ngay trong ngay)
                    long[] mark = marks();
                    orderService.cancel(id);
                    backdate(mark, at.plusMinutes(30));
                    cancelled++;
                }
            }
            // Thinh thoang khach den tra no (giong man Khach hang & cong no)
            if (rnd.nextInt(4) == 0) {
                Customer c = customers.findById(custs.get(rnd.nextInt(custs.size())).getId()).orElseThrow();
                if (c.getDebt().signum() > 0) {
                    BigDecimal amt = roundThousand(c.getDebt().multiply(BigDecimal.valueOf(30 + rnd.nextInt(71)))
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)).min(c.getDebt());
                    LocalDateTime at = day.atTime(17, 30);
                    if (amt.signum() > 0 && at.isBefore(LocalDateTime.now())) {
                        long[] mark = marks();
                        debtService.pay(c.getId(), amt, "Khách trả nợ");
                        backdate(mark, at);
                        payments++;
                    }
                }
            }
        }
        log.warn("SEED_DEMO: da tao {} san pham, {} khach, {} hoa don ({} huy), {} lan tra no, {} phieu nhap trong {} ms",
                sheetProducts.size() + unitProducts.size(), customers.count(), orderCount, cancelled, payments, receiptCount,
                System.currentTimeMillis() - t0);
    }

    private String seedStaff() {
        if (users.findAll().stream().noneMatch(u -> u.getUsername().equals("nhanvien"))) {
            AppUser u = new AppUser();
            u.setUsername("nhanvien");
            u.setPasswordHash(encoder.encode("nhanvien123"));
            u.setFullName("Nguyễn Văn Bán");
            u.setRole("STAFF");
            users.save(u);
        }
        return "Nguyễn Văn Bán";
    }

    private void seedProducts(List<Product> sheet, List<Product> unit) {
        Object[][] data = {
            {"Tôn lợp", new P[]{
                new P("Tôn lạnh màu xanh ngọc 0.40mm", "m", 92_000, true),
                new P("Tôn lạnh màu đỏ đô 0.40mm", "m", 92_000, true),
                new P("Tôn lạnh màu xám 0.45mm", "m", 104_000, true),
                new P("Tôn kẽm 0.35mm", "m", 68_000, true),
                new P("Tôn cách nhiệt PU 3 lớp", "m", 175_000, true),
                new P("Tôn sóng ngói đỏ 0.45mm", "m", 118_000, true),
                new P("Tôn nhựa lấy sáng", "m", 85_000, true)}},
            {"Thép hộp", new P[]{
                new P("Thép hộp mạ kẽm 20x40x1.2", "cây", 145_000, false),
                new P("Thép hộp mạ kẽm 30x60x1.4", "cây", 265_000, false),
                new P("Thép hộp mạ kẽm 40x80x1.8", "cây", 455_000, false),
                new P("Thép hộp vuông 25x25x1.2", "cây", 118_000, false),
                new P("Thép hộp vuông 50x50x1.8", "cây", 330_000, false)}},
            {"Sắt thép xây dựng", new P[]{
                new P("Thép cuộn phi 6", "kg", 16_500, false),
                new P("Thép cuộn phi 8", "kg", 16_500, false),
                new P("Thép thanh vằn D10", "cây", 98_000, false),
                new P("Thép thanh vằn D12", "cây", 142_000, false),
                new P("Thép thanh vằn D16", "cây", 252_000, false),
                new P("Thép V lỗ 40x40", "cây", 185_000, false)}},
            {"Nhôm", new P[]{
                new P("Nhôm tấm phẳng 1.0mm", "m", 210_000, true),
                new P("Nhôm hộp 25x50", "cây", 165_000, false),
                new P("Nhôm Xingfa hệ 55", "cây", 640_000, false),
                new P("Nẹp nhôm chữ U", "cây", 45_000, false)}},
            {"Phụ kiện", new P[]{
                new P("Vít bắn tôn 5cm", "hộp", 85_000, false),
                new P("Vít bắn tôn 7cm", "hộp", 98_000, false),
                new P("Keo silicon Apollo", "chai", 42_000, false),
                new P("Úp nóc tôn", "m", 55_000, false),
                new P("Máng xối inox", "m", 120_000, false),
                new P("Que hàn Kim Tín 2.5", "kg", 52_000, false),
                new P("Sơn chống rỉ", "lon", 135_000, false)}},
        };
        for (Object[] row : data) {
            Category cat = new Category();
            cat.setName((String) row[0]);
            categories.save(cat);
            for (P p : (P[]) row[1]) {
                Product pr = new Product();
                pr.setCategory(cat);
                pr.setName(p.name());
                pr.setUnit(p.unit());
                pr.setPrice(BigDecimal.valueOf(p.price()));
                products.save(pr);
                stock.ensureBalance(pr.getId());
                categoryOf.put(pr.getId(), (String) row[0]);
                (p.bySheet() ? sheet : unit).add(pr);
            }
        }
        // 1 san pham ngung ban (xoa mem) de thu bo loc
        Product off = new Product();
        off.setCategory(categories.findAll().get(0));
        off.setName("Tôn kẽm 0.30mm (ngừng bán)");
        off.setUnit("m");
        off.setPrice(BigDecimal.valueOf(55_000));
        off.setActive(false);
        products.save(off);
    }

    private List<Customer> seedCustomers(LocalDateTime start) {
        String[][] data = {
            {"Anh Hùng thợ hàn", "Thôn Đông, xã Kiềm Điền", "0912345678", "3500000"},
            {"Chị Lan", "Xóm 3, xã Kiềm Điền", "0987654321", "0"},
            {"Cơ khí Minh Phát", "Km 5 quốc lộ 1A", "0903111222", "12800000"},
            {"Anh Tuấn xây dựng", "Thị trấn Hưng Hà", "0978222333", "0"},
            {"Chú Bảy", "Thôn Tây, xã Kiềm Điền", "0356444555", "850000"},
            {"Nhà thầu Thành Công", "Đường Lê Lợi, TP Thái Bình", "0915666777", "25000000"},
            {"Anh Dũng làm mái tôn", "Xã Tân Lập", "0868777888", "0"},
            {"Cô Hoa tạp hóa", "Chợ Kiềm Điền", "0393888999", "0"},
            {"Xưởng nhôm kính Hải Nam", "Cụm công nghiệp phía Bắc", "0904555666", "6200000"},
            {"Anh Sơn", "Thôn Nam, xã Kiềm Điền", "0377123123", "0"},
            {"Trường tiểu học Kiềm Điền", "Trung tâm xã", "0225388001", "0"},
            {"Anh Quang thợ cơ khí", "Xã Hòa Bình", "0945321321", "1500000"},
            {"Chị Mai", "Xóm 7, xã Kiềm Điền", "0962456456", "0"},
            {"Công ty TNHH Phú Gia", "KCN Phúc Khánh", "0225377999", "40000000"},
            {"Bác Thịnh", "Thôn Bắc, xã Kiềm Điền", "", "0"},
            {"Anh Long", "Xã Đông Phong", "0833987987", "0"},
        };
        List<Customer> list = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            String[] d = data[i];
            Customer c = new Customer();
            c.setName(d[0]);
            c.setAddress(d[1]);
            c.setPhone(d[2].isEmpty() ? null : d[2]);
            customers.save(c);
            // 12 khach co tu dau, 4 khach moi them dan trong 2 thang gan day (de "khach moi thang nay" co so lieu)
            LocalDateTime created = i < 12 ? start.minusDays(30) : LocalDateTime.now().minusDays(rnd.nextInt(55) + 1);
            jdbc.update("update customer set created_at = ? where id = ?", Timestamp.valueOf(created), c.getId());
            BigDecimal opening = new BigDecimal(d[3]);
            if (opening.signum() > 0) {
                long[] mark = marks();
                debtService.setDebt(c.getId(), opening, "Nợ cũ chuyển sang");
                backdate(mark, created);
            }
            if (i < 12) list.add(c);
            else newCustomers.add(c);
        }
        return list;
    }

    private final List<Customer> newCustomers = new ArrayList<>();

    private Long createOrder(LocalDateTime at, Customer customer, String staff,
                             List<Product> sheetProducts, List<Product> unitProducts) {
        // Khach moi chi mua sau ngay duoc tao
        if (customer != null && !newCustomers.isEmpty() && rnd.nextInt(5) == 0) {
            Customer nc = newCustomers.get(rnd.nextInt(newCustomers.size()));
            LocalDateTime created = jdbc.queryForObject("select created_at from customer where id = ?",
                    Timestamp.class, nc.getId()).toLocalDateTime();
            if (at.isAfter(created)) customer = nc;
        }
        Order o = orderService.createDraft(staff);
        int lines = 1 + rnd.nextInt(5);
        for (int i = 0; i < lines; i++) {
            if (!sheetProducts.isEmpty() && rnd.nextInt(10) < 5) {
                Product p = sheetProducts.get(rnd.nextInt(sheetProducts.size()));
                BigDecimal length = BigDecimal.valueOf(15 + rnd.nextInt(46), 1); // 1.5m .. 6.0m
                orderService.addItem(o.getId(), new ItemRequest(p.getId(), length, 2 + rnd.nextInt(30), null, null));
            } else {
                Product p = unitProducts.get(rnd.nextInt(unitProducts.size()));
                int qty = "kg".equals(p.getUnit()) ? 10 + rnd.nextInt(200) : 1 + rnd.nextInt(20);
                // Thinh thoang ban gia khac gia niem yet (giong sua don gia o man Ban hang)
                BigDecimal price = rnd.nextInt(8) == 0 ? roundThousand(p.getPrice().multiply(new BigDecimal("0.95"))) : null;
                orderService.addItem(o.getId(), new ItemRequest(p.getId(), BigDecimal.valueOf(qty), 0, null, price));
            }
        }
        Order draft = orderService.get(o.getId());
        BigDecimal ship = rnd.nextInt(4) == 0 ? BigDecimal.valueOf((1 + rnd.nextInt(5)) * 50_000L) : BigDecimal.ZERO;
        BigDecimal charge = draft.getItemsTotal().add(ship);

        long[] mark = marks();
        CheckoutRequest req;
        if (customer == null) {
            // Khach le: tra du (lam tron len nghin, phan thua tra lai khach)
            String[] walkIns = {"Khách lẻ", "Anh Nam", "Chị Thu", "Anh Bình", "Chú Tư", "Anh Khoa"};
            req = new CheckoutRequest(null, walkIns[rnd.nextInt(walkIns.length)], null, null, ship,
                    roundUpThousand(charge), null);
        } else {
            BigDecimal debt = customers.findById(customer.getId()).orElseThrow().getDebt();
            BigDecimal payable = charge.add(debt);
            int r = rnd.nextInt(10);
            BigDecimal paid;
            if (r < 4) paid = roundUpThousand(charge);                       // tra du don nay
            else if (r < 6) paid = payable;                                  // tra het ca no cu
            else if (r < 9) paid = roundThousand(charge.multiply(BigDecimal.valueOf(20 + rnd.nextInt(60)))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)); // tra mot phan
            else paid = BigDecimal.ZERO;                                     // ghi no het
            req = new CheckoutRequest(customer.getId(), null, null, null, ship, paid,
                    rnd.nextInt(6) == 0 ? "Giao hàng tận nơi" : null);
        }
        orderService.checkout(o.getId(), req);
        jdbc.update("update orders set created_at = ?, checked_out_at = ? where id = ?",
                Timestamp.valueOf(at.minusMinutes(5 + rnd.nextInt(20))), Timestamp.valueOf(at), o.getId());
        backdate(mark, at);
        return o.getId();
    }

    private static final String[] BACKDATED = {"debt_ledger", "stock_movement", "supplier_ledger"};

    /** id lon nhat hien tai cua cac so (cong no khach, so kho, cong no NCC) - de lui ngay cac dong sinh ra sau do. */
    private long[] marks() {
        long[] m = new long[BACKDATED.length];
        for (int i = 0; i < m.length; i++) {
            Long v = jdbc.queryForObject("select coalesce(max(id), 0) from " + BACKDATED[i], Long.class);
            m[i] = v == null ? 0 : v;
        }
        return m;
    }

    private void backdate(long[] mark, LocalDateTime at) {
        for (int i = 0; i < mark.length; i++)
            jdbc.update("update " + BACKDATED[i] + " set created_at = ? where id > ?", Timestamp.valueOf(at), mark[i]);
    }

    private void seedSuppliers() {
        String[][] data = {
            {"Tôn lợp", "Đại lý tôn Phương Nam", "0912000111", "KCN Phúc Khánh"},
            {"Thép hộp", "Kho thép Minh Long", "0912000222", "Quốc lộ 10"},
            {"Sắt thép xây dựng", "Kho thép Minh Long", "0912000222", "Quốc lộ 10"},
            {"Nhôm", "Nhôm kính Hải Đăng", "0912000333", "TP Thái Bình"},
            {"Phụ kiện", "Phụ kiện Tân Tiến", "0912000444", "Chợ Bo"},
        };
        java.util.Map<String, Supplier> byName = new java.util.HashMap<>();
        for (String[] d : data) {
            Supplier s = byName.computeIfAbsent(d[1], n -> {
                Supplier x = new Supplier();
                x.setName(n);
                x.setPhone(d[2]);
                x.setAddress(d[3]);
                return suppliers.save(x);
            });
            supplierOf.put(d[0], s);
        }
    }

    /**
     * Nhap hang qua ReceiptService that (phieu -> ghi so): dau ky nhap day kho; cac tuan sau chi nhap bu mat hang duoi 30% muc.
     * Gia nhap ~ 75-82% gia ban; tra truoc 50-100%, phan con lai thanh no NCC. Tra ve so phieu da tao.
     */
    private int restock(List<Product> all, LocalDateTime at, boolean opening) {
        java.util.Map<Supplier, List<ReceiptService.LineRequest>> bySupplier = new java.util.LinkedHashMap<>();
        var bal = stock.balancesOf(all.stream().map(Product::getId).toList());
        for (Product p : all) {
            BigDecimal target = BigDecimal.valueOf("m".equals(p.getUnit()) ? 2500 : "kg".equals(p.getUnit()) ? 3000 : 300);
            BigDecimal onHand = bal.get(p.getId()) == null ? BigDecimal.ZERO : bal.get(p.getId()).getOnHand();
            if (!opening && onHand.compareTo(target.multiply(new BigDecimal("0.3"))) >= 0) continue;
            BigDecimal qty = opening ? target : target.subtract(onHand).setScale(0, RoundingMode.UP);
            BigDecimal cost = roundThousand(p.getPrice().multiply(BigDecimal.valueOf(75 + rnd.nextInt(8))).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN));
            bySupplier.computeIfAbsent(supplierOf.get(categoryOf.get(p.getId())), k -> new ArrayList<>())
                    .add(new ReceiptService.LineRequest(p.getId(), null, qty, cost, null));
        }
        int n = 0;
        for (var e : bySupplier.entrySet()) {
            BigDecimal total = e.getValue().stream().map(l -> l.inputQty().multiply(l.inputPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = roundThousand(total.multiply(BigDecimal.valueOf(50 + rnd.nextInt(51))).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN));
            long[] mark = marks();
            var r = receipts.create(new ReceiptService.ReceiptRequest(e.getKey().getId(), opening ? "Nhập kho đầu kỳ" : "Nhập bổ sung", paid, e.getValue()), "Quản trị");
            receipts.post(r.getId(), "Quản trị");
            jdbc.update("update goods_receipt set created_at = ?, posted_at = ? where id = ?", Timestamp.valueOf(at.minusMinutes(20)), Timestamp.valueOf(at), r.getId());
            backdate(mark, at);
            n++;
        }
        return n;
    }

    private static BigDecimal roundThousand(BigDecimal v) {
        return v.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(1000));
    }

    private static BigDecimal roundUpThousand(BigDecimal v) {
        return v.divide(BigDecimal.valueOf(1000), 0, RoundingMode.UP).multiply(BigDecimal.valueOf(1000));
    }
}
