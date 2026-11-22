package de.robnice.homeshoplist.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.robnice.homeshoplist.R

enum class ShoppingArea(
    val key: String,
    val emoji: String
) {
    PRODUCE("produce", "\uD83E\uDD66"),
    BAKERY("bakery", "\uD83E\uDD56"),
    MEAT("meat", "\uD83E\uDD69"),
    FISH_SEAFOOD("fish_seafood", "\uD83D\uDC1F"),
    DAIRY_EGGS("dairy_eggs", "\uD83E\uDD5B"),
    CHEESE_DELI("cheese_deli", "\uD83E\uDDC0"),
    GROCERIES("groceries", "\uD83D\uDED2"),
    MILK("milk", "\uD83E\uDD5B"),
    EGGS("eggs", "\uD83E\uDD5A"),
    COLD_CUTS("cold_cuts", "\uD83E\uDD53"),
    REFRIGERATED_CASES("refrigerated_cases", "\uD83E\uDDCA"),
    BUTCHER_COUNTER("butcher_counter", "\uD83D\uDD2A"),
    COSMETICS("cosmetics", "\uD83D\uDC84"),
    SKIN_CARE("skin_care", "\uD83E\uDDF4"),
    HAIR_CARE("hair_care", "\uD83D\uDC87"),
    ORAL_CARE("oral_care", "\uD83E\uDEA5"),
    BODY_CARE("body_care", "\uD83E\uDDFC"),
    FRAGRANCES("fragrances", "\uD83C\uDF38"),
    FEMININE_HYGIENE("feminine_hygiene", "\uD83C\uDF37"),
    SHAVING("shaving", "\uD83E\uDE92"),
    LAUNDRY("laundry", "\uD83E\uDDFA"),
    HYGIENE("hygiene", "\uD83E\uDDF4"),
    SUPPLEMENTS("supplements", "\uD83D\uDC8A"),
    NATURAL_COSMETICS("natural_cosmetics", "\uD83C\uDF3F"),
    PHOTO("photo", "\uD83D\uDCF8"),
    TRAVEL_SIZES("travel_sizes", "\uD83E\uDDF3"),
    TOOLS("tools", "\uD83D\uDD27"),
    POWER_TOOLS("power_tools", "\uD83D\uDEE0\uFE0F"),
    HARDWARE("hardware", "\u2699\uFE0F"),
    SCREWS_FASTENERS("screws_fasteners", "\uD83D\uDD29"),
    BUILDING_MATERIALS("building_materials", "\uD83E\uDDF1"),
    WOOD("wood", "\uD83E\uDEB5"),
    PAINT_VARNISH("paint_varnish", "\uD83C\uDFA8"),
    PAINTING_SUPPLIES("painting_supplies", "\uD83D\uDD8C\uFE0F"),
    ELECTRICAL("electrical", "\uD83D\uDD0C"),
    LIGHTING("lighting", "\uD83D\uDCA1"),
    PLUMBING("plumbing", "\uD83D\uDEB0"),
    BATHROOM("bathroom", "\uD83D\uDEC1"),
    HEATING("heating", "\uD83C\uDF21\uFE0F"),
    FLOORING("flooring", "\uD83E\uDEB5"),
    TILES("tiles", "\u25FB\uFE0F"),
    DOORS_WINDOWS("doors_windows", "\uD83D\uDEAA"),
    INSULATION("insulation", "\uD83C\uDFE0"),
    ADHESIVES_SEALANTS("adhesives_sealants", "\uD83E\uDDF4"),
    WORKWEAR("workwear", "\uD83E\uDDBA"),
    STORAGE_SHELVING("storage_shelving", "\uD83D\uDDC4\uFE0F"),
    CAR_ACCESSORIES("car_accessories", "\uD83D\uDE97"),
    MACHINERY("machinery", "\u2699\uFE0F"),
    INDOOR_PLANTS("indoor_plants", "\uD83E\uDEB4"),
    OUTDOOR_PLANTS("outdoor_plants", "\uD83C\uDF31"),
    FLOWERS("flowers", "\uD83D\uDC90"),
    SEEDS_BULBS("seeds_bulbs", "\uD83C\uDF30"),
    SOIL_SUBSTRATES("soil_substrates", "\uD83E\uDEB4"),
    FERTILIZERS("fertilizers", "\uD83C\uDF3F"),
    PLANT_PROTECTION("plant_protection", "\uD83D\uDC1B"),
    PLANTERS_POTS("planters_pots", "\uD83C\uDFFA"),
    GARDEN_TOOLS("garden_tools", "\uD83E\uDE8F"),
    GARDEN_MACHINERY("garden_machinery", "\uD83D\uDE9C"),
    WATERING_IRRIGATION("watering_irrigation", "\uD83D\uDCA7"),
    GARDEN_FURNITURE("garden_furniture", "\uD83E\uDE91"),
    GARDEN_DECOR("garden_decor", "\uD83D\uDDFF"),
    GRILLS_BBQ("grills_bbq", "\u2668\uFE0F"),
    PONDS_WATER_FEATURES("ponds_water_features", "\u26F2"),
    GARDEN_BUILDINGS("garden_buildings", "\uD83C\uDFE1"),
    LAWN_CARE("lawn_care", "\uD83C\uDF31"),
    HARVEST_GROWING("harvest_growing", "\uD83E\uDD55"),
    WATER("water", "\uD83D\uDCA7"),
    SOFT_DRINKS("soft_drinks", "\uD83E\uDD64"),
    JUICES("juices", "\uD83E\uDDC3"),
    BEER("beer", "\uD83C\uDF7A"),
    WINE("wine", "\uD83C\uDF77"),
    SPIRITS("spirits", "\uD83E\uDD43"),
    SPARKLING_WINE("sparkling_wine", "\uD83E\uDD42"),
    ENERGY_DRINKS("energy_drinks", "\u26A1"),
    SPORTS_DRINKS("sports_drinks", "\uD83C\uDFC3"),
    NON_ALCOHOLIC("non_alcoholic", "\uD83C\uDF79"),
    SYRUPS("syrups", "\uD83C\uDF6F"),
    MIXERS("mixers", "\uD83C\uDF78"),
    CRATES_DEPOSITS("crates_deposits", "\u267B\uFE0F"),
    PARTY_SUPPLIES("party_supplies", "\uD83C\uDF89"),
    ICE_CUBES("ice_cubes", "\uD83E\uDDCA"),
    AREA_1("area_1", "1\uFE0F\u20E3"),
    AREA_2("area_2", "2\uFE0F\u20E3"),
    AREA_3("area_3", "3\uFE0F\u20E3"),
    AREA_4("area_4", "4\uFE0F\u20E3"),
    AREA_5("area_5", "5\uFE0F\u20E3"),
    AREA_6("area_6", "6\uFE0F\u20E3"),
    AREA_7("area_7", "7\uFE0F\u20E3"),
    AREA_8("area_8", "8\uFE0F\u20E3"),
    IMPULSE_BUY("impulse_buy", "\uD83C\uDF6C"),
    FROZEN("frozen", "\u2744\uFE0F"),
    DRY_GOODS("dry_goods", "\uD83C\uDF7E"),
    CANNED_JARS("canned_jars", "\uD83E\uDD6B"),
    SAUCES_SPICES("sauces_spices", "\uD83E\uDDC2"),
    BREAKFAST("breakfast", "\uD83E\uDD63"),
    SNACKS_SWEETS("snacks_sweets", "\uD83C\uDF6B"),
    DRINKS("drinks", "\uD83E\uDDC3"),
    ALCOHOL("alcohol", "\uD83C\uDF77"),
    COFFEE_TEA("coffee_tea", "\u2615"),
    HOUSEHOLD("household", "\uD83C\uDFE0"),
    CLEANING("cleaning", "\uD83E\uDDFD"),
    PAPER_GOODS("paper_goods", "\uD83E\uDDFB"),
    PERSONAL_CARE("personal_care", "\uD83E\uDDD4"),
    BABY("baby", "\uD83C\uDF7C"),
    PET("pet", "\uD83D\uDC3E"),
    HEALTH("health", "\uD83D\uDC8A"),
    NON_FOOD("non_food", "\uD83D\uDED2"),
    OTHER("other", "\u2754");

    companion object {
        fun fromKey(key: String?): ShoppingArea? {
            val normalizedKey = key
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            return entries.firstOrNull { it.key == normalizedKey }
                ?: entries.firstOrNull { it.name.lowercase() == normalizedKey }
        }

        fun orderedFromStorage(storedOrder: String?): List<ShoppingArea> {
            val storedAreas = storedOrder
                .orEmpty()
                .split(',')
                .mapNotNull { fromKey(it.trim()) }
                .distinct()

            if (storedAreas.isEmpty()) {
                return entries.toList()
            }

            return storedAreas + entries.filterNot { it in storedAreas }
        }

        /**
         * Specialised categories (drugstore, DIY store, garden centre, drinks store) are off unless the
         * user turns them on in a store, so the category choice stays short for a normal supermarket.
         */
        val disabledByDefault: Set<ShoppingArea> = setOf(
            COSMETICS,
            SKIN_CARE,
            HAIR_CARE,
            ORAL_CARE,
            BODY_CARE,
            FRAGRANCES,
            FEMININE_HYGIENE,
            SHAVING,
            LAUNDRY,
            HYGIENE,
            SUPPLEMENTS,
            NATURAL_COSMETICS,
            PHOTO,
            TRAVEL_SIZES,
            TOOLS,
            POWER_TOOLS,
            HARDWARE,
            SCREWS_FASTENERS,
            BUILDING_MATERIALS,
            WOOD,
            PAINT_VARNISH,
            PAINTING_SUPPLIES,
            ELECTRICAL,
            LIGHTING,
            PLUMBING,
            BATHROOM,
            HEATING,
            FLOORING,
            TILES,
            DOORS_WINDOWS,
            INSULATION,
            ADHESIVES_SEALANTS,
            WORKWEAR,
            STORAGE_SHELVING,
            CAR_ACCESSORIES,
            MACHINERY,
            INDOOR_PLANTS,
            OUTDOOR_PLANTS,
            FLOWERS,
            SEEDS_BULBS,
            SOIL_SUBSTRATES,
            FERTILIZERS,
            PLANT_PROTECTION,
            PLANTERS_POTS,
            GARDEN_TOOLS,
            GARDEN_MACHINERY,
            WATERING_IRRIGATION,
            GARDEN_FURNITURE,
            GARDEN_DECOR,
            GRILLS_BBQ,
            PONDS_WATER_FEATURES,
            GARDEN_BUILDINGS,
            LAWN_CARE,
            HARVEST_GROWING,
            WATER,
            SOFT_DRINKS,
            JUICES,
            BEER,
            WINE,
            SPIRITS,
            SPARKLING_WINE,
            ENERGY_DRINKS,
            SPORTS_DRINKS,
            NON_ALCOHOLIC,
            SYRUPS,
            MIXERS,
            CRATES_DEPOSITS,
            PARTY_SUPPLIES,
            ICE_CUBES
        )

        /** Categories enabled in a new store or after "reset". */
        val enabledByDefault: List<ShoppingArea>
            get() = entries.filterNot { it in disabledByDefault }

        fun enabledFromStorage(
            storedEnabled: String?,
            orderedAreas: List<ShoppingArea> = entries.toList()
        ): List<ShoppingArea> {
            val enabledSet = storedEnabled
                .orEmpty()
                .split(',')
                .mapNotNull { fromKey(it.trim()) }
                .toSet()

            if (enabledSet.isEmpty()) {
                return orderedAreas.filterNot { it in disabledByDefault }
            }

            return orderedAreas.filter { it in enabledSet }
        }

        fun serializeOrder(areas: List<ShoppingArea>): String {
            return areas.joinToString(",") { it.key }
        }

        fun serializeEnabledAreas(areas: List<ShoppingArea>): String {
            return areas.joinToString(",") { it.key }
        }
    }
}

