package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchResponse
import ru.mugalimov.volthome.data.remote.dto.ProjectDeltaResponse
import ru.mugalimov.volthome.data.remote.dto.ProjectShortDto

/**
 * Контракты соответствуют серверу (routes/projects.js):
 *
 *  GET    /v1/projects?since=&limit=
 *  POST   /v1/projects                  { id?, name, note? }
 *  GET    /v1/projects/{id}
 *  PUT    /v1/projects/{id}             { name?, note? }
 *  DELETE /v1/projects/{id}
 *  GET    /v1/projects/{id}/delta?since=
 *  POST   /v1/projects/{id}/batch       { baseVersion?, ops{...} }
 */

data class ProjectsListResponse(
    val items: List<ProjectShortDto>,
    val next: String?
)

data class CreateProjectRequest(
    val id: String? = null,
    val name: String,
    val note: String? = null
)

interface ProjectsApi {

    @GET("/v1/projects")
    suspend fun listProjects(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int = 50
    ): ProjectsListResponse

    @POST("/v1/projects")
    suspend fun createProject(
        @Body body: CreateProjectRequest
    ): ProjectShortDto

    data class UpdateProjectRequest(
        val name: String? = null,
        val note: String? = null
    )

    @PUT("/v1/projects/{id}")
    suspend fun updateProjectMeta(
        @Path("id") id: String,
        @Body body: UpdateProjectRequest
    ): ProjectShortDto

    @DELETE("/v1/projects/{id}")
    suspend fun deleteProject(
        @Path("id") id: String
    ): ProjectShortDto

    // Название метода — "delta", как в твоём SyncManager
    @GET("/v1/projects/{id}/delta")
    suspend fun delta(
        @Path("id") id: String,
        @Query("since") since: String
    ): ProjectDeltaResponse

    // Оставляю и альтернативное имя (на будущее), один и тот же эндпоинт
    @GET("/v1/projects/{id}/delta")
    suspend fun getDelta(
        @Path("id") id: String,
        @Query("since") since: String
    ): ProjectDeltaResponse

    @POST("/v1/projects/{id}/batch")
    suspend fun applyBatch(
        @Path("id") id: String,
        @Body body: ProjectBatchRequest
    ): ProjectBatchResponse
}