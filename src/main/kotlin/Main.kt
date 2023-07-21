import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.ContentType.APPLICATION_JS
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

data class NvdaHufTimeSeries(val map: Map<String, NvdaHufEntry>) : Map<String, NvdaHufEntry> by map
data class NvdaHufEntry(val nvdaUsd: Double, val usdHuf: Double, val nvdaHuf: Double)

private val mapper = ObjectMapper()

fun parseTimeSeriesEntry(entry: Map.Entry<Any?, Any?>): Pair<String, Double>? {
    val date = entry.key as? String
    val value = entry.value as? Map<*, *>
    val closeString = value?.get("4. close") as? String
    val close = closeString?.toDouble()
    return if (date == null || close == null) {
        null
    } else {
        date to close
    }
}

private val dateFormat = SimpleDateFormat("YYYY-MM-dd")

fun getNvdaHuf(apikey: String): File {
    val today = dateFormat.format(Date())
    val nvdaHufFile = File("${today}_nvdahuf.json")
    if (!nvdaHufFile.exists()) {
        val nvdaUrl = URL(
            "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=NVDA&outputsize=full&apikey=$apikey"
        )
        val nvdaUsdRaw = mapper.readValue(nvdaUrl, Map::class.java)
        val nvdaUsdTimeSeries = nvdaUsdRaw["Time Series (Daily)"] as? Map<*, *>
        val nvdaUsd = nvdaUsdTimeSeries
            ?.asSequence()
            ?.mapNotNull { parseTimeSeriesEntry(it) }
            ?.map {
                if (it.first < "2021-07-20") {
                    it.first to it.second / 4
                } else {
                    it
                }
            }
            ?.toMap()?:error("Could not get NVDA-USD data")

        val usdHufUrl = URL(
            "https://www.alphavantage.co/query?function=FX_DAILY&from_symbol=USD&to_symbol=HUF&outputsize=full&apikey=$apikey"
        )
        val usdHufRaw = mapper.readValue(usdHufUrl, Map::class.java)
        val usdHufTimeSeries = usdHufRaw["Time Series FX (Daily)"] as? Map<*, *>
        val usdHuf = usdHufTimeSeries
            ?.asSequence()
            ?.mapNotNull { parseTimeSeriesEntry(it) }
            ?.toMap()?:error("Could not get USD-HUF data")

        val dates = nvdaUsd.keys + usdHuf.keys
        val nvdaHuf = NvdaHufTimeSeries(
            dates.asSequence()
                .mapNotNull {
                    val curNvdaUsd = nvdaUsd[it]
                    val curUsdHuf = usdHuf[it]
                    if (curNvdaUsd == null || curUsdHuf == null) {
                        null
                    } else {
                        it to NvdaHufEntry(curNvdaUsd, curUsdHuf, curNvdaUsd * curUsdHuf)
                    }
                }.toMap()
        )
        mapper.writeValue(nvdaHufFile, nvdaHuf)
    }
    return nvdaHufFile
}

val webPage = NvdaHufTimeSeries::class.java.getResourceAsStream("index.html")?.reader()?.readText()
val js = NvdaHufTimeSeries::class.java.getResourceAsStream("script.js")?.reader()?.readText()

fun main() {
    val apiKey = NvdaHufTimeSeries::class.java
        .getResourceAsStream("apikey")
        ?.reader()
        ?.readText()
        ?: Random.nextLong().toString()
    Javalin.create()
        .exception(Exception::class.java) { e, ctx ->
            ctx.result(e.stackTraceToString())
        }
        .get("/script.js") { ctx ->
            js?.let {
                ctx.contentType(APPLICATION_JS)
                ctx.result(it)
            } ?: ctx.run {
                result("Could not get JS")
                status(500)
            }
        }
        .get("/") { ctx ->
            webPage?.let {
                ctx.html(it)
            } ?: ctx.run {
                result("Could not get webpage")
                status(500)
            }
        }
        .get("/json") { ctx ->
            try {
                ctx.result(getNvdaHuf(apiKey).inputStream())
            } catch (e : IllegalStateException) {
                ctx.status(500)
                ctx.result(e.message?:"Unknown error")
            }
        }
        .start(80)
}