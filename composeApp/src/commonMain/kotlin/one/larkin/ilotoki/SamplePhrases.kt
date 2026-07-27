package one.larkin.ilotoki

import kotlin.random.Random

/**
 * Short toki pona phrases offered under the slab when there is nothing to translate.
 *
 * Written out here rather than fetched: the whole claim of the app is that nothing
 * leaves the phone and it works with the plane on, so a word list downloaded at
 * runtime would be the one thing contradicting it. A couple of dozen lines of text
 * is not worth a network call anyway.
 *
 * Every entry is a complete, grammatical sentence — `mi` and `sina` take no `li`,
 * everything else does — because they are also the first toki pona a new user reads.
 */
object SamplePhrases {

    private val phrases = listOf(
        "toki a",
        "mi wile e telo",
        "tomo li suli",
        "kili li suwi",
        "mi olin e sina",
        "jan li moku e kili",
        "soweli lili li lape lon tomo",
        "sina pona tawa mi",
        "mi pilin pona",
        "ma li pona",
        // Not "waso li tawa sewi": `sewi` is both "up/sky" and "divine", so the
        // obvious reading of the bird flying is shadowed by the bird going to heaven.
        "waso li kalama musi",
        "mi wile moku",
        "sina sona ala sona e toki pona",
        "jan pona mi li kama",
        "telo li lete",
        "mun li suli lon tenpo pimeja",
        "kasi li kama suli",
        "mi lape lon tomo mi",
        "ilo mi li pakala",
        "pipi lili li lon kasi",
        "seli li wawa",
        "mi pali e tomo",
        // Not "sina lukin e seme": neutral in toki pona, but the app translates it
        // into "what are you looking at?", which is a fight in English.
        "sina pali e seme",
        "tenpo suno ni li pona",
    )

    /**
     * [count] different phrases, shortest first so the row does not jump about in
     * width every time it is rerolled.
     */
    fun pick(count: Int, random: Random = Random): List<String> =
        phrases.shuffled(random).take(count).sortedBy { it.length }
}
