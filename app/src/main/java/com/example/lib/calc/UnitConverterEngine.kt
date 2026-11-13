package com.example.lib.calc

import kotlin.math.abs

enum class UnitDomain(val label: String) {
    LENGTH("Length"),
    MASS("Mass"),
    TEMPERATURE("Temp"),
    AREA("Area & Land"),
    DATA("Data (SI/IEC)"),
    SPEED("Speed"),
    TIME("Time")
}

data class UnitDef(
    val id: String,
    val label: String,
    val symbol: String,
    val factorToBase: Double
)

object UnitConverterEngine {
    val UNITS_BY_DOMAIN: Map<UnitDomain, List<UnitDef>> = mapOf(
        UnitDomain.LENGTH to listOf(
            UnitDef("m", "Meter", "m", 1.0),
            UnitDef("km", "Kilometer", "km", 1000.0),
            UnitDef("cm", "Centimeter", "cm", 0.01),
            UnitDef("mm", "Millimeter", "mm", 0.001),
            UnitDef("mi", "Mile", "mi", 1609.344),
            UnitDef("yd", "Yard", "yd", 0.9144),
            UnitDef("ft", "Foot", "ft", 0.3048),
            UnitDef("in", "Inch", "in", 0.0254),
            UnitDef("nm", "Nautical Mile", "nmi", 1852.0)
        ),
        UnitDomain.MASS to listOf(
            UnitDef("kg", "Kilogram", "kg", 1.0),
            UnitDef("g", "Gram", "g", 0.001),
            UnitDef("mg", "Milligram", "mg", 0.000001),
            UnitDef("lb", "Pound", "lb", 0.45359237),
            UnitDef("oz", "Ounce", "oz", 0.028349523125),
            UnitDef("t", "Metric Tonne", "t", 1000.0)
        ),
        UnitDomain.TEMPERATURE to listOf(
            UnitDef("c", "Celsius", "°C", 1.0),
            UnitDef("f", "Fahrenheit", "°F", 1.0),
            UnitDef("k", "Kelvin", "K", 1.0)
        ),
        UnitDomain.AREA to listOf(
            UnitDef("sqm", "Square Meter", "m²", 1.0),
            UnitDef("sqft", "Square Foot", "ft²", 0.09290304),
            UnitDef("gaj", "Square Yard / Gaj", "gaj", 0.83612736),
            UnitDef("acre", "Acre", "ac", 4046.8564224),
            UnitDef("ha", "Hectare", "ha", 10000.0),
            UnitDef("bigha", "Pucca Bigha", "bigha", 2529.285264),
            UnitDef("kanal", "Kanal", "kanal", 505.857),
            UnitDef("marla", "Marla", "marla", 25.29285),
            UnitDef("guntha", "Guntha", "guntha", 101.1714)
        ),
        UnitDomain.DATA to listOf(
            UnitDef("b", "Byte", "B", 1.0),
            UnitDef("kb", "Kilobyte (10³)", "KB", 1000.0),
            UnitDef("mb", "Megabyte (10⁶)", "MB", 1_000_000.0),
            UnitDef("gb", "Gigabyte (10⁹)", "GB", 1_000_000_000.0),
            UnitDef("kib", "Kibibyte (2¹⁰)", "KiB", 1024.0),
            UnitDef("mib", "Mebibyte (2²⁰)", "MiB", 1_048_576.0),
            UnitDef("gib", "Gibibyte (2³⁰)", "GiB", 1_073_741_824.0),
            UnitDef("bit", "Bit", "b", 0.125)
        ),
        UnitDomain.SPEED to listOf(
            UnitDef("mps", "Meters / sec", "m/s", 1.0),
            UnitDef("kmh", "Kilometers / hr", "km/h", 1.0 / 3.6),
            UnitDef("mph", "Miles / hr", "mph", 0.44704),
            UnitDef("kn", "Knots", "kn", 0.514444444)
        ),
        UnitDomain.TIME to listOf(
            UnitDef("s", "Second", "s", 1.0),
            UnitDef("min", "Minute", "min", 60.0),
            UnitDef("hr", "Hour", "hr", 3600.0),
            UnitDef("day", "Day", "d", 86400.0),
            UnitDef("wk", "Week", "wk", 604800.0),
            UnitDef("ms", "Millisecond", "ms", 0.001)
        )
    )

    fun convert(domain: UnitDomain, value: Double, fromId: String, toId: String): Double {
        if (fromId == toId) return value
        if (domain == UnitDomain.TEMPERATURE) {
            val celsius = when (fromId) {
                "c" -> value
                "f" -> (value - 32.0) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> value
            }
            return when (toId) {
                "c" -> celsius
                "f" -> celsius * 9.0 / 5.0 + 32.0
                "k" -> celsius + 273.15
                else -> celsius
            }
        }
        val list = UNITS_BY_DOMAIN[domain].orEmpty()
        val from = list.firstOrNull { it.id == fromId } ?: return value
        val to = list.firstOrNull { it.id == toId } ?: return value
        val baseValue = value * from.factorToBase
        return baseValue / to.factorToBase
    }

    fun formatValue(v: Double): String {
        if (abs(v) < 1e-12) return "0"
        return if (abs(v) >= 1_000_000_000 || (abs(v) < 0.0001 && v != 0.0)) {
            String.format(java.util.Locale.US, "%.4e", v)
        } else {
            ExpressionEvaluator.formatNumberClean(v)
        }
    }
}
