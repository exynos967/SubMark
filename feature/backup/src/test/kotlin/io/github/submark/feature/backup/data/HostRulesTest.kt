package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HostRulesTest {

    @Test fun `https public host is ok`() {
        assertThat(HostRules.check("https://dav.example.com/dav", allowHttpLocal = false)).isEqualTo(HostRules.Verdict.Ok)
    }

    @Test fun `http public host rejected without flag`() {
        assertThat(HostRules.check("http://dav.example.com", allowHttpLocal = false))
            .isEqualTo(HostRules.Verdict.PlainHttpNotAllowed)
    }

    @Test fun `http public host rejected even with flag`() {
        assertThat(HostRules.check("http://dav.example.com", allowHttpLocal = true))
            .isEqualTo(HostRules.Verdict.HostNotLocal)
    }

    @Test fun `http localhost allowed with flag`() {
        assertThat(HostRules.check("http://localhost:8080", allowHttpLocal = true)).isEqualTo(HostRules.Verdict.Ok)
        assertThat(HostRules.check("http://127.0.0.1:5244", allowHttpLocal = true)).isEqualTo(HostRules.Verdict.Ok)
    }

    @Test fun `http private ranges allowed with flag`() {
        assertThat(HostRules.isLocalHostName("10.0.0.5")).isTrue()
        assertThat(HostRules.isLocalHostName("172.16.0.1")).isTrue()
        assertThat(HostRules.isLocalHostName("172.31.255.254")).isTrue()
        assertThat(HostRules.isLocalHostName("172.15.0.1")).isFalse()
        assertThat(HostRules.isLocalHostName("172.32.0.1")).isFalse()
        assertThat(HostRules.isLocalHostName("192.168.1.10")).isTrue()
        assertThat(HostRules.isLocalHostName("169.254.10.20")).isTrue() // link-local
    }

    @Test fun `dot-local and loopback v6`() {
        assertThat(HostRules.isLocalHostName("nas.local")).isTrue()
        assertThat(HostRules.isLocalHostName("[::1]")).isTrue()
        assertThat(HostRules.isLocalHostName("::1")).isTrue()
        assertThat(HostRules.isLocalHostName("fe80::1")).isTrue()
        assertThat(HostRules.isLocalHostName("fe80::1%wlan0")).isTrue()
    }

    @Test fun `public hosts are not local`() {
        assertThat(HostRules.isLocalHostName("example.com")).isFalse()
        assertThat(HostRules.isLocalHostName("8.8.8.8")).isFalse()
        assertThat(HostRules.isLocalHostName("11.0.0.1")).isFalse()
        assertThat(HostRules.isLocalHostName("192.169.0.1")).isFalse()
    }

    @Test fun `garbage is invalid url`() {
        assertThat(HostRules.check("not a url", allowHttpLocal = true)).isEqualTo(HostRules.Verdict.InvalidUrl)
    }
}
