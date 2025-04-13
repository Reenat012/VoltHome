package ru.mugalimov.volthome.domain.model

data class ElectricalGroup(
    val roomName: String,
    val lightGroups: List<SubGroup>,
    val socketGroups: List<SubGroup>
)