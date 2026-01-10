/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.dashboardfeature.ui.mydata.model.DataCategory
import eu.europa.ec.dashboardfeature.ui.mydata.model.DataCategoryUi
import eu.europa.ec.dashboardfeature.ui.mydata.model.MyDataUi
import eu.europa.ec.dashboardfeature.ui.mydata.model.PersonalDataField
import eu.europa.ec.dashboardfeature.ui.mydata.model.VerificationStatus
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class MyDataInteractorPartialState {
    data class Success(val data: MyDataUi) : MyDataInteractorPartialState()
    data class Failure(val error: String) : MyDataInteractorPartialState()
}

interface MyDataInteractor {
    fun getMyData(): Flow<MyDataInteractorPartialState>
}

class MyDataInteractorImpl(
    private val resourceProvider: ResourceProvider,
) : MyDataInteractor {

    override fun getMyData(): Flow<MyDataInteractorPartialState> = flow {
        try {
            // In real implementation, this would aggregate data from credentials
            // For now, using mock data to demonstrate the feature
            val categories = listOf(
                DataCategoryUi(
                    category = DataCategory.IDENTITY,
                    title = resourceProvider.getString(R.string.my_data_category_identity),
                    icon = AppIcons.User,
                    fields = listOf(
                        PersonalDataField(
                            id = "full_name",
                            label = resourceProvider.getString(R.string.my_data_field_full_name),
                            value = "John Michael Doe",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "National PID",
                            isEditable = false
                        ),
                        PersonalDataField(
                            id = "date_of_birth",
                            label = resourceProvider.getString(R.string.my_data_field_date_of_birth),
                            value = "15 March 1990",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "National PID",
                            isEditable = false
                        ),
                        PersonalDataField(
                            id = "nationality",
                            label = resourceProvider.getString(R.string.my_data_field_nationality),
                            value = "Finnish",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "National PID",
                            isEditable = false
                        )
                    )
                ),
                DataCategoryUi(
                    category = DataCategory.CONTACT,
                    title = resourceProvider.getString(R.string.my_data_category_contact),
                    icon = AppIcons.Message,
                    fields = listOf(
                        PersonalDataField(
                            id = "email",
                            label = resourceProvider.getString(R.string.my_data_field_email),
                            value = "john.doe@email.com",
                            status = VerificationStatus.SELF_DECLARED,
                            sourceCredential = null,
                            isEditable = true
                        ),
                        PersonalDataField(
                            id = "phone",
                            label = resourceProvider.getString(R.string.my_data_field_phone),
                            value = "+358 40 123 4567",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "Authbound",
                            isEditable = false
                        )
                    )
                ),
                DataCategoryUi(
                    category = DataCategory.ADDRESS,
                    title = resourceProvider.getString(R.string.my_data_category_address),
                    icon = AppIcons.Home,
                    fields = listOf(
                        PersonalDataField(
                            id = "home_address",
                            label = resourceProvider.getString(R.string.my_data_field_address),
                            value = "Mannerheimintie 1\n00100 Helsinki, Finland",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "Residence Certificate",
                            isEditable = false
                        )
                    )
                ),
                DataCategoryUi(
                    category = DataCategory.DRIVING,
                    title = resourceProvider.getString(R.string.my_data_category_driving),
                    icon = AppIcons.Id,
                    fields = listOf(
                        PersonalDataField(
                            id = "license_categories",
                            label = "License Categories",
                            value = "B, BE",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "Mobile Driving License",
                            isEditable = false
                        ),
                        PersonalDataField(
                            id = "license_valid_until",
                            label = "Valid Until",
                            value = "15 June 2028",
                            status = VerificationStatus.VERIFIED,
                            sourceCredential = "Mobile Driving License",
                            isEditable = false
                        )
                    )
                )
            )

            emit(
                MyDataInteractorPartialState.Success(
                    data = MyDataUi(
                        categories = categories,
                        lastUpdated = "Today"
                    )
                )
            )
        } catch (e: Exception) {
            emit(MyDataInteractorPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }
}
