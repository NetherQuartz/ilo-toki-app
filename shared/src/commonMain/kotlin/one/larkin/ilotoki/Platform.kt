package one.larkin.ilotoki

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform