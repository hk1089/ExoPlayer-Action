package com.lib.exo.listPlayer

/**
 * @author Hemraj Kumawat
 * PlayStrategy used with listExoPlayerHelper used for playing video inside recyclerview, which determine when to play item,
 * Value should be between 0 to 1, default is 0.75 meana when item is visible 75% then it will start play.
 */
object PlayStrategy {
    const val DEFAULT = 0.75f
}