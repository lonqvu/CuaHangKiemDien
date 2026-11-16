# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** Nhom Sat Kiem Dien
**Generated:** 2026-11-15 19:31:04
**Category:** Smart Home/IoT Dashboard
**Design Dials:** Variance 3/10 (Centered / Minimal) | Motion 2/10 (Subtle) | Density 8/10 (Dense / Dashboard)

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#1E293B` | `--color-primary` |
| On Primary | `#FFFFFF` | `--color-on-primary` |
| Secondary | `#334155` | `--color-secondary` |
| On Secondary | `#FFFFFF` | `--color-on-secondary` |
| Accent/CTA | `#22C55E` | `--color-accent` |
| On Accent/CTA | `#0F172A` | `--color-on-accent` |
| Background | `#0F172A` | `--color-background` |
| Foreground | `#F8FAFC` | `--color-foreground` |
| Card | `#1B2336` | `--color-card` |
| Card Foreground | `#F8FAFC` | `--color-card-foreground` |
| Muted | `#272F42` | `--color-muted` |
| Muted Foreground | `#94A3B8` | `--color-muted-foreground` |
| Border | `#475569` | `--color-border` |
| Destructive | `#EF4444` | `--color-destructive` |
| On Destructive | `#000000` | `--color-on-destructive` |
| Ring | `#FFFFFF` | `--color-ring` |

**Color Notes:** Dark tech + status green

### Typography

- **Heading Font:** Fira Code
- **Body Font:** Fira Sans
- **Mood:** dashboard, data, analytics, code, technical, precise
- **Google Fonts:** [Fira Code + Fira Sans](https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500;600;700&family=Fira+Sans:wght@300;400;500;600;700&display=swap)

**CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500;600;700&family=Fira+Sans:wght@300;400;500;600;700&display=swap');
```

### Spacing Variables

*Density: 8/10 — Dense / Dashboard*

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `2px` / `0.125rem` | Tight gaps |
| `--space-sm` | `4px` / `0.25rem` | Icon gaps, inline spacing |
| `--space-md` | `8px` / `0.5rem` | Standard padding |
| `--space-lg` | `12px` / `0.75rem` | Section padding |
| `--space-xl` | `16px` / `1rem` | Large gaps |
| `--space-2xl` | `24px` / `1.5rem` | Section margins |
| `--space-3xl` | `32px` / `2rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #22C55E;
  color: #0F172A;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: #F8FAFC;
  border: 2px solid #1E293B;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: #0F172A;
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  transition: all 200ms ease;
  cursor: pointer;
}

