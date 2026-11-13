package de.robnice.homeshoplist.wear.model

/** Display name of a todo entity, same rules as the phone app ("todo.einkaufsliste" -> "Einkaufsliste"). */
fun todoEntityTitle(entity: String): String {
    val rawName = entity.substringAfter("todo.", entity)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
    if (rawName.isBlank()) return "Shopping List"
    return rawName
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
}
