package io.github.adccz.elias.domain.model

/** A point on Earth in decimal degrees. North and east are positive. */
data class GeoLocation(val latitude: Double, val longitude: Double) {
    companion object {
        /** Pilsen, Czech Republic. Elias's home. */
        val PILSEN = GeoLocation(latitude = 49.7475, longitude = 13.3776)
    }
}
