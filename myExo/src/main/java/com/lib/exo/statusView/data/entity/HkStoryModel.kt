package com.lib.exo.statusView.data.entity

import android.os.Parcelable
import com.lib.exo.statusView.util.HkMediaType
import kotlinx.parcelize.Parcelize

@Parcelize
data class HkStoryModel(
    var mediaUrl: String?,
    var name: String?,
    var time: String?,
    var isStorySeen: Boolean = false,
    var mediaType: HkMediaType = HkMediaType.IMAGE,
    var isMediaTypeVideo: Boolean = (mediaType == HkMediaType.VIDEO)
) : Parcelable