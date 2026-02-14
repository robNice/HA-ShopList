package de.robnice.homeasssistant_shoppinglist.util

fun normalizeHaUrl(input: String): String {

    var url = input.trim()

    if (url.isBlank()) return ""

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }

    if (!url.endsWith("/")) {
        url += "/"
    }

    return url
}
