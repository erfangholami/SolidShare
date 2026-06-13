package com.erfangholami.solidshare.presentation.container

import com.erfangholami.solidshare.R

enum class SortField {
    DEFAULT, NAME, LAST_MODIFIED, SIZE;

    fun labelRes(): Int = when (this) {
        DEFAULT -> R.string.sort_default
        NAME -> R.string.sort_name
        LAST_MODIFIED -> R.string.sort_last_modified
        SIZE -> R.string.sort_size
    }
}

enum class SortDirection { ASCENDING, DESCENDING }

enum class ViewMode { LIST, GRID }
