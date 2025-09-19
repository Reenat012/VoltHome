package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.*
import retrofit2.Response
import ru.mugalimov.volthome.data.remote.dto.*

interface ProjectsApi {

    // GET /v1/projects?since=&limit=
    @GET("v1/projects")
    suspend fun listProjects(
        @Query("since") since: String?,
        @Query("limit") limit: Int = 50
    ): ProjectsListResponse

    // POST /v1/projects
    @POST("v1/projects")
    suspend fun createProject(@Body req: ProjectCreateRequest): ProjectShortDto

    // GET /v1/projects/{id}
    @GET("v1/projects/{id}")
    suspend fun getProject(@Path("id") id: String): ProjectTreeDto

    // PUT /v1/projects/{id}
    @PUT("v1/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: String,
        @Body req: ProjectUpdateRequest
    ): ProjectShortDto

    // DELETE /v1/projects/{id}
    @DELETE("v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String): ProjectShortDto

    // GET /v1/projects/{id}/delta?since=
    @GET("v1/projects/{id}/delta")
    suspend fun getDelta(
        @Path("id") id: String,
        @Query("since") since: String
    ): DeltaResponse

    // POST /v1/projects/{id}/batch
    @POST("v1/projects/{id}/batch")
    suspend fun applyBatch(
        @Path("id") id: String,
        @Body req: BatchRequest
    ): BatchResponse
}