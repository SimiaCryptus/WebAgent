package com.simiacryptus.skyenet

import com.gargoylesoftware.htmlunit.WebClient
import com.simiacryptus.skyenet.util.LoggingInterceptor
import com.gargoylesoftware.htmlunit.html.HtmlPage
import org.slf4j.LoggerFactory

object HtmlUnitTest {
    val logger = LoggerFactory.getLogger(HtmlUnitTest::class.java)
    @JvmStatic
    fun main(args: Array<String>) {
        require(null != WebClient()) // Ensure that the class is loaded
        val stringBuffer = StringBuffer()
        LoggingInterceptor.withIntercept(stringBuffer, "com.gargoylesoftware", "c.g", "ROOT") {
            //val url = "https://www.google.com"
            val url = "https://www.understandingwar.org/backgrounder/ukraine-conflict-updates"
            //val url = "https://www.cnn.com"
            //val url = "https://blog.simiacrypt.us"
            val page = fetch(url)
            println(page?.asNormalizedText())
            println(String.format("Loaded %s bytes from %s, logging %s bytes", page?.webResponse?.contentAsString?.length, url, stringBuffer.length))
        }
    }

    private fun fetch(url: String): HtmlPage? {
        val webClient = WebClient()
        webClient.options.isJavaScriptEnabled = true
        webClient.options.isThrowExceptionOnScriptError = false
        webClient.options.isUseInsecureSSL = true
        webClient.addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
        )
        return webClient.getPage(url)
    }
}