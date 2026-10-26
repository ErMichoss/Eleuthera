package com.tuapp.eleuthera.data.musicbrainz

class MusicBrainzRepository(
    private val service: MusicBrainzService
) {
    val canLogIn: Boolean get() = service.canLogIn

    fun authorizeUrl(): String = service.authorizeUrl()

    suspend fun exchangeCodeForToken(code: String): Boolean =
        service.exchangeCodeForToken(code)

    suspend fun searchRecordings(title: String, artist: String?, limit: Int): RecordingSearchResponse {
        val query = buildString {
            append("recording:\"").append(escapeLucene(title)).append("\" AND video:false")
            if (!artist.isNullOrBlank()) {
                append(" AND artistname:\"").append(escapeLucene(artist)).append("\"")
            }
        }
        return service.searchRecordings(query, limit)
    }

    suspend fun searchReleases(title: String, artist: String?, limit: Int): ReleaseSearchResponse {
        val query = buildString {
            append("release:\"").append(escapeLucene(title)).append("\"")
            if (!artist.isNullOrBlank()) {
                append(" AND artistname:\"").append(escapeLucene(artist)).append("\"")
            }
        }
        return service.searchReleases(query, limit)
    }

    suspend fun getRecordingDetails(recordingId: String): Recording =
        service.getRecordingDetails(recordingId)

    suspend fun getReleaseDetails(releaseId: String): Release =
        service.getReleaseDetails(releaseId)

    private fun escapeLucene(str: String): String =
        str.replace(Regex("([+\\-|!(){}\\[\\]^\"~*?:\\\\/])")) { "\\${it.value}" }
}