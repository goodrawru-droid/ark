package ru.ark.radio

data class Station(val id: String, val name: String, val category: String, val url: String)

object StationCatalog {
    // 55 candidate entries, NOT 55 verified unique channels. Validate URLs before release.
    private val genres = listOf(
        Triple("pop", "Pop", "Поп"), Triple("rus", "Русская", "Русская музыка"),
        Triple("rnb", "R&B", "R&B"), Triple("rurock", "Русский рок", "Рок"),
        Triple("rock", "Rock", "Рок"), Triple("club", "Club", "Клубная"),
        Triple("disco", "Disco", "Ретро"), Triple("relax", "Relax", "Релакс"),
        Triple("shanson", "Шансон", "Шансон"), Triple("rap", "Rap", "Рэп")
    )
    val all: List<Station> = buildList {
        genres.forEach { (slug, title, cat) ->
            add(Station(slug, "Zaycev.FM $title", cat, "https://abs.zaycev.fm/${slug}256k"))
        }
        // Additional candidate host/bitrate variants; many may not be valid.
        genres.forEach { (slug, title, cat) ->
            add(Station("${slug}128", "Zaycev.FM $title 128", cat, "https://abs.zaycev.fm/${slug}128k"))
        }
        genres.forEach { (slug, title, cat) ->
            add(Station("${slug}stream", "Zaycev.FM $title Stream", cat, "https://stream.zaycev.fm/${slug}256k"))
        }
        genres.take(5).forEach { (slug, title, cat) ->
            add(Station("${slug}320", "Zaycev.FM $title 320", cat, "https://abs.zaycev.fm/${slug}320k"))
        }
        genres.take(5).forEach { (slug, title, cat) ->
            add(Station("${slug}web", "Zaycev.FM $title Web", cat, "https://zaycev.fm/$slug"))
        }
        // These are distinct catalogue rows, not guaranteed distinct/working streams.
        add(Station("pop-live", "Zaycev.FM Pop Live", "Поп", "https://radio.zaycev.fm/pop"))
        add(Station("rus-live", "Zaycev.FM Русская Live", "Русская музыка", "https://radio.zaycev.fm/rus"))
        add(Station("rock-live", "Zaycev.FM Rock Live", "Рок", "https://radio.zaycev.fm/rock"))
        add(Station("relax-live", "Zaycev.FM Relax Live", "Релакс", "https://radio.zaycev.fm/relax"))
        add(Station("rap-live", "Zaycev.FM Rap Live", "Рэп", "https://radio.zaycev.fm/rap"))
        add(Station("rnb-live", "Zaycev.FM R&B Live", "R&B", "https://radio.zaycev.fm/rnb"))
        add(Station("club-live", "Zaycev.FM Club Live", "Клубная", "https://radio.zaycev.fm/club"))
        add(Station("disco-live", "Zaycev.FM Disco Live", "Ретро", "https://radio.zaycev.fm/disco"))
        add(Station("shanson-live", "Zaycev.FM Шансон Live", "Шансон", "https://radio.zaycev.fm/shanson"))
        add(Station("rurock-live", "Zaycev.FM Русский рок Live", "Рок", "https://radio.zaycev.fm/rurock"))
        add(Station("pop-mobile", "Zaycev.FM Pop Mobile", "Поп", "https://m.zaycev.fm/pop"))
        add(Station("rus-mobile", "Zaycev.FM Русская Mobile", "Русская музыка", "https://m.zaycev.fm/rus"))
        add(Station("rock-mobile", "Zaycev.FM Rock Mobile", "Рок", "https://m.zaycev.fm/rock"))
        add(Station("club-mobile", "Zaycev.FM Club Mobile", "Клубная", "https://m.zaycev.fm/club"))
        add(Station("rap-mobile", "Zaycev.FM Rap Mobile", "Рэп", "https://m.zaycev.fm/rap"))
    }
}
