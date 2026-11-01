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

class SelfUpdateVersionTest {
    @org.junit.Test
    fun selfUpdateVersionOrder() {
        val v = net.nekomo.nex.util.Versions
        org.junit.Assert.assertTrue(v.compare("v0.2.0-alpha", "0.1.9-alpha") > 0)
        org.junit.Assert.assertTrue(v.compare("0.2.0-alpha", "0.2.0-alpha") == 0)
        org.junit.Assert.assertTrue(v.compare("0.10.0-alpha", "0.9.9-alpha") > 0)
        org.junit.Assert.assertTrue(v.compare("0.1.1-alpha", "0.2.0-alpha") < 0)
    }
}

class ShortcodeRegistryTest {
    @org.junit.Test
    fun registryMatchesNekomoAndRejectsUnknownCodes() {
        val sc = net.nekomo.nex.util.Shortcodes
        org.junit.Assert.assertEquals(10, sc.size)
        org.junit.Assert.assertEquals("c7d89d5973291b4a5f65ccb23e9083ded998bcf09307de3f574d250437661a19", sc.digest)
        org.junit.Assert.assertNull(sc.lookup(""))
        org.junit.Assert.assertNull(sc.lookup("not-a-code"))
        org.junit.Assert.assertNull(sc.lookup("nekomo"))
    }
}
