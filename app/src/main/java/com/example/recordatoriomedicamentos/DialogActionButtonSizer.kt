package com.example.recordatoriomedicamentos

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Mantiene idénticos los límites visibles de los botones en las parejas de acciones modales. */
internal object DialogActionButtonSizer {
    fun equalize(dialog: Dialog, firstButtonId: Int, secondButtonId: Int) {
        val first = dialog.findViewById<View>(firstButtonId) ?: return
        val second = dialog.findViewById<View>(secondButtonId) ?: return
        val row = first.parent as? LinearLayout ?: return
        first.post {
            if (row.width <= 0 || first.width <= 0 || second.width <= 0) return@post
            val commonWidth = minOf(first.width, second.width)
            val commonHeight = minOf(first.height, second.height)
            row.gravity = Gravity.CENTER_HORIZONTAL
            listOf(first, second).forEach { button ->
                val params = button.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
                params.width = commonWidth
                params.height = commonHeight
                params.weight = 0f
                params.marginStart = 0
                params.marginEnd = 0
                button.minimumWidth = 0
                button.layoutParams = params
            }
        }
    }
}
