package net.nekomo.nex

import net.nekomo.nex.util.Hashes
import net.nekomo.nex.util.Urls
import net.nekomo.nex.util.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test fun validatesUrls() {
        assertTrue(Urls.validate("https://example.com/index.min.json") is Urls.Validation.Valid)
        assertTrue(Urls.validate("http://example.com/index.min.json") is Urls.Validation.Invalid)
        assertTrue(Urls.validate("http://example.com/index.min.json", allowHttp = true) is Urls.Validation.Valid)
        assertTrue(Urls.validate("not a url") is Urls.Validation.Invalid)
        assertTrue(Urls.validate("ftp://example.com/x") is Urls.Validation.Invalid)
        assertTrue(Urls.validate("") is Urls.Validation.Invalid)
    }

    @Test fun resolvesRelative() {
        assertEquals("https://e.com/a/b.js", Urls.resolve("https://e.com/a/index.json", "b.js"))
        assertEquals("https://x.com/c.js", Urls.resolve("https://e.com/a/index.json", "https://x.com/c.js"))
    }

    @Test fun comparesVersions() {
        assertTrue(Versions.compare("1.4.10", "1.4.9") > 0)
        assertTrue(Versions.compare("v2.0", "2.0.0") == 0)
        assertTrue(Versions.compare("0.9", "1.0") < 0)
        assertEquals(1 to 4, Versions.libMajorMinor("1.4.190"))
    }

    @Test fun hashesBytes() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashes.sha256(ByteArray(0)))
    }
}
