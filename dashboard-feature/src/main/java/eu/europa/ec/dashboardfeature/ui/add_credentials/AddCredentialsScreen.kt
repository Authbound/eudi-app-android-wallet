
package eu.europa.ec.dashboardfeature.ui.add_credentials

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.dashboardfeature.interactor.AddCredentialsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.TransactionsInteractorImpl
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SIZE_XXX_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.WrapIcon

@Composable
fun AddCredentialsScreen(
    navHostController: NavController,
    viewModel: AddCredentialsViewModel,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
            topBar = { TopBar() }
        ) {
            Content()
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .height(SIZE_XXX_LARGE.dp)
            .fillMaxSize()
            .padding(SPACING_MEDIUM.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge,
            text = stringResource(R.string.transactions_screen_title)
        )
        WrapIcon(
            modifier = Modifier.align(Alignment.CenterVertically),
            iconData = AppIcons.Add
        )
    }
}

@Composable
private fun Content() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Text("Add Credentials")
    }
}

@ThemeModePreviews
@Composable
private fun AddCredentialsScreenPreview() {
    AddCredentialsScreen(
        rememberNavController(),
        viewModel = AddCredentialsViewModel(AddCredentialsInteractorImpl())
    )
}