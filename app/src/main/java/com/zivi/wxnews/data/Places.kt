package com.zivi.wxnews.data

/** The atolls from north to south, with the names the Met Office and the news use for them. */
object Atolls {
    class Atoll(val code: String, val name: String, val spellings: List<String>)

    val list = listOf(
        Atoll("HA", "Haa Alif", listOf("haa alifu", "haa alif")),
        Atoll("HDh", "Haa Dhaalu", listOf("haa dhaalu", "haa dhaal")),
        Atoll("Sh", "Shaviyani", listOf("shaviyani")),
        Atoll("N", "Noonu", listOf("noonu")),
        Atoll("R", "Raa", listOf("raa")),
        Atoll("B", "Baa", listOf("baa")),
        Atoll("Lh", "Lhaviyani", listOf("lhaviyani")),
        Atoll("K", "Kaafu (Malé)", listOf("kaafu", "malé city", "male' city", "male city", "malé", "male'")),
        Atoll("AA", "Alif Alif", listOf("alifu alifu", "alif alif")),
        Atoll("ADh", "Alif Dhaal", listOf("alifu dhaalu", "alif dhaalu", "alif dhaal")),
        Atoll("V", "Vaavu", listOf("vaavu")),
        Atoll("M", "Meemu", listOf("meemu")),
        Atoll("F", "Faafu", listOf("faafu")),
        Atoll("Dh", "Dhaalu", listOf("dhaalu")),
        Atoll("Th", "Thaa", listOf("thaa")),
        Atoll("L", "Laamu", listOf("laamu")),
        Atoll("GA", "Gaafu Alif", listOf("gaafu alifu", "gaafu alif", "gaaf alif")),
        Atoll("GDh", "Gaafu Dhaalu", listOf("gaafu dhaalu", "gaafu dhaal", "gaaf dhaal")),
        Atoll("Fv", "Fuvahmulah", listOf("fuvahmulah", "fuvammulah", "gnaviyani")),
        Atoll("S", "Addu", listOf("addu", "seenu")),
    )

    private val NORTH = 0..6
    private val CENTRAL = 7..15
    private val SOUTH = 16..19

    /**
     * Which atolls an alert's area text covers, e.g. "Baa Atoll to Dhaalu Atoll", "Fuvahmulah City to Addu City",
     * "Central Atolls" or "entire country". Empty when the text names nothing recognisable.
     */
    fun inArea(area: String): Set<Int> {
        val text = " " + area.lowercase().replace('’', '\'') + " "
        val out = HashSet<Int>()
        if (Regex("\\b(entire|whole|all (of )?the|all atolls|across the country|nationwide)\\b").containsMatchIn(text)) {
            return list.indices.toSet()
        }
        // Mentions in the order written. Longer names are found first and blanked out, so "dhaalu" inside
        // "haa dhaalu" isn't also read as Dhaalu Atoll.
        val masked = StringBuilder(text)
        val found = ArrayList<Pair<Int, Int>>()    // position, atoll
        val all = list.withIndex().flatMap { (i, a) -> a.spellings.map { it to i } }.sortedByDescending { it.first.length }
        for ((sp, i) in all) {
            val re = Regex("(?<![a-z])" + Regex.escape(sp.trim()) + "(?![a-z])")
            for (m in re.findAll(masked.toString())) {
                found += m.range.first to i
                for (k in m.range) masked.setCharAt(k, ' ')
            }
        }
        found.sortBy { it.first }
        for (k in found.indices) {
            val (pos, atoll) = found[k]
            out += atoll
            if (k + 1 < found.size) {
                val (nextPos, nextAtoll) = found[k + 1]
                val between = text.substring(pos, nextPos)
                if (Regex("\\bto\\b|–|—|\\bthrough\\b|\\bupto\\b").containsMatchIn(between)) {
                    for (a in minOf(atoll, nextAtoll)..maxOf(atoll, nextAtoll)) out += a
                }
            }
        }
        if (Regex("\\bnorth(ern)?\\b").containsMatchIn(text) && found.isEmpty()) out += NORTH
        if (Regex("\\bcentral\\b").containsMatchIn(text) && found.isEmpty()) out += CENTRAL
        if (Regex("\\bsouth(ern)?\\b").containsMatchIn(text) && found.isEmpty()) out += SOUTH
        return out
    }
}

class Airport(val icao: String, val name: String, val atoll: Int)

class Region(val name: String, val atolls: IntRange, val lat: Double, val lon: Double) {
    val airports: List<Airport> get() = Maldives.airports.filter { it.atoll in atolls }
    val atollNames: String get() = atolls.joinToString(", ") { Atolls.list[it].name }
}

object Maldives {
    val airports = listOf(
        Airport("VRAH", "Hoarafushi", 0),
        Airport("VRMH", "Hanimaadhoo", 1),
        Airport("VRBK", "Kulhudhuffushi", 1),
        Airport("VRCF", "Funadhoo", 2),
        Airport("VRDA", "Maafaru", 3),
        Airport("VREI", "Ifuru", 4),
        Airport("VRMD", "Dharavandhoo", 5),
        Airport("VRGD", "Madivaru", 6),
        Airport("VRMM", "Velana (Malé)", 7),
        Airport("VRMV", "Maamigili", 9),
        Airport("VRMU", "Dhaalu", 13),
        Airport("VRNT", "Thimarafushi", 14),
        Airport("VRKM", "Muli", 11),
        Airport("VRMK", "Kadhdhoo", 15),
        Airport("VRMO", "Kooddoo", 16),
        Airport("VRQM", "Maavarulu", 17),
        Airport("VRMT", "Kaadedhdhoo", 17),
        Airport("VRQF", "Faresmaathodaa", 17),
        Airport("VRMR", "Fuvahmulah", 18),
        Airport("VRMG", "Gan (Addu)", 19),
    )

    val regions = listOf(
        Region("North", 0..3, 6.60, 73.10),
        Region("North-central", 4..6, 5.45, 73.20),
        Region("Malé area", 7..10, 4.10, 73.45),
        Region("Central", 11..15, 2.50, 73.20),
        Region("South-central", 16..17, 0.50, 73.30),
        Region("South", 18..19, -0.45, 73.30),
    )
}