@Composable
fun ShoppingArea.label(): String = stringResource(labelRes())

/** String resource of the category name; also usable outside of Compose (e.g. for the watch). */
fun ShoppingArea.labelRes(): Int {
    return when (this) {
        ShoppingArea.PRODUCE -> R.string.area_produce
        ShoppingArea.BAKERY -> R.string.area_bakery
        ShoppingArea.MEAT -> R.string.area_meat
        ShoppingArea.FISH_SEAFOOD -> R.string.area_fish_seafood
        ShoppingArea.DAIRY_EGGS -> R.string.area_dairy_eggs
        ShoppingArea.CHEESE_DELI -> R.string.area_cheese_deli
        ShoppingArea.GROCERIES -> R.string.area_groceries
        ShoppingArea.MILK -> R.string.area_milk
        ShoppingArea.EGGS -> R.string.area_eggs
        ShoppingArea.COLD_CUTS -> R.string.area_cold_cuts
        ShoppingArea.REFRIGERATED_CASES -> R.string.area_refrigerated_cases
        ShoppingArea.BUTCHER_COUNTER -> R.string.area_butcher_counter
        ShoppingArea.IMPULSE_BUY -> R.string.area_impulse_buy
        ShoppingArea.FROZEN -> R.string.area_frozen
        ShoppingArea.DRY_GOODS -> R.string.area_dry_goods
        ShoppingArea.CANNED_JARS -> R.string.area_canned_jars
        ShoppingArea.SAUCES_SPICES -> R.string.area_sauces_spices
        ShoppingArea.BREAKFAST -> R.string.area_breakfast
        ShoppingArea.SNACKS_SWEETS -> R.string.area_snacks_sweets
        ShoppingArea.DRINKS -> R.string.area_drinks
        ShoppingArea.ALCOHOL -> R.string.area_alcohol
        ShoppingArea.COFFEE_TEA -> R.string.area_coffee_tea
        ShoppingArea.HOUSEHOLD -> R.string.area_household
        ShoppingArea.CLEANING -> R.string.area_cleaning
        ShoppingArea.PAPER_GOODS -> R.string.area_paper_goods
        ShoppingArea.PERSONAL_CARE -> R.string.area_personal_care
        ShoppingArea.BABY -> R.string.area_baby
        ShoppingArea.PET -> R.string.area_pet
        ShoppingArea.HEALTH -> R.string.area_health
        ShoppingArea.NON_FOOD -> R.string.area_non_food
        ShoppingArea.COSMETICS -> R.string.area_cosmetics
        ShoppingArea.SKIN_CARE -> R.string.area_skin_care
        ShoppingArea.HAIR_CARE -> R.string.area_hair_care
        ShoppingArea.ORAL_CARE -> R.string.area_oral_care
        ShoppingArea.BODY_CARE -> R.string.area_body_care
        ShoppingArea.FRAGRANCES -> R.string.area_fragrances
        ShoppingArea.FEMININE_HYGIENE -> R.string.area_feminine_hygiene
        ShoppingArea.SHAVING -> R.string.area_shaving
        ShoppingArea.LAUNDRY -> R.string.area_laundry
        ShoppingArea.HYGIENE -> R.string.area_hygiene
        ShoppingArea.SUPPLEMENTS -> R.string.area_supplements
        ShoppingArea.NATURAL_COSMETICS -> R.string.area_natural_cosmetics
        ShoppingArea.PHOTO -> R.string.area_photo
        ShoppingArea.TRAVEL_SIZES -> R.string.area_travel_sizes
        ShoppingArea.TOOLS -> R.string.area_tools
        ShoppingArea.POWER_TOOLS -> R.string.area_power_tools
        ShoppingArea.HARDWARE -> R.string.area_hardware
        ShoppingArea.SCREWS_FASTENERS -> R.string.area_screws_fasteners
        ShoppingArea.BUILDING_MATERIALS -> R.string.area_building_materials
        ShoppingArea.WOOD -> R.string.area_wood
        ShoppingArea.PAINT_VARNISH -> R.string.area_paint_varnish
        ShoppingArea.PAINTING_SUPPLIES -> R.string.area_painting_supplies
        ShoppingArea.ELECTRICAL -> R.string.area_electrical
        ShoppingArea.LIGHTING -> R.string.area_lighting
        ShoppingArea.PLUMBING -> R.string.area_plumbing
        ShoppingArea.BATHROOM -> R.string.area_bathroom
        ShoppingArea.HEATING -> R.string.area_heating
        ShoppingArea.FLOORING -> R.string.area_flooring
        ShoppingArea.TILES -> R.string.area_tiles
        ShoppingArea.DOORS_WINDOWS -> R.string.area_doors_windows
        ShoppingArea.INSULATION -> R.string.area_insulation
        ShoppingArea.ADHESIVES_SEALANTS -> R.string.area_adhesives_sealants
        ShoppingArea.WORKWEAR -> R.string.area_workwear
        ShoppingArea.STORAGE_SHELVING -> R.string.area_storage_shelving
        ShoppingArea.CAR_ACCESSORIES -> R.string.area_car_accessories
        ShoppingArea.MACHINERY -> R.string.area_machinery
        ShoppingArea.INDOOR_PLANTS -> R.string.area_indoor_plants
        ShoppingArea.OUTDOOR_PLANTS -> R.string.area_outdoor_plants
        ShoppingArea.FLOWERS -> R.string.area_flowers
        ShoppingArea.SEEDS_BULBS -> R.string.area_seeds_bulbs
        ShoppingArea.SOIL_SUBSTRATES -> R.string.area_soil_substrates
        ShoppingArea.FERTILIZERS -> R.string.area_fertilizers
        ShoppingArea.PLANT_PROTECTION -> R.string.area_plant_protection
        ShoppingArea.PLANTERS_POTS -> R.string.area_planters_pots
        ShoppingArea.GARDEN_TOOLS -> R.string.area_garden_tools
        ShoppingArea.GARDEN_MACHINERY -> R.string.area_garden_machinery
        ShoppingArea.WATERING_IRRIGATION -> R.string.area_watering_irrigation
        ShoppingArea.GARDEN_FURNITURE -> R.string.area_garden_furniture
        ShoppingArea.GARDEN_DECOR -> R.string.area_garden_decor
        ShoppingArea.GRILLS_BBQ -> R.string.area_grills_bbq
        ShoppingArea.PONDS_WATER_FEATURES -> R.string.area_ponds_water_features
        ShoppingArea.GARDEN_BUILDINGS -> R.string.area_garden_buildings
        ShoppingArea.LAWN_CARE -> R.string.area_lawn_care
        ShoppingArea.HARVEST_GROWING -> R.string.area_harvest_growing
        ShoppingArea.WATER -> R.string.area_water
        ShoppingArea.SOFT_DRINKS -> R.string.area_soft_drinks
        ShoppingArea.JUICES -> R.string.area_juices
        ShoppingArea.BEER -> R.string.area_beer
        ShoppingArea.WINE -> R.string.area_wine
        ShoppingArea.SPIRITS -> R.string.area_spirits
        ShoppingArea.SPARKLING_WINE -> R.string.area_sparkling_wine
        ShoppingArea.ENERGY_DRINKS -> R.string.area_energy_drinks
        ShoppingArea.SPORTS_DRINKS -> R.string.area_sports_drinks
        ShoppingArea.NON_ALCOHOLIC -> R.string.area_non_alcoholic
        ShoppingArea.SYRUPS -> R.string.area_syrups
        ShoppingArea.MIXERS -> R.string.area_mixers
        ShoppingArea.CRATES_DEPOSITS -> R.string.area_crates_deposits
        ShoppingArea.PARTY_SUPPLIES -> R.string.area_party_supplies
        ShoppingArea.ICE_CUBES -> R.string.area_ice_cubes
        ShoppingArea.OTHER -> R.string.area_other
        ShoppingArea.AREA_1 -> R.string.area_1
        ShoppingArea.AREA_2 -> R.string.area_2
        ShoppingArea.AREA_3 -> R.string.area_3
        ShoppingArea.AREA_4 -> R.string.area_4
        ShoppingArea.AREA_5 -> R.string.area_5
        ShoppingArea.AREA_6 -> R.string.area_6
        ShoppingArea.AREA_7 -> R.string.area_7
        ShoppingArea.AREA_8 -> R.string.area_8
    }
}
