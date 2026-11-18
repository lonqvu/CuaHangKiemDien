package com.shop.auth;

import com.shop.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** Chan do mat khau: 5 lan sai / IP + ten, 20 lan / IP; het 15 phut thi mo; dang nhap dung xoa dem; IP khac khong bi anh huong. */
class LoginThrottleTest {
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-18T08:00:00Z");
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void blocksAfterFiveFailures_perIpAndUser_thenReleasesAfterWindow() {
        MovableClock clock = new MovableClock();
        LoginThrottle t = new LoginThrottle(clock);
        for (int i = 0; i < 5; i++) { t.check("1.1.1.1", "admin"); t.failed("1.1.1.1", "admin"); }
        ApiException e = assertThrows(ApiException.class, () -> t.check("1.1.1.1", "Admin "));
        assertEquals(429, e.getStatus().value());
        assertTrue(e.getMessage().contains("15 phút"), e.getMessage());
        // ke xau o IP khac khong khoa duoc chu tai khoan
        assertDoesNotThrow(() -> t.check("2.2.2.2", "admin"));
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> t.check("1.1.1.1", "admin"));
    }

    @Test
    void successClearsUserCount_andIpLimitCoversManyUsernames() {
        LoginThrottle t = new LoginThrottle(new MovableClock());
        for (int i = 0; i < 4; i++) t.failed("1.1.1.1", "nv");
        t.succeeded("1.1.1.1", "nv");
        for (int i = 0; i < 4; i++) t.failed("1.1.1.1", "nv");
        assertDoesNotThrow(() -> t.check("1.1.1.1", "nv"), "dung xong thi dem lai tu dau");
        for (int i = 0; i < 20; i++) t.failed("3.3.3.3", "user" + i);
        assertThrows(ApiException.class, () -> t.check("3.3.3.3", "khac"), "do nhieu ten tu 1 IP");
    }
}
