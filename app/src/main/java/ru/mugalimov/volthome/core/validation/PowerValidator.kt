package ru.mugalimov.volthome.core.validation

object PowerValidator {
    fun validatePower(value: Int): Boolean {
        return value in InputConstraints.MIN_POWER_W..InputConstraints.MAX_POWER_W
    }
    fun errorMessage(value: Int): String? {
        return when {
            value < InputConstraints.MIN_POWER_W ->
                "Минимум ${InputConstraints.MIN_POWER_W} Вт"
            value > InputConstraints.MAX_POWER_W ->
                "Максимум ${InputConstraints.MAX_POWER_W} Вт"
            else -> null
        }
    }
}