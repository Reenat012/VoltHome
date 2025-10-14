package ru.mugalimov.volthome.data.remote.auth

sealed class AuthError {
    object Cancelled : AuthError()
    object Connection : AuthError()
    object Security : AuthError()
    object OAuthTokenInvalid : AuthError()
    object JwtAuthorization : AuthError()
    data class Other(val message: String?) : AuthError()
}