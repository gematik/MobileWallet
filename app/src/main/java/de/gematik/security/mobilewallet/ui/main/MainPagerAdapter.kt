package de.gematik.security.mobilewallet.ui.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Created by rk on 08.10.2021.
 * gematik.de
 */

const val CONNECTIONS_PAGE_ID = 0
const val CREDENTIALS_PAGE_ID = 1
class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val fragmentCreators: Map<Int, () -> Fragment> = mapOf(
        CONNECTIONS_PAGE_ID to { ConnectionListFragment() },
        CREDENTIALS_PAGE_ID to { CredentialListFragment() }
    )

    override fun getItemCount() = fragmentCreators.size

    override fun createFragment(position: Int): Fragment {
        return fragmentCreators[position]?.invoke() ?: throw IndexOutOfBoundsException()
    }

}