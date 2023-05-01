package com.simiacryptus.skyenet

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import java.io.File

class GoogleSearch(
    val apiKey: String = File(File(System.getProperty("user.home")), "googlesearch.key").readText().trim(),
) {

    data class SearchResult(val title: String, val link: String, val snippet: String)

    fun search(query: String): List<SearchResult> {
        val searchEngineId = "c580d140ac62d42f6"
        val httpClient = OkHttpClient()
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("customsearch/v1")
            .addQueryParameter("key", apiKey)
            .addQueryParameter("cx", searchEngineId)
            .addQueryParameter("q", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        val response: Response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Request failed: ${response.message}")
        }

        val responseBody: ResponseBody = response.body ?: throw Exception("Response body is null")

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any>>

        val jsonResponse = jsonAdapter.fromJson(responseBody.string())
        val items = jsonResponse?.get("items") as? List<Map<String, Any>>

        return items?.map { item ->
            SearchResult(
                title = item["title"] as String,
                link = item["link"] as String,
                snippet = item["snippet"] as String
            )
        } ?: emptyList()
    }
}