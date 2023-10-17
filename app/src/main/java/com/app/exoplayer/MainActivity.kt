package com.app.exoplayer

import android.app.Activity
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.app.exoplayer.databinding.ActivityMainBinding
import com.lib.exo.statusView.data.entity.HkUserStoryModel
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mViewModel: MainViewModel by viewModels()
    private lateinit var storyAdapter: StoryAdapter

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            try {
                if (it.resultCode == Activity.RESULT_OK) {
                    val list = arrayListOf<HkUserStoryModel>()

                    it.data?.hasExtra(HkStoryDisplayActivity.HK_LIST_OF_STORY)
                        ?.let { hasHkStoryList ->
                            if (hasHkStoryList) {
                                it.data?.getParcelableArrayListExtra<HkUserStoryModel>(
                                    HkStoryDisplayActivity.HK_LIST_OF_STORY
                                )?.let { listOfUserStories ->
                                    list.addAll(listOfUserStories)
                                }
                            }
                        }

                    if (!mViewModel.mListOfUsers.containsAll(list)) {
                        storyAdapter.setUserStoryData(list)
                        mViewModel.updateListOfUser(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mViewModel.readAssetsData(this)
        initView()


    }
    private fun initView() {
        with(binding.rvStory) {
            setHasFixedSize(true)
            ContextCompat.getDrawable(this@MainActivity, R.drawable.divider)
                ?.let { DividerItemDecorator(it) }?.let {
                    addItemDecoration(it)
                }
            storyAdapter =
                StoryAdapter(mViewModel.mListOfUsers, { launcher }, { this@MainActivity })
            adapter = storyAdapter
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                startPostponedEnterTransition()
            }
        }

        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1))
    }

}