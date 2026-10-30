package net.nekomo.nex

import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.PackageKind
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.integrations.Formats
import net.nekomo.nex.integrations.RepoFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    private val mihon = """[{"name":"Tachiyomi: MangaDex","pkg":"eu.kanade.tachiyomi.extension.all.mangadex","apk":"tachiyomi-all.mangadex-v1.4.190.apk","lang":"all","code":190,"version":"1.4.190","nsfw":1,"sources":[{"name":"MangaDex","lang":"en","id":"2499283573021220255","baseUrl":"https://mangadex.org"}]}]"""
    private val aniyomi = """[{"name":"Aniyomi: AllAnime","pkg":"eu.kanade.tachiyomi.animeextension.en.allanime","apk":"aniyomi-en.allanime-v14.31.apk","lang":"en","code":31,"version":"14.31","nsfw":0}]"""
    private val csRepo = """{"name":"Test repo","description":"d","manifestVersion":1,"pluginLists":["https://example.com/plugins.json"]}"""
    private val csPlugins = """[{"url":"https://example.com/A.cs3","status":1,"version":7,"apiVersion":1,"name":"Alpha","internalName":"AlphaProvider","authors":["x"],"language":"en","tvTypes":["Movie"],"fileSize":1234}]"""
    private val zg = """{"name":"ZG","sources":[{"id":"hi","name":"HiAnime","version":"3","type":"anime","lang":"en","file":"sources/hi.js","logo":"logos/hi.png"}]}"""
    private val nekomo = """{"nekomoRepository":1,"name":"Nekomo Official","repositories":[{"type":"cloudstream","name":"CS","url":"https://example.com/repo.json"},{"type":"zangetsu","name":"ZG","url":"https://example.com/index.json"}],"apis":[]}"""

    @Test fun sniffsEveryFormat() {
        assertEquals(RepoFormat.TACHIYOMI_INDEX, Formats.sniff(mihon.toByteArray()))
        assertEquals(RepoFormat.ANIYOMI_INDEX, Formats.sniff(aniyomi.toByteArray()))
        assertEquals(RepoFormat.CLOUDSTREAM_REPO, Formats.sniff(csRepo.toByteArray()))
        assertEquals(RepoFormat.ZANGETSU_INDEX, Formats.sniff(zg.toByteArray()))
        assertEquals(RepoFormat.NEKOMO_MANIFEST, Formats.sniff(nekomo.toByteArray()))
        assertNull(Formats.sniff("<html>".toByteArray()))
        assertNull(Formats.sniff("{}".toByteArray()))
    }

    @Test fun parsesMihonIndex() {
        val idx = Formats.parseTachiyomi(AppId.MIHON, RepoFormat.TACHIYOMI_INDEX, "https://raw.example.com/repo/index.min.json", mihon.toByteArray())
        val e = idx.extensions.single()
        assertEquals("MangaDex", e.name)
        assertEquals(190L, e.versionCode)
        assertTrue(e.nsfw)
        assertEquals(PackageKind.APK, e.kind)
        assertEquals("https://raw.example.com/repo/apk/tachiyomi-all.mangadex-v1.4.190.apk", e.downloadUrl)
        assertEquals("https://raw.example.com/repo/icon/eu.kanade.tachiyomi.extension.all.mangadex.png", e.iconUrl)
    }

    @Test fun parsesAniyomiIndexAndStripsPrefix() {
        val idx = Formats.parseTachiyomi(AppId.ANIYOMI, RepoFormat.ANIYOMI_INDEX, "https://e.com/index.min.json", aniyomi.toByteArray())
        assertEquals("AllAnime", idx.extensions.single().name)
    }

    @Test(expected = RepoFormatException::class) fun rejectsProtobufIndex() {
        Formats.parseTachiyomi(AppId.MIHON, RepoFormat.TACHIYOMI_INDEX, "https://e.com/index.pb", byteArrayOf(1, 2, 3))
    }

    @Test fun normalizesTachiyomiUrls() {
        assertEquals("https://e.com/repo/index.min.json", Formats.normalizeTachiyomiUrl("https://e.com/repo/"))
        assertEquals("https://e.com/repo/index.min.json", Formats.normalizeTachiyomiUrl("https://e.com/repo/index.json"))
    }

    @Test fun parsesCloudStream() {
        val repo = Formats.parseCloudStreamRepo(csRepo.toByteArray())
        assertEquals(listOf("https://example.com/plugins.json"), repo.pluginLists)
        val p = Formats.parseCloudStreamPlugins(AppId.CLOUDSTREAM, csPlugins.toByteArray()).single()
        assertEquals("AlphaProvider", p.id)
        assertEquals("Alpha", p.name)
        assertEquals(7L, p.versionCode)
        assertEquals(PackageKind.CS3, p.kind)
    }

    @Test fun normalizesCloudStreamLinks() {
        assertEquals("https://e.com/repo.json", Formats.normalizeCloudStreamUrl("cloudstreamrepo://e.com/repo.json"))
        assertEquals("https://e.com/repo.json", Formats.normalizeCloudStreamUrl("https://cs.repo/?https://e.com/repo.json"))
    }

    @Test fun parsesZangetsuWithRelativePaths() {
        val idx = Formats.parseZangetsu(AppId.ZANGETSU, "https://e.com/zg/index.json", zg.toByteArray())
        val s = idx.extensions.single()
        assertEquals("https://e.com/zg/sources/hi.js", s.downloadUrl)
        assertEquals("https://e.com/zg/logos/hi.png", s.iconUrl)
        assertEquals(PackageKind.JS, s.kind)
    }

    @Test fun parsesNekomoManifest() {
        val m = Formats.parseNekomoManifest(nekomo.toByteArray())
        assertEquals(2, m.repositories.size)
        assertEquals(RepoFormat.CLOUDSTREAM_REPO, Formats.childFormat(m.repositories[0].type))
        assertEquals(RepoFormat.ZANGETSU_INDEX, Formats.childFormat(m.repositories[1].type))
    }

    @Test(expected = RepoFormatException::class) fun rejectsManifestWithoutMarker() {
        Formats.parseNekomoManifest("""{"repositories":[]}""".toByteArray())
    }
}
