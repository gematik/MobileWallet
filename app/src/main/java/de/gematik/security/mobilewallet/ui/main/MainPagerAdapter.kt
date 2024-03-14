/*
 * Copyright 2021-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package de.gematik.security.mobilewallet.ui.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Created by rk on 08.10.2021.
 * gematik.de
 */

const val INVITATIONS_PAGE_ID = 0
const val CREDENTIALS_PAGE_ID = 1
class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val fragmentCreators: Map<Int, () -> Fragment> = mapOf(
        INVITATIONS_PAGE_ID to { InvitationListFragment() },
        CREDENTIALS_PAGE_ID to { CredentialListFragment() }
    )

    override fun getItemCount() = fragmentCreators.size

    override fun createFragment(position: Int): Fragment {
        return fragmentCreators[position]?.invoke() ?: throw IndexOutOfBoundsException()
    }

}