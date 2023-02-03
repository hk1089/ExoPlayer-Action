package com.lib.exo.statusView.ui.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lib.exo.statusView.common.INITIAL_STORY_INDEX
import com.lib.exo.statusView.data.entity.HkUserStoryModel

class HkStoryDisplayViewModel : ViewModel() {

    var listOfUserStory = ArrayList<HkUserStoryModel>()
    private var mainStoryIndex: Int = INITIAL_STORY_INDEX
    private var horizontalProgressViewAttributes = hashMapOf<String, Any>()

    private var _startOverStoryLiveData = MutableLiveData<Boolean>()
    val startOverStoryLiveData: LiveData<Boolean> = _startOverStoryLiveData

    fun addListOfUserStories(listOfUserStories: ArrayList<HkUserStoryModel>?) {
        listOfUserStory.clear()
        listOfUserStories?.let { listOfUserStory.addAll(it) }
    }

    fun updateStoryPoint(internalStoryIndex: Int) {
        try {
            listOfUserStory[this.mainStoryIndex].lastStoryPointIndex = internalStoryIndex
            listOfUserStory[this.mainStoryIndex].userStoryList[internalStoryIndex].isStorySeen = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMainStoryIndex(mainStoryIndex: Int) {
        this.mainStoryIndex = mainStoryIndex
        startOverStory()
    }

    fun setHorizontalProgressViewAttributes(horizontalProgressViewAttributes: HashMap<String, Any>) {
        this.horizontalProgressViewAttributes = horizontalProgressViewAttributes
    }

    fun getHorizontalProgressViewAttributes() = horizontalProgressViewAttributes

    private fun startOverStory() {
        _startOverStoryLiveData.postValue(true)
    }

    override fun onCleared() {
        super.onCleared()
        horizontalProgressViewAttributes = hashMapOf()
    }
}