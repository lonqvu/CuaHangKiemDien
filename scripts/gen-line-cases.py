#!/usr/bin/env python3
"""
Sinh bo case tinh tien dong hang dung chung cho test backend (LineCalculator) va frontend (previewLine).
Ket qua mong doi tinh DOC LAP bang Decimal cua Python (so thap phan chinh xac), theo nghiep vu app cu:
  - co so tam ma he so = 0 -> he so 1.08
  - Tong SL tho = SL neu so tam = 0 va he so = 0, nguoc lai = SL x So tam x He so
  - Tong SL luu = Tong SL tho lam tron 3 so le, HALF_EVEN
  - Thanh tien = Don gia x Tong SL tho, lam tron ve dong, HALF_EVEN
  - Tong SL luu = 0 -> backend tu choi dong hang (reject)
Chay lai: python3 scripts/gen-line-cases.py > backend/src/test/resources/line-cases.json
"""
import json, random
from decimal import Decimal as D, ROUND_HALF_EVEN

def expected(q, s, f, p):
    q, f, p = D(q), D(f), D(p)
    if s > 0 and f == 0:
        f = D("1.08")
    raw = q if (s == 0 and f == 0) else q * s * f
    stored = raw.quantize(D("0.001"), ROUND_HALF_EVEN)
    total = (p * raw).quantize(D("1"), ROUND_HALF_EVEN)
    return f, stored, total

cases = []
def add(name, q, s, f, p):
    f2, stored, total = expected(q, s, f, p)
    cases.append({"name": name, "quantity": q, "sheets": s, "factor": f, "unitPrice": p,
                  "expectFactor": str(f2.normalize()), "expectTotalQuantity": str(stored),
                  "expectLineTotal": str(total), "reject": stored == 0})

# --- case tay: nghiep vu va cac diem lam tron nguy hiem ---
add("ban le, khong tam", "6", 0, "0", "285000")
add("ton 4,2m x 12 tam, he so mac dinh", "4.2", 12, "0", "95000")
add("ton 4,2m x 12 tam, he so 1,08 go tay", "4.2", 12, "1.08", "95000")
add("sua dong: he so 1", "4.5", 2, "1", "98000")
add("so le 3 chu so", "2.375", 0, "0", "120000")
add("lam tron .5 ve chan (12.5 -> 12)", "0.5", 0, "0", "25")
add("lam tron .5 ve chan (13.5 -> 14)", "0.5", 0, "0", "27")
add("333 x 1,08 = 359,64 -> 360", "1", 1, "1.08", "333")
add("Tong SL 3 so le nhung tien tinh tren so tho", "1.111", 3, "1.08", "99999")
add("Tong SL 0,0005 -> 0,000 (half even) -> tu choi", "0.0005", 0, "0", "100000")
add("Tong SL 0,0015 -> 0,002", "0.0015", 0, "0", "100000")
add("so tam 0 nhung he so 1,08 -> Tong SL 0 -> tu choi", "5", 0, "1.08", "91000")
add("don gia 0 (hang tang)", "3", 0, "0", "0")
add("so lon: 1000 tam", "6", 1000, "1.08", "485000")
add("he so 1,05", "3.7", 8, "1.05", "102500")
add("so float kho: 4,35 x 12 x 1,08", "4.35", 12, "1.08", "95000")
add("so float kho: 0,1 x 3", "0.1", 3, "1", "70000")
add("so float kho: 1,15 x 1 x 1,08", "1.15", 1, "1.08", "150000")
add("so float kho: 2,675 m", "2.675", 0, "0", "100000")

# --- case ngau nhien (co dinh seed) ---
rnd = random.Random(20261116)
factors = ["0", "0", "1", "1.08", "1.05", "1.1", "1.02"]
for i in range(400):
    q = str(D(rnd.randint(1, 20000)) / D(1000) if rnd.random() < .7 else D(rnd.randint(1, 30)))
    s = rnd.choice([0, 0, 0, rnd.randint(1, 40)])
    f = rnd.choice(factors) if s else rnd.choice(["0", "0", "0", "1"])
    p = str(rnd.choice([rnd.randint(1, 1000) * 500, rnd.randint(1000, 999999)]))
    add(f"ngau nhien {i + 1}", str(D(q).normalize()), s, f, p)

print(json.dumps(cases, ensure_ascii=False, indent=1))
