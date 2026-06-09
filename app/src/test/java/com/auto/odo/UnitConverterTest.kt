package com.auto.odo

import com.auto.odo.core.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test
    fun milesRoundTrip_keepsDistanceStable() {
        val miles = 12345.6
        val km = UnitConverter.milesToKm(miles)
        assertEquals(miles, UnitConverter.kmToMiles(km), 0.001)
    }

    @Test
    fun gallonsRoundTrip_keepsFuelQuantityStable() {
        val gallons = 12.75
        val liters = UnitConverter.gallonsToLiters(gallons)
        assertEquals(gallons, UnitConverter.litersToGallons(liters), 0.001)
    }

    @Test
    fun mpgCalculation_usesCanonicalKilometersAndLiters() {
        val km = 160.9344
        val liters = 3.78541
        assertEquals(100.0, UnitConverter.calculateMpg(km, liters), 0.01)
    }
}
