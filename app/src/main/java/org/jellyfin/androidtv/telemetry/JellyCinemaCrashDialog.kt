package org.jellyfin.androidtv.telemetry

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import org.acra.dialog.CrashReportDialog

/**
 * The "Jelly Cinema stopped — send a report?" question, made for the remote: the buttons have focus
 * right away (the stock dialog focuses its text first, so OK did nothing until you pressed Down).
 */
class JellyCinemaCrashDialog : CrashReportDialog() {
	override fun buildCustomView(savedInstanceState: Bundle?): View =
		super.buildCustomView(savedInstanceState).also { view ->
			view.isFocusable = false
			(view as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		}

	override fun buildAndShowDialog(savedInstanceState: Bundle?) {
		super.buildAndShowDialog(savedInstanceState)
		dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button -> button.post { button.requestFocus() } }
	}
}
