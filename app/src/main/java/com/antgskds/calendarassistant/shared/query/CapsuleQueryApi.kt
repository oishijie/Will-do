package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import kotlinx.coroutines.flow.StateFlow

interface CapsuleQueryApi {
    val uiState: StateFlow<CapsuleUiState>

    /**
     * 当前挂在实况通知上的随手记 id，未挂起时为 null。
     *
     * 详情页的「挂实况」按钮靠它还原激活态——胶囊状态原本只在内部流转，
     * UI 拿不到「现在挂的是哪一条」，退出重进就会复位成未挂起。
     */
    val pinnedFlashNoteId: StateFlow<Long?>
}
