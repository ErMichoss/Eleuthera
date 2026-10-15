package com.tuapp.eleuthera.data.musicbrainz
import com.google.gson.annotations.SerializedName

data class MusicBrainzTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String?
)

data class RecordingSearchResponse(
    @SerializedName("recordings") val recordings: List<Recording>?
)

data class ReleaseSearchResponse(
    @SerializedName("releases") val releases: List<Release>?
)

data class Recording(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("length") val length: Long?,
    @SerializedName("artist-credit") val artistCredit: List<ArtistCredit>?,
    @SerializedName("releases") val releases: List<Release>?,
    @SerializedName("tag-list") val tagList: List<Tag>?,
    @SerializedName("genres") val genres: List<Tag>?, // Añadido para capturar los géneros oficiales
    @SerializedName("relation-list") val relationList: List<Relation>?
)

data class Release(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("track-count") val trackCount: Int?,
    @SerializedName("artist-credit") val artistCredit: List<ArtistCredit>?,
    @SerializedName("media") val media: List<Media>?
)

data class ArtistCredit(
    @SerializedName("name") val name: String?,
    @SerializedName("artist") val artist: Artist?
)

data class Artist(
    @SerializedName("name") val name: String?
)

data class Tag(
    @SerializedName("name") val name: String?
)

data class Relation(
    @SerializedName("type") val type: String?,
    @SerializedName("artist") val artist: Artist?
)

data class Media(
    @SerializedName("position") val position: Int?, // Añadido para el número de disco
    @SerializedName("track") val track: List<Track>?
)

data class Track(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("number") val number: String?,
    @SerializedName("recording") val recording: Recording?
)