package com.app.exoplayer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lib.exo.statusView.data.entity.HkUserStoryModel
import java.io.IOException

class MainViewModel : ViewModel() {
    val mListOfUsers: ArrayList<HkUserStoryModel> = ArrayList()

    fun readAssetsData(context: Context): String {
        val json: String?
        try {
            val inputStream = context.assets.open("storyData.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            json = java.lang.String(buffer, "UTF-8").toString()

            mListOfUsers.addAll(
                Gson().fromJson(
                    json, object : TypeToken<ArrayList<HkUserStoryModel?>?>() {}.type
                )
            )
        } catch (ex: IOException) {
            ex.printStackTrace()
            return ""
        }
        return json
    }

    fun updateListOfUser(mListOfUsers: ArrayList<HkUserStoryModel>) {
        this.mListOfUsers.clear()
        this.mListOfUsers.addAll(mListOfUsers)
    }

}