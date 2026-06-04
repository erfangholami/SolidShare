package com.erfangholami.solidshare.util

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StringProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) context.getString(id)
        else context.getString(id, *formatArgs)
}
