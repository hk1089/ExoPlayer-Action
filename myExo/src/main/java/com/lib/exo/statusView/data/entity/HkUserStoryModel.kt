package com.lib.exo.statusView.data.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HkUserStoryModel(
    val id: String?,
    val userName: String?,
    val userStoryList: ArrayList<HkStoryModel>,
    var lastStoryPointIndex: Int = 0
) : Parcelable