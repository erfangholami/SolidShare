package com.erfangh.solidshare.presentation.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangh.solidshare.R
import com.erfangh.solidshare.domain.model.LoginFilledMethod
import com.erfangh.solidshare.domain.model.PodServer
import com.erfangh.solidshare.presentation.navigation.MainNavItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Login(
    navController: NavController,
    viewModel: LoginViewModel,
) {

    val podServersState by viewModel.podServersState.collectAsStateWithLifecycle()
    val loginFilledDataState by viewModel.loginFilledDataState.collectAsStateWithLifecycle()
    val openOfficialPodListSheet = remember { mutableStateOf(false) }

    BackHandler {
        navController.popBackStack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .padding(innerPaddings)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ){
            Text(
                text = "Login",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = if (loginFilledDataState.type == LoginFilledMethod.OFFICIAL_POD) loginFilledDataState.podServer!!.name else "",
                onValueChange = {},
                modifier = Modifier.clickable {
                    openOfficialPodListSheet.value = true
                },
                readOnly = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = {
                    Text(
                        text = stringResource(R.string.choose_server),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.select_your_server),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                    )
                }
            )


            OutlinedTextField(
                value = if (loginFilledDataState.type == LoginFilledMethod.PERSONAL_SERVER) loginFilledDataState.podServer!!.url else "",
                onValueChange = {
                    viewModel.setPersonalServerUrl(it)
                },
                label = {
                    Text(
                        text = stringResource(R.string.personal_server),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.type_your_personal_server_here),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )




            FilledTonalButton(
                onClick = {
                    viewModel.login()
                },
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
                enabled = loginFilledDataState.type != LoginFilledMethod.NONE
            ) {
                Text(
                    text = stringResource(R.string.continue_string),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = stringResource(R.string.dont_have_a_server),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.what_is_a_pod_how_to_get),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }

    }

    when {
        openOfficialPodListSheet.value -> {
            ModalBottomSheet(
                onDismissRequest = {
                    openOfficialPodListSheet.value = false
                }
            ){
                Text(
                    text = stringResource(R.string.select_your_server),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyColumn() {
                    items(podServersState) {
                        OfficialPosServer(it)
                    }
                }
            }
        }
    }
}

@Composable
fun OfficialPosServer(
    podServer: PodServer,
) {
    Row(
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_solid),
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp, 4.dp)
                .size(24.dp)
        )
        Text(
            text = podServer.name,
            style = MaterialTheme.typography.bodySmall,
        )
    }

}