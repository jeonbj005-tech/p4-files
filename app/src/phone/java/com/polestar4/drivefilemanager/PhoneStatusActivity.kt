package com.polestar4.drivefilemanager

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PhoneStatusActivity : Activity() {
    private val api by lazy { DriveApi(this) }
    private val auth by lazy { Identity.getAuthorizationClient(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshing = AtomicBoolean(false)
    private lateinit var state: TextView
    private lateinit var log: TextView
    private val bg = Color.rgb(10, 12, 15)
    private val panel = Color.rgb(22, 26, 31)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private val poll = object : Runnable {
        override fun run() {
            if (api.connected) refresh() else state.text = "Google Drive 연결을 눌러 다시 인증하세요."
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "P4 차량 상태"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "차량 앱이 Google Drive에 기록한 DVR 연결 상태"
            textSize = 15f
            setTextColor(Color.rgb(151, 162, 174))
            setPadding(0, dp(8), 0, dp(18))
        })
        state = TextView(this).apply {
            text = "상태를 불러오려면 차량과 같은 Google 계정으로 연결하세요."
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply { setColor(panel); cornerRadius = dp(24).toFloat() }
        }
        root.addView(state)
        log = TextView(this).apply {
            text = "차량에서 ‘깨우기 테스트’를 시작하면 기록이 여기에 나타납니다.\n영상이나 원본 파일은 전송하지 않습니다."
            textSize = 15f
            setTextColor(Color.rgb(205, 213, 222))
            setPadding(dp(4), dp(18), dp(4), dp(18))
        }
        root.addView(ScrollView(this).apply { addView(log) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        actions.addView(actionButton("Drive 연결") { login() })
        actions.addView(actionButton("새로고침") { if (api.connected) refresh() else login() })
        root.addView(actions)
        setContentView(root)
        if (api.connected) refresh()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun login() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveApi.DRIVE_SCOPE)))
            .build()
        auth.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) try {
                    startIntentSenderForResult(result.pendingIntent!!.intentSender, 85, null, 0, 0, 0)
                } catch (_: Exception) {
                    state.text = "Google 인증 화면을 열 수 없습니다."
                } else finishLogin(result)
            }
            .addOnFailureListener { state.text = "Google 인증 실패 · ${it.javaClass.simpleName}" }
    }

    private fun finishLogin(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            state.text = "Google Drive 권한을 받지 못했습니다."
            return
        }
        api.acceptAccessToken(token, DriveApi.DRIVE_SCOPE)
        refresh()
    }

    @Deprecated("Google authorization resolution callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 85) return
        if (resultCode != RESULT_OK || data == null) {
            state.text = "Google Drive 연결이 취소되었습니다."
            return
        }
        try {
            finishLogin(auth.getAuthorizationResultFromIntent(data))
        } catch (error: Exception) {
            state.text = "Google 인증 실패 · ${error.javaClass.simpleName}"
        }
    }

    private fun refresh() {
        if (!api.connected || !refreshing.compareAndSet(false, true)) return
        state.text = "Google Drive에서 차량 상태 확인 중…"
        executor.execute {
            try {
                val file = api.findWakeStatusFile()
                    ?: throw IllegalStateException("차량 상태 파일이 아직 없습니다")
                if (file.size !in 1..65_536) throw IllegalStateException("상태 파일 크기가 올바르지 않습니다")
                val json = JSONObject(api.readTextFile(file.id))
                val updated = json.optLong("updatedAt")
                val time = if (updated > 0) SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA).format(Date(updated)) else "시간 미상"
                val stale = updated <= 0 || System.currentTimeMillis() - updated > 90_000
                val entries = json.optJSONArray("logs")
                val lines = if (entries == null) emptyList() else (0 until entries.length()).map { entries.optString(it) }
                runOnUiThread {
                    state.text = "${if (stale) "마지막 응답" else json.optString("status", "상태 미상")} · $time"
                    log.text = lines.takeLast(60).joinToString("\n").ifBlank { "아직 기록이 없습니다." }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    state.text = if (error is IllegalStateException && error.message?.contains("아직") == true)
                        "차량 기록을 기다리는 중" else "Drive 확인 실패 · ${(error as? AuthFailure)?.code ?: error.javaClass.simpleName}"
                    if (state.text == "차량 기록을 기다리는 중") log.text = "차량에서 Drive에 연결한 뒤 ‘깨우기 테스트’를 시작하세요."
                }
            } finally {
                refreshing.set(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
