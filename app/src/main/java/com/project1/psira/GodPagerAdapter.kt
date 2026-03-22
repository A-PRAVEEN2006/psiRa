package com.project1.psira

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class GodPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GodAgentsFragment()
            1 -> GodEnclavesFragment()
            2 -> GodDirectLinksFragment()
            3 -> GodVaultsFragment()
            else -> GodAgentsFragment()
        }
    }
}
