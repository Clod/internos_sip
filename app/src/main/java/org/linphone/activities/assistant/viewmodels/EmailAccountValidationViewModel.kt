/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.AccountCreator
import org.linphone.core.AccountCreatorListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.PhoneNumberUtils

class EmailAccountValidationViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EmailAccountValidationViewModel(accountCreator) as T
    }
}

class EmailAccountValidationViewModel(val accountCreator: AccountCreator) : ViewModel() {
    val email = MutableLiveData<String>()

    val waitForServerAnswer = MutableLiveData<Boolean>()

    val leaveAssistantEvent = MutableLiveData<Event<Boolean>>()

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val listener = object : AccountCreatorListenerStub() {
        override fun onIsAccountActivated(
            creator: AccountCreator,
            status: AccountCreator.Status,
            response: String?
        ) {
            Log.i("[Assistant] [Account Validation] onIsAccountActivated status is $status")
            waitForServerAnswer.value = false

            when (status) {
                AccountCreator.Status.AccountActivated -> {
                    if (createAccountAndAuthInfo()) {
                        leaveAssistantEvent.value = Event(true)
                    } else {
                        onErrorEvent.value = Event("Error: ${status.name}")
                    }
                }
                AccountCreator.Status.AccountNotActivated -> {
                    onErrorEvent.value = Event(
                        AppUtils.getString(R.string.assistant_create_email_account_not_validated)
                    )
                }
                else -> {
                    onErrorEvent.value = Event("Error: ${status.name}")
                }
            }
        }
    }

    init {
        accountCreator.addListener(listener)
        email.value = accountCreator.email
    }

    override fun onCleared() {
        accountCreator.removeListener(listener)
        super.onCleared()
    }

    fun finish() {
        waitForServerAnswer.value = true
        val status = accountCreator.isAccountActivated
        Log.i("[Assistant] [Account Validation] Account exists returned $status")
        if (status != AccountCreator.Status.RequestOk) {
            waitForServerAnswer.value = false
            onErrorEvent.value = Event("Error: ${status.name}")
        }
    }

    private fun createAccountAndAuthInfo(): Boolean {
        val account = accountCreator.createAccountInCore()

        if (account == null) {
            Log.e("[Assistant] [Account Validation] Account creator couldn't create account")
            onErrorEvent.value = Event("Error: Failed to create account object")
            return false
        }

        val params = account.params.clone()
        params.pushNotificationAllowed = true

        if (params.internationalPrefix.isNullOrEmpty()) {
            val dialPlan = PhoneNumberUtils.getDialPlanForCurrentCountry(coreContext.context)
            if (dialPlan != null) {
                Log.i(
                    "[Assistant] [Account Validation] Found dial plan country ${dialPlan.country} with international prefix ${dialPlan.countryCallingCode}"
                )
                params.internationalPrefix = dialPlan.countryCallingCode
            } else {
                Log.w("[Assistant] [Account Validation] Failed to find dial plan")
            }
        }

        account.params = params

        Log.i("[Assistant] [Account Validation] Account created")
        return true
    }
}
