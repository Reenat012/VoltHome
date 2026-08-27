package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.DemoProjectSpec

/** Открывает настоящий, но безопасно пересоздаваемый демонстрационный проект. */
class OpenDemoProjectUseCase @Inject constructor(
    private val projectsRepository: ProjectsRepository,
    private val roomRepository: RoomRepository,
) {
    suspend operator fun invoke(): Result<String> = runCatching {
        projectsRepository.listProjects().first()
            .filter { DemoProjectSpec.isDemo(it.note) }
            .forEach { projectsRepository.deleteProject(it.id) }

        val projectId = projectsRepository.createProject(
            name = DemoProjectSpec.PROJECT_NAME,
            note = DemoProjectSpec.NOTE_MARKER
        )

        try {
            DemoProjectSpec.rooms.forEachIndexed { index, room ->
                roomRepository.addRoomWithDevices(
                    req = room,
                    opId = "demo-$projectId-$index"
                )
            }
            projectsRepository.openProject(projectId)
            projectId
        } catch (error: Throwable) {
            projectsRepository.deleteProject(projectId)
            throw error
        }
    }
}
