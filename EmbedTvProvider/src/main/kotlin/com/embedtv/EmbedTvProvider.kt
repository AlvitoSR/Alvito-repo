package com.embedtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document

class EmbedTvProvider : MainAPI() {

    override var mainUrl  = "https://w1.embedtv.live"
    override var name     = "EmbedTV"
    override val hasMainPage = true
    override var lang     = "pt"
    override val supportedTypes = setOf(TvType.Live)

    // ── CDN real descoberto via DevTools (aba Rede do Firefox) ─────────────────
    // GET https://xnn--d2ma04s8hp22.cloudfronte.lat/zzstream/sportv.m3u8
    private val cdnBase = "https://xnn--d2ma04s8hp22.cloudfronte.lat/zzstream"

    // Headers que o CDN exige (visíveis nos "Cabeçalhos da requisição")
    private val streamHeaders = mapOf(
        "Referer"        to "https://w1.embedtv.live/",
        "Origin"         to "https://w1.embedtv.live",
        "User-Agent"     to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept"         to "*/*",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "cross-site"
    )

    // ── Modelo de canal ────────────────────────────────────────────────────────
    data class Channel(
        val slug  : String,   // ex: "sportv"  → slug da URL da página
        val name  : String,   // ex: "SporTV"
        val logo  : String?,
        val group : String
    ) {
        // Monta a URL do stream com o padrão confirmado pelo DevTools
        val streamUrl: String get() =
            "https://xnn--d2ma04s8hp22.cloudfronte.lat/zzstream/$slug.m3u8"
    }

    // ── Tenta fazer scraping da lista de canais da página principal ────────────
    private suspend fun fetchChannels(): List<Channel> {
        val channels = mutableListOf<Channel>()
        runCatching {
            val doc: Document = app.get(
                mainUrl,
                headers = mapOf("Referer" to "$mainUrl/")
            ).document

            // Seleciona links relativos que apontam para canais
            val links = doc.select("a[href]").filter { el ->
                val href = el.attr("href")
                href.isNotBlank()
                    && !href.startsWith("http")
                    && !href.startsWith("#")
                    && !href.contains(".")
            }

            for (el in links) {
                val href  = el.attr("href").trimStart('/')
                if (href.isBlank()) continue
                val label = el.text().trim().ifBlank { href }
                val logo  = el.selectFirst("img")?.attr("src")
                             ?.let { if (it.startsWith("http")) it else "$mainUrl/$it" }
                val group = el.attr("data-group").ifBlank { "Canais" }
                channels.add(Channel(slug = href, name = label, logo = logo, group = group))
            }
        }
        // Fallback garantido se o scraping falhar ou retornar vazio
        return channels.ifEmpty { KNOWN_CHANNELS }
    }

    // ── Página inicial ─────────────────────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels()
        val pages = channels.groupBy { it.group }.map { (group, chs) ->
            HomePageList(
                name  = group,
                list  = chs.map { ch ->
                    LiveSearchResponse(
                        name      = ch.name,
                        url       = ch.streamUrl,
                        apiName   = this.name,
                        type      = TvType.Live,
                        posterUrl = ch.logo
                    )
                }
            )
        }
        return newHomePageResponse(pages)
    }

    // ── Busca ──────────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> =
        fetchChannels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { ch ->
                LiveSearchResponse(
                    name      = ch.name,
                    url       = ch.streamUrl,
                    apiName   = this.name,
                    type      = TvType.Live,
                    posterUrl = ch.logo
                )
            }

    // ── Load: a URL já é o stream direto ──────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        val channelName = url
            .substringAfterLast("/")
            .substringBefore(".")
            .replaceFirstChar { it.uppercase() }
        return LiveStreamLoadResponse(
            name    = channelName,
            url     = url,
            apiName = this.name,
            dataUrl = url
        )
    }

    // ── Entrega o link com Referer e headers corretos ──────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = data,
                referer = "https://w1.embedtv.live/",
                quality = Qualities.Unknown.value,
                isM3u8  = true,
                headers = streamHeaders
            )
        )
        return true
    }

    // ── Fallback: canais confirmados/prováveis ─────────────────────────────────
    // Confirme os slugs abrindo w1.embedtv.live e olhando os links dos canais
    private val KNOWN_CHANNELS = listOf(
        // Esportes
        Channel("sportv",        "SporTV",            null, "Esportes"),
        Channel("sportv2",       "SporTV 2",          null, "Esportes"),
        Channel("sportv3",       "SporTV 3",          null, "Esportes"),
        Channel("espn",          "ESPN",               null, "Esportes"),
        Channel("espn2",         "ESPN 2",             null, "Esportes"),
        Channel("espn3",         "ESPN 3",             null, "Esportes"),
        Channel("espn4",         "ESPN 4",             null, "Esportes"),
        Channel("cazetv",        "CazeTV",             null, "Esportes"),
        Channel("bandsports",    "Band Sports",        null, "Esportes"),
        // Abertos
        Channel("globo",         "Globo",              null, "Abertos"),
        Channel("record",        "Record",             null, "Abertos"),
        Channel("sbt",           "SBT",                null, "Abertos"),
        Channel("band",          "Band",               null, "Abertos"),
        Channel("redetv",        "RedeTV",             null, "Abertos"),
        // Notícias
        Channel("cnn",           "CNN Brasil",         null, "Notícias"),
        Channel("glonews",       "GloboNews",          null, "Notícias"),
        Channel("jovempan",      "Jovem Pan News",     null, "Notícias"),
        Channel("recordnews",    "Record News",        null, "Notícias"),
        // Entretenimento
        Channel("tnt",           "TNT",                null, "Entretenimento"),
        Channel("hbo",           "HBO",                null, "Entretenimento"),
        Channel("multishow",     "Multishow",          null, "Entretenimento"),
        Channel("discovery",     "Discovery",          null, "Entretenimento"),
        Channel("telecine",      "Telecine",           null, "Entretenimento"),
        Channel("mtv",           "MTV",                null, "Entretenimento"),
        // Infantil
        Channel("cartoon",       "Cartoon Network",    null, "Infantil"),
        Channel("disneychannel", "Disney Channel",     null, "Infantil"),
        Channel("nickjr",        "Nick Jr",            null, "Infantil"),
    )
}
