package com.kixyu9527.kixyubook.core.database

import kotlinx.coroutines.sync.Mutex

/** Import, deletion, indexing asset writes and restore installation cannot replace files together. */
internal object LibraryStorageGate { val mutex = Mutex() }
