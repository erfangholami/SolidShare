package com.erfangholami.solidshare.presentation.login

import android.content.Context
import android.content.Intent
import android.webkit.URLUtil
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.MainNavItem
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Login(
    navController: NavController,
    viewModel: LoginViewModel,
) {
    val scope = rememberCoroutineScope()
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var customUrl by remember { mutableStateOf("") }
    var customUrlError by remember { mutableStateOf<String?>(null) }
    var showProviderSheet by remember { mutableStateOf(false) }

    fun onCustomUrlChanged(newValue: String) {
        customUrl = newValue
        customUrlError = null
    }

    fun submitCustomUrl() {
        val trimmed = customUrl.trim()

        if(trimmed.isEmpty() || !URLUtil.isValidUrl(trimmed)) {
            customUrlError = "Url is not valid"
        } else if(!URLUtil.isHttpsUrl(trimmed)) {
            customUrlError = "Url is not HTTPS type"
        } else {
            viewModel.loginWithOidcIssuer(trimmed)
        }

    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.LaunchAuthorizationIntent ->
                    doAuthenticationInBrowser.launch(event.intent)

                LoginEvent.NavigateAfterLogin -> if (viewModel.isAddingAccount) {
                    navController.popBackStack()
                } else {
                    navController.navigate(MainNavItem) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage, customUrlError) {
        val messageToShow = customUrlError ?: uiState.errorMessage
        if (!messageToShow.isNullOrEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(messageToShow)
            }
        }
    }

    Scaffold (
        modifier = Modifier
            .fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .imePadding()
            )
        }
    ){  innerPaddings ->
        if (uiState.isLoading) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(innerPaddings)
                    .imePadding()
                    .imeNestedScroll()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if(previousWebIds.isEmpty()) stringResource(R.string.login_title) else stringResource(R.string.login_back_title),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(32.dp))

                PreviouslyLoggedInWebIDs(
                    previousWebIds
                ) {
                    viewModel.loginWithWebId(it)
                }

                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProviderSheet = true },
                    label = { Text(text = stringResource(R.string.login_select_provider)) },
                    enabled = false,
                    readOnly = true,
                    trailingIcon = {
                        Text(
                            text = "\u25BE",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                OrDivider()

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        onCustomUrlChanged(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.login_custom_issuer_label)) },
                    placeholder = { Text(text = stringResource(R.string.login_custom_issuer_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submitCustomUrl() }),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button (
                    onClick = { submitCustomUrl() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.continue_string))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dont_have_a_server),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Url("https://solidproject.org/get_a_pod")
                        ) {
                            append(stringResource(R.string.what_is_a_pod_how_to_get))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                )
            }

            if (showProviderSheet) {
                PodProviderBottomSheet(
                    podServers = viewModel.podServers,
                    onDismiss = { showProviderSheet = false },
                    onProviderSelected = { issuerUrl ->
                        showProviderSheet = false
                        viewModel.loginWithOidcIssuer(issuerUrl)
                    },
                )
            }
        }
    }

    BackHandler {
        navController.popBackStack()
    }
}

@Composable
private fun PreviouslyLoggedInWebIDs(
    webIDs: List<String>,
    onWebIdClicked: (String) -> Unit,
) {
    if(webIDs.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp)),
        ) {
            webIDs.forEachIndexed { index, webId ->
                PreviouslyLoggedInWebIDItem(
                    webId = webId,
                    onClick = {
                        onWebIdClicked(webId)
                    },
                )
                if (index < webIDs.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OrDivider()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PreviouslyLoggedInWebIDItem(
    webId: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background( MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = webId.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onSurfaceVariant ,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = webId,
                modifier = Modifier
                    .basicMarquee(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrDivider() {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodProviderBottomSheet(
    podServers: List<UiPodServer>,
    onDismiss: () -> Unit,
    onProviderSelected: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.login_select_provider),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )

            podServers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderSelected(provider.url) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(provider.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp)
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
