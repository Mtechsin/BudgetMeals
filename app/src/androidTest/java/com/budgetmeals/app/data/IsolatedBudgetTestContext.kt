package com.budgetmeals.app.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Keeps instrumentation data away from the app's normal database and preferences. */
internal class IsolatedBudgetTestContext(
    base: Context,
    private val prefix: String,
) : ContextWrapper(base) {
    private val delegate = base

    override fun getApplicationContext(): Context = this

    override fun getDatabasePath(name: String): File = delegate.getDatabasePath(prefixed(name)).also {
        it.parentFile?.mkdirs()
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
    ): SQLiteDatabase = delegate.openOrCreateDatabase(prefixed(name), mode, factory)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase = delegate.openOrCreateDatabase(prefixed(name), mode, factory, errorHandler)

    override fun getSharedPreferences(name: String, mode: Int) =
        delegate.getSharedPreferences(prefixed(name), mode)

    override fun deleteDatabase(name: String): Boolean = delegate.deleteDatabase(prefixed(name))

    override fun deleteSharedPreferences(name: String): Boolean = delegate.deleteSharedPreferences(prefixed(name))

    fun clearPreferences(name: String) {
        getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefixed(name: String) = "$prefix$name"
}
