package com.gaabaariaa.music.feature.health

import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.HealthIssue

fun HealthIssue.label(): Int = when (this) {
    HealthIssue.MISSING_TITLE -> R.string.issue_missing_title
    HealthIssue.MISSING_ARTIST -> R.string.issue_missing_artist
    HealthIssue.MISSING_ALBUM -> R.string.issue_missing_album
    HealthIssue.MISSING_GENRE -> R.string.issue_missing_genre
    HealthIssue.UNRECOGNIZED -> R.string.issue_unrecognized
    HealthIssue.LOW_QUALITY -> R.string.issue_low_quality
    HealthIssue.MISSING_ARTWORK -> R.string.issue_missing_artwork
    HealthIssue.MISSING_LYRICS -> R.string.issue_missing_lyrics
}
