package com.tuapp.eleuthera.data.musicbrainz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Respuestas de la API de MusicBrainz (https://musicbrainz.org/doc/MusicBrainz_API).
// Todo es opcional: MusicBrainz omite los campos que no conoce y así no falla al leerlos.

@Serializable
data class MusicBrainzTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class RecordingSearchResponse(
    @SerialName("recordings") val recordings: List<Recording>? = null,
)

@Serializable
data class ReleaseSearchResponse(
    @SerialName("releases") val releases: List<Release>? = null,
)

@Serializable
data class Recording(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("length") val length: Long? = null,
    @SerialName("disambiguation") val disambiguation: String? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<ArtistCredit>? = null,
    @SerialName("releases") val releases: List<Release>? = null,
    @SerialName("tags") val tagList: List<Tag>? = null,
    /** Por si vienen los géneros procesados. */
    @SerialName("genres") val genres: List<Tag>? = null,
)

@Serializable
data class Release(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("track-count") val trackCount: Int? = null,
    @SerialName("artist-credit") val artistCredit: List<ArtistCredit>? = null,
    @SerialName("media") val media: List<Media>? = null,
)

@Serializable
data class ArtistCredit(
    @SerialName("name") val name: String? = null,
    @SerialName("artist") val artist: Artist? = null,
)

@Serializable
data class Artist(
    @SerialName("name") val name: String? = null,
)

@Serializable
data class Tag(
    @SerialName("name") val name: String? = null,
)

@Serializable
data class Relation(
    @SerialName("type") val type: String? = null,
    @SerialName("artist") val artist: Artist? = null,
)

@Serializable
data class Media(
    @SerialName("position") val position: Int? = null,
    @SerialName("tracks") val tracks: List<Track>? = null,
    @SerialName("format") val format: String? = null,
)

@Serializable
data class Track(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("number") val number: String? = null,
    @SerialName("recording") val recording: Recording? = null,
    @SerialName("length") val length: Long? = null,
    @SerialName("position") val position: Int? = null,
)
