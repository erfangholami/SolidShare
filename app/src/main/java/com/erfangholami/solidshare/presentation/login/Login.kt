package com.erfangholami.solidshare.presentation.login

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.LoginFilledMethod
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.presentation.navigation.MainNavItem
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Login(
    navController: NavController,
    viewModel: LoginViewModel,
) {
    val doAuthenticationInBrowser = rememberLauncherForActivityResult(object : ActivityResultContract<Intent, Intent?>() {
        override fun createIntent(context: Context, input: Intent): Intent = input
        override fun parseResult(resultCode: Int, intent: Intent?): Intent? = intent
    }) { intent: Intent? ->
        if (intent != null) {
            val resp: AuthorizationResponse? = AuthorizationResponse.fromIntent(intent)
            val ex: AuthorizationException? = AuthorizationException.fromIntent(intent)
            viewModel.submitAuthorizationResponse(resp, ex)
        }
    }

    val previousWebIds by viewModel.previouslyLoggedOutWebIds.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!viewModel.isAddingAccount && viewModel.isLoggedIn()) {
            navController.navigate(MainNavItem) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    LaunchedEffect(viewModel.loginBrowserIntent.value) {
        viewModel.loginBrowserIntent.value?.let { intent ->
            doAuthenticationInBrowser.launch(intent)
            viewModel.loginBrowserIntent.value = null
        }
    }

    LaunchedEffect(viewModel.loginResult.value) {
        if (viewModel.loginResult.value) {
            if (viewModel.isAddingAccount) {
                navController.popBackStack()
            } else {
                navController.navigate(MainNavItem) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    Scaffold (
        modifier = Modifier.fillMaxWidth()
    ){  innerPaddings ->
        if (viewModel.loginLoading.value) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPaddings)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }
        } else {
            var customUrl by remember { mutableStateOf("") }
            var customUrlError by remember { mutableStateOf(false) }

            fun submitCustomUrl() {
                val trimmed = customUrl.trim()
                if (trimmed.isEmpty()) {
                    customUrlError = true
                } else {
                    viewModel.loginWithOidcIssuer(trimmed)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(32.dp))

                if(previousWebIds.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(previousWebIds) { webId ->
                            Button(
                                onClick = {
                                    viewModel.loginWithWebId(webId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = webId)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }


                OutlinedButton(
                    onClick = {
                        viewModel.loginWithOidcIssuer("https://login.inrupt.com")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.login_with_inrupt))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.loginWithOidcIssuer("https://solidcommunity.net")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.login_with_solidcommunity))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.loginWithOidcIssuer("https://datapod.com")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.login_with_datapod))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.login_or),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it
                        customUrlError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.login_custom_issuer_label)) },
                    placeholder = { Text(text = stringResource(R.string.login_custom_issuer_placeholder)) },
                    singleLine = true,
                    isError = customUrlError,
                    supportingText = if (customUrlError) {
                        { Text(text = stringResource(R.string.login_custom_issuer_error)) }
                    } else if (viewModel.loginBrowserIntentErrorMessage.value != null) {
                        { Text(text = viewModel.loginBrowserIntentErrorMessage.value ?: "", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submitCustomUrl() }),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { submitCustomUrl() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.login_with_custom_issuer))
                }
            }
        }
    }

    BackHandler {
        navController.popBackStack()
    }
}