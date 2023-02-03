package com.lib.exo.statusView.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lib.exo.statusView.data.entity.HkUserStoryModel
import com.lib.exo.statusView.ui.fragment.HkStoryDisplayFragment

class HkStoryDisplayAdapter(
    fa: FragmentActivity,
    private val listOfUserStory: ArrayList<HkUserStoryModel>,
    private val invokeNextStory: (Int) -> Unit,
    private val invokePreviousStory: (Int) -> Unit
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = listOfUserStory.count()

    override fun createFragment(position: Int): Fragment = HkStoryDisplayFragment.newInstance(
        position, invokeNextStory, invokePreviousStory
    )
}