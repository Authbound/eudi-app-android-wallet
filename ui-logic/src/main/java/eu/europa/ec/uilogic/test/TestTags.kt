/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.test

object StartupTestTags {
    object Splash {
        const val ROOT = "startup_splash_root"
    }
}

object AuthTestTags {
    object Login {
        const val ROOT = "login_screen_root"
        const val EMAIL_FIELD = "login_email_field"
        const val PASSWORD_FIELD = "login_password_field"
        const val PRIMARY_BUTTON = "login_primary_button"
        const val GOOGLE_BUTTON = "login_google_button"
        const val TOGGLE_MODE_BUTTON = "login_toggle_mode_button"
    }

    object ProfileCompletion {
        const val ROOT = "profile_completion_screen_root"
        const val HANDLE_FIELD = "profile_completion_handle_field"
        const val DISPLAY_NAME_FIELD = "profile_completion_display_name_field"
        const val ACTIVATE_BUTTON = "profile_completion_activate_button"
        const val SIGN_OUT_BUTTON = "profile_completion_sign_out_button"
    }

    object DeviceSecurityRequired {
        const val ROOT = "device_security_required_screen_root"
        const val RETRY_BUTTON = "device_security_required_retry_button"
        const val OPEN_SETTINGS_BUTTON = "device_security_required_open_settings_button"
    }

    object WalletSetup {
        const val ROOT = "wallet_setup_screen_root"
        const val LOADING_STATE = "wallet_setup_loading_state"
        const val ERROR_STATE = "wallet_setup_error_state"
        const val ALREADY_ACTIVATED_STATE = "wallet_setup_already_activated_state"
        const val CONTINUE_TO_HOME_BUTTON = "wallet_setup_continue_to_home_button"
        const val ALREADY_ACTIVATED_SIGN_OUT_BUTTON = "wallet_setup_already_activated_sign_out_button"
        const val RETRY_BUTTON = "wallet_setup_retry_button"
        const val ERROR_SIGN_OUT_BUTTON = "wallet_setup_error_sign_out_button"
        const val DELETE_WALLET_BUTTON = "wallet_setup_delete_wallet_button"
        const val CANCEL_CONFIRMATION_DIALOG = "wallet_setup_cancel_confirmation_dialog"
        const val CANCEL_CONFIRM_BUTTON = "wallet_setup_cancel_confirmation_confirm_button"
        const val CANCEL_DISMISS_BUTTON = "wallet_setup_cancel_confirmation_dismiss_button"
        const val DELETE_CONFIRMATION_DIALOG = "wallet_setup_delete_confirmation_dialog"
        const val DELETE_CONFIRM_BUTTON = "wallet_setup_delete_confirmation_confirm_button"
        const val DELETE_DISMISS_BUTTON = "wallet_setup_delete_confirmation_dismiss_button"
    }

    object QuickPin {
        const val ROOT = "quick_pin_screen_root"
        const val TITLE = "quick_pin_title"
        const val SUBTITLE = "quick_pin_subtitle"
        const val KEYPAD = "quick_pin_keypad"
        const val ERROR_MESSAGE = "quick_pin_error_message"
        const val OVERFLOW_BUTTON = "quick_pin_overflow_button"
        const val FORGOT_PIN_ACTION = "quick_pin_forgot_pin_action"
        const val BIOMETRIC_CTA = "quick_pin_biometric_cta"
        const val BACKSPACE = "quick_pin_backspace"

        fun digit(value: Int): String = "quick_pin_digit_$value"
        fun indicator(index: Int): String = "quick_pin_indicator_$index"
    }
}

object DashboardTestTags {
    object Screen {
        const val ROOT = "dashboard_screen_root"
    }
}
