package com.lib.exo.listPlayer

import androidx.annotation.IntDef
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * @author Hemraj Kumawat
 * MuteStrategy used with listExoPlayerHelper used for playing video inside recyclerview
 * We can set
 * MuteStrategy.ALL - When this set single mute on single item will mute all other instances, just like instagram
 * MuteStrategy.INDIVIDUAL - When this set User have to manage individual mute status as per items in recyclerview.
 */
object MuteStrategy {
    const val ALL = 1
    const val INDIVIDUAL = 2

    @IntDef(ALL, INDIVIDUAL)
    @Retention(RetentionPolicy.SOURCE)
    annotation class Values
}