.card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
```

### Inputs

```css
.input {
  padding: 12px 16px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #1E293B;
  outline: none;
  box-shadow: 0 0 0 3px #1E293B20;
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Minimalism & Swiss Style

**Keywords:** Clean, simple, spacious, functional, white space, high contrast, geometric, sans-serif, grid-based, essential

**Best For:** Enterprise apps, dashboards, documentation sites, SaaS platforms, professional tools

**Key Effects:** Subtle hover (200-250ms), smooth transitions, sharp shadows if any, clear type hierarchy, fast loading

### Page Pattern

**Pattern Name:** Real-Time / Operations Landing

- **Conversion Strategy:** Offer a demo or sandbox and show trust signals. Label telemetry as live only when backed by a current source, with update time and stale state. Provide pause/hide or update-frequency controls for tickers and previews, stop offscreen/hidden work, support keyboard controls, and render a static final snapshot under reduced motion.
- **CTA Placement:** Primary CTA in nav + After metrics
- **Section Order:** Hero (product + live preview or status) > Key metrics/indicators > How it works > CTA (Start trial / Contact)

---

## Motion

**Scroll Reveal** (Subtle) — Trigger: scroll (viewport enter) | Duration: 300-400ms | Easing: `power1.out`

```js
gsap.from(el, { opacity: 0, y: 12, duration: 0.35, ease: 'power1.out', scrollTrigger: { trigger: el, start: 'top 90%', toggleActions: 'play none none reverse' } });
```

**Framework notes:** Requires the ScrollTrigger plugin registered once via gsap.registerPlugin(ScrollTrigger); Use matchMedia('(prefers-reduced-motion: reduce)') to skip non-essential motion and render the final state immediately

- ✅ Keep the y offset small (8-16px) so it reads as a fade, not a slide
- ❌ Don't reveal below-the-fold content needed for SEO/crawlers as invisible-by-default without a no-JS fallback
- ⚡ toggleActions 'play none none reverse' avoids re-triggering on every scroll direction change

---

## Anti-Patterns (Do NOT Use)

- ❌ Slow updates
- ❌ No automation

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

---

## Quyết định riêng cho dự án (ghi đè phần trên)

Sinh bằng `ui-ux-pro-max --design-system "retail POS admin dashboard" --density 8 --variance 3 --motion 2`.
Giữ: phong cách Minimalism & Swiss, font Fira Sans (chữ) + Fira Code (số tiền, mã hóa đơn), mật độ dày, chuyển động tối thiểu, màu trạng thái xanh/hổ phách/đỏ.

Đổi so với output gốc:
- **Nền sáng thay vì nền tối.** Máy đặt ở quầy, dùng cả ngày dưới đèn và ánh sáng cửa hàng, hay in hóa đơn → nền sáng dễ đọc hơn. Thanh menu trái giữ màu thép tối `#16243A`.
- **Muted text `#475569`** thay `#94A3B8` (trên nền trắng `#94A3B8` chỉ ~2.6:1, không đạt 4.5:1).
- **Màu chính `#1E3A5F` (xanh thép)** thay `#1E293B`, để link/nút chính phân biệt được với chữ thường.
- **Xanh tiền `#15803D`** thay `#22C55E` (đủ tương phản khi làm chữ trên nền trắng).
- **Không dùng GSAP / scroll reveal**: app nội bộ, thao tác nhanh, không cần hiệu ứng.
- Token nằm ở `frontend/src/styles/theme.css` (map sang biến CSS của Element Plus).

Quy tắc dùng chung:
- Số tiền/số lượng: `class="num"` (Fira Code, tabular-nums), cột số căn phải.
- Không hiển thị số lẻ dạng `1.000` trong ô nhập (người Việt đọc thành một nghìn).
- Công nợ luôn màu hổ phách **kèm chữ** ("Còn nợ", "nợ 12.450.000"), không chỉ dựa vào màu.
- Mỗi màn chỉ 1 điểm nhấn: màn Bán hàng là "phiếu thanh toán" bên phải; màn Tổng quan là ô "Khách đang nợ".
- Ô nhập tiền: dưới ô hiện dòng đọc lại có dấu chấm (`class="echo"`, vd "= 12.450.000"), để không gõ nhầm số chữ số.
- Nút bị khóa phải nói lý do ngay cạnh nút (vd "Chọn khách hàng hoặc nhập tên khách lẻ để chốt"), không chỉ làm mờ.
- Vùng bấm tối thiểu 28px (nút `small`, nút link); không dùng nút `small` cho thao tác chính.
- Bảng chính phải vừa khung ở 1366px không cần cuộn ngang: cột quan trọng (khách, thành tiền) đứng trước, cột phụ đưa vào ngăn chi tiết.
- Lưới chứa bảng dùng `minmax(0, 1fr)` (không dùng `1fr` trần) để bảng cuộn bên trong, không đẩy tràn trang.
- **Không dùng `el-input-number`** (nó hiểu "4,2" thành 42). Mọi ô nhập số dùng `components/NumberField.vue`: `kind="decimal"` cho số lượng/hệ số (dấu phẩy hay chấm đều là dấu thập phân), `kind="integer"` cho tiền/số tấm (bỏ mọi dấu ngăn cách).
- Ô tìm sản phẩm/khách: tìm không dấu, theo từng từ, không cần đúng thứ tự (backend `TextSearch`); Enter chọn kết quả đầu rồi nhảy sang ô tiếp theo; tô đậm phần khớp; khi không thấy thì nói rõ và đưa lối tạo mới.
