package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.graphics.Typeface
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.entity.UserFontEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFontRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: FontDao,
) : FontRepository {
    override fun observeFonts() = dao.observeFonts().map { list -> list.map { UserFont(it.uuid, it.name, it.filePath, it.createdTime) } }

    override suspend fun importFont(uriString: String): Result<UserFont> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = uriString.toUri()
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "自定义字体"
            require(name.endsWith(".ttf", true) || name.endsWith(".otf", true)) { "仅支持 TTF / OTF" }
            val uuid = UUID.randomUUID().toString()
            val target = File(context.filesDir, "fonts/$uuid.${name.substringAfterLast('.').lowercase()}").also { it.parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) } ?: error("无法读取字体")
            Typeface.createFromFile(target)
            val model = UserFont(uuid, name.substringBeforeLast('.'), target.absolutePath, System.currentTimeMillis())
            dao.insert(UserFontEntity(model.uuid, model.name, model.filePath, model.createdTime))
            model
        }
    }

    override suspend fun deleteFont(fontUuid: String) = withContext(Dispatchers.IO) {
        dao.getFont(fontUuid)?.filePath?.let(::File)?.delete()
        dao.delete(fontUuid)
    }

    override suspend fun getFont(fontUuid: String) = withContext(Dispatchers.IO) {
        dao.getFont(fontUuid)?.let { UserFont(it.uuid, it.name, it.filePath, it.createdTime) }
    }
}
