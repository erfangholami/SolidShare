package com.erfangholami.solidshare.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun LazyListState.isScrollingUp(): State<Boolean> =
    rememberIsScrollingUp({ firstVisibleItemIndex }, { firstVisibleItemScrollOffset })

@Composable
fun LazyGridState.isScrollingUp(): State<Boolean> =
    rememberIsScrollingUp({ firstVisibleItemIndex }, { firstVisibleItemScrollOffset })

@Composable
private fun rememberIsScrollingUp(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
): State<Boolean> {
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex()) }
    var previousOffset by remember { mutableIntStateOf(firstVisibleItemScrollOffset()) }
    return remember {
        derivedStateOf {
            val index = firstVisibleItemIndex()
            val offset = firstVisibleItemScrollOffset()
            val scrollingUp = if (previousIndex != index) {
                previousIndex > index
            } else {
                previousOffset >= offset
            }
            previousIndex = index
            previousOffset = offset
            scrollingUp
        }
    }
}
