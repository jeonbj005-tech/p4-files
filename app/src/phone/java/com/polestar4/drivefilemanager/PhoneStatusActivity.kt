package com.polestar4.drivefilemanager

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
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
    private val authorizing = AtomicBoolean(false)
    private var renewalAttempted = false
    private val linkPrefs by lazy { getSharedPreferences("vehicle_link", MODE_PRIVATE) }
    private lateinit var state: TextView
    private lateinit var log: TextView
    private val bg = Color.rgb(8, 12, 20)
    private val panel = Color.rgb(17, 24, 35)
    private val raised = Color.rgb(26, 36, 50)
    private val accent = Color.rgb(91, 194, 255)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.argb(28, 190, 215, 242))
    }

    private val poll = object : Runnable {
        override fun run() {
            if (api.connected) {
                renewalAttempted = false
                refresh()
            } else if (api.hasCachedAccessToken && !renewalAttempted) {
                renewalAttempted = true
                authorize()
            } else state.text = "Google Drive 연결을 눌러 인증하거나 세션을 갱신하세요."
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(12, 20, 33), bg, Color.rgb(9, 15, 25)))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(24) + insets.systemWindowInsetLeft, dp(24) + insets.systemWindowInsetTop,
                dp(24) + insets.systemWindowInsetRight, dp(18) + insets.systemWindowInsetBottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = "P4  /  VEHICLE LINK"
            textSize = 12f
            letterSpacing = .16f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "P4 차량 상태"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "차량 앱이 Google Drive에 기록한 DVR 연결 상태"
            textSize = 16f
            setTextColor(Color.rgb(164, 178, 196))
            setPadding(0, dp(8), 0, dp(22))
        })
        state = TextView(this).apply {
            text = "상태를 불러오려면 차량과 같은 Google 계정으로 연결하세요."
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(raised, 24)
        }
        root.addView(state, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        root.addView(TextView(this).apply {
            text = "최근 연결 기록"
            textSize = 14f
            letterSpacing = .08f
            setTextColor(Color.rgb(164, 178, 196))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(8))
        })
        log = TextView(this).apply {
            text = "차량에서 ‘깨우기 테스트’를 시작하면 기록이 여기에 나타납니다.\n영상이나 원본 파일은 전송하지 않습니다."
            textSize = 16f
            setTextColor(Color.rgb(219, 228, 239))
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(panel, 20)
        }
        root.addView(ScrollView(this).apply { addView(log); isFillViewport = true; clipToPadding = false },
            LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }
        actions.addView(actionButton("Drive 연결", true) { renewalAttempted = false; authorize() }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(8) })
        actions.addView(actionButton("새로고침") { if (api.connected) refresh() else { renewalAttempted = false; authorize() } }, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(actions)
        setContentView(root)
        if (api.connected) refresh()
    }

    private fun actionButton(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(27, 126, 208), Color.rgb(28, 183, 214))).apply { cornerRadius = dp(18).toFloat() }
            else rounded(raised, 18)
        if (Build.VERSION.SDK_INT >= 21) foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(42, 255, 255, 255)), null, rounded(Color.WHITE, 18))
        minHeight = dp(54)
        setOnClickListener { action() }
    }

    private fun authorize() {
        if (!authorizing.compareAndSet(false, true)) return
        state.text = "Google Drive 세션 확인 중…"
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveApi.VEHICLE_SCOPE)))
            .build()
        auth.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) try {
                    startIntentSenderForResult(result.pendingIntent!!.intentSender, 85, null, 0, 0, 0)
                } catch (_: Exception) {
                    authorizing.set(false)
                    state.text = "Google 인증 화면을 열 수 없습니다."
                } else {
                    authorizing.set(false)
                    finishLogin(result)
                }
            }
            .addOnFailureListener { authorizing.set(false); state.text = "Google 인증 실패 · ${it.javaClass.simpleName}" }
    }

    private fun finishLogin(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            state.text = "Google Drive 권한을 받지 못했습니다."
            return
        }
        api.acceptAccessToken(token, DriveApi.VEHICLE_SCOPE)
        refresh()
    }

    @Deprecated("Google authorization resolution callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 85) return
        if (resultCode != RESULT_OK || data == null) {
            authorizing.set(false)
            state.text = "Google Drive 연결이 취소되었습니다."
            return
        }
        try {
            authorizing.set(false)
            finishLogin(auth.getAuthorizationResultFromIntent(data))
        } catch (error: Exception) {
            authorizing.set(false)
            state.text = "Google 인증 실패 · ${error.javaClass.simpleName}"
        }
    }

    private fun refresh() {
        if (!api.connected || !refreshing.compareAndSet(false, true)) return
        state.text = "Google Drive에서 차량 상태 확인 중…"
        executor.execute {
            try {
                var content: String? = null
                val savedId = linkPrefs.getString("status_file_id", null)
                if (!savedId.isNullOrBlank()) try {
                    content = api.readTextFile(savedId)
                } catch (error: AuthFailure) {
                    if (isStaleWakeStatusFailure(error.reason)) linkPrefs.edit().remove("status_file_id").apply() else throw error
                }
                if (content == null) {
                    val file = api.findWakeStatusFile()
                        ?: throw IllegalStateException("차량 상태 파일이 아직 없습니다")
                    if (file.size !in 1..65_536) throw IllegalStateException("상태 파일 크기가 올바르지 않습니다")
                    content = api.readTextFile(file.id)
                    linkPrefs.edit().putString("status_file_id", file.id).apply()
                }
                val statusContent = content ?: throw IllegalStateException("차량 상태 파일이 아직 없습니다")
                if (statusContent.toByteArray(Charsets.UTF_8).size !in 1..65_536) throw IllegalStateException("상태 파일 크기가 올바르지 않습니다")
                val json = JSONObject(statusContent)
                val updated = json.optLong("updatedAt")
                val time = if (updated > 0) SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA).format(Date(updated)) else "시간 미상"
                val stale = updated <= 0 || System.currentTimeMillis() - updated > 90_000
                val entries = json.optJSONArray("logs")
                val lines = if (entries == null) emptyList() else (0 until entries.length()).map { entries.optString(it) }
                runOnUiThread {
                    state.text = "${if (stale) "마지막 차량 기록" else "차량 기록 수신"} · $time"
                    log.text = lines.takeLast(60).joinToString("\n").ifBlank { "아직 기록이 없습니다." }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (error is AuthFailure && error.code == "TOKEN_ERROR") {
                        api.markAccessTokenExpired()
                        renewalAttempted = false
                    }
                    state.text = if (error is IllegalStateException && error.message?.contains("아직") == true)
                        "차량 기록을 기다리는 중" else {
                        val detail=(error as? AuthFailure)?.let{"${it.code} · ${it.message}"}?:error.javaClass.simpleName
                        "Drive 확인 실패 · ${detail.take(180)}"
                    }
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
