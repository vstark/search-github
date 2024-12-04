package com.sg.data.api

import com.sg.data.api.rest.Links
import com.sg.data.api.rest.RepoJson
import com.sg.data.api.rest.ReposJson
import com.sg.data.api.rest.UserJson
import com.sg.data.api.rest.ktorClient
import com.sg.data.model.ReposPage
import com.sg.data.model.User
import com.sg.data.model.toRepos
import com.sg.data.model.toUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parameters

class RemoteApiImpl(
    private val httpClient: HttpClient,
) : RemoteApi {

    override suspend fun searchForRepositories(
        token: String,
        query: String,
        perPage: Int,
    ): Result<ReposPage> =
        makeHttpRequest(
            token,
            "https://api.github.com/search/repositories",
            HttpMethod.Get,
            mapOf(
                "q" to query,
                "per_page" to perPage
            )
        ).map {
            prepareRepos(it)
        }

    override suspend fun loadPage(token: String, url: String): Result<ReposPage> =
        makeHttpRequest(token, url, HttpMethod.Get)
            .map {
                prepareRepos(it)
            }

    override suspend fun getUserInfo(token: String): Result<User> =
        makeHttpRequest(token, "https://api.github.com/user", HttpMethod.Get)
            .map {
                it.body<UserJson>()
                    .toUser()
            }

    override suspend fun getUserStarredRepos(token: String): Result<Map<Long, String>> =
        makeHttpRequest(token, "https://api.github.com/user/starred", HttpMethod.Get)
            .map { resp ->
                resp.body<List<RepoJson>>()
                    .associate {
                        it.id to it.nodeId
                    }
            }

    private suspend fun prepareRepos(httpResponse: HttpResponse): ReposPage {
        val links = parseLinks(httpResponse.headers[HttpHeaders.Link])
        val repos = httpResponse.body<ReposJson>().toRepos()
        val page = httpResponse.request.url.parameters["page"]?.toIntOrNull() ?: 1
        val pages = Url(links.last).parameters["page"]?.toIntOrNull() ?: 1
        return repos.copy(
            page = page,
            pages = pages,
            nextPageUrl = links.next,
            previousPageUrl = links.prev,
            hasNextPage = links.next.isNotEmpty(),
            hasPreviousPage = links.prev.isNotEmpty(),
        )
    }

    private fun parseLinks(links: String?): Links {
        return links?.let {
            val matchResult = "<(?<url>.+?)>;\\s*rel=\"(?<value>.+?)\"".toRegex().findAll(links)
            var linksTmp = Links()
            matchResult.forEach {
                val (url, rel) = it.destructured
                when (rel) {
                    "next" -> linksTmp = linksTmp.copy(next = url)
                    "prev" -> linksTmp = linksTmp.copy(prev = url)
                    "first" -> linksTmp = linksTmp.copy(first = url)
                    "last" -> linksTmp = linksTmp.copy(last = url)
                    else -> {}
                }
            }
            linksTmp
        } ?: Links()
    }

    private suspend fun makeHttpRequest(
        token: String,
        url: String,
        method: HttpMethod,
        params: Map<String, Any> = emptyMap()
    ): Result<HttpResponse> =
        runCatching {
            httpClient.prepareRequest(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-Github-Next-Global-ID", "1")
                }
                if (params.isNotEmpty()) {
                    parameters {
                        params.forEach { (key, value) ->
                            parameter(key, value)
                        }
                    }
                }
                this.method = method
            }.execute()
        }.fold(
            onSuccess = { httpResponse ->
                return@fold when {
                    httpResponse.status.isSuccess() -> {
                        Result.success(httpResponse)
                    }

                    httpResponse.status == HttpStatusCode.Forbidden -> {
                        Result.failure(Exception("Invalid token."))
                    }

                    else -> {
                        Result.failure(Exception("Unknown error."))
                    }
                }
            },
            onFailure = {
                Result.failure(it)
            }
        )

    companion object {
        val instance by lazy { RemoteApiImpl(ktorClient) }
    }
}