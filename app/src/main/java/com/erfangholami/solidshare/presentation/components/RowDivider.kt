package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun RowDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RowDividerPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "First item")
            RowDivider()
            Text(text = "Second item")
        }
    }
}
