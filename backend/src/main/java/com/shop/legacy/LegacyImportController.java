package com.shop.legacy;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Nhap du lieu tu phan mem cu (chi quan tri: SecurityConfig anyRequest -> ADMIN).
 *  GET  /api/admin/legacy-import          : nhap duoc khong (database con trong?), lan nhap truoc
 *  POST /api/admin/legacy-import?dryRun=  : than request = file du-lieu-cu.json (hoac .json.gz)
 *        dryRun=true : nap + doi chieu roi HUY (xem bao cao truoc)
 *        dryRun=false: nap + doi chieu, KHOP HET moi luu; lech -> huy het
 *  POST /api/admin/legacy-import/reset    : xoa du lieu thu (chua nhap du lieu cu lan nao), go "XOA DU LIEU THU"
 */
@RestController
@RequestMapping("/api/admin/legacy-import")
public class LegacyImportController {
    private final LegacyImportService service;

    public LegacyImportController(LegacyImportService service) { this.service = service; }

    @GetMapping
    public LegacyImportService.Status status() { return service.status(); }

    public record ResetRequest(String confirm) {}

    /** Xoa du lieu thu (chi khi chua nhap du lieu cu lan nao); phai go dung "XOA DU LIEU THU". */
    @PostMapping("/reset")
    public LegacyImportService.ResetResult reset(@RequestBody ResetRequest r, Authentication auth) {
        return service.resetTestData(r.confirm(), auth.getName());
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public LegacyImportService.Result run(@RequestBody byte[] body, @RequestParam(defaultValue = "true") boolean dryRun,
                                          Authentication auth) {
        return service.run(body, dryRun, auth.getName());
    }
}
