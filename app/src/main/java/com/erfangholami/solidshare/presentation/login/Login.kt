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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Login(
    navController: NavController,
    viewModel: LoginViewModel,
) {
    val scope = rememberCoroutineScope()
    val doAuthenticationInBrowser = rememberLauncherForActivityResult(object :
        ActivityResultContract<Intent, Intent?>() {
        override fun createIntent(context: Context, input: Intent): Intent = input
        override fun parseResult(resultCode: Int, intent: Intent?): Intent? = intent
    }) { intent: Intent? ->
        if (intent != null) {
            viewModel.submitAuthorizationResponse(intent)
        }
    }

    val previousWebIds by viewModel.previouslyLoggedOutWebIds.collectAsStateWithLifecycle()
    val isExistingUser = previousWebIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    var customUrl by remember { mutableStateOf("") }
    var customUrlError by remember { mutableStateOf<String?>(null) }
    var showProviderSheet by remember { mutableStateOf(false) }
    val urlInvalidMsg = stringResource(R.string.login_url_invalid)
    val urlNotHttpsMsg = stringResource(R.string.login_url_not_https)

    fun submitCustomUrl() {
        val trimmed = customUrl.trim()
        if (trimmed.isEmpty() || !URLUtil.isValidUrl(trimmed)) {
            customUrlError = urlInvalidMsg
        } else if (!URLUtil.isHttpsUrl(trimmed)) {
            customUrlError = urlNotHttpsMsg
        } else {
            viewModel.loginWithOidcIssuer(trimmed)
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

    LaunchedEffect(viewModel.loginBrowserIntentErrorMessage.value, customUrlError) {
        val messageToShow = customUrlError
            ?: viewModel.loginBrowserIntentErrorMessage.value
        if (!messageToShow.isNullOrEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(messageToShow) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
    ) { innerPaddings ->
        if (viewModel.loginLoading.value) {
            RedirectingState(
                providerName = customUrl.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPaddings),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddings)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            HexagonHero()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = if (isExistingUser) stringResource(R.string.login_back_title)
                    else stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isExistingUser) stringResource(R.string.welcome_back_subtitle)
                    else stringResource(R.string.select_your_server),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                if (isExistingUser) {
                    PreviouslyLoggedInWebIDs(
                        webIDs = previousWebIds,
                        onWebIdClicked = { viewModel.loginWithWebId(it) },
                    )

                    Spacer(Modifier.height(16.dp))
                    OrDivider()
                    Spacer(Modifier.height(16.dp))

                    ChooseServerField(onClick = { showProviderSheet = true })
                    Spacer(Modifier.height(12.dp))
                    CustomUrlField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            customUrlError = null
                        },
                        isError = customUrlError != null,
                        onGo = { submitCustomUrl() },
                    )
                } else {
                    ChooseServerField(onClick = { showProviderSheet = true })
                    Spacer(Modifier.height(16.dp))
                    OrDivider()
                    Spacer(Modifier.height(16.dp))
                    CustomUrlField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            customUrlError = null
                        },
                        isError = customUrlError != null,
                        onGo = { submitCustomUrl() },
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withLink(LinkAnnotation.Url("https://solidproject.org/get_a_pod")) {
                            append(stringResource(R.string.i_dont_have_a_server))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { submitCustomUrl() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = customUrl.isNotBlank(),
                ) {
                    Text(
                        stringResource(R.string.continue_string),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dont_have_a_server),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildAnnotatedString {
                        withLink(LinkAnnotation.Url("https://solidproject.org/get_a_pod")) {
                            append(stringResource(R.string.what_is_a_pod_how_to_get))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))
            }
        }

        if (showProviderSheet) {
            PodProviderBottomSheet(
                podServers = viewModel.podServers,
                onDismiss = { showProviderSheet = false },
                onProviderSelected = { provider ->
                    showProviderSheet = false
                    viewModel.loginWithOidcIssuer(provider.url)
                },
            )
        }
    }

    BackHandler { navController.popBackStack() }
}

@Composable
private fun HexagonHero() {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.TopStart)
                .offset(x = (-60).dp, y = (-40).dp),
        )
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint.copy(alpha = 0.06f)),
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = 20.dp),
        )
    }
}

@Composable
private fun ChooseServerField(onClick: () -> Unit) {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        label = { Text(stringResource(R.string.choose_server)) },
        placeholder = {
            Text(
                stringResource(R.string.select_your_server),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        enabled = false,
        readOnly = true,
        trailingIcon = {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun CustomUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onGo: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_custom_issuer_label)) },
        placeholder = {
            Text(
                stringResource(R.string.login_custom_issuer_placeholder),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun PreviouslyLoggedInWebIDs(
    webIDs: List<String>,
    onWebIdClicked: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        webIDs.forEach { webId ->
            PreviouslyLoggedInWebIDItem(webId = webId, onClick = { onWebIdClicked(webId) })
        }
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
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = webId,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@Composable
private fun RedirectingState(
    providerName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.redirecting),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connecting_to, providerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodProviderBottomSheet(
    podServers: List<UiPodServer>,
    onDismiss: () -> Unit,
    onProviderSelected: (UiPodServer) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.select_your_server),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            podServers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderSelected(provider) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(provider.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(provider.iconTint),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
