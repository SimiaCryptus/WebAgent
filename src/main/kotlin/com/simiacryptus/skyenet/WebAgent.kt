@file:Suppress("unused")

package com.simiacryptus.skyenet

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.openai.proxy.Description
import com.simiacryptus.skyenet.heart.GroovyInterpreter
import com.simiacryptus.skyenet.util.AbbrevWhitelistYamlDescriber
import com.simiacryptus.skyenet.util.LoggingInterceptor
import com.simiacryptus.skyenet.body.SessionServerUtil.asJava
import com.simiacryptus.skyenet.body.SkyenetSessionServer
import java.awt.Desktop
import java.io.File
import java.net.URI

object WebAgent {

    class HttpUtil(
        private val googleSearchKey: String = File(File(System.getProperty("user.home")), "googlesearch.key").readText()
            .trim(),
        private val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
    ) {

        fun apacheClient() = org.apache.http.impl.client.HttpClients.createDefault()

        fun fetchHtmlPageText(url : String): String {
            val stringBuffer = StringBuffer()
            return LoggingInterceptor.withIntercept(stringBuffer, "com.gargoylesoftware", "c.g", "ROOT") {
                val webClient = WebClient()
                webClient.options.isJavaScriptEnabled = true
                webClient.options.isThrowExceptionOnScriptError = false
                webClient.options.isUseInsecureSSL = true
                webClient.addRequestHeader("User-Agent", userAgent)
                webClient.getPage<HtmlPage>(url)?.asNormalizedText() ?: "Error loading page"
            }
        }

        fun okHttp() = okhttp3.OkHttpClient()

        fun searchGoogle(query: String) = GoogleSearch(apiKey = googleSearchKey).search(query)

    }

    interface TextProcessor {

        fun summarize(text: String, words: Int = 100): String

        @Description("Use the text to answer the question.")
        fun query(
            question: String,
            text: String,
        ): String

        @Description("Edit the text according to the instruction.")
        fun edit(
            instruction: String,
            text: String,
        ): String

    }

    class MacroTextProcessor(
        private val inner: TextProcessor,
        private val minChars: Int,
        private val maxChars: Int,
    ) : TextProcessor {
        override fun summarize(text: String, words: Int): String {
            if (text.length < maxChars) {
                return inner.summarize(text, words)
            }

            // If text is longer than 4000 characters, break it into 4000 character chunks using word and sentence boundaries and summarize each chunk. Then, concatenate the results and summarize the result.
            val chunks =
                text.split("[\\.\\?\\!\\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

            // Aggregate chunks until we have a chunk that is at least 2000 characters long
            val currentBuffer = StringBuffer()
            val newChunks = mutableListOf<String>()
            while (chunks.size > 0) {
                currentBuffer.append(chunks.removeAt(0))
                if (currentBuffer.length > minChars || chunks.size == 0) {
                    newChunks.add(currentBuffer.toString())
                    currentBuffer.setLength(0)
                }
            }

            if (1 == newChunks.size) return inner.summarize(newChunks[0], words)
            val individualSummaries = newChunks.map { chunk ->
                inner.summarize(chunk, words)
            }
            return inner.summarize(individualSummaries.joinToString("\n"), words)
        }

        override fun query(question: String, text: String): String {
            if (text.length < maxChars) return inner.query(question, text)

            // If text is longer than 4000 characters, break it into 4000 character chunks using word and sentence boundaries and summarize each chunk. Then, concatenate the results and summarize the result.
            val chunks =
                text.split("[\\.\\?\\!\\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

            // Aggregate chunks until we have a chunk that is at least 2000 characters long
            val currentBuffer = StringBuffer()
            val newChunks = mutableListOf<String>()
            while (chunks.size > 0) {
                currentBuffer.append(chunks.removeAt(0))
                if (currentBuffer.length > minChars || chunks.size == 0) {
                    newChunks.add(currentBuffer.toString())
                    currentBuffer.setLength(0)
                }
            }

            if (1 == newChunks.size) return inner.query(question, newChunks[0])
            val individualAnswers = newChunks.map { chunk ->
                inner.query(question, chunk)
            }
            return inner.query(question, individualAnswers.joinToString("\n"))
        }

        override fun edit(instruction: String, text: String): String {
            if (text.length < maxChars) return inner.edit(instruction, text)

            // If text is longer than 4000 characters, break it into 4000 character chunks using word and sentence boundaries and summarize each chunk. Then, concatenate the results and summarize the result.
            val chunks =
                text.split("[\\.\\?\\!\\n]+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

            // Aggregate chunks until we have a chunk that is at least 2000 characters long
            val currentBuffer = StringBuffer()
            val newChunks = mutableListOf<String>()
            while (chunks.size > 0) {
                currentBuffer.append(chunks.removeAt(0))
                if (currentBuffer.length > minChars || chunks.size == 0) {
                    newChunks.add(currentBuffer.toString())
                    currentBuffer.setLength(0)
                }
            }

            if (1 == newChunks.size) return inner.edit(instruction, newChunks[0])
            val individualEdits = newChunks.map { chunk ->
                inner.edit(instruction, chunk)
            }
            return individualEdits.joinToString("\n")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val port = 8080
        val baseURL = "http://localhost:$port"
        val server = object : SkyenetSessionServer(
            applicationName = "AwsAgent",
            //oauthConfig = File(File(System.getProperty("user.home")), "client_secret_google_oauth.json").absolutePath,
            yamlDescriber = AbbrevWhitelistYamlDescriber(
                "com.simiacryptus"
            ),
            baseURL = baseURL,
            model = "gpt-4-0314"
        ) {
            override fun hands() = mapOf(
                "clients" to HttpUtil() as Object,
                "nlp" to MacroTextProcessor(
                    ChatProxy(clazz = TextProcessor::class.java, api = api).create(),
                    2000,
                    4000
                ) as Object,
            ).asJava

            override fun toString(e: Throwable): String {
                return e.message ?: e.toString()
            }

            override fun heart(hands: java.util.Map<String, Object>): Heart = GroovyInterpreter(hands)
        }.start(port)
        Desktop.getDesktop().browse(URI(baseURL))
        server.join()
    }

}

