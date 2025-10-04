package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchResponse
import ru.mugalimov.volthome.data.remote.dto.ProjectDeltaResponse
import ru.mugalimov.volthome.data.remote.dto.ProjectShortDto
import ru.mugalimov.volthome.data.remote.dto.ProjectTreeDto

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

    /**
     * Основной актуальный эндпоинт: частичное обновление метаданных.
     */
    @PATCH("/v1/projects/{id}/meta")
    suspend fun updateProjectMetaPatch(
        @Path("id") id: String,
        @Body body: UpdateProjectRequest
    ): ProjectShortDto

    /**
     * Фолбэк №1: если сервер ожидает PUT на /meta.
     */
    @PUT("/v1/projects/{id}/meta")
    suspend fun updateProjectMetaPutLegacy(
        @Path("id") id: String,
        @Body body: UpdateProjectRequest
    ): ProjectShortDto

    /**
     * Фолбэк №2: если сервер принимает обновление по корню /v1/projects/{id}.
     */
    @PUT("/v1/projects/{id}")
    suspend fun updateProjectPutRootLegacy(
        @Path("id") id: String,
        @Body body: UpdateProjectRequest
    ): ProjectShortDto

    @DELETE("/v1/projects/{id}")
    suspend fun deleteProject(
        @Path("id") id: String
    ): ProjectShortDto

    // delta
    @GET("/v1/projects/{id}/delta")
    suspend fun getDelta(
        @Path("id") id: String,
        @Query("since") since: String
    ): ProjectDeltaResponse

    // snapshot дерева проекта
    @GET("/v1/projects/{id}")
    suspend fun getProjectTree(
        @Path("id") id: String
    ): ProjectTreeDto

    @POST("/v1/projects/{id}/batch")
    suspend fun applyBatch(
        @Path("id") id: String,
        @Body body: ProjectBatchRequest
    ): ProjectBatchResponse
}