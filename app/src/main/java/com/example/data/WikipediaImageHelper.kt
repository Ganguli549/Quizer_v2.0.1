package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebIllustration(
    val imageUrl: String,
    val title: String,
    val caption: String,
    val sourceUrl: String,
    val provider: String = "Web Search", // "Wikipedia", "Wikimedia Commons", or web domain e.g. "britannica.com"
    val mediaType: String = "image",     // "image", "gif", "chart", "video"
    val videoUrl: String? = null,
    val thumbUrl: String = ""
)

object WikipediaImageHelper {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; QuizerEducationalApp/1.0; contact@quizer.app) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()

    fun verifyUrlAccessible(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 416 || response.code in 300..399
            }
        } catch (e: Exception) {
            url.startsWith("https://upload.wikimedia.org") || url.startsWith("https://commons.wikimedia.org")
        }
    }

    private fun downloadBytes(url: String, maxBytes: Int = 1024 * 1024): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val stream = response.body?.byteStream() ?: return null
                val buffer = java.io.ByteArrayOutputStream()
                val data = ByteArray(4096)
                var total = 0
                var n: Int
                while (stream.read(data).also { n = it } != -1) {
                    buffer.write(data, 0, n)
                    total += n
                    if (total >= maxBytes) break
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun detectMediaType(url: String, mime: String?, title: String): String {
        val lowerUrl = url.lowercase()
        val lowerTitle = title.lowercase()
        val lowerMime = mime?.lowercase() ?: ""

        return when {
            lowerMime.startsWith("video/") || lowerUrl.endsWith(".webm") || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".ogv") -> "video"
            lowerMime == "image/gif" || lowerUrl.endsWith(".gif") || lowerTitle.contains("animation") || lowerTitle.contains("animated") -> "gif"
            lowerMime.contains("svg") || lowerUrl.endsWith(".svg") ||
                    lowerTitle.contains("diagram") || lowerTitle.contains("chart") ||
                    lowerTitle.contains("graph") || lowerTitle.contains("schematic") ||
                    lowerTitle.contains("cycle") || lowerTitle.contains("table") ||
                    lowerTitle.contains("plot") || lowerTitle.contains("structure") ||
                    lowerTitle.contains("anatomy") -> "chart"
            else -> "image"
        }
    }

    /**
     * Search Bing Web Images for educational figures, diagrams, and illustrations across the web.
     */
    fun searchWebImages(query: String, customCaption: String?): List<WebIllustration> {
        val results = mutableListOf<WebIllustration>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.bing.com/images/search?q=$encoded&first=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()
                val matches = Regex("m=\"([^\"]+)\"").findAll(html)
                for (match in matches) {
                    try {
                        val unescaped = android.text.Html.fromHtml(match.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                        val json = JSONObject(unescaped)
                        val murl = json.optString("murl", "")
                        val turl = json.optString("turl", murl).ifBlank { murl }
                        val purl = json.optString("purl", "")
                        val title = json.optString("t", json.optString("desc", ""))
                            .replace(Regex("<.*?>"), "")
                            .take(70)

                        if (murl.isNotBlank() || turl.isNotBlank()) {
                            val domain = try {
                                URI(purl).host?.removePrefix("www.") ?: "Web Search"
                            } catch (e: Exception) {
                                "Web Search"
                            }
                            val mediaType = detectMediaType(murl, null, title)
                            val caption = customCaption?.takeIf { it.isNotBlank() } ?: "Illustration: $title"
                            if (isJunkCandidate(title, caption, query)) continue

                            results.add(
                                WebIllustration(
                                    imageUrl = murl,
                                    title = title.ifBlank { query },
                                    caption = caption,
                                    sourceUrl = purl.ifBlank { murl },
                                    provider = domain,
                                    mediaType = mediaType,
                                    thumbUrl = turl
                                )
                            )
                            if (results.size >= 5) break
                        }
                    } catch (e: Exception) {
                        // Skip malformed entry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Search Wikimedia Commons for diagrams, SVGs, charts, animated GIFs, and videos.
     */
    fun searchWikimediaCommons(query: String, customCaption: String?): List<WebIllustration> {
        val results = mutableListOf<WebIllustration>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=5&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=960&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query")
                    val pages = queryObj?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val page = pages.getJSONObject(key)
                            val title = page.optString("title", "").removePrefix("File:")
                            val imageInfoArr = page.optJSONArray("imageinfo")
                            if (imageInfoArr != null && imageInfoArr.length() > 0) {
                                val info = imageInfoArr.getJSONObject(0)
                                val originalUrl = info.optString("url", "")
                                val thumbUrl = info.optString("thumburl", originalUrl).ifBlank { originalUrl }
                                val mime = info.optString("mime", "")

                                val mediaType = detectMediaType(originalUrl, mime, title)
                                val videoUrl = if (mediaType == "video") originalUrl else null
                                val isDirectVideoThumb = thumbUrl.endsWith(".webm", true) || thumbUrl.endsWith(".ogv", true) || thumbUrl.endsWith(".mp4", true)
                                val displayUrl = if (mediaType == "video") {
                                    if (thumbUrl.isNotBlank() && !isDirectVideoThumb && thumbUrl != originalUrl) thumbUrl else ""
                                } else {
                                    if (thumbUrl.isNotBlank()) thumbUrl else originalUrl
                                }

                                if (displayUrl.isNotBlank() || mediaType == "video") {
                                    val sourceUrl = page.optString(
                                        "descriptionurl",
                                        "https://commons.wikimedia.org/wiki/File:${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                                    )
                                    val cleanTitle = title.substringBeforeLast(".")
                                        .replace("_", " ")
                                        .take(65)

                                    val finalCaption = customCaption?.takeIf { it.isNotBlank() }
                                        ?: "Illustration: $cleanTitle"
                                    if (isJunkCandidate(cleanTitle, finalCaption, query)) continue

                                    results.add(
                                        WebIllustration(
                                            imageUrl = displayUrl,
                                            title = cleanTitle,
                                            caption = finalCaption,
                                            sourceUrl = sourceUrl,
                                            provider = "Wikimedia Commons",
                                            mediaType = mediaType,
                                            videoUrl = videoUrl,
                                            thumbUrl = thumbUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Search Wikipedia API for encyclopedic articles and high-resolution thumbnails.
     */
    fun searchWikipedia(query: String, customCaption: String?): List<WebIllustration> {
        val results = mutableListOf<WebIllustration>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=5&prop=pageimages|extracts|info&piprop=thumbnail|original&pithumbsize=960&inprop=url&exintro=1&explaintext=1&exchars=200&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val pages = root.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val page = pages.getJSONObject(key)
                            val thumb = page.optJSONObject("thumbnail")?.optString("source")
                                ?: page.optJSONObject("original")?.optString("source")

                            if (!thumb.isNullOrBlank()) {
                                val pageTitle = page.optString("title", query)
                                val pageUrl = page.optString(
                                    "fullurl",
                                    "https://en.wikipedia.org/wiki/${URLEncoder.encode(pageTitle.replace(" ", "_"), "UTF-8")}"
                                )
                                val extract = page.optString("extract", "").trim()
                                val finalCaption = customCaption?.takeIf { it.isNotBlank() }
                                    ?: if (extract.isNotBlank()) extract.take(170) + "..." else "Illustration for $pageTitle"

                                val mediaType = detectMediaType(thumb, null, pageTitle)
                                if (isJunkCandidate(pageTitle, finalCaption, query)) continue

                                results.add(
                                    WebIllustration(
                                        imageUrl = thumb,
                                        title = pageTitle,
                                        caption = finalCaption,
                                        sourceUrl = pageUrl,
                                        provider = "Wikipedia",
                                        mediaType = mediaType,
                                        thumbUrl = thumb
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private val JUNK_KEYWORDS = listOf(
        "book cover", "cover art", "notebook", "paperback", "hardcover",
        "journal", "novel", "album cover", "cd cover", "dvd cover",
        "poster", "t-shirt", "merchandise", "audiobook", "kindle edition",
        "clipart", "stock vector", "shutterstock", "alamy", "istockphoto",
        "dreamstime", "watermark", "product page", "buy now"
    )

    fun isJunkCandidate(title: String, caption: String, topic: String): Boolean {
        val topicLower = topic.lowercase()
        val isBookTopic = topicLower.contains("book") || topicLower.contains("novel") || topicLower.contains("author") || topicLower.contains("literature")
        if (!isBookTopic) {
            val titleLower = title.lowercase()
            val capLower = caption.lowercase()
            for (kw in JUNK_KEYWORDS) {
                if (titleLower.contains(kw) || capLower.contains(kw)) {
                    return true
                }
            }
            if (titleLower.contains("cover") || capLower.contains("cover")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a topic or question is suitable for an illustration.
     * Vocabulary, word meanings, synonyms, antonyms, grammar rules, and abstract words are NOT suitable.
     */
    fun isConceptSuitableForIllustration(query: String, contextInfo: String = ""): Boolean {
        val q = query.lowercase().trim()
        val ctx = contextInfo.lowercase()

        // Explicit video, gif, or animation requests are always suitable and must never be blocked
        if (q.contains("video") || q.contains("gif") || q.contains("animation") || q.contains("animated") ||
            ctx.contains("video") || ctx.contains("gif") || ctx.contains("animation") || ctx.contains("animated")) {
            return true
        }

        val vocabTerms = listOf(
            "meaning", "synonym", "antonym", "definition", "define", "idiom", "phrase",
            "grammar", "spelling", "tense", "preposition", "adjective", "adverb", "verb",
            "one word substitution", "vocabulary", "word meaning", "proverb"
        )
        if (vocabTerms.any { q.contains(it) || ctx.contains("meaning of $q") || ctx.contains("synonym of $q") || ctx.contains("antonym of $q") }) {
            return false
        }

        val commonAbstractWords = setOf(
            "amble", "saunter", "abate", "benevolent", "ephemeral", "pragmatic", "ubiquitous",
            "lucid", "candid", "audacious", "lethargic", "mitigate", "alleviate", "equivocal",
            "metaphor", "simile", "irony", "alliteration", "hyperbole", "oxymoron"
        )
        if (commonAbstractWords.contains(q)) {
            return false
        }

        return true
    }

    /**
     * AI watches the candidate image and strictly decides if it genuinely illustrates the topic.
     * Rejects book covers, notebooks, generic logos, clip art, and out-of-context images.
     */
    private suspend fun verifyImageRelevanceWithAi(
        context: Context,
        imageBytes: ByteArray,
        topic: String,
        title: String,
        caption: String,
        mimeType: String = "image/jpeg",
        onDevLog: ((String) -> Unit)? = null
    ): Boolean {
        val prompt = """
            You are an educational visual reviewer evaluating an image for students.
            Subject/Question topic: "$topic"
            Candidate image title: "$title"
            Candidate image caption: "$caption"

            Examine the attached image:
            Does this image accurately, directly, and helpfully illustrate the actual educational concept or object of "$topic"?

            STRICT REJECTION RULES (Reply 'NO' if ANY apply):
            1. The image is a book cover, notebook cover, novel, binder, journal, or CD/album (even if the title contains the search word).
            2. The image is a generic logo, stock watermark, clipart, advertisement, or unrelated graphic.
            3. The image is out of context, unrelated, or misleading.
            4. The image does not clearly depict or explain the actual physical object, specimen, diagram, anatomy, or phenomenon.

            ACCEPTANCE RULE:
            Reply 'YES' ONLY if the image genuinely and clearly depicts the actual educational subject itself (e.g. an authentic diagram, scientific schematic, anatomical illustration, real photograph of the object/specimen, or accurate educational visual).

            Format:
            VERDICT: YES (or) VERDICT: NO
        """.trimIndent()

        return try {
            val response = GeminiHelper.generateContent(
                context = context,
                prompt = prompt,
                attachments = listOf(AiFileAttachment(bytes = imageBytes, mimeType = mimeType)),
                onDevLog = onDevLog
            )
            val upper = response.uppercase()
            val isApproved = (upper.contains("VERDICT: YES") || upper.startsWith("YES")) &&
                    !upper.contains("VERDICT: NO") && !upper.startsWith("NO") && !upper.contains("REJECT")
            onDevLog?.invoke("AI visual check: isApproved=$isApproved (response: ${response.take(60)})")
            isApproved
        } catch (e: Exception) {
            e.printStackTrace()
            onDevLog?.invoke("AI visual check failed/error: ${e.message}")
            false // NEVER allow unverified or erroring images through
        }
    }

    /**
     * Main entry point:
     * - Checks if concept is suitable for illustration
     * - Uses the source the AI decided is best (e.g. Wikipedia, Commons, or Web Search)
     * - Live updates status in chat
     * - Natural visual inspection so relevant images are approved
     */
    suspend fun searchIllustrationWithAiValidation(
        context: Context,
        query: String,
        preferredSource: String? = null,
        customCaption: String? = null,
        conceptContext: String = "",
        onStatusUpdate: ((String, Float) -> Unit)? = null,
        onDevLog: ((String) -> Unit)? = null
    ): WebIllustration? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^query\\s*=\\s*\"?", RegexOption.IGNORE_CASE), "")
            .trimEnd('"')
        if (cleanQuery.isBlank()) return@withContext null

        val sourceChoice = preferredSource?.lowercase()?.trim() ?: "auto"

        // Let AI decide which sources are best, or auto-order sensibly
        val sourcesToTry: List<String> = when (sourceChoice) {
            "wikipedia", "wiki" -> listOf("wikipedia", "commons", "web")
            "commons", "wikimedia" -> listOf("commons", "wikipedia", "web")
            "web", "bing", "search" -> listOf("web", "wikipedia", "commons")
            else -> {
                val isVisual = cleanQuery.contains("diagram", ignoreCase = true) ||
                        cleanQuery.contains("chart", ignoreCase = true) ||
                        cleanQuery.contains("schematic", ignoreCase = true) ||
                        cleanQuery.contains("cycle", ignoreCase = true) ||
                        cleanQuery.contains("animation", ignoreCase = true) ||
                        cleanQuery.contains("gif", ignoreCase = true) ||
                        cleanQuery.contains("video", ignoreCase = true)
                if (isVisual) listOf("commons", "wikipedia", "web") else listOf("wikipedia", "commons", "web")
            }
        }

        val topicContext = if (conceptContext.isNotBlank()) conceptContext else cleanQuery

        if (!isConceptSuitableForIllustration(cleanQuery, topicContext)) {
            onDevLog?.invoke("Skipping illustration: '$cleanQuery' is vocabulary/abstract concept not suitable for visual illustration.")
            return@withContext null
        }

        // Try the chosen source first, then fallback to others if needed
        for ((idx, source) in sourcesToTry.withIndex()) {
            val sourceLabel = when (source) {
                "wikipedia" -> "Wikipedia"
                "commons" -> "Wikimedia Commons"
                else -> "Web Search"
            }
            val baseProgress = 0.5f + (idx * 0.15f)
            onStatusUpdate?.invoke("Searching $sourceLabel...", baseProgress)
            onDevLog?.invoke("Searching $sourceLabel for: '$cleanQuery'")

            val candidates: List<WebIllustration> = when (source) {
                "wikipedia" -> searchWikipedia(cleanQuery, customCaption)
                "commons" -> searchWikimediaCommons(cleanQuery, customCaption)
                else -> searchWebImages(cleanQuery, customCaption)
            }

            if (candidates.isEmpty()) continue

            // Evaluate top candidates from this source
            for (candidate in candidates.take(3)) {
                if (isJunkCandidate(candidate.title, candidate.caption, cleanQuery)) {
                    onDevLog?.invoke("Skipping candidate with junk/book cover keyword: '${candidate.title}'")
                    continue
                }

                // If candidate is a video, approve it directly so educational video links are preserved
                if (candidate.mediaType == "video" || candidate.videoUrl?.isNotBlank() == true) {
                    onStatusUpdate?.invoke("Adding video illustration...", 0.9f)
                    onDevLog?.invoke("AI APPROVED video visual: '${candidate.title}' from ${candidate.provider}")
                    return@withContext candidate
                }

                val isCandidateGif = candidate.mediaType == "gif" || candidate.imageUrl.endsWith(".gif", true) || candidate.thumbUrl.endsWith(".gif", true) || candidate.title.contains("animation", true) || candidate.title.contains("animated", true)
                if (isCandidateGif && !isJunkCandidate(candidate.title, candidate.caption, cleanQuery)) {
                    onStatusUpdate?.invoke("Adding animation...", 0.9f)
                    onDevLog?.invoke("AI APPROVED educational GIF visual: '${candidate.title}' from ${candidate.provider}")
                    return@withContext candidate
                }

                val downloadUrl = candidate.thumbUrl.ifBlank { candidate.imageUrl }
                onStatusUpdate?.invoke("Checking visual relevance...", baseProgress + 0.05f)
                onDevLog?.invoke("Evaluating visual candidate: '${candidate.title}' from ${candidate.provider}")

                val imageBytes = downloadBytes(downloadUrl)
                if (imageBytes == null || imageBytes.isEmpty()) {
                    continue
                }

                val isGif = isCandidateGif || (imageBytes.size > 3 && imageBytes[0] == 'G'.code.toByte() && imageBytes[1] == 'I'.code.toByte() && imageBytes[2] == 'F'.code.toByte())
                val detectedMime = if (isGif) "image/gif" else "image/jpeg"

                var isRelevant = verifyImageRelevanceWithAi(
                    context = context,
                    imageBytes = imageBytes,
                    topic = topicContext,
                    title = candidate.title,
                    caption = candidate.caption,
                    mimeType = detectedMime,
                    onDevLog = onDevLog
                )

                // Fallback for GIFs: if AI verification failed due to format/decoder, accept if title clearly matches query
                if (!isRelevant && isGif && !isJunkCandidate(candidate.title, candidate.caption, cleanQuery)) {
                    onDevLog?.invoke("Approving educational GIF by fallback: '${candidate.title}'")
                    isRelevant = true
                }

                if (isRelevant) {
                    val accessibleUrl = if (verifyUrlAccessible(candidate.imageUrl)) {
                        candidate.imageUrl
                    } else if (candidate.thumbUrl.isNotBlank() && verifyUrlAccessible(candidate.thumbUrl)) {
                        candidate.thumbUrl
                    } else {
                        candidate.thumbUrl.ifBlank { candidate.imageUrl }
                    }

                    onStatusUpdate?.invoke("Adding illustration...", 0.9f)
                    onDevLog?.invoke("AI APPROVED visual: '${candidate.title}' from ${candidate.provider}")
                    return@withContext candidate.copy(imageUrl = accessibleUrl)
                } else {
                    onDevLog?.invoke("AI rejected visual candidate: '${candidate.title}'")
                }
            }
        }

        onDevLog?.invoke("No suitable illustration found for: '$cleanQuery'")
        return@withContext null
    }

    /**
     * Backward-compatible simple search
     */
    suspend fun searchIllustration(query: String, customCaption: String? = null): WebIllustration? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().removeSurrounding("\"")
        if (cleanQuery.isBlank()) return@withContext null

        val candidates = mutableListOf<WebIllustration>()
        candidates.addAll(searchWikipedia(cleanQuery, customCaption))
        candidates.addAll(searchWikimediaCommons(cleanQuery, customCaption))
        candidates.addAll(searchWebImages(cleanQuery, customCaption))

        for (cand in candidates) {
            val url = if (verifyUrlAccessible(cand.imageUrl)) cand.imageUrl else cand.thumbUrl
            if (url.isNotBlank() && verifyUrlAccessible(url)) {
                return@withContext cand.copy(imageUrl = url)
            }
        }
        return@withContext null
    }
}
