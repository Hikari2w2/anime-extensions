package eu.kanade.tachiyomi.extension.all.webdav

import android.util.Base64
import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import eu.kanade.tachiyomi.animesource.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class WebDAV : ParsedAnimeHttpSource() {

    override val name = "WebDAV"
    override val baseUrl by lazy { preferences.getString("server_url", "")!! }
    override val lang = "all"
    override val supportsLatest = true

    private var sardine: Sardine? = null

    override fun setup() {
        val url = baseUrl
        if (url.isBlank()) throw Exception("Configura la URL del servidor en los ajustes de la extensión.")
        val username = preferences.getString("username", "")!!
        val password = preferences.getString("password", "")!!
        sardine = SardineFactory.begin(username, password)
        // Verificar conexión simple
        try {
            sardine!!.list(url)
        } catch (e: Exception) {
            throw Exception("No se pudo conectar al servidor WebDAV. Verifica URL, usuario y contraseña.")
        }
    }

    override fun getLogin(): Login {
        val fields = listOf(
            Login.Field("server_url", "URL del servidor", "https://tuservidor.com/remote.php/dav/files/usuario/Anime"),
            Login.Field("username", "Usuario"),
            Login.Field("password", "Contraseña", fieldType = 'P')
        )
        return Login("Iniciar sesión en WebDAV", fields)
    }

    // Popular: listar las carpetas de primer nivel como series
    override fun popularAnimeRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularAnimeParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        // En WebDAV, parsearemos manualmente con Sardine en lugar de usar Jsoup.
        // Pero Aniyomi espera retornar un Document parseado; adaptaremos:
        val mangas = mutableListOf<SManga>()
        val resources = sardine!!.list(baseUrl)
        for (res in resources) {
            if (res.isDirectory && res.name != "." && res.name != "..") {
                val manga = SManga.create().apply {
                    title = res.name
                    url = res.path  // ruta relativa desde baseUrl
                }
                mangas.add(manga)
            }
        }
        return MangasPage(mangas, false)
    }

    // Detalles de una serie (carpeta)
    override fun mangaDetailsParse(document: Document): SManga {
        // No implementado con Jsoup porque usamos Sardine; mejor reimplementar mangaDetailsRequest
        // Sobreescribiremos el método mangaDetailsRequest y mangaDetailsParse adaptado.
        // Para simplificar, usaremos una URL ficticia y parsearemos con Sardine en fetchMangaDetails.
        // Como estamos extendiendo ParsedAnimeHttpSource, podemos sobrescribir fetchMangaDetails directamente.
        throw UnsupportedOperationException("Usa fetchMangaDetails")
    }

    override fun fetchMangaDetails(manga: SManga): SManga {
        // Intentar leer un archivo poster.jpg si existe
        val posterUrl = "${baseUrl}${manga.url}/poster.jpg"
        try {
            sardine!!.get(posterUrl) // solo para probar existencia
            manga.thumbnail_url = posterUrl
        } catch (e: Exception) {
            // no hay poster
        }
        manga.description = "Serie en WebDAV"
        manga.status = SManga.UNKNOWN
        return manga
    }

    // Capítulos (archivos de video dentro de la carpeta de la serie)
    override fun fetchChapterList(manga: SManga): List<SChapter> {
        val seriesPath = "${baseUrl}${manga.url}"
        val resources = sardine!!.list(seriesPath)
        val chapters = mutableListOf<SChapter>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (res in resources) {
            if (!res.isDirectory) {
                val name = res.name
                // Filtrar solo archivos de video (extensiones comunes)
                if (name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".avi", true) || name.endsWith(".webm", true)) {
                    val chapter = SChapter.create().apply {
                        this.name = name.substringBeforeLast(".") // sin extensión
                        this.url = res.path // ruta relativa
                        this.date_upload = dateFormat.format(res.modified)
                    }
                    chapters.add(chapter)
                }
            }
        }
        // Ordenar por nombre (opcional)
        chapters.sortBy { it.name }
        return chapters
    }

    // Páginas del capítulo: un solo elemento (el archivo de video)
    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun fetchPageList(chapter: SChapter): List<Page> {
        val videoUrl = "${baseUrl}${chapter.url}"
        return listOf(Page(0, videoUrl, videoUrl))
    }

    // Método para obtener los headers con autenticación (para la descarga/reproducción)
    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
        val username = preferences.getString("username", "")!!
        val password = preferences.getString("password", "")!!
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        builder.add("Authorization", "Basic $encoded")
        return builder
    }

    // Búsqueda simple por nombre en la raíz
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET(baseUrl, headers)
    }

    override fun searchAnimeParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        val resources = sardine!!.list(baseUrl)
        val searchQuery = response.request.url.queryParameter("q") ?: ""
        for (res in resources) {
            if (res.isDirectory && res.name.contains(searchQuery, true)) {
                mangas.add(SManga.create().apply {
                    title = res.name
                    url = res.path
                })
            }
        }
        return MangasPage(mangas, false)
    }

    // Últimas actualizaciones: igual que popular
    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularAnimeParse(response)
}
