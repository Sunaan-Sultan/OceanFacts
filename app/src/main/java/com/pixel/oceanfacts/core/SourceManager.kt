package com.pixel.oceanfacts.core

import java.net.URI

/**
 * Where each fact came from.
 *
 * Kept out of the fact JSON on purpose: a source is a claim about the world rather than a piece
 * of copy, and holding all of them in one list makes them reviewable together. The key is the
 * zone id and the fact's position within its file, so adding a fact in the middle of a zone is
 * the one edit that has to be mirrored here.
 */
object SourceManager {

    const val FALLBACK_NAME = "NOAA / Ocean Science"

    private val LINKS: Map<String, String> = mapOf(
        "sunlit.1" to "https://en.wikipedia.org/wiki/Largest_organisms",
        "sunlit.2" to "https://www.fisheries.noaa.gov/species/blue-whale",
        "sunlit.3" to "https://oceanservice.noaa.gov/facts/light_travel.html",
        "sunlit.4" to "https://oceanservice.noaa.gov/facts/ocean-color.html",
        "sunlit.5" to "https://oceanservice.noaa.gov/facts/ocean-oxygen.html",
        "sunlit.6" to "https://oceanservice.noaa.gov/facts/wavesinocean.html",

        "twilight.1" to "https://twilightzone.whoi.edu/explore-the-otz/",
        "twilight.2" to "https://oceanexplorer.noaa.gov/facts/marinesnow.html",
        "twilight.3" to "https://oceanexplorer.noaa.gov/facts/bioluminescence.html",
        "twilight.4" to "https://en.wikipedia.org/wiki/Counter-illumination",
        "twilight.5" to "https://en.wikipedia.org/wiki/Diel_vertical_migration",
        "twilight.6" to "https://ocean.si.edu/ocean-life/invertebrates/giant-squid",

        "midnight.1" to "https://en.wikipedia.org/wiki/Pelagic_zone",
        "midnight.2" to "https://en.wikipedia.org/wiki/Deep_sea",
        "midnight.3" to "https://ocean.si.edu/ocean-life/fish/anglerfish",
        "midnight.4" to "https://en.wikipedia.org/wiki/Anglerfish",
        "midnight.5" to "https://en.wikipedia.org/wiki/Whale_fall",
        "midnight.6" to "https://en.wikipedia.org/wiki/Grimpoteuthis",

        "trench.1" to "https://en.wikipedia.org/wiki/Challenger_Deep",
        "trench.2" to "https://en.wikipedia.org/wiki/Mariana_Trench",
        "trench.3" to "https://en.wikipedia.org/wiki/Pseudoliparis_swirei",
        "trench.4" to "https://oceanexplorer.noaa.gov/facts/hydrothermal-vents.html",
        "trench.5" to "https://en.wikipedia.org/wiki/List_of_people_who_descended_to_Challenger_Deep",
        "trench.6" to "https://seabed2030.org/",

        "deep.1" to "https://en.wikipedia.org/wiki/Wreck_of_the_Titanic",
        "deep.2" to "https://www.usgs.gov/special-topics/water-science-school/science/water-cycle",
        "deep.3" to "https://oceanservice.noaa.gov/education/tutorial_tides/tides02_cause.html",
        "deep.4" to "https://en.wikipedia.org/wiki/Amazon_River",
        "deep.5" to "https://en.wikipedia.org/wiki/Great_Pacific_garbage_patch",
        "deep.6" to "https://oceanservice.noaa.gov/education/tutorial_currents/06conveyor.html",
    )

    private val PUBLISHERS: Map<String, String> = mapOf(
        "oceanservice.noaa.gov" to "NOAA Ocean Service",
        "oceanexplorer.noaa.gov" to "NOAA Ocean Exploration",
        "fisheries.noaa.gov" to "NOAA Fisheries",
        "coastwatch.noaa.gov" to "NOAA CoastWatch",
        "noaa.gov" to "NOAA",
        "twilightzone.whoi.edu" to "Woods Hole (OTZ)",
        "whoi.edu" to "Woods Hole",
        "mbari.org" to "MBARI",
        "ocean.si.edu" to "Smithsonian Ocean",
        "si.edu" to "Smithsonian",
        "usgs.gov" to "USGS",
        "seabed2030.org" to "Seabed 2030",
        "schmidtocean.org" to "Schmidt Ocean Institute",
        "nationalgeographic.com" to "National Geographic",
        "wikipedia.org" to "Wikipedia",
        "nature.com" to "Nature",
        "science.org" to "Science",
        "sciencedirect.com" to "ScienceDirect",
        "pnas.org" to "PNAS",
    )

    /** Every key in the link map, for the content-validation test in `ContentTest`. */
    internal val keys: Set<String> get() = LINKS.keys

    fun keyFor(zone: String, num: Int): String = "$zone.$num"

    fun urlFor(zone: String, num: Int): String? =
        LINKS[keyFor(zone, num)]?.trim()?.takeIf { it.startsWith("https://") }

    fun sourceFor(zone: String, num: Int): FactSource? =
        urlFor(zone, num)?.let { FactSource(name = publisherOf(it), url = it) }

    fun publisherOf(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()
            .orEmpty()
            .lowercase()
            .removePrefix("www.")
        if (host.isEmpty()) return FALLBACK_NAME
        PUBLISHERS[host]?.let { return it }
        return PUBLISHERS.entries.firstOrNull { host.endsWith(".${it.key}") }?.value ?: host
    }
}
