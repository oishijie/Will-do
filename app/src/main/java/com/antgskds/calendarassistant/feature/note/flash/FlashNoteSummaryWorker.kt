package com.antgskds.calendarassistant.feature.note.flash

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.application.ai.ApiCallResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.ApiModelProvider
import com.antgskds.calendarassistant.feature.recognition.application.ai.activeAiConfig
import com.antgskds.calendarassistant.feature.recognition.application.ai.isConfigured
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelMessage
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelRequest
import com.antgskds.calendarassistant.feature.settings.data.SettingsRepository
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSummaryState
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import java.util.concurrent.TimeUnit

/**
 * 闪记摘要生成。
 *
 * 走 WorkManager 而非自起协程：App 被杀后任务仍会完成，用户下次进列表能看到摘要。
 */
class FlashNoteSummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        val content = inputData.getString(KEY_CONTENT).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        if (noteId <= 0 || content.isBlank()) return Result.success()

        val app = applicationContext as? App ?: return Result.failure()
        val settings = SettingsRepository(applicationContext).loadSettings()
        // 用户在「设置 → 功能设置 → 随手记」关掉了 AI 摘要：直接把摘要段落标记为不生成并结束。
        // 兜住两类情况——创建时按设置已落 SKIPPED 的条目不会走到这里，
        // 但旧版本遗留的 PENDING 条目、以及将来新增的调用点都由这一层保证不白调模型。
        if (!settings.flashNoteAiSummaryEnabled) {
            app.noteCenter.updateFlashSummary(noteId, FlashNoteSummaryState.SKIPPED)
            return Result.success()
        }
        val config = settings.activeAiConfig()
        if (!config.isConfigured()) {
            // 未配置模型时不重试，等用户配好再手动重新收藏。
            app.noteCenter.updateFlashSummary(noteId, FlashNoteSummaryState.FAILED)
            return Result.success()
        }

        return try {
            val summary = requestSummary(config.key, config.url, config.name, title, content)
            when {
                summary.isNullOrBlank() -> {
                    app.noteCenter.updateFlashSummary(noteId, FlashNoteSummaryState.FAILED)
                    Result.success()
                }
                else -> {
                    app.noteCenter.updateFlashSummary(noteId, summary)
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "flash summary failed noteId=$noteId attempt=${runAttemptCount}", e)
            // 重试上限 2 次，之后标记失败不再消耗额度。
            if (runAttemptCount >= MAX_ATTEMPTS) {
                runCatching { app.noteCenter.updateFlashSummary(noteId, FlashNoteSummaryState.FAILED) }
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun requestSummary(
        apiKey: String,
        baseUrl: String,
        modelName: String,
        title: String,
        content: String
    ): String? {
        val request = ModelRequest(
            model = modelName.ifBlank { DEFAULT_MODEL },
            messages = listOf(
                ModelMessage("system", SUMMARY_SYSTEM_PROMPT),
                ModelMessage("user", buildUserPrompt(title, content))
            ),
            temperature = 0.3
        )
        val result = ApiModelProvider.generate(
            request = request,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName.ifBlank { DEFAULT_MODEL },
            disableThinking = true
        )
        return when (result) {
            is ApiCallResult.Success -> result.content.trim().take(MAX_SUMMARY_LENGTH)
            is ApiCallResult.Failure -> {
                Log.w(TAG, "flash summary api failed kind=${result.kind} status=${result.statusCode}")
                null
            }
        }
    }

    private fun buildUserPrompt(title: String, content: String): String = buildString {
        if (title.isNotBlank()) appendLine("标题：$title")
        appendLine("正文：")
        appendLine(content.take(MAX_INPUT_LENGTH))
    }

    companion object {
        private const val TAG = "FlashNoteSummary"
        private const val DEFAULT_MODEL = "deepseek-chat"
        private const val MAX_ATTEMPTS = 2
        private const val MAX_SUMMARY_LENGTH = 200
        private const val MAX_INPUT_LENGTH = 6000

        const val KEY_NOTE_ID = "note_id"
        const val KEY_TITLE = "title"
        const val KEY_CONTENT = "content"

        /** 摘要只需要抽取与概括，不需要推理，固定关闭思考以压缩延迟。 */
        private val SUMMARY_SYSTEM_PROMPT = """
请为以下网页正文撰写一段简短的摘要。

要求：
1. 控制在 50~100 字。
2. 准确提炼核心主旨与关键信息，不写空话套话。
3. 客观还原原文语义，不添加主观解读或评价。
4. 直接输出摘要正文，不要标题、前言、说明或markdown 标记。
""".trimIndent()

        /** 为指定闪记条目排队生成摘要。[content] 为空时直接跳过。 */
        fun enqueue(context: Context, noteId: Long, title: String, content: String) {
            if (noteId <= 0 || content.isBlank()) return
            val request = OneTimeWorkRequestBuilder<FlashNoteSummaryWorker>()
                .setInputData(
                    Data.Builder()
                        .putLong(KEY_NOTE_ID, noteId)
                        .putString(KEY_TITLE, title)
                        .putString(KEY_CONTENT, content)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "flash_summary_$noteId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
