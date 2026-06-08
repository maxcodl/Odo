package com.auto.odo.core

object UnitConverter {
    const val KILOMETERS_TO_MILES = 0.621371
    const val LITERS_TO_GALLONS = 0.264172

    fun kmToMiles(km: Double): Double = km * KILOMETERS_TO_MILES
    fun milesToKm(miles: Double): Double = miles / KILOMETERS_TO_MILES

    fun litersToGallons(liters: Double): Double = liters * LITERS_TO_GALLONS
    fun gallonsToLiters(gallons: Double): Double = gallons / LITERS_TO_GALLONS

    // Calculate efficiency
    fun calculateKmpl(km: Double, liters: Double): Double = if (liters > 0) km / liters else 0.0
    fun calculateMpg(km: Double, liters: Double): Double {
        val miles = kmToMiles(km)
        val gallons = litersToGallons(liters)
        return if (gallons > 0) miles / gallons else 0.0
    }
}
