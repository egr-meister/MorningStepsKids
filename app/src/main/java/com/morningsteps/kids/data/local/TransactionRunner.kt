package com.morningsteps.kids.data.local

/** Runs a block atomically. Room-backed in the app, a plain call in unit tests. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
