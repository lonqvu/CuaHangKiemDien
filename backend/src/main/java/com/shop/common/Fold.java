package com.shop.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Tim khong dau ngay trong SQL (cho danh sach dai, phan trang o DB: hoa don, phieu nhap, kiem ke, ton kho).
 * Cot: lower() roi translate() bo dau tieng Viet - ca PostgreSQL va H2 (test) deu co TRANSLATE.
 * Cau tim: {@link #plain(String)} bo dau giong het -> "tuan" thay "Anh Tuấn", "ton lanh" thay "Tôn lạnh".
 *
 * Dung trong @Query (hang so luc bien dich): {@code Fold.L + "o.customerName" + Fold.R} = "<cot da bo dau> like :q".
 */
public final class Fold {
    /**
     * Chu co dau (dang dung san NFC) va chu khong dau tuong ung, cung thu tu. Co ca chu HOA: Postgres voi collation "C"
     * chi lower() chu ASCII ("Ấ" van la "Ấ") -> van bo dau dung du cau hinh DB nao.
     */
    public static final String FROM =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
            + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ";
    public static final String TO =
            "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd"
            + "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd";

    // cast ... as String: Hibernate 6 khong biet kieu tra ve cua function('translate') -> ep ve chuoi de dung voi LIKE
    public static final String L = "cast(function('translate', lower(";
    public static final String R = "), '" + FROM + "', '" + TO + "') as String) like :q";

    private Fold() {}

    /** Bo dau + chu thuong, KHONG doi so thap phan (khac TextSearch.fold) - de khop voi translate() trong SQL. */
    public static String plain(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }
}
