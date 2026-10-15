package com.polestar4.drivefilemanager

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity:Activity(){
    private enum class SortMode(val label:String){NAME_ASC("이름 A–Z"),NAME_DESC("이름 Z–A"),NEWEST("최신순"),OLDEST("오래된순"),SIZE_DESC("용량 큰순"),SIZE_ASC("용량 작은순")}
    private val api by lazy { DriveApi(this) }
    private val dashcamApi by lazy { DashcamApi() }
    private val appUpdater by lazy { AppUpdater(this) }
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private val worker=Executors.newSingleThreadExecutor()
    private val thumbWorker=Executors.newFixedThreadPool(3)
    private val dashcamThumbs=android.util.LruCache<String,Bitmap>(40)
    private val dashcamThumbViews=ConcurrentHashMap<String,ImageView>()
    private val dashcamThumbInFlight=ConcurrentHashMap.newKeySet<String>()
    private val dashcamThumbnailDays=linkedSetOf<String>()
    private val cancelled=AtomicBoolean(false)
    private var vehicleAuthCancel:AtomicBoolean?=null
    private var vehicleAuthDialog:AlertDialog?=null
    private lateinit var body:LinearLayout
    private lateinit var bodyScroll:ScrollView
    private lateinit var title:TextView
    private lateinit var subtitle:TextView
    private lateinit var state:TextView
    private lateinit var actions:LinearLayout
    private lateinit var progress:ProgressBar
    private var busy=false
    private var mode="home"
    private var driveSort=SortMode.NAME_ASC
    private var localSort=SortMode.NAME_ASC
    private var driveItems=listOf<DriveApi.DriveFile>()
    private var localItems=listOf<DocumentFile>()
    private var searchText=""
    private var typeFilter="전체"
    private var selectionMode=false
    private val selectedDrive=linkedSetOf<String>()
    private val selectedLocal=linkedSetOf<String>()
    private var localClipboard:Pair<DocumentFile,Boolean>?=null
    private val handler=Handler(Looper.getMainLooper())
    private val queueRefresh=object:Runnable{override fun run(){if(mode=="queue"){renderQueue();handler.postDelayed(this,1000)}}}
    private val remotePath=mutableListOf("root" to "Google Drive")
    private val localPath=mutableListOf<DocumentFile>()
    private var pendingDownload:DriveApi.DriveFile?=null
    private var pendingDashcam:DashcamApi.Media?=null
    private var pendingInstallUri:Uri?=null
    private var dashcamItems=listOf<Pair<DashcamApi.Category,DashcamApi.Media>>()
    private var dashcamCategories=listOf<DashcamApi.Category>()
    private val dashcamOffsets=linkedMapOf<String,Int>()
    private val dashcamComplete=linkedSetOf<String>()
    private var dashcamDay:String?=null
    private var dashcamKind="상시"
    private var dashcamVisibleLimit=50
    private var dashcamLoading=false
    private var firstDashcamIntro=true
    private val dashcamMonth=java.util.Calendar.getInstance().apply{set(java.util.Calendar.DAY_OF_MONTH,1)}
    private val authorizationRequestCode=74
    private val bg=Color.rgb(10,12,15)
    private val surface=Color.rgb(22,26,31)
    private val surfaceRaised=Color.rgb(30,35,42)
    private val muted=Color.rgb(151,162,174)
    private val accent=Color.rgb(92,164,255)
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun text(s:String,size:Float=16f,color:Int=Color.rgb(244,246,248))=TextView(this).apply{text=s;textSize=size;setTextColor(color);fontFeatureSettings="kern"}
    private fun panel(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(16).toFloat()}
    private fun caption(value:String){subtitle.text=value;subtitle.visibility=if(value.isBlank())View.GONE else View.VISIBLE}
    private fun carLayout()=packageManager.hasSystemFeature("android.hardware.type.automotive")||(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&resources.configuration.screenWidthDp>=600)
    private fun button(s:String,click:()->Unit)=Button(this).apply{text=s;isAllCaps=false;textSize=if(carLayout())18f else 13f;setTextColor(Color.rgb(226,237,251));setPadding(dp(if(carLayout())20 else 13),0,dp(if(carLayout())20 else 13),0);minHeight=dp(if(carLayout())54 else 42);minimumWidth=0;minWidth=0;background=panel(surfaceRaised);setOnClickListener{if(!busy)click()}}
    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=bg;window.navigationBarColor=bg;layout();restoreSavedStorage();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),90);showDashcam(true)}
    private fun layout(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(8));setBackgroundColor(bg)}
        root.setOnApplyWindowInsetsListener { view,insets -> view.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(10)+insets.systemWindowInsetTop,dp(16)+insets.systemWindowInsetRight,dp(8)+insets.systemWindowInsetBottom);insets }
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(button("←"){back()},LinearLayout.LayoutParams(dp(48),dp(44)).apply{rightMargin=dp(10)})
        title=text("P4 FILES",if(carLayout())34f else 24f).apply{setTypeface(null,Typeface.BOLD);letterSpacing=.03f;gravity=Gravity.CENTER_VERTICAL};header.addView(title,LinearLayout.LayoutParams(0,dp(if(carLayout())60 else 46),1f))
        header.addView(button("HOME"){home()});root.addView(header)
        subtitle=text("",if(carLayout())17f else 12f,muted);subtitle.setPadding(dp(2),dp(3),0,dp(if(carLayout())14 else 10));root.addView(subtitle)
        actions=LinearLayout(this).apply{dividerDrawable=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);showDividers=LinearLayout.SHOW_DIVIDER_MIDDLE;dividerPadding=dp(4)};root.addView(HorizontalScrollView(this).apply{addView(actions);isHorizontalScrollBarEnabled=false})
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        bodyScroll=ScrollView(this).apply{addView(body);isFillViewport=true};root.addView(bodyScroll,LinearLayout.LayoutParams(-1,0,1f))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;visibility=View.GONE};root.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        state=text("준비됨",if(carLayout())20f else 11f,muted).apply{setPadding(dp(if(carLayout())18 else 12),dp(if(carLayout())12 else 7),dp(if(carLayout())18 else 12),dp(if(carLayout())12 else 7));background=panel(surface)};root.addView(state,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(if(carLayout())10 else 6)})
        setContentView(root)
    }
    private fun card(icon:String,name:String,detail:String,click:()->Unit){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));background=panel(surface);setOnClickListener{if(!busy)click()}}
        row.addView(text(icon,23f,accent).apply{gravity=Gravity.CENTER;background=panel(Color.rgb(28,47,68))},LinearLayout.LayoutParams(dp(48),dp(48)).apply{rightMargin=dp(16)})
        val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text(name,18f).apply{setTypeface(null,Typeface.BOLD)});addView(text(detail,13f,muted).apply{setPadding(0,dp(6),0,0)})}
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f));row.addView(text("›",26f,muted))
        body.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
    }
    private fun entryRow(name:String,badge:String,meta:String,folder:Boolean,secondary:(()->Unit)?=null,click:()->Unit){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(9),dp(12),dp(9));background=panel(surface);setOnClickListener{if(!busy)click()}}
        val badgeView=text(if(folder)"DIR" else badge.take(5),10f,if(folder)Color.rgb(210,237,255) else Color.rgb(232,238,245)).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD);letterSpacing=.08f;background=panel(if(folder)Color.rgb(23,86,130) else Color.rgb(58,64,72))}
        row.addView(badgeView,LinearLayout.LayoutParams(dp(58),dp(38)).apply{rightMargin=dp(12)})
        val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        labels.addView(text((if(folder)"▰  " else "")+name,16f).apply{setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=TextUtils.TruncateAt.END})
        labels.addView(text((if(folder)"폴더  ·  " else "파일  ·  ")+meta,12f,muted).apply{setPadding(0,dp(3),0,0);maxLines=1;ellipsize=TextUtils.TruncateAt.END})
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f));val more=text(if(secondary==null)"›" else "⋮",22f,Color.rgb(122,135,149)).apply{gravity=Gravity.CENTER;setOnClickListener{if(!busy)(secondary?:click).invoke()}};row.addView(more,LinearLayout.LayoutParams(dp(32),dp(40)))
        if(secondary!=null)row.setOnLongClickListener{if(!busy)secondary();true}
        body.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(6)})
    }
    private fun section(s:String){body.addView(text(s.uppercase(),11f,muted).apply{letterSpacing=.1f;setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(15),0,dp(9))})}
    private fun home(){mode="home";selectionMode=false;selectedDrive.clear();selectedLocal.clear();title.text="P4 FILES";caption("차량과 클라우드 파일을 한곳에서");body.removeAllViews();actions.removeAllViews()
        actions.addView(button("대시캠"){showDashcam()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("전송 대기열"){showQueue()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("즐겨찾기"){showFavorites()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("최근 항목"){showRecent()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("인증 진단"){showDiagnostics()},LinearLayout.LayoutParams(-2,dp(42)))
        if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&resources.configuration.screenWidthDp>=700){showDualPane();return}
        section("기기 저장공간")
        card("▣","USB / 내장 저장소",localPath.firstOrNull()?.let{"연결됨 · ${it.name?:"선택한 저장소"}"}?:"최초 한 번만 시스템에서 폴더 선택"){openSavedStorage()}
        card("↓","다운로드한 파일","Drive에서 받은 파일 열기"){downloads()}
        card("＋","파일 선택","기존 파일 선택기로 열기 / 업로드"){pickFiles()}
        section("차량 기능")
        card("●","대시캠 영상","상시·긴급·서라운드·사진 조회 / 재생 / 저장"){showDashcam()}
        section("클라우드 저장공간")
        card("△","Google Drive",if(api.connected){if(api.vehicleMode)"차량 호환 모드 · 앱 파일" else "전체 권한 연결됨 · 폴더 열기"}else "계정 연결"){if(api.connected)showDrive() else login()}
        if(api.connected)actions.addView(button("Drive 연결 해제"){api.logout();home()})
    }
    private fun openPolarisDashcam(){
        val packageName="com.ps4.dashboard"
        try{
            val intent=Intent().setComponent(ComponentName(packageName,"$packageName.DashboardActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            state.text="순정 대시캠 실행 요청 완료"
        }catch(_:Exception){
            try{
                val launch=packageManager.getLaunchIntentForPackage(packageName)
                if(launch==null)throw ActivityNotFoundException("$packageName 없음")
                startActivity(launch)
                state.text="순정 대시캠 실행 요청 완료"
            }catch(e:Exception){
                state.text="대시캠 실행 실패"
                AlertDialog.Builder(this).setTitle("대시캠을 열 수 없음")
                    .setMessage("차량에 순정 Polaris 대시캠 앱이 설치되어 있고 실행 허용 상태인지 확인하세요.\n\n오류: ${e.message?:e.javaClass.simpleName}")
                    .setPositiveButton("확인",null).show()
            }
        }
    }
    private fun showDashcam(loadFromVehicle:Boolean=false){
        mode="dashcam";title.text="대시캠";caption("");actions.removeAllViews();body.removeAllViews()
        if(loadFromVehicle){dashcamThumbnailDays.clear();dashcamThumbViews.clear()}
        loadDashcamCache()
        dashcamLoading=loadFromVehicle
        if(dashcamDay==null)dashcamDay=SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(Date())
        renderDashcam(dashcamItems)
        if(loadFromVehicle)loadDashcamMonth() else state.text="캐시된 내역 ${dashcamItems.size}개 · 차량 조회는 '내역 새로고침'"
    }
    private fun loadDashcamMonth(){
        dashcamLoading=true;renderDashcam(dashcamItems)
        val targetStart=java.util.Calendar.getInstance().apply{timeInMillis=dashcamMonth.timeInMillis;set(java.util.Calendar.DAY_OF_MONTH,1);set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
        val targetEnd=java.util.Calendar.getInstance().apply{timeInMillis=targetStart;add(java.util.Calendar.MONTH,1)}.timeInMillis
        task("${dashcamMonth.get(java.util.Calendar.MONTH)+1}월 내역 읽는 중"){
            try{
                dashcamApi.withFileList {
                if(dashcamCategories.isEmpty())dashcamCategories=dashcamApi.categories()
                val merged=dashcamItems.toMutableList()
                dashcamCategories.forEach{category->
                    val existingKeys=merged.filter{it.first.type==category.type}.map{"${it.second.type}/${it.second.id}/${it.second.name}"}.toHashSet()
                    if(existingKeys.isNotEmpty()){
                        var refreshOffset=0
                        refresh@ while(refreshOffset<1000){
                            val page=dashcamApi.list(category,refreshOffset,200)
                            if(page.isEmpty())break
                            val hitKnown=page.any{"${it.type}/${it.id}/${it.name}" in existingKeys}
                            val fresh=page.filterNot{"${it.type}/${it.id}/${it.name}" in existingKeys}
                            merged.addAll(fresh.map{category to it});existingKeys.addAll(fresh.map{"${it.type}/${it.id}/${it.name}"})
                            if(fresh.isNotEmpty())dashcamOffsets[category.type]=dashcamOffsets.getOrDefault(category.type,0)+fresh.size
                            update("${category.label} 새 영상 ${fresh.size}개 확인")
                            if(hitKnown||page.size<200)break@refresh
                            refreshOffset+=200
                        }
                    }
                    var offset=dashcamOffsets.getOrDefault(category.type,0)
                    while(category.type !in dashcamComplete){
                        val known=merged.filter{it.first.type==category.type}.map{dashcamTime(it.second.dateTime,it.second.name)}
                        if(known.any{it in targetStart until targetEnd}&&known.minOrNull()?.let{it<targetStart}==true)break
                        val page=dashcamApi.list(category,offset,200)
                        val keys=merged.asSequence().map{"${it.second.type}/${it.second.id}/${it.second.name}"}.toHashSet()
                        val unique=page.filter{keys.add("${it.type}/${it.id}/${it.name}")}
                        merged.addAll(unique.map{category to it})
                        offset+=page.size;dashcamOffsets[category.type]=offset
                        update("${category.label} ${offset}개 확인 · ${dashcamMonth.get(java.util.Calendar.MONTH)+1}월 찾는 중…")
                        if(page.size<200||unique.isEmpty()){dashcamComplete.add(category.type);break}
                        val pageTimes=page.map{dashcamTime(it.dateTime,it.name)}.filter{it>0}
                        if(pageTimes.any{it in targetStart until targetEnd}&&pageTimes.minOrNull()!!<targetStart)break
                    }
                }
                val items=merged.distinctBy{"${it.second.type}/${it.second.id}/${it.second.name}"}.sortedByDescending{dashcamTime(it.second.dateTime,it.second.name)}
                dashcamItems=items;saveDashcamCache()
                runOnUiThread{
                    dashcamLoading=false
                    selectDashcamDayAfterLoad(items,targetStart,targetEnd)
                    firstDashcamIntro=false
                    renderDashcam(items)
                    val monthCount=items.count{dashcamTime(it.second.dateTime,it.second.name) in targetStart until targetEnd}
                    state.text="${dashcamMonth.get(java.util.Calendar.MONTH)+1}월 ${monthCount}개 · 누적 ${items.size}개 · 캐시 저장됨"
                }
                }
            }catch(e:Exception){runOnUiThread{dashcamLoading=false;renderDashcam(dashcamItems)};throw e}
        }
    }
    private fun renderDashcam(items:List<Pair<DashcamApi.Category,DashcamApi.Media>>){
        dashcamItems=items;mode="dashcam";body.removeAllViews()
        if(dashcamDay==null&&items.isNotEmpty()){
            dashcamDay=dashcamDayKey(items.first().second)
            try{dashcamMonth.time=SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).parse(dashcamDay!!)?:Date()}catch(_:Exception){}
            dashcamMonth.set(java.util.Calendar.DAY_OF_MONTH,1)
        }
        caption("")
        actions.removeAllViews()
        actions.addView(button("내역 새로고침"){showDashcam(true)},LinearLayout.LayoutParams(-2,dp(if(carLayout())56 else 42)).apply{rightMargin=dp(8)})
        actions.addView(button("전체 다시 읽기"){confirmDashcamCacheReset()},LinearLayout.LayoutParams(-2,dp(if(carLayout())56 else 42)).apply{rightMargin=dp(8)})
        actions.addView(button("앱 업데이트"){checkAppUpdate()},LinearLayout.LayoutParams(-2,dp(if(carLayout())56 else 42)))
        if(firstDashcamIntro)body.addView(dashcamIntro(),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val holder=LinearLayout(this).apply{orientation=if(landscape)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        val calendarPane=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(12),dp(18),dp(10))}
        val recordsPane=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(12),dp(8),dp(10))}
        renderDashcamMonth(calendarPane)
        renderDashcamRecords(recordsPane,landscape)
        if(holder.orientation==LinearLayout.HORIZONTAL){holder.addView(calendarPane,LinearLayout.LayoutParams(0,-1,1.18f));holder.addView(recordsPane,LinearLayout.LayoutParams(0,-1,.82f))}
        else{holder.addView(calendarPane);holder.addView(recordsPane)}
        body.addView(holder,LinearLayout.LayoutParams(-1,if(landscape&&bodyScroll.height>0)bodyScroll.height else -2))
        if(landscape)bodyScroll.post{holder.layoutParams=holder.layoutParams.apply{height=bodyScroll.height};bodyScroll.scrollTo(0,0)}
    }
    private fun dashcamIntro():View{
        val glow=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(25,86,151),Color.rgb(36,181,220),Color.rgb(104,82,210))).apply{cornerRadius=dp(22).toFloat()}
        return LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(if(carLayout())26 else 18),dp(if(carLayout())20 else 16),dp(if(carLayout())26 else 18),dp(if(carLayout())20 else 16));background=glow
            addView(text("◉",if(carLayout())42f else 32f,Color.WHITE).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(if(carLayout())64 else 52),dp(if(carLayout())64 else 52)).apply{rightMargin=dp(16)})
            val copy=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
            copy.addView(text("P4 DASHCAM",if(carLayout())24f else 19f,Color.WHITE).apply{setTypeface(null,Typeface.BOLD);letterSpacing=.08f})
            copy.addView(text(if(dashcamLoading)"차량의 오늘 기록과 미리보기를 준비하고 있습니다" else "날짜를 선택하면 해당 기록을 바로 확인할 수 있습니다",if(carLayout())15f else 12f,Color.argb(225,255,255,255)).apply{setPadding(0,dp(5),0,0);maxLines=2})
            addView(copy,LinearLayout.LayoutParams(0,-2,1f))
            addView(ProgressBar(this@MainActivity).apply{isIndeterminate=true},LinearLayout.LayoutParams(dp(28),dp(28)))
        }
    }
    private fun selectDashcamDayAfterLoad(items:List<Pair<DashcamApi.Category,DashcamApi.Media>>,start:Long,end:Long){
        val today=SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(Date())
        val monthItems=items.filter{dashcamTime(it.second.dateTime,it.second.name) in start until end}
        val todayHasItems=monthItems.any{dashcamDayKey(it.second)==today}
        val selected=when{
            todayHasItems->today
            monthItems.isNotEmpty()->dashcamDayKey(monthItems.maxByOrNull{dashcamTime(it.second.dateTime,it.second.name)}!!.second)
            items.isNotEmpty()->dashcamDayKey(items.first().second)
            else->today
        }
        if(selected.isNotBlank())dashcamDay=selected
    }
    private fun renderDashcamMonth(host:LinearLayout){
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(button("◀"){dashcamMonth.add(java.util.Calendar.MONTH,-1);dashcamVisibleLimit=50;dashcamDay=String.format(Locale.KOREA,"%04d-%02d-01",dashcamMonth.get(java.util.Calendar.YEAR),dashcamMonth.get(java.util.Calendar.MONTH)+1);loadDashcamMonth()},LinearLayout.LayoutParams(dp(if(carLayout())78 else 58),dp(if(carLayout())58 else 42)))
        header.addView(text(String.format(Locale.KOREA,"%d년 %d월",dashcamMonth.get(java.util.Calendar.YEAR),dashcamMonth.get(java.util.Calendar.MONTH)+1),dvrText(24f)).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(if(carLayout())70 else 56),1f))
        header.addView(button("▶"){dashcamMonth.add(java.util.Calendar.MONTH,1);dashcamVisibleLimit=50;dashcamDay=String.format(Locale.KOREA,"%04d-%02d-01",dashcamMonth.get(java.util.Calendar.YEAR),dashcamMonth.get(java.util.Calendar.MONTH)+1);loadDashcamMonth()},LinearLayout.LayoutParams(dp(if(carLayout())78 else 58),dp(if(carLayout())58 else 42)))
        host.addView(header)
        val weekdays=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        arrayOf("일","월","화","수","목","금","토").forEachIndexed{i,label->weekdays.addView(text(label,dvrText(14f),when(i){0->Color.rgb(255,120,120);6->Color.rgb(110,175,255);else->muted}).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(if(carLayout())48 else 38),1f))}
        host.addView(weekdays)
        val year=dashcamMonth.get(java.util.Calendar.YEAR);val month=dashcamMonth.get(java.util.Calendar.MONTH)
        val first=java.util.Calendar.getInstance().apply{set(year,month,1,0,0,0)}
        val leading=first.get(java.util.Calendar.DAY_OF_WEEK)-1;val days=first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        var cell=0
        while(cell<leading+days){
            val week=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            repeat(7){
                val day=cell-leading+1
                if(day in 1..days){
                    val key=String.format(Locale.KOREA,"%04d-%02d-%02d",year,month+1,day)
                    val records=dashcamItems.filter{dashcamDayKey(it.second)==key}
                    week.addView(dashcamDayCell(day,key,records),LinearLayout.LayoutParams(0,dp(if(carLayout())130 else 98),1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
                }else week.addView(View(this),LinearLayout.LayoutParams(0,dp(if(carLayout())130 else 98),1f))
                cell++
            }
            host.addView(week)
        }
    }
    private fun dashcamDayCell(day:Int,key:String,records:List<Pair<DashcamApi.Category,DashcamApi.Media>>):View{
        val selected=key==dashcamDay
        val counts=records.groupingBy{dashcamGroup(it.first)}.eachCount()
        return LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;setPadding(dp(2),dp(4),dp(2),dp(2));background=panel(if(selected)Color.rgb(25,91,145) else if(records.isNotEmpty())Color.rgb(25,31,38) else Color.TRANSPARENT)
            addView(text(day.toString(),dvrText(15f),if(selected)Color.WHITE else if(records.isNotEmpty())Color.rgb(235,242,250) else muted).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)})
            if(records.isNotEmpty())addView(text(listOf("상시","긴급","서라운드","사진").mapNotNull{kind->counts[kind]?.takeIf{it>0}?.let{"$kind $it"}}.joinToString("\n"),dvrText(9f),if(selected)Color.WHITE else muted).apply{gravity=Gravity.CENTER;maxLines=4})
            isClickable=true;setOnClickListener{dashcamDay=key;dashcamVisibleLimit=50;val present=listOf("상시","긴급","서라운드","사진").firstOrNull{counts.getOrDefault(it,0)>0};if(counts.getOrDefault(dashcamKind,0)==0&&present!=null)dashcamKind=present;renderDashcam(dashcamItems)}
        }
    }
    private fun renderDashcamRecords(host:LinearLayout,independentScroll:Boolean){
        val day=dashcamDay
        if(dashcamLoading){host.addView(text("내역 불러오는 중…",dvrText(18f),muted).apply{setPadding(dp(8),dp(24),0,0)});return}
        if(day==null){host.addView(text("달력에서 날짜를 선택하세요.",dvrText(18f),muted));return}
        val dayItems=dashcamItems.filter{dashcamDayKey(it.second)==day}
        host.addView(text("$day · ${dayItems.size}개",dvrText(22f)).apply{setTypeface(null,Typeface.BOLD);setPadding(dp(2),0,0,dp(10))})
        val counts=dayItems.groupingBy{dashcamGroup(it.first)}.eachCount()
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("상시","긴급","서라운드","사진").forEach{kind->tabs.addView(button("${if(kind==dashcamKind)"● " else ""}$kind (${counts.getOrDefault(kind,0)})"){dashcamKind=kind;dashcamVisibleLimit=50;renderDashcam(dashcamItems)},LinearLayout.LayoutParams(0,dp(42),1f).apply{rightMargin=dp(3)})}
        host.addView(tabs,LinearLayout.LayoutParams(-1,dp(46)).apply{bottomMargin=dp(8)})
        val allVisible=dayItems.filter{dashcamGroup(it.first)==dashcamKind}.sortedByDescending{dashcamTime(it.second.dateTime,it.second.name)}
        val visible=allVisible.take(dashcamVisibleLimit)
        val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        visible.forEach{(category,media)->dashcamRecordRow(rows,category,media)}
        if(dashcamThumbnailDays.add(day))loadDashcamThumbnails(visible.map{it.second})
        if(visible.isEmpty())rows.addView(text("$dashcamKind 기록이 없습니다.",dvrText(14f),muted).apply{setPadding(dp(4),dp(14),0,0)})
        if(visible.size<allVisible.size)rows.addView(button("더 보기 · ${visible.size}/${allVisible.size}"){dashcamVisibleLimit+=50;renderDashcam(dashcamItems)},LinearLayout.LayoutParams(-1,dp(if(carLayout())58 else 46)).apply{topMargin=dp(4);bottomMargin=dp(10)})
        if(independentScroll)host.addView(ScrollView(this).apply{addView(rows);isFillViewport=false},LinearLayout.LayoutParams(-1,0,1f)) else host.addView(rows)
    }
    private fun dashcamRecordRow(host:LinearLayout,category:DashcamApi.Category,media:DashcamApi.Media){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8));background=panel(surface);setOnClickListener{if(!busy)dashcamPreview(media)}}
        val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(34,40,48));setImageResource(android.R.drawable.ic_menu_gallery)}
        row.addView(image,LinearLayout.LayoutParams(dp(150),dp(88)).apply{rightMargin=dp(12)})
        loadDashcamThumbnail(image,media)
        val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text(media.name,dvrText(14f)).apply{setTypeface(null,Typeface.BOLD);maxLines=2;ellipsize=TextUtils.TruncateAt.END});addView(text("${date(dashcamTime(media.dateTime,media.name))}\n${bytes(media.size)}",dvrText(11f),muted).apply{setPadding(0,dp(5),0,0)})}
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("Drive ↑"){dashcamDownload(media,false,true)},LinearLayout.LayoutParams(dp(92),dp(46)).apply{rightMargin=dp(5)})
        row.addView(text("⋮",22f,muted).apply{gravity=Gravity.CENTER;setOnClickListener{dashcamActions(category,media)}},LinearLayout.LayoutParams(dp(38),dp(42)))
        host.addView(row,LinearLayout.LayoutParams(-1,dp(106)).apply{bottomMargin=dp(7)})
    }
    private fun loadDashcamThumbnail(view:ImageView,media:DashcamApi.Media){
        val key="${media.type}:${media.id}:${media.dateTime}"
        dashcamThumbs.get(key)?.let{view.setImageBitmap(it);return}
        val disk=File(File(filesDir,"dvr_thumbs").apply{mkdirs()},"${key.hashCode().toUInt().toString(16)}.jpg")
        if(disk.exists())BitmapFactory.decodeFile(disk.absolutePath)?.let{dashcamThumbs.put(key,it);view.setImageBitmap(it);return}
        dashcamThumbViews[key]=view
    }
    private fun loadDashcamThumbnails(media:List<DashcamApi.Media>){
        val missing=media.filter{m->
            val key="${m.type}:${m.id}:${m.dateTime}"
            dashcamThumbs.get(key)==null && dashcamThumbInFlight.add(key)
        }
        if(missing.isEmpty())return
        thumbWorker.execute{
            try{
                dashcamApi.thumbnails(missing).forEach{(media,bytes)->
                    val key="${media.type}:${media.id}:${media.dateTime}"
                    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return@forEach
                    val disk=File(File(filesDir,"dvr_thumbs").apply{mkdirs()},"${key.hashCode().toUInt().toString(16)}.jpg")
                    try{disk.writeBytes(bytes)}catch(_:Exception){}
                    dashcamThumbs.put(key,bitmap)
                    runOnUiThread{if(!isFinishing)dashcamThumbViews[key]?.setImageBitmap(bitmap)}
                }
            }finally{missing.forEach{m->dashcamThumbInFlight.remove("${m.type}:${m.id}:${m.dateTime}")}}
        }
    }
    private fun dashcamCacheFile()=File(filesDir,"dvr_cache.json")
    private fun loadDashcamCache(){
        val file=dashcamCacheFile();if(!file.exists())return
        try{
            val root=JSONObject(file.readText());val categoryMap=linkedMapOf<String,DashcamApi.Category>()
            val categories=root.optJSONArray("categories")?:JSONArray()
            for(i in 0 until categories.length()){val j=categories.optJSONObject(i)?:continue;val c=DashcamApi.Category(j.optString("type"),j.optString("label"),j.optString("path"),j.optInt("count",-1));if(c.type.isNotBlank())categoryMap[c.type]=c}
            val loaded=mutableListOf<Pair<DashcamApi.Category,DashcamApi.Media>>();val items=root.optJSONArray("items")?:JSONArray()
            for(i in 0 until items.length()){val j=items.optJSONObject(i)?:continue;val type=j.optString("type");val c=categoryMap[type]?:DashcamApi.Category(type,type,j.optString("path"),-1).also{categoryMap[type]=it};val m=DashcamApi.Media(type,j.optString("id"),j.optString("name"),j.optLong("size"),j.optLong("dateTime"),j.optInt("duration"),j.optString("url"));if(m.name.isNotBlank())loaded.add(c to m)}
            dashcamCategories=categoryMap.values.toList();dashcamItems=loaded.distinctBy{"${it.second.type}/${it.second.id}/${it.second.name}"}.sortedByDescending{dashcamTime(it.second.dateTime,it.second.name)}
            dashcamOffsets.clear();val offsets=root.optJSONObject("offsets")?:JSONObject();offsets.keys().forEach{dashcamOffsets[it]=offsets.optInt(it)}
            dashcamComplete.clear();val complete=root.optJSONArray("complete")?:JSONArray();for(i in 0 until complete.length())dashcamComplete.add(complete.optString(i))
        }catch(_:Exception){dashcamItems=emptyList();dashcamCategories=emptyList();dashcamOffsets.clear();dashcamComplete.clear()}
    }
    private fun saveDashcamCache(){
        try{
            val root=JSONObject().put("timestamp",System.currentTimeMillis());val categories=JSONArray();dashcamCategories.forEach{categories.put(JSONObject().put("type",it.type).put("label",it.label).put("path",it.path).put("count",it.count))};root.put("categories",categories)
            val items=JSONArray();dashcamItems.forEach{(c,m)->items.put(JSONObject().put("type",c.type).put("path",c.path).put("id",m.id).put("name",m.name).put("size",m.size).put("dateTime",m.dateTime).put("duration",m.duration).put("url",m.url))};root.put("items",items)
            val offsets=JSONObject();dashcamOffsets.forEach{(k,v)->offsets.put(k,v)};root.put("offsets",offsets);root.put("complete",JSONArray(dashcamComplete.toList()))
            val file=dashcamCacheFile();val temp=File(file.parentFile,"${file.name}.tmp");temp.writeText(root.toString());if(!temp.renameTo(file)){file.writeText(root.toString());temp.delete()}
        }catch(_:Exception){}
    }
    private fun confirmDashcamCacheReset(){AlertDialog.Builder(this).setTitle("대시캠 캐시 초기화").setMessage("저장된 목록과 썸네일을 지우고 차량에서 전체 내역을 한 번 다시 읽습니다.").setNegativeButton("취소",null).setPositiveButton("전체 다시 읽기"){_,_->dashcamCacheFile().delete();File(filesDir,"dvr_thumbs").listFiles()?.forEach{it.delete()};dashcamThumbs.evictAll();dashcamThumbnailDays.clear();dashcamItems=emptyList();dashcamCategories=emptyList();dashcamOffsets.clear();dashcamComplete.clear();showDashcam(true)}.show()}
    private fun dvrText(size:Float)=if(carLayout())size*2.0f else size
    private fun dashcamGroup(category:DashcamApi.Category)=when(category.type.lowercase(Locale.ROOT)){"emergency"->"긴급";"avm","avm_emergency"->"서라운드";"photo"->"사진";else->"상시"}
    private fun dashcamActions(category:DashcamApi.Category,media:DashcamApi.Media){
        val options=arrayOf("재생 / 보기","P4 Files 다운로드에 저장","저장 위치 선택","Google Drive 업로드","정보")
        AlertDialog.Builder(this).setTitle(media.name).setItems(options){_,index->when(index){
            0->dashcamPreview(media)
            1->dashcamDownload(media,false,false)
            2->{pendingDashcam=media;launch(Intent(Intent.ACTION_CREATE_DOCUMENT).setType(dashcamApi.mime(media)).putExtra(Intent.EXTRA_TITLE,media.name),75)}
            3->dashcamDownload(media,false,true)
            else->showInfo(media.name,"${dashcamApi.mime(media)} · ${category.label}",media.size,dashcamTime(media.dateTime,media.name))
        }}.show()
    }
    private fun dashcamPreview(media:DashcamApi.Media){dashcamDownload(media,true,false)}
    private fun dashcamDownload(media:DashcamApi.Media,open:Boolean,upload:Boolean){
        task(if(open)"대시캠 재생 준비 중" else "대시캠 저장 중"){
            val dir=File(getExternalFilesDir(null),"Downloads").apply{mkdirs()}
            val safe=media.name.replace(Regex("[^A-Za-z0-9가-힣._-]"),"_")
            val file=File(dir,safe)
            dashcamApi.withFileList{file.outputStream().use{out->dashcamApi.download(media,out){n->update("${bytes(n)} 받는 중 · ${media.name}")}}}
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            runOnUiThread{when{open->openDashcamFile(file,media);upload->uploadFiles(listOf(uri));else->state.text="대시캠 저장 완료: ${media.name}"}}
        }
    }
    private fun openDashcamFile(file:File,media:DashcamApi.Media){
        val mime=dashcamApi.mime(media)
        if(mime.startsWith("image/")){
            val bitmap=BitmapFactory.decodeFile(file.absolutePath)?:run{state.text="사진을 열 수 없습니다";return}
            val frame=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
            frame.addView(ImageView(this).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER},FrameLayout.LayoutParams(-1,-1))
            val dialog=Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            frame.addView(playerCloseButton{dialog.dismiss()},FrameLayout.LayoutParams(dp(124),dp(54),Gravity.TOP or Gravity.END).apply{setMargins(0,dp(18),dp(18),0)})
            dialog.setContentView(frame);dialog.show();return
        }
        val player=androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        val playerView=androidx.media3.ui.PlayerView(this).apply{this.player=player;setBackgroundColor(Color.BLACK);useController=true}
        val frame=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);addView(playerView,FrameLayout.LayoutParams(-1,-1))}
        val dialog=Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        frame.addView(playerCloseButton{dialog.dismiss()},FrameLayout.LayoutParams(dp(124),dp(54),Gravity.TOP or Gravity.END).apply{setMargins(0,dp(18),dp(18),0)})
        dialog.setOnDismissListener{player.release()}
        dialog.setContentView(frame);dialog.show()
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare();player.playWhenReady=true
    }
    private fun playerCloseButton(close:()->Unit)=Button(this).apply{text="✕ 닫기";isAllCaps=false;textSize=15f;setTextColor(Color.WHITE);background=panel(Color.rgb(38,44,52));setOnClickListener{close()}}
    private fun dashcamTime(value:Long,name:String):Long{
        val digits=name.filter{it.isDigit()}
        val stamp=Regex("20\\d{12}").find(digits)?.value
        if(stamp!=null)return try{SimpleDateFormat("yyyyMMddHHmmss",Locale.KOREA).apply{isLenient=false}.parse(stamp)?.time?:value}catch(_:Exception){value}
        if(value in 1..9_999_999_999L)return value*1000
        if(value>=10_000_000_000_000L)return try{SimpleDateFormat("yyyyMMddHHmmss",Locale.KOREA).apply{isLenient=false}.parse(value.toString().take(14))?.time?:value}catch(_:Exception){value}
        return value
    }
    private fun dashcamDayKey(media:DashcamApi.Media):String{
        val digits=media.name.filter{it.isDigit()}
        Regex("20\\d{6}").find(digits)?.value?.let{return "${it.substring(0,4)}-${it.substring(4,6)}-${it.substring(6,8)}"}
        val time=dashcamTime(media.dateTime,media.name)
        return if(time<=0)"" else SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(Date(time))
    }
    private fun task(label:String,job:()->Unit){if(busy)return;busy=true;state.text=label;progress.visibility=View.VISIBLE;worker.execute{try{job()}catch(e:Exception){runOnUiThread{state.text="$label 실패";val msg=when(e){is AuthFailure->"${e.code}: ${e.message}";is java.net.UnknownHostException->"DNS_UNAVAILABLE: 차량 앱에서 Google 서버 주소를 찾지 못했습니다.\\n\\n${e.message}"else->e.message?:e.javaClass.simpleName};AlertDialog.Builder(this).setTitle("작업 실패").setMessage(msg).setPositiveButton("확인",null).show()}}finally{runOnUiThread{busy=false;progress.visibility=View.GONE}}}}
    private fun showDiagnostics(){val report=AuthDiagnostics.report(this);Log.i(AuthDiagnostics.TAG,"DIAGNOSTIC_REPORT\n$report");AlertDialog.Builder(this).setTitle("인증 진단").setMessage(report+"\\nP4DriveAuth 단계 로그를 확인하세요.").setPositiveButton("확인",null).setNegativeButton("클립보드 복사"){_,_->(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("P4DriveAuth",report))}.show()}
    private fun bytes(n:Long)=if(n<1024)"$n B" else if(n<1048576)"${n/1024} KB" else String.format("%.1f MB",n/1048576.0)
    private fun fileBadge(name:String):String{val ext=name.substringAfterLast('.',"").uppercase(Locale.getDefault());return if(ext.isBlank()||ext==name.uppercase(Locale.getDefault()))"파일" else ext.take(4)}
    private fun date(time:Long)=if(time<=0)"날짜 정보 없음" else SimpleDateFormat("yyyy.MM.dd  HH:mm",Locale.KOREA).format(Date(time))
    private fun sortedDrive(list:List<DriveApi.DriveFile>):List<DriveApi.DriveFile>{
        val comparator=when(driveSort){
            SortMode.NAME_ASC->compareBy<DriveApi.DriveFile>{it.name.lowercase(Locale.getDefault())}
            SortMode.NAME_DESC->compareByDescending<DriveApi.DriveFile>{it.name.lowercase(Locale.getDefault())}
            SortMode.NEWEST->compareByDescending<DriveApi.DriveFile>{it.modified}
            SortMode.OLDEST->compareBy<DriveApi.DriveFile>{it.modified}
            SortMode.SIZE_DESC->compareByDescending<DriveApi.DriveFile>{it.size}
            SortMode.SIZE_ASC->compareBy<DriveApi.DriveFile>{it.size}
        }
        return list.filter{it.folder}.sortedWith(comparator)+list.filter{!it.folder}.sortedWith(comparator)
    }
    private fun sortedLocal(list:List<DocumentFile>):List<DocumentFile>{
        val comparator=when(localSort){
            SortMode.NAME_ASC->compareBy<DocumentFile>{it.name.orEmpty().lowercase(Locale.getDefault())}
            SortMode.NAME_DESC->compareByDescending<DocumentFile>{it.name.orEmpty().lowercase(Locale.getDefault())}
            SortMode.NEWEST->compareByDescending<DocumentFile>{it.lastModified()}
            SortMode.OLDEST->compareBy<DocumentFile>{it.lastModified()}
            SortMode.SIZE_DESC->compareByDescending<DocumentFile>{it.length()}
            SortMode.SIZE_ASC->compareBy<DocumentFile>{it.length()}
        }
        return list.filter{it.isDirectory}.sortedWith(comparator)+list.filter{!it.isDirectory}.sortedWith(comparator)
    }
    private fun chooseSort(drive:Boolean){
        val modes=SortMode.entries.toTypedArray();val current=if(drive)driveSort else localSort
        AlertDialog.Builder(this).setTitle("정렬 기준").setSingleChoiceItems(modes.map{it.label}.toTypedArray(),modes.indexOf(current)){dialog,index->
            if(drive)driveSort=modes[index] else localSort=modes[index];dialog.dismiss();if(drive)showDrive() else showLocal()
        }.setNegativeButton("취소",null).show()
    }
    private fun matches(name:String,mime:String):Boolean{
        if(searchText.isNotBlank()&&!name.contains(searchText,true))return false
        return when(typeFilter){"동영상"->mime.startsWith("video/");"이미지"->mime.startsWith("image/");"APK"->name.endsWith(".apk",true);"PDF"->name.endsWith(".pdf",true);else->true}
    }
    private fun searchDialog(drive:Boolean){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),0,dp(22),0)}
        val query=EditText(this).apply{hint="파일명 검색";setText(searchText);setTextColor(Color.WHITE);setSingleLine()}
        val filters=arrayOf("전체","동영상","이미지","APK","PDF");val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,filters);setSelection(filters.indexOf(typeFilter).coerceAtLeast(0))}
        box.addView(query);box.addView(spinner)
        AlertDialog.Builder(this).setTitle("검색 및 필터").setView(box).setNeutralButton("초기화"){_,_->searchText="";typeFilter="전체";if(drive)renderDrive(driveItems)else renderLocal(localItems)}.setNegativeButton("취소",null).setPositiveButton("적용"){_,_->searchText=query.text.toString().trim();typeFilter=spinner.selectedItem.toString();if(drive)renderDrive(driveItems)else renderLocal(localItems)}.show()
    }
    private fun favPrefs()=getSharedPreferences("p4_favorites",0)
    private fun favoriteKey(drive:Boolean,id:String)=(if(drive)"D:" else "L:")+id
    private fun isFavorite(drive:Boolean,id:String)=favPrefs().getStringSet("keys",emptySet())?.contains(favoriteKey(drive,id))==true
    private fun toggleFavorite(drive:Boolean,id:String,name:String){val key=favoriteKey(drive,id);val set=favPrefs().getStringSet("keys",emptySet())?.toMutableSet()?:mutableSetOf();if(!set.add(key))set.remove(key);favPrefs().edit().putStringSet("keys",set).putString("name_$key",name).apply();state.text=if(set.contains(key))"즐겨찾기에 추가했습니다" else "즐겨찾기에서 제거했습니다"}
    private fun showFavorites(){mode="favorites";title.text="즐겨찾기";subtitle.text="자주 사용하는 Drive·로컬 항목";actions.removeAllViews();body.removeAllViews();val keys=favPrefs().getStringSet("keys",emptySet()).orEmpty().sorted();body.addView(text("FAVORITES · ${keys.size}개",11f,muted).apply{setPadding(dp(4),dp(12),0,dp(9))});keys.forEach{key->val name=favPrefs().getString("name_$key","항목")?:"항목";entryRow(name,if(key.startsWith("D:"))"DRIVE" else "LOCAL","길게 눌러 즐겨찾기에서 제거",false,{toggleFavorite(key.startsWith("D:"),key.substring(2),name);showFavorites()}){if(key.startsWith("D:"))task("즐겨찾기 여는 중"){val f=api.getFile(key.substring(2));runOnUiThread{if(f.folder){remotePath.add(f.id to f.name);showDrive()}else driveActions(f)}} else DocumentFile.fromSingleUri(this,Uri.parse(key.substring(2)))?.let{f->if(f.isDirectory){localPath.add(f);showLocal()}else localActions(f.uri,f.name?:name,f.type?:"application/octet-stream")}}};if(keys.isEmpty())body.addView(text("항목 메뉴에서 즐겨찾기를 추가하세요.",15f,muted).apply{setPadding(dp(4),dp(20),0,0)})}
    private fun touchRecent(drive:Boolean,id:String,name:String){val old=try{JSONArray(favPrefs().getString("recent","[]"))}catch(_:Exception){JSONArray()};val out=JSONArray().put(JSONObject().put("drive",drive).put("id",id).put("name",name));for(i in 0 until old.length()){val j=old.optJSONObject(i)?:continue;if(j.optBoolean("drive")==drive&&j.optString("id")==id)continue;if(out.length()<20)out.put(j)};favPrefs().edit().putString("recent",out.toString()).apply()}
    private fun showRecent(){mode="recent";title.text="최근 항목";subtitle.text="최근 열어본 파일과 폴더";actions.removeAllViews();body.removeAllViews();val array=try{JSONArray(favPrefs().getString("recent","[]"))}catch(_:Exception){JSONArray()};body.addView(text("RECENT · ${array.length()}개",11f,muted).apply{setPadding(dp(4),dp(12),0,dp(9))});for(i in 0 until array.length()){val j=array.getJSONObject(i);val drive=j.optBoolean("drive");val id=j.optString("id");val name=j.optString("name","항목");entryRow(name,if(drive)"DRIVE" else "LOCAL","최근 열어본 항목",false){if(drive)task("최근 항목 여는 중"){val f=api.getFile(id);runOnUiThread{if(f.folder){remotePath.add(f.id to f.name);showDrive()}else driveActions(f)}}else DocumentFile.fromSingleUri(this,Uri.parse(id))?.let{f->if(f.isDirectory){localPath.add(f);showLocal()}else localActions(f)}}}}
    private fun showQueue(){mode="queue";title.text="전송 대기열";subtitle.text="백그라운드 업로드·다운로드";renderQueue();handler.removeCallbacks(queueRefresh);handler.postDelayed(queueRefresh,1000)}
    private fun renderQueue(){if(mode!="queue")return;actions.removeAllViews();body.removeAllViews();actions.addView(button("완료 항목 정리"){TransferStore.clearFinished(this);renderQueue()});val tasks=TransferStore.list(this);val active=tasks.count{it.status in setOf("QUEUED","RUNNING","PAUSED")};body.addView(text("TRANSFERS · ${tasks.size}개 · 진행 $active",11f,muted).apply{setPadding(dp(4),dp(12),0,dp(9))});tasks.forEach{t->val pct=if(t.size>0)(t.progress*100/t.size).coerceIn(0,100) else 0;val meta="${if(t.kind=="UPLOAD")"업로드" else "다운로드"} · ${t.status} · $pct%${if(t.error.isNotBlank())" · ${t.error}" else ""}";entryRow(t.name,if(t.kind=="UPLOAD")"UP" else "DOWN",meta,false,{transferActions(t)}){transferActions(t)}};if(tasks.isEmpty())body.addView(text("대기 중인 전송이 없습니다.",15f,muted).apply{setPadding(dp(4),dp(20),0,0)})}
    private fun transferActions(t:TransferTask){val options=when(t.status){"RUNNING","QUEUED"->arrayOf("일시정지","취소");"PAUSED"->arrayOf("계속","취소");"FAILED","CANCELED"->arrayOf("재시도");else->emptyArray()};if(options.isEmpty())return;AlertDialog.Builder(this).setTitle(t.name).setItems(options){_,i->when(options[i]){"일시정지"->TransferManager.pause(this,t.id);"취소"->TransferManager.cancel(this,t.id);"계속","재시도"->TransferManager.resume(this,t.id)};renderQueue()}.show()}
    private fun showDualPane(){mode="dual";title.text="P4 FILES";subtitle.text="차량 저장소 ↔ Google Drive";body.removeAllViews();task("2분할 불러오는 중"){val local=localPath.lastOrNull()?.listFiles()?.toList().orEmpty();val drive=if(api.connected)api.listFolder(remotePath.last().first)else emptyList();runOnUiThread{renderDual(local,drive);state.text="차량 ${local.size}개 · Drive ${drive.size}개"}}}
    private fun renderDual(local:List<DocumentFile>,drive:List<DriveApi.DriveFile>){
        mode="dual";body.removeAllViews()
        val panes=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val left=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,dp(7),0)}
        val right=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(7),0,0,0)}
        left.addView(text("차량 저장소",18f).apply{setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(12),0,dp(10))})
        right.addView(text("Google Drive",18f).apply{setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(12),0,dp(10))})
        val localToolbar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        localToolbar.addView(button("정렬 · ${localSort.label}"){chooseSort(false)},LinearLayout.LayoutParams(0,dp(42),1f).apply{rightMargin=dp(4)})
        localToolbar.addView(button("새로고침"){showDualPane()},LinearLayout.LayoutParams(0,dp(42),1f))
        left.addView(localToolbar,LinearLayout.LayoutParams(-1,dp(46)).apply{bottomMargin=dp(6)})
        if(localPath.isEmpty()) emptyPane(left,"▣","저장소가 선택되지 않았습니다","폴스타 시스템 선택기는 최초 한 번만 사용합니다.","저장소 선택"){chooseFolder()}
        else if(local.isEmpty()) emptyPane(left,"□","이 폴더가 비어 있습니다","다른 폴더를 선택하거나 파일을 추가하세요.","저장소 변경"){chooseFolder()}
        else sortedLocal(local).take(30).forEach{f->miniRow(left,f.name?:"파일",if(f.isDirectory)"폴더" else bytes(f.length())){if(f.isDirectory){localPath.add(f);showDualPane()}else localActions(f.uri,f.name?:"파일",f.type?:"application/octet-stream")}}
        if(!api.connected) emptyPane(right,"△","Google Drive 연결 필요","차량은 Device Code 방식으로 연결합니다.","Google 계정 연결"){login()}
        else if(drive.isEmpty()) emptyPane(right,"□","표시할 Drive 파일이 없습니다","Drive 웹 업로드 파일은 차량에 안 보일 수 있습니다. P4 Files로 업로드하세요.","P4 Files로 업로드"){pickFilesForDrive()}
        else sortedDrive(drive).take(30).forEach{f->miniRow(right,f.name,if(f.folder)"폴더" else bytes(f.size)){if(f.folder){remotePath.add(f.id to f.name);showDualPane()}else driveActions(f)}}
        panes.addView(left,LinearLayout.LayoutParams(0,-2,1f));panes.addView(right,LinearLayout.LayoutParams(0,-2,1f));body.addView(panes)
    }
    private fun miniRow(host:LinearLayout,name:String,meta:String,click:()->Unit){val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(9),dp(12),dp(9));background=panel(surface);setOnClickListener{if(!busy)click()}};row.addView(text(name,15f).apply{setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=TextUtils.TruncateAt.END});row.addView(text(meta,11f,muted));host.addView(row,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(5)})}
    private fun emptyPane(host:LinearLayout,icon:String,titleText:String,detail:String,actionText:String,action:()->Unit){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(18),dp(28),dp(18),dp(28));background=panel(surface)}
        box.addView(text(icon,30f,accent).apply{gravity=Gravity.CENTER})
        box.addView(text(titleText,16f).apply{setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setPadding(0,dp(9),0,dp(5))})
        box.addView(text(detail,12f,muted).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;setPadding(0,0,0,dp(14))})
        box.addView(button(actionText,action),LinearLayout.LayoutParams(-1,dp(44)))
        host.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
    }
    private fun update(s:String){runOnUiThread{state.text=s}}
    private fun login(){
        if(busy)return
        val playServices=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if(playServices!=ConnectionResult.SUCCESS){vehicleLogin(playServices);return}
        busy=true;progress.visibility=View.VISIBLE;state.text="Google 전체 권한 요청 중"
        AuthDiagnostics.event("CONFIG_CHECK","SUCCESS","ANDROID_OAUTH_CLIENT_REQUIRED","package=$packageName sha1=${AuthDiagnostics.fingerprint(this)}")
        AuthDiagnostics.event("AUTH_START","START","AUTHORIZATION_CLIENT")
        val request=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveApi.DRIVE_SCOPE))).build()
        authorizationClient.authorize(request)
            .addOnSuccessListener{result->
                AuthDiagnostics.event("AUTH_CALLBACK","SUCCESS",if(result.hasResolution())"CONSENT_REQUIRED" else "TOKEN_READY")
                if(result.hasResolution()){
                    busy=false;progress.visibility=View.GONE
                    try{startIntentSenderForResult(result.pendingIntent!!.intentSender,authorizationRequestCode,null,0,0,0)}
                    catch(e:Exception){authFailed("AUTH_UI_FAILED",e.message?:e.javaClass.simpleName)}
                }else finishAuthorization(result)
            }
            .addOnFailureListener{e->authFailed(classifyAuthorizationError(e),e.message?:e.javaClass.simpleName)}
    }
    private fun vehicleLogin(playServicesCode:Int){
        val auth=DeviceCodeAuth()
        if(!auth.configured()){authFailed("CONFIG_MISSING","차량용 TVs and Limited Input devices OAuth Client가 설정되지 않았습니다.");return}
        busy=true;progress.visibility=View.VISIBLE;state.text="차량 호환 인증 준비 중"
        val cancel=AtomicBoolean(false);vehicleAuthCancel=cancel
        AuthDiagnostics.event("CONFIG_CHECK","SUCCESS","VEHICLE_DEVICE_CODE","playServicesCode=$playServicesCode scope=${DriveApi.VEHICLE_SCOPE}")
        worker.execute{
            try{
                val challenge=auth.begin()
                AuthDiagnostics.event("AUTH_CALLBACK","SUCCESS","DEVICE_CODE_READY")
                runOnUiThread{
                    state.text="휴대폰에서 ${challenge.verificationUrl} 접속 후 코드를 입력하세요"
                    vehicleAuthDialog=AlertDialog.Builder(this)
                        .setTitle("차량 Google Drive 연결")
                        .setMessage("휴대폰이나 차량 브라우저에서 아래 주소를 열고 코드를 입력하세요.\n\n${challenge.verificationUrl}\n\n인증 코드: ${challenge.userCode}\n\n차량에서는 Google 정책상 이 앱이 만든 파일과 사용자가 이 앱에 허용한 파일만 표시됩니다.")
                        .setPositiveButton("코드 복사 · 브라우저"){_,_->
                            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Google 인증 코드",challenge.userCode))
                            try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(challenge.verificationUrl)))}catch(_:Exception){state.text="브라우저를 열 수 없습니다. 휴대폰에서 주소를 여세요."}
                        }
                        .setNegativeButton("취소"){_,_->cancel.set(true)}
                        .setOnCancelListener{cancel.set(true)}
                        .show()
                }
                val tokens=auth.poll(challenge){cancel.get()||cancelled.get()}
                AuthDiagnostics.event("TOKEN_EXCHANGE","SUCCESS","VEHICLE_DEVICE_CODE")
                api.acceptVehicleTokens(tokens.access,tokens.refresh,tokens.expiresIn,tokens.scope)
                val list=api.listFolder("root")
                AuthDiagnostics.event("AUTH_COMPLETE","SUCCESS","VEHICLE_DRIVE_FILE_SCOPE")
                runOnUiThread{
                    vehicleAuthDialog?.dismiss();vehicleAuthDialog=null
                    remotePath.clear();remotePath.add("root" to "Google Drive")
                    busy=false;progress.visibility=View.GONE
                    renderDrive(list);state.text="차량 호환 연결 완료 · 앱에서 관리 가능한 ${list.size}개 항목"
                }
            }catch(e:Exception){runOnUiThread{authFailed((e as? AuthFailure)?.code ?: if(e is java.net.UnknownHostException)"DNS_UNAVAILABLE" else "AUTHORIZATION_FAILED",e.message?:e.javaClass.simpleName)}}
            finally{vehicleAuthCancel=null}
        }
    }
    private fun classifyAuthorizationError(e:Exception):String{
        val status=(e as? ApiException)?.statusCode
        return when(status){CommonStatusCodes.NETWORK_ERROR->"NETWORK_ERROR";CommonStatusCodes.CANCELED->"OAUTH_ACCESS_DENIED";else->"AUTHORIZATION_FAILED"}
    }
    private fun authFailed(code:String,detail:String){vehicleAuthDialog?.dismiss();vehicleAuthDialog=null;busy=false;progress.visibility=View.GONE;state.text="$code: Google 로그인 실패";AuthDiagnostics.event("AUTH_COMPLETE","FAILED",code,detail);AlertDialog.Builder(this).setTitle("Google Drive 연결 실패").setMessage("$code: $detail").setPositiveButton("확인",null).show()}
    private fun finishAuthorization(result:AuthorizationResult){
        val token=result.accessToken
        if(token.isNullOrBlank()){authFailed("TOKEN_MISSING","Google Play services가 액세스 토큰을 반환하지 않았습니다.");return}
        AuthDiagnostics.event("TOKEN_EXCHANGE","SUCCESS","PLAY_SERVICES_TOKEN")
        api.acceptAccessToken(token)
        AuthDiagnostics.event("AUTH_COMPLETE","SUCCESS","FULL_DRIVE_SCOPE")
        busy=false;progress.visibility=View.GONE
        task("Drive 권한 확인 중"){val list=api.listFolder("root");runOnUiThread{remotePath.clear();remotePath.add("root" to "Google Drive");renderDrive(list);state.text="Google Drive 전체 권한 연결 완료"}}
    }
    private fun showDrive(){if(!api.connected){login();return};task("폴더 불러오는 중"){val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list);state.text=if(api.vehicleMode)"차량 호환 모드 · ${list.size}개 항목" else "${list.size}개 항목"}}}
    private fun renderDrive(list:List<DriveApi.DriveFile>){driveItems=list;mode="drive";title.text=remotePath.last().second;subtitle.text=(if(api.vehicleMode)"차량 호환 모드 · 앱이 만든/허용된 파일  ·  " else "")+remotePath.joinToString("  ›  "){it.second};body.removeAllViews();actions.removeAllViews()
        if(selectionMode){actions.addView(button("선택 종료"){selectionMode=false;selectedDrive.clear();renderDrive(driveItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("모두"){selectedDrive.addAll(driveItems.map{it.id});renderDrive(driveItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("다운로드"){bulkDrive("download")},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("휴지통"){bulkDrive("trash")})}
        else{actions.addView(button("↑ 업로드"){pickFiles()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("검색"){searchDialog(true)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("정렬 · ${driveSort.label}"){chooseSort(true)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("선택"){selectionMode=true;renderDrive(driveItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("＋"){newFolder()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("대기열"){showQueue()})}
        val visible=sortedDrive(list).filter{matches(it.name,it.mime)}
        body.addView(text("GOOGLE DRIVE  ·  ${visible.size}개  ·  ${driveSort.label}${if(searchText.isNotBlank()||typeFilter!="전체")" · 필터 적용" else ""}",11f,muted).apply{letterSpacing=.06f;setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(12),0,dp(9))})
        if(visible.isEmpty())card("□","표시할 항목이 없습니다","검색 조건을 바꾸거나 파일을 업로드하세요."){}
        visible.forEach{f->val checked=selectedDrive.contains(f.id);entryRow((if(checked)"✓ " else if(isFavorite(true,f.id))"★ " else "")+f.name,if(f.folder)"폴더" else fileBadge(f.name),if(f.folder)"수정 ${date(f.modified)}" else "${bytes(f.size)}  ·  수정 ${date(f.modified)}",f.folder,if(selectionMode)null else ({driveActions(f)})){if(selectionMode){if(!selectedDrive.add(f.id))selectedDrive.remove(f.id);renderDrive(driveItems)}else if(f.folder){remotePath.add(f.id to f.name);showDrive()}else driveActions(f)}}
    }
    private fun bulkDrive(action:String){val files=driveItems.filter{it.id in selectedDrive};if(files.isEmpty()){state.text="선택한 항목이 없습니다";return};if(action=="download"){files.filter{!it.folder}.forEach{download(it)};selectionMode=false;selectedDrive.clear();showQueue()}else AlertDialog.Builder(this).setTitle("${files.size}개 항목을 휴지통으로 이동").setMessage("Google Drive 휴지통에서 복구할 수 있습니다.").setNegativeButton("취소",null).setPositiveButton("이동"){_,_->task("휴지통 이동 중"){files.forEach{api.trash(it.id)};val list=api.listFolder(remotePath.last().first);runOnUiThread{selectionMode=false;selectedDrive.clear();renderDrive(list)}}}.show()}
    private fun driveActions(f:DriveApi.DriveFile){touchRecent(true,f.id,f.name);val options=mutableListOf<String>();if(f.folder)options.add("열기") else options.addAll(listOf("다운로드 대기열에 추가","저장 위치 선택","미리보기 / 정보"));options.add(if(isFavorite(true,f.id))"즐겨찾기 해제" else "즐겨찾기 추가");options.add("이름 변경");if(!f.folder)options.add("현재 폴더에 복사");if(remotePath.size>1)options.add("상위 폴더로 이동");options.add("휴지통으로 이동");options.add("정보");AlertDialog.Builder(this).setTitle(f.name).setItems(options.toTypedArray()){_,i->when(options[i]){"열기"->{remotePath.add(f.id to f.name);showDrive()};"다운로드 대기열에 추가"->{download(f);showQueue()};"저장 위치 선택"->{pendingDownload=f;launch(Intent(Intent.ACTION_CREATE_DOCUMENT).setType(f.mime).putExtra(Intent.EXTRA_TITLE,f.name),73)};"미리보기 / 정보"->previewDrive(f);"즐겨찾기 추가","즐겨찾기 해제"->{toggleFavorite(true,f.id,f.name);renderDrive(driveItems)};"이름 변경"->renameDrive(f);"현재 폴더에 복사"->task("복사 중"){api.copyFile(f.id,"${f.name} 복사본",remotePath.last().first);val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list)}};"상위 폴더로 이동"->task("이동 중"){api.move(f.id,remotePath.last().first,remotePath[remotePath.lastIndex-1].first);val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list)}};"휴지통으로 이동"->confirmDriveTrash(f);"정보"->showInfo(f.name,f.mime,f.size,f.modified)}}.show()}
    private fun renameDrive(f:DriveApi.DriveFile){val edit=EditText(this).apply{setText(f.name);selectAll()};AlertDialog.Builder(this).setTitle("이름 변경").setView(edit).setNegativeButton("취소",null).setPositiveButton("변경"){_,_->val name=edit.text.toString().trim();if(name.isNotEmpty())task("이름 변경 중"){api.rename(f.id,name);val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list)}}}.show()}
    private fun confirmDriveTrash(f:DriveApi.DriveFile){AlertDialog.Builder(this).setTitle("휴지통으로 이동").setMessage(f.name).setNegativeButton("취소",null).setPositiveButton("이동"){_,_->task("휴지통 이동 중"){api.trash(f.id);val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list)}}}.show()}
    private fun showInfo(name:String,mime:String,size:Long,modified:Long){AlertDialog.Builder(this).setTitle(name).setMessage("형식: $mime\n크기: ${bytes(size)}\n수정: ${date(modified)}").setPositiveButton("확인",null).show()}
    private fun previewDrive(f:DriveApi.DriveFile){if(f.size>20L*1024*1024||f.mime.startsWith("video/")){showInfo(f.name,f.mime,f.size,f.modified);return};task("미리보기 준비 중"){val file=File(cacheDir,"preview-${f.id}-${f.name.replace(Regex("[^A-Za-z0-9._-]"),"_")}");file.outputStream().use{api.download(f.id,it){n->update("${bytes(n)} 받는 중")}};val uri=FileProvider.getUriForFile(this,"$packageName.files",file);runOnUiThread{openUri(uri,f.mime)}}}
    private fun newFolder(){val edit=EditText(this).apply{hint="폴더 이름"};AlertDialog.Builder(this).setTitle("Google Drive 새 폴더").setView(edit).setNegativeButton("취소",null).setPositiveButton("만들기"){_,_->val name=edit.text.toString().trim();if(name.isNotEmpty())task("폴더 만드는 중"){api.createFolder(name,remotePath.last().first);val list=api.listFolder(remotePath.last().first);runOnUiThread{renderDrive(list);state.text="폴더 생성 완료"}}}.show()}
    private fun storagePrefs()=getSharedPreferences("storage_access",MODE_PRIVATE)
    private fun restoreSavedStorage():Boolean{
        val value=storagePrefs().getString("tree_uri",null)?:return false
        val uri=try{Uri.parse(value)}catch(_:Exception){storagePrefs().edit().clear().apply();return false}
        val granted=contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}
        val dir=try{if(granted)DocumentFile.fromTreeUri(this,uri) else null}catch(_:Exception){null}
        if(dir==null||try{!dir.exists()}catch(_:Exception){true}){storagePrefs().edit().clear().apply();return false}
        localPath.clear();localPath.add(dir);return true
    }
    private fun openSavedStorage(){
        if(localPath.isEmpty()&&!restoreSavedStorage()){chooseFolder();return}
        if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&resources.configuration.screenWidthDp>=700)showDualPane()else showLocal()
    }
    private fun chooseFolder(){launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),70)}
    private fun pickFiles(){launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),71)}
    private fun pickFilesForDrive(){launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),72)}
    private fun launch(intent:Intent,code:Int){try{startActivityForResult(intent,code)}catch(e:Exception){state.text="파일 선택기를 열 수 없습니다: ${e.message}"}}
    private fun showLocal(){val dir=localPath.last();task("파일 불러오는 중"){val list=dir.listFiles().toList();runOnUiThread{renderLocal(list);state.text="${list.size}개 항목 · ${localSort.label}"}}}
    private fun renderLocal(list:List<DocumentFile>){localItems=list;mode="local";val dir=localPath.last();title.text=dir.name?:"저장소";subtitle.text=localPath.joinToString("  ›  "){it.name?:"폴더"};actions.removeAllViews();body.removeAllViews()
        if(selectionMode){actions.addView(button("선택 종료"){selectionMode=false;selectedLocal.clear();renderLocal(localItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("모두"){selectedLocal.addAll(localItems.map{it.uri.toString()});renderLocal(localItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("Drive 업로드"){bulkLocal("upload")},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("삭제"){bulkLocal("delete")})}
        else{actions.addView(button("저장소 변경"){chooseFolder()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("새로고침"){showLocal()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("검색"){searchDialog(false)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("정렬 · ${localSort.label}"){chooseSort(false)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("선택"){selectionMode=true;renderLocal(localItems)},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});if(localClipboard!=null)actions.addView(button("여기에 붙여넣기"){pasteLocal()},LinearLayout.LayoutParams(-2,dp(42)).apply{rightMargin=dp(6)});actions.addView(button("대기열"){showQueue()})}
        val visible=sortedLocal(list).filter{matches(it.name.orEmpty(),it.type.orEmpty())};body.addView(text("LOCAL  ·  ${visible.size}개  ·  ${localSort.label}${if(searchText.isNotBlank()||typeFilter!="전체")" · 필터 적용" else ""}",11f,muted).apply{letterSpacing=.06f;setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(12),0,dp(9))});visible.forEach{f->val name=f.name?:"파일";val checked=selectedLocal.contains(f.uri.toString());entryRow((if(checked)"✓ " else if(isFavorite(false,f.uri.toString()))"★ " else "")+name,if(f.isDirectory)"폴더" else fileBadge(name),if(f.isDirectory)"수정 ${date(f.lastModified())}" else "${bytes(f.length())}  ·  수정 ${date(f.lastModified())}",f.isDirectory,if(selectionMode)null else ({localActions(f)})){if(selectionMode){if(!selectedLocal.add(f.uri.toString()))selectedLocal.remove(f.uri.toString());renderLocal(localItems)}else if(f.isDirectory){localPath.add(f);showLocal()}else localActions(f)}};if(visible.isEmpty())body.addView(text("표시할 파일이 없습니다.",16f,muted))
    }
    private fun localActions(uri:Uri,name:String,mime:String){DocumentFile.fromSingleUri(this,uri)?.let{localActions(it)}?:openUri(uri,mime)}
    private fun localActions(f:DocumentFile){val name=f.name?:"파일";touchRecent(false,f.uri.toString(),name);val options=mutableListOf<String>();if(f.isFile)options.addAll(listOf("열기","Google Drive 업로드"));else options.add("열기");options.add(if(isFavorite(false,f.uri.toString()))"즐겨찾기 해제" else "즐겨찾기 추가");options.addAll(listOf("이름 변경","복사 준비","이동 준비","삭제","정보"));AlertDialog.Builder(this).setTitle(name).setItems(options.toTypedArray()){_,i->when(options[i]){"열기"->if(f.isDirectory){localPath.add(f);showLocal()}else openUri(f.uri,f.type);"Google Drive 업로드"->uploadFiles(listOf(f.uri));"즐겨찾기 추가","즐겨찾기 해제"->{toggleFavorite(false,f.uri.toString(),name);renderLocal(localItems)};"이름 변경"->renameLocal(f);"복사 준비"->{localClipboard=f to false;state.text="대상 폴더에서 '여기에 붙여넣기'를 누르세요"};"이동 준비"->{localClipboard=f to true;state.text="대상 폴더에서 '여기에 붙여넣기'를 누르세요"};"삭제"->confirmLocalDelete(listOf(f));"정보"->showInfo(name,f.type?:if(f.isDirectory)"folder" else "unknown",f.length(),f.lastModified())}}.show()}
    private fun renameLocal(f:DocumentFile){val edit=EditText(this).apply{setText(f.name);selectAll()};AlertDialog.Builder(this).setTitle("이름 변경").setView(edit).setNegativeButton("취소",null).setPositiveButton("변경"){_,_->if(!f.renameTo(edit.text.toString().trim()))state.text="이름 변경 실패" else showLocal()}.show()}
    private fun confirmLocalDelete(files:List<DocumentFile>){AlertDialog.Builder(this).setTitle("${files.size}개 항목 삭제").setMessage("이 작업은 되돌릴 수 없습니다.").setNegativeButton("취소",null).setPositiveButton("삭제"){_,_->task("삭제 중"){files.forEach{if(!it.delete())throw java.io.IOException("삭제 실패: ${it.name}")};val list=localPath.last().listFiles().toList();runOnUiThread{selectionMode=false;selectedLocal.clear();renderLocal(list)}}}.show()}
    private fun bulkLocal(action:String){val files=localItems.filter{it.uri.toString() in selectedLocal};if(files.isEmpty()){state.text="선택한 항목이 없습니다";return};if(action=="upload"){uploadFiles(files.filter{it.isFile}.map{it.uri});selectionMode=false;selectedLocal.clear()}else confirmLocalDelete(files)}
    private fun pasteLocal(){val pair=localClipboard?:return;val source=pair.first;val move=pair.second;val target=localPath.last();task(if(move)"이동 중" else "복사 중"){copyLocal(source,target);if(move&&!source.delete())throw java.io.IOException("원본 삭제 실패");val list=target.listFiles().toList();runOnUiThread{localClipboard=null;renderLocal(list);state.text=if(move)"이동 완료" else "복사 완료"}}}
    private fun copyLocal(source:DocumentFile,target:DocumentFile){if(source.isDirectory){val dir=target.createDirectory(source.name?:"폴더")?:throw java.io.IOException("폴더 생성 실패");source.listFiles().forEach{copyLocal(it,dir)}}else{val out=target.createFile(source.type?:"application/octet-stream",source.name?:"파일")?:throw java.io.IOException("파일 생성 실패");(contentResolver.openInputStream(source.uri)?:throw java.io.IOException("원본 열기 실패")).use{input->(contentResolver.openOutputStream(out.uri)?:throw java.io.IOException("대상 열기 실패")).use{input.copyTo(it)}}}}
    private fun uploadFiles(uris:List<Uri>){if(!api.connected){AlertDialog.Builder(this).setMessage("먼저 홈의 Google Drive에서 계정을 연결하세요.").setPositiveButton("확인",null).show();return}
        AlertDialog.Builder(this).setTitle("${uris.size}개 파일 업로드").setMessage("대상: ${remotePath.joinToString(" / "){it.second}}\n전송은 백그라운드 대기열에서 진행됩니다.").setNegativeButton("취소",null).setPositiveButton("대기열 추가"){_,_->uris.forEach{u->try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};val info=contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{c->if(c.moveToFirst())Pair(c.getString(0)?:"파일",if(c.isNull(1))0L else c.getLong(1)) else null}?:Pair("파일",0L);TransferManager.enqueueUpload(this,u.toString(),info.first,contentResolver.getType(u)?:"application/octet-stream",remotePath.last().first,info.second)};showQueue()}.show()
    }
    private fun download(f:DriveApi.DriveFile){TransferManager.enqueueDownload(this,f.id,f.name,f.mime,f.size);state.text="다운로드 대기열 추가: ${f.name}"}
    private fun downloads(){mode="downloads";title.text="다운로드";subtitle.text="이 앱에서 받은 파일";actions.removeAllViews();body.removeAllViews();val list=File(getExternalFilesDir(null),"Downloads").listFiles()?.sortedByDescending{it.lastModified()}.orEmpty();body.addView(text("DOWNLOADED  ·  ${list.size}개",11f,muted).apply{letterSpacing=.06f;setTypeface(null,Typeface.BOLD);setPadding(dp(4),dp(12),0,dp(9))});list.forEach{f->entryRow(f.name,fileBadge(f.name),"${bytes(f.length())}  ·  ${date(f.lastModified())}",false){val uri=FileProvider.getUriForFile(this,"$packageName.files",f);if(f.extension.equals("apk",true))installApk(uri)else{val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())?:"application/octet-stream";openUri(uri,mime)}}};if(list.isEmpty())body.addView(text("아직 다운로드한 파일이 없습니다.",16f,muted))}
    private fun openUri(u:Uri,mime:String?){if(mime=="application/vnd.android.package-archive"){installApk(u);return};try{val intent=Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(mime==null)intent.data=u else intent.setDataAndType(u,mime);startActivity(intent)}catch(e:ActivityNotFoundException){AlertDialog.Builder(this).setMessage("파일은 저장되어 있습니다. 이 파일을 열 수 있는 앱이 설치되어 있지 않습니다.").setPositiveButton("확인",null).show()}}
    private fun updatePrefs()=getSharedPreferences("p4_update",0)
    private fun updateManifestUrl()=updatePrefs().getString("manifest_url",null)?.trim().orEmpty().ifBlank{BuildConfig.UPDATE_MANIFEST_URL.trim()}
    private fun checkAppUpdate(showLatest:Boolean=true){
        val saved=updateManifestUrl();if(saved.isBlank()){
            val edit=EditText(this).apply{hint="https://github.com/OWNER/REPO/releases/latest/download/update.json";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI}
            AlertDialog.Builder(this).setTitle("업데이트 주소 설정").setMessage("GitHub Release의 update.json 고정 주소를 입력하세요.").setView(edit).setNegativeButton("취소",null).setPositiveButton("저장"){_,_->val url=edit.text.toString().trim();if(url.startsWith("https://")){updatePrefs().edit().putString("manifest_url",url).apply();checkAppUpdate()}else state.text="HTTPS 업데이트 주소가 필요합니다"}.show();return
        }
        task("업데이트 확인 중"){
            val info=appUpdater.check(saved)
            runOnUiThread{
                if(info.versionCode<=BuildConfig.VERSION_CODE){if(showLatest)AlertDialog.Builder(this).setTitle("최신 버전입니다").setMessage("설치됨 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").setPositiveButton("확인",null).setNeutralButton("주소 변경"){_,_->updatePrefs().edit().remove("manifest_url").apply();checkAppUpdate()}.show()}
                else AlertDialog.Builder(this).setTitle("P4 Files v${info.versionName} 업데이트").setMessage("설치됨 v${BuildConfig.VERSION_NAME}\n새 버전 v${info.versionName}\n\n${info.notes}\n\n차량 정책상 P4 Files에서 직접 설치하지 않고 순정 Download 폴더로 넘깁니다.").setNegativeButton("나중에",null).setNeutralButton("주소 변경"){_,_->updatePrefs().edit().remove("manifest_url").apply();checkAppUpdate()}.setPositiveButton("다운로드"){_,_->downloadAppUpdate(info)}.show()
            }
        }
    }
    private fun downloadAppUpdate(info:UpdateInfo){task("업데이트 다운로드 중"){val file=appUpdater.download(info){done,total->update(if(total>0)"업데이트 ${done*100/total}%" else "업데이트 ${bytes(done)}")};val name="P4-Files-v${info.versionName}.apk";val uri=publishToPublicDownloads(file,name);runOnUiThread{showDownloadedUpdate(uri,name)}}}
    private fun publishToPublicDownloads(source:File,name:String):Uri{
        if(Build.VERSION.SDK_INT>=29){
            val values=android.content.ContentValues().apply{put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/vnd.android.package-archive");put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS);put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)}
            val uri=contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw java.io.IOException("공용 Download 파일을 만들 수 없습니다")
            try{(contentResolver.openOutputStream(uri)?:throw java.io.IOException("공용 Download 파일을 열 수 없습니다")).use{out->source.inputStream().use{it.copyTo(out)}};values.clear();values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0);contentResolver.update(uri,values,null,null);return uri}catch(e:Exception){contentResolver.delete(uri,null,null);throw e}
        }
        throw java.io.IOException("이 Android 버전에서는 공용 Download 저장을 지원하지 않습니다")
    }
    private fun showDownloadedUpdate(uri:Uri,name:String){
        pendingInstallUri=uri;state.text="Download/$name 저장 완료"
        AlertDialog.Builder(this).setTitle("업데이트 다운로드 완료").setMessage("공용 Download 폴더에 저장했습니다.\n\n$name\n\n차량 보안 정책 때문에 P4 Files에서 설치하면 차단됩니다. 지금 순정 파일 앱의 Download 폴더를 열어 APK를 눌러 설치하세요.").setNegativeButton("나중에",null).setPositiveButton("순정 Download 열기"){_,_->openSystemDownloads()}.show()
    }
    private fun openSystemDownloads(){
        val intents=listOf(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),Intent(Intent.ACTION_VIEW,android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Download")).setType("vnd.android.document/directory").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        for(intent in intents)try{startActivity(intent);return}catch(_:Exception){}
        AlertDialog.Builder(this).setTitle("순정 파일 앱을 열 수 없음").setMessage("차량의 기본 파일 앱을 직접 열고 Download 폴더에서 P4-Files APK를 선택하세요.").setPositiveButton("확인",null).show()
    }
    private fun installApk(uri:Uri){
        if(Build.VERSION.SDK_INT>=26&&!packageManager.canRequestPackageInstalls()){
            pendingInstallUri=uri
            AlertDialog.Builder(this).setTitle("차량 설치 권한 제한").setMessage("P4 Files가 설치 권한 설정을 열 수 없는 차량일 수 있습니다. APK는 공용 Download 폴더에 저장되어 있으니 순정 파일 앱에서 선택하세요.").setNegativeButton("닫기",null).setNeutralButton("설정 열기"){_,_->try{startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))}catch(_:Exception){openSystemDownloads()}}.setPositiveButton("순정 Download 열기"){_,_->openSystemDownloads()}.show();return
        }
        pendingInstallUri=null
        try{startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,false).apply{clipData=android.content.ClipData.newRawUri("APK",uri)})}
        catch(e:Exception){AlertDialog.Builder(this).setTitle("APK 설치 실행 실패").setMessage("차량 패키지 설치기 또는 차량 정책이 설치 요청을 처리하지 못했습니다.\n\n${e.message?:e.javaClass.simpleName}").setPositiveButton("확인",null).show()}
    }
    override fun onResume(){super.onResume();val uri=pendingInstallUri;if(uri!=null&&(Build.VERSION.SDK_INT<26||packageManager.canRequestPackageInstalls()))installApk(uri)}
    private fun back(){if(busy)return;when{mode=="drive" && remotePath.size>1->{remotePath.removeAt(remotePath.lastIndex);showDrive()};mode=="local" && localPath.size>1->{localPath.removeAt(localPath.lastIndex);showLocal()};mode=="dual"&&localPath.size>1->{localPath.removeAt(localPath.lastIndex);showDualPane()};mode=="dual"&&remotePath.size>1->{remotePath.removeAt(remotePath.lastIndex);showDualPane()};mode=="dual"->finish();mode!="home"->home();else->finish()}}
    @Deprecated("Legacy activity navigation") override fun onBackPressed(){back()}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d)
        if(r==authorizationRequestCode){
            if(c!=RESULT_OK||d==null){authFailed("OAUTH_ACCESS_DENIED","사용자가 Google 권한 승인을 취소했거나 승인 결과가 없습니다.");return}
            try{finishAuthorization(authorizationClient.getAuthorizationResultFromIntent(d))}catch(e:ApiException){authFailed(classifyAuthorizationError(e),"status=${e.statusCode}: ${e.message.orEmpty()}")}
            return
        }
        if(c!=RESULT_OK||d==null)return
        when(r){70->{val u=d.data?:return;try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}catch(_:SecurityException){};val dir=DocumentFile.fromTreeUri(this,u)?:return;storagePrefs().edit().putString("tree_uri",u.toString()).apply();localPath.clear();localPath.add(dir);if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&resources.configuration.screenWidthDp>=700)showDualPane()else showLocal()}
            71,72->{val uris=d.clipData?.let{clip->(0 until clip.itemCount).map{clip.getItemAt(it).uri}}?:listOfNotNull(d.data);if(r==72||mode=="drive"||uris.size>1)uploadFiles(uris)else uris.firstOrNull()?.let{localActions(it,"선택한 파일",contentResolver.getType(it)?:"application/octet-stream")}}
            73->{val f=pendingDownload?:return;val uri=d.data?:return;pendingDownload=null;task("파일 저장 중"){(contentResolver.openOutputStream(uri)?:throw java.io.IOException("저장 파일을 열 수 없습니다.")).use{api.download(f.id,it){n->update("${bytes(n)} 저장 중")}};update("저장 완료: ${f.name}")}}
            75->{val f=pendingDashcam?:return;val uri=d.data?:return;pendingDashcam=null;task("대시캠 저장 중"){dashcamApi.withFileList{(contentResolver.openOutputStream(uri)?:throw java.io.IOException("저장 파일을 열 수 없습니다.")).use{out->dashcamApi.download(f,out){n->update("${bytes(n)} 저장 중 · ${f.name}")}}};update("대시캠 저장 완료: ${f.name}")}}
        }
    }
    override fun onDestroy(){cancelled.set(true);vehicleAuthCancel?.set(true);vehicleAuthDialog?.dismiss();handler.removeCallbacks(queueRefresh);worker.shutdownNow();thumbWorker.shutdownNow();dashcamThumbs.evictAll();super.onDestroy()}
}
