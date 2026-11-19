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
 */
@RestController
@RequestMapping("/api/admin/legacy-import")
public class LegacyImportController {
    private final LegacyImportService service;

    public LegacyImportController(LegacyImportService service) { this.service = service; }

    @GetMapping
    public LegacyImportService.Status status() { return service.status(); }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public LegacyImportService.Result run(@RequestBody byte[] body, @RequestParam(defaultValue = "true") boolean dryRun,
                                          Authentication auth) {
        return service.run(body, dryRun, auth.getName());
    }
}
