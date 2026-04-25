package de.robnice.homeshoplist.util

fun normalizeHaUrl(input: String): String {

    var url = input.trim()

    if (url.isBlank()) return ""

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }

    url = url.trimEnd('/')

    return "$url/"
}
