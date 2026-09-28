package ru.rudra.androidos.pa.sync

import android.content.Context
import ru.rudra.androidos.pa.domain.sync.FilePairStore
import ru.rudra.androidos.pa.domain.sync.PairStore
import java.io.File

/**
 * Android binding of the shared [FilePairStore]: same format and semantics as
 * the laptop peer, rooted in the app's private files dir.
 */
class AndroidPairStore(context: Context) : PairStore by FilePairStore(File(context.filesDir, "pairing"))