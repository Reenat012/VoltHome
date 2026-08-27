package ru.mugalimov.volthome.domain.model.create

sealed interface AutoCalculationResult {
    data class Success(
        val linesCount: Int,
        val installedPowerW: Double,
        val calculatedPowerW: Double
    ) : AutoCalculationResult

    data class Skipped(val reason: String) : AutoCalculationResult
    data class Failure(val message: String) : AutoCalculationResult
}

data class BatchRoomCreationResult(
    val rooms: List<CreatedRoomResult>,
    val calculation: AutoCalculationResult
)
