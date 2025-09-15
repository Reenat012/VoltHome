package ru.mugalimov.volthome.data.remote.secure

data class JwtStore(
    val token: String = "",
    val expiryEpochSec: Long = 0L
) {
    val isEmpty: Boolean get() = token.isBlank()
}
