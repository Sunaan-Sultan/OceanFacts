package com.pixel.oceanfacts.core

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads every piece of catalog content (zones, facts, creatures, the descent) from JSON in
 * `assets/` instead of hard-coding it in Kotlin.
 *
 * Call [init] once at app start before any screen reads the data; it is idempotent, so every
 * entry point can call it without coordinating. The public catalog API in Catalog.kt simply
 * forwards to the fields populated here.
 */
object OceanData {

    /** The Deep Dive pseudo-zone is always loaded but never shown as a zone card. */
    const val DEEP_DIVE = "deep"

    var creatures: List<Creature> = emptyList(); private set
    var zoneMeta: Map<String, ZoneMeta> = emptyMap(); private set
    var featured: List<FeaturedZone> = emptyList(); private set
    var lockedRealms: List<Realm> = emptyList(); private set
    var descent: List<DescentStop> = emptyList(); private set

    /** Facts keyed by zone id (incl. Deep Dive), in authoring order. */
    private var factsByZone: Map<String, List<Fact>> = emptyMap()

    /** Every fact across the explorable zones, then Deep Dive. */
    var allFacts: List<Fact> = emptyList(); private set

    /** The authored quiz bank, minus any entry pointing at a fact that is no longer shipped. */
    var quiz: List<QuizEntry> = emptyList(); private set

    @Volatile private var loaded = false

    fun factsForZone(zone: String): List<Fact> =
        factsByZone[zone] ?: factsByZone["sunlit"] ?: emptyList()

    /** Idempotent — safe to call from every Activity.onCreate and from the reminder worker. */
    fun init(context: Context) {
        if (loaded) return
        val assets = context.applicationContext.assets
        load { path -> assets.open(path).bufferedReader().use { it.readText() } }
    }

    /**
     * The catalog load, with reading left to the caller so a JVM test can hand it the files
     * from `src/main/assets` directly instead of needing a Context and an emulator.
     */
    internal fun load(read: (String) -> String) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            creatures = parseArray(read("creatures.json")) { it.toCreature() }
            descent = parseArray(read("descent.json")) { it.toDescentStop() }
                .sortedBy { it.depthM }

            val root = JSONObject(read("zones.json"))

            val explorable = root.getJSONArray("explorable")
            val meta = LinkedHashMap<String, ZoneMeta>()
            val cards = ArrayList<FeaturedZone>()
            val facts = LinkedHashMap<String, List<Fact>>()
            for (i in 0 until explorable.length()) {
                val o = explorable.getJSONObject(i)
                val id = o.getString("id")
                meta[id] = o.getJSONObject("meta").toZoneMeta()
                cards += o.getJSONObject("featured").toFeatured(id)
                facts[id] = parseFacts(read(o.getString("factsFile")), id)
            }
            // Deep Dive loads as its own zone but stays out of the explorable lists.
            facts[DEEP_DIVE] = parseFacts(read("facts/deepdive.json"), DEEP_DIVE)

            zoneMeta = meta
            featured = cards
            factsByZone = facts
            lockedRealms = parseArray(root.getJSONArray("locked")) { it.toRealm() }

            allFacts = facts.entries
                .filter { it.key != DEEP_DIVE }
                .flatMap { it.value } + (facts[DEEP_DIVE] ?: emptyList())

            val factIds = allFacts.mapTo(HashSet()) { it.id }
            quiz = parseArray(read("quiz.json")) { it.toQuizEntry() }
                .filter { it.factId in factIds }

            loaded = true
        }
    }

    // ───────────────────────── parsing helpers ─────────────────────────

    private inline fun <T> parseArray(json: String, map: (JSONObject) -> T): List<T> =
        parseArray(JSONArray(json), map)

    private inline fun <T> parseArray(arr: JSONArray, map: (JSONObject) -> T): List<T> =
        (0 until arr.length()).map { map(arr.getJSONObject(it)) }

    private fun parseFacts(json: String, zone: String): List<Fact> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it).toFact(zone, it + 1) }
    }

    /** A 6-hex string is opaque RGB; an 8-hex string is taken as full ARGB (used for glows). */
    private fun color(s: String): Color {
        val v = s.removePrefix("0x").removePrefix("#")
        val n = v.toLong(16)
        return if (v.length <= 6) Color(n or 0xFF000000) else Color(n)
    }

    private fun JSONArray.toColors(): List<Color> =
        (0 until length()).map { color(getString(it)) }

    private fun JSONObject.colorList(key: String) = getJSONArray(key).toColors()

    private fun JSONObject.toCreature() = Creature(
        name = getString("name"),
        lengthM = getDouble("lengthM").toFloat(),
        kind = BodyKind.valueOf(getString("kind")),
        c = colorList("colors"),
    )

    private fun JSONObject.toZoneMeta() = ZoneMeta(
        label = getString("label"),
        eyebrow = getString("eyebrow"),
        eyebrowColor = color(getString("eyebrowColor")),
        title = getString("title"),
        blurb = getString("blurb"),
    )

    private fun JSONObject.toFeatured(id: String) = FeaturedZone(
        zoneId = id,
        scene = SceneId.valueOf(getString("scene")),
        dur = getDouble("dur").toFloat(),
        hero = getDouble("hero").toFloat(),
        depthM = getInt("depthM"),
        pill = getString("pill"),
        pillColor = color(getString("pillColor")),
        title = getString("title"),
        tagline = getString("tagline"),
    )

    private fun JSONObject.toRealm() = Realm(
        name = getString("name"),
        depth = getString("depth"),
        desc = getString("desc"),
        color = colorList("colors"),
    )

    private fun JSONObject.toFact(zone: String, num: Int): Fact {
        val stats = getJSONArray("stats")
        return Fact(
            id = getString("id"),
            cat = getString("cat"),
            accent = color(getString("accent")),
            scene = SceneId.valueOf(getString("scene")),
            dur = 15f,                       // every scene plays on a uniform 15 s loop
            hero = getDouble("hero").toFloat(),
            depthM = getInt("depthM"),
            title = getString("title"),
            sub = getString("sub"),
            blurb = getString("blurb"),
            stats = (0 until stats.length()).map {
                val p = stats.getJSONArray(it)
                p.getString(0) to p.getString(1)
            },
            zone = zone,
            source = SourceManager.sourceFor(zone, num),
        )
    }

    private fun JSONObject.toQuizEntry(): QuizEntry {
        val wrong = getJSONArray("w")
        return QuizEntry(
            factId = getString("fact"),
            prompt = getString("q"),
            answer = getString("a"),
            wrong = (0 until wrong.length()).map { wrong.getString(it) },
        )
    }

    private fun JSONObject.toDescentStop() = DescentStop(
        name = getString("name"),
        sub = getString("sub"),
        depthText = getString("depthText"),
        depthM = getInt("depthM"),
        kind = StopKind.valueOf(getString("kind")),
        colors = colorList("colors"),
        glow = optString("glow").takeIf { it.isNotEmpty() }?.let { color(it) } ?: Color.Transparent,
    )
}
