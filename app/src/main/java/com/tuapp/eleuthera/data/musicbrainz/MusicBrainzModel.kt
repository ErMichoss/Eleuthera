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
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("length") val length: Long?,
    @SerializedName("disambiguation") val disambiguation: String?,
    @SerializedName("first-release-date") val firstReleaseDate: String?,
    @SerializedName("artist-credit") val artistCredit: List<ArtistCredit>?,
    @SerializedName("releases") val releases: List<Release>?,
    @SerializedName("tags") val tagList: List<Tag>?,
    @SerializedName("genres") val genres: List<Tag>? // Por si vienen los géneros procesados
)

data class Release(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
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
    @SerializedName("position") val position: Int?,
    @SerializedName("tracks") val tracks: List<Track>?,
    @SerializedName("format") val format: String?
)

data class Track(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("number") val number: String?,
    @SerializedName("recording") var recording: Recording?,
    @SerializedName("length") val length: Long?,
    @SerializedName("position") val position: Int?,
)