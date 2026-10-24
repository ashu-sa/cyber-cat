package com.cybercat.stopwatch

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

private val Ink=Color(0xFF203B2B)
private val Moss=Color(0xFF476948)
private val Paper=Color(0xFFF5F6EF)
private val Sage=Color(0xFFE6EEDC)
private val Muted=Color(0xFF6B7869)
private val Coral=Color(0xFFAE6854)
private val AppColors=lightColorScheme(primary=Moss,onPrimary=Color.White,secondary=Color(0xFF7B8562),background=Paper,surface=Color(0xFFFEFFF9),onSurface=Ink,onBackground=Ink,surfaceVariant=Sage,onSurfaceVariant=Muted,outline=Color(0xFFC1CCB9),secondaryContainer=Sage,primaryContainer=Sage,onPrimaryContainer=Ink,onSecondaryContainer=Ink,onTertiaryContainer=Ink,surfaceContainerHighest=Color(0xFFE4E9DF),surfaceContainer=Paper,error=Coral)
private fun duration(ms:Long):String {val m=(ms.coerceAtLeast(0)/60000);return if(m>=60)"${m/60}h ${m%60}m" else "${m}m"}
private fun clock(ms:Long):String {val sec=ms.coerceAtLeast(0)/1000;return "%02d:%02d:%02d".format(java.util.Locale.ROOT,sec/3600,sec/60%60,sec%60)}
private fun dateOf(record:JSONObject)=Instant.ofEpochMilli(record.getLong("start")).atZone(ZoneId.systemDefault()).toLocalDate()
private fun sum(records:List<JSONObject>)=records.sumOf {it.getLong("duration")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocanopyScreen(revision:Int,tab:String,onTab:(String)->Unit,profileOpen:Boolean,onProfile:(Boolean)->Unit,saveOpen:Boolean,onSaveChange:(Boolean)->Unit,onAction:(String)->Unit) {
    // The foreground activity supplies one monotonic timer refresh per second.
    @Suppress("UNUSED_VARIABLE") val frame=revision
    val snackbar=remember { SnackbarHostState() }
    val pageStates=rememberSaveableStateHolder()
    val error=Repository.error
    LaunchedEffect(error) { if(error!=null) {snackbar.showSnackbar(error);Repository.error=null} }
    MaterialTheme(colorScheme=AppColors,typography=Typography()) {
        BackHandler(enabled=tab!="Timer") {onTab("Timer")}
        Scaffold(containerColor=Paper,snackbarHost={SnackbarHost(snackbar)},topBar={
            Column(Modifier.statusBarsPadding().padding(horizontal=24.dp).padding(top=14.dp,bottom=10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Spa,null,tint=Moss,modifier=Modifier.size(17.dp))
                    Text("  FOCANOPY",fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold,color=Moss,modifier=Modifier.weight(1f))
                    SmallIcon("Sync sessions",Icons.Default.Sync,busy=Cloud.busy.get()) {if(Cloud.owner==null)onProfile(true);onAction("sync")}
                    Spacer(Modifier.width(8.dp))
                    SmallIcon("Google profile",if(Cloud.owner==null)Icons.Default.PersonOutline else Icons.Default.AccountCircle) {onProfile(true);if(Cloud.owner==null)onAction("google")}
                }
                Spacer(Modifier.height(12.dp))
                Text(when(tab){"Journal"->"Your time, in bloom.";"Settings"->"A space of your own.";else->"Make room to grow."},fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-0.7).sp)
            }
        },bottomBar={
            NavigationBar(containerColor=Paper,tonalElevation=0.dp) {
                listOf(Triple("Timer","Focus",Icons.Default.Spa),Triple("Journal","Journal",Icons.Default.CalendarMonth),Triple("Settings","Settings",Icons.Default.Tune)).forEach {(key,label,icon)->
                    NavigationBarItem(selected=tab==key,onClick={onTab(key)},icon={Icon(icon,label)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=Sage,selectedIconColor=Moss,selectedTextColor=Ink,unselectedIconColor=Muted))
                }
            }
        }) { padding ->
            AnimatedContent(targetState=tab,transitionSpec={ (fadeIn(tween(220))+slideInHorizontally(tween(220)){it/14}) togetherWith fadeOut(tween(130)) },label="page") { page ->
                pageStates.SaveableStateProvider(page) {
                when(page) {
                    "Journal"->JournalPage(Modifier.padding(padding),revision,onAction)
                    "Settings"->SettingsPage(Modifier.padding(padding),revision,onAction,{onProfile(true)})
                    else->FocusPage(Modifier.padding(padding),revision,onAction,{onAction("finish");onSaveChange(true)},{onSaveChange(true)})
                }
                }
            }
        }
        if(profileOpen) ModalBottomSheet(onDismissRequest={onProfile(false)},containerColor=Paper) {
            ProfileSheet(revision,onProfile,onAction)
        }
        if(saveOpen) SaveSheet(onDismiss={onSaveChange(false)},onSaved={onSaveChange(false);onTab("Journal");onAction("saved")})
    }
}

@Composable private fun SmallIcon(label:String,icon:ImageVector,busy:Boolean=false,onClick:()->Unit) {
    val angle=if(busy) {
        val transition=rememberInfiniteTransition(label="sync")
        val turn by transition.animateFloat(0f,360f,infiniteRepeatable(tween(1100,easing=LinearEasing)),label="sync arrows")
        turn
    } else 0f
    IconButton(onClick=onClick,enabled=!busy,modifier=Modifier.size(44.dp).background(Sage,CircleShape)) {
        Icon(icon,label,Modifier.size(21.dp).rotate(angle),tint=Moss)
    }
}

@Composable private fun FocusPage(modifier:Modifier,revision:Int,onAction:(String)->Unit,onFinish:()->Unit,onSave:()->Unit) {
    val compact=LocalConfiguration.current.screenHeightDp<700
    val phase=Repository.engine.phase
    val pomo=Repository.pomodoroEnabled
    val stage=Repository.pomodoro.stage
    val running=phase=="running"
    val focus=stage=="focus"
    val elapsed=Repository.engine.elapsed()
    val today=Repository.sessions.filter {dateOf(it)==LocalDate.now()}
    val progress=if(pomo) ((Pomodoro.FOCUS-Repository.pomodoro.remaining(elapsed)).toFloat()/Pomodoro.FOCUS).coerceIn(0f,1f) else (Repository.growthElapsed()%Forest.HOUR).toFloat()/Forest.HOUR
    val animatedProgress by animateFloatAsState(progress,tween(850),label="growth progress")
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,6.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(Cloud.googlePending) item {
            Surface(shape=RoundedCornerShape(20.dp),color=Sage) {Column(Modifier.padding(16.dp)) {
                Text("Google sign-in is waiting",fontWeight=FontWeight.Medium)
                Text("Complete it in the secure tab, or cancel to start focusing.",fontSize=12.sp,color=Muted)
                TextButton(onClick={onAction("cancelGoogle")}){Text("Cancel sign-in")}
            }}
        }
        item {
            Surface(shape=RoundedCornerShape(20.dp),color=Color.White) {
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFlorist,null,tint=Moss,modifier=Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("Flower focus",fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        Text("Optional · 25 min focus, a gentle break",fontSize=11.sp,color=Muted)
                    }
                    Switch(checked=pomo,onCheckedChange={Repository.selectMode(it,Repository.pomodoro.breakMinutes);onAction("refresh")},enabled=phase=="idle" || stage=="dead")
                }
            }
        }
        item {
            val heroColor by animateColorAsState(if(phase=="paused" || stage=="dead")Color(0xFFEAE6DC) else Sage,tween(700),label="tree environment")
            Surface(shape=RoundedCornerShape(32.dp),color=heroColor) {
                Column(Modifier.fillMaxWidth().animateContentSize().padding(if(compact)20.dp else 24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 12.dp)) {
                    val badge=when {pomo && stage=="dead"->"A FRESH START AWAITS";pomo && stage=="break"->"REST & RESET";pomo && stage=="ready"->"READY WHEN YOU ARE";phase=="paused"->"WATERING PAUSED";running->"GROWING WITH YOU";phase=="finished"->"BEAUTIFULLY DONE";else->"YOUR LITTLE GROVE"}
                    Text(badge,fontSize=10.sp,letterSpacing=1.8.sp,color=Moss,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    AndroidView(factory={TreeView(it)},update={ @Suppress("UNUSED_VARIABLE") val tick=revision;it.refresh()},modifier=Modifier.fillMaxWidth().height(if(compact)96.dp else 166.dp))
                    Text(if(pomo) {val ms=Repository.displayTime();"%02d:%02d".format(java.util.Locale.ROOT,ms/60000,ms/1000%60)} else clock(Repository.displayTime()),fontSize=44.sp,fontWeight=FontWeight.Light,fontFamily=FontFamily.Monospace,letterSpacing=(-2).sp,maxLines=1)
                    val message=when {pomo && stage=="dead"->"This flower rests. You can begin again.";pomo && stage=="break"->"Stop watering. Stretch and move a little.";pomo && stage=="ready"->"Come back when you’re ready to water.";phase=="paused"->"Take a breath. Your progress is safe.";phase=="finished"->"Give your time a little name.";running->"A little water. A little growth.";else->"One small start. Something beautiful grows."}
                    AnimatedContent(targetState=message,transitionSpec={fadeIn() togetherWith fadeOut()},label="focus message") { value ->
                        Text(value,fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        val primaryIcon=if(running)Icons.Default.Pause else if(phase=="finished")Icons.Default.Save else Icons.Default.PlayArrow
                        FilledIconButton(onClick={if(phase=="finished")onSave() else onAction("primary")},enabled=!Cloud.googlePending && !(Cloud.busy.get() && Cloud.owner==null),modifier=Modifier.size(54.dp),shape=RoundedCornerShape(17.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Moss)) {
                            Crossfade(primaryIcon,label="play pause") { Icon(it,if(running)"Pause" else if(phase=="finished")"Save session" else "Start or resume",Modifier.size(23.dp)) }
                        }
                        if(!pomo && phase!="finished") FilledTonalIconButton(onClick=onFinish,enabled=phase!="idle",modifier=Modifier.size(50.dp),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Default.Done,"Finish session",Modifier.size(21.dp))}
                        if(pomo && phase!="idle" && stage!="dead") FilledTonalIconButton(onClick={onAction("cancel")},modifier=Modifier.size(50.dp),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Default.Close,"Cancel round · tree will die",tint=Coral)}
                    }
                    LinearProgressIndicator(progress={animatedProgress},modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),color=Moss,trackColor=Color.White.copy(alpha=.55f))
                    Text(if(pomo) {when(stage){"break"->"${Repository.pomodoro.breakMinutes}-minute break · Next round starts with you";"ready"->"Your next flower is waiting";"dead"->"Start again to grow a new flower";else->"Focus time · ${clock(elapsed)}"}} else Forest.nextTreeText(Repository.growthElapsed()),fontSize=11.sp,color=Muted,textAlign=TextAlign.Center)
                    if(phase=="finished") Row {
                        TextButton(onClick={onAction("primary")}){Text("Resume")}
                        TextButton(onClick={onAction("reset")}){Text("Reset",color=Coral)}
                    }
                }
            }
        }
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MetricCard("FOCUS TODAY",duration(sum(today)),Modifier.weight(1f))
            MetricCard(if(pomo)"FLOWERS" else "TREES TODAY",if(pomo)(elapsed/Pomodoro.FOCUS).toString() else Forest.trees(sum(today)).toString(),Modifier.weight(1f))
        }}
        if(pomo && phase=="idle") item {
            Surface(shape=RoundedCornerShape(22.dp),color=Color.White) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Break length",fontSize=13.sp,modifier=Modifier.weight(1f))
                listOf(5,10).forEach {minutes->FilterChip(selected=Repository.pomodoro.breakMinutes==minutes,onClick={Repository.selectMode(true,minutes);onAction("refresh")},label={Text("$minutes min")})}
            }}
        }
        if(pomo && BuildConfig.APP_BLOCKING_AVAILABLE) item {ActionRow("Protect your focus","Choose apps to keep out of your grove",Icons.Default.Shield){onAction("apps")}}
        item {TextButton(onClick={onAction("preview")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FastForward,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Watch your grove grow")}}
        Repository.error?.let {error->item {Text(error,color=Coral,fontSize=12.sp)}}
    }
}

@Composable private fun MetricCard(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=Color.White) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,fontSize=9.sp,letterSpacing=1.sp,color=Muted,fontWeight=FontWeight.SemiBold)
        Text(value,fontSize=25.sp,fontWeight=FontWeight.Medium)
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun JournalPage(modifier:Modifier,revision:Int,onAction:(String)->Unit) {
    var month by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    var selected by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var scope by rememberSaveable {mutableStateOf("Day")}
    var edit by remember {mutableStateOf<JSONObject?>(null)}
    var deletion by remember {mutableStateOf<JSONObject?>(null)}
    var undo by remember {mutableStateOf<JSONObject?>(null)}
    val date=LocalDate.parse(selected);val ym=YearMonth.parse(month)
    val sessions=Repository.sessions.toList()
    val dayTotals=sessions.groupBy {dateOf(it)}.mapValues {sum(it.value)}
    val week=date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val records=sessions.filter {val d=dateOf(it);when(scope){"Week"-> !d.isBefore(week)&&d.isBefore(week.plusDays(7));"Month"->YearMonth.from(d)==YearMonth.from(date);else->d==date}}
    val gardenDays=records.groupBy {dateOf(it)}.values.count {Forest.garden(sum(it))}
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf("Day","Week","Month").forEach {value->FilterChip(selected=scope==value,onClick={scope=value},label={Text(value)},modifier=Modifier.weight(1f))}}}
        item {
            Surface(shape=RoundedCornerShape(26.dp),color=Color.White) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        IconButton(onClick={month=ym.minusMonths(1).toString()}){Icon(Icons.Default.ChevronLeft,"Previous month")}
                        IconButton(onClick={month=ym.plusMonths(1).toString()}){Icon(Icons.Default.ChevronRight,"Next month")}
                    }
                    Row {listOf("M","T","W","T","F","S","S").forEach {Text(it,Modifier.weight(1f),color=Muted,fontSize=10.sp,textAlign=TextAlign.Center)}}
                    Spacer(Modifier.height(10.dp))
                    val offset=ym.atDay(1).dayOfWeek.value-1
                    val cells=((ym.lengthOfMonth()+offset+6)/7)*7
                    for(row in 0 until cells/7) Row {
                        for(col in 0..6) {
                            val number=row*7+col-offset+1
                            val d=if(number in 1..ym.lengthOfMonth())ym.atDay(number) else null
                            val ms=dayTotals[d] ?: 0
                            val chosen=d==date
                            val intensity=(ms.toFloat()/Forest.HOUR/4).coerceIn(0f,1f)
                            Column(Modifier.weight(1f).height(59.dp).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if(chosen)Moss else if(ms>0)Sage.copy(alpha=.55f+intensity*.45f) else Color.Transparent)
                                .then(if(d!=null)Modifier.clickable {selected=d.toString()} .semantics {contentDescription="$d, ${duration(ms)}, ${Forest.trees(ms)} trees"} else Modifier),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                if(d!=null) {
                                    Text(number.toString(),fontSize=12.sp,fontWeight=if(d==LocalDate.now())FontWeight.Bold else FontWeight.Normal,color=if(chosen)Color.White else Ink)
                                    if(ms>0) Text(duration(ms),fontSize=8.sp,maxLines=1,color=if(chosen)Color.White.copy(alpha=.85f) else Moss)
                                    else if(d==LocalDate.now()) Box(Modifier.padding(top=4.dp).size(3.dp).background(if(chosen)Color.White else Moss,CircleShape))
                                }
                            }
                        }
                    }
                    Text("A little greener with every focused hour",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
                }
            }
        }
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MetricCard("${scope.uppercase()} TOTAL",duration(sum(records)),Modifier.weight(1f))
            MetricCard(if(gardenDays>0)"GARDEN DAYS" else "TREES GROWN",if(gardenDays>0)gardenDays.toString() else Forest.trees(sum(records)).toString(),Modifier.weight(1f))
        }}
        if(records.isNotEmpty()) item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                records.groupBy {Repository.deviceLabel(it)}.forEach {(device,items)->
                    Surface(shape=CircleShape,color=Sage) {Text("$device · ${duration(sum(items))}",fontSize=11.sp,color=Moss,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))}
                }
            }
        }
        item {Row(verticalAlignment=Alignment.CenterVertically) {Text("Your sessions",fontSize=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${records.size} saved",fontSize=12.sp,color=Muted)}}
        if(undo!=null) item {TextButton(onClick={Repository.restore(undo!!);undo=null;onAction("refresh")}){Text("Session deleted · Undo")}}
        if(records.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Spa,null,tint=Moss,modifier=Modifier.size(30.dp))
                Text("A little space for growth",fontWeight=FontWeight.Medium)
                Text("Your saved sessions will bloom here.",color=Muted,fontSize=12.sp)
            }
        }
        items(records.sortedByDescending {it.getLong("start")},key={it.getString("id")}) {record->
            var menu by remember {mutableStateOf(false)}
            Surface(shape=RoundedCornerShape(22.dp),color=Color.White,modifier=Modifier.animateItem()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Spa,null,tint=Moss,modifier=Modifier.size(36.dp).background(Sage,RoundedCornerShape(12.dp)).padding(8.dp))
                    Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(record.getString("name"),fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${Repository.deviceLabel(record)} · ${duration(record.getLong("duration"))}",fontSize=11.sp,color=Muted)
                        Text(Instant.ofEpochMilli(record.getLong("start")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")),fontSize=10.sp,color=Muted)
                    }
                    Box {
                        IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Session options",tint=Muted)}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("Rename")},onClick={menu=false;edit=record})
                            DropdownMenuItem(text={Text("Delete",color=Coral)},onClick={menu=false;deletion=record})
                        }
                    }
                }
            }
        }
        item {Text("Paused time is excluded. Sessions use their local start date.",fontSize=10.sp,color=Muted)}
    }
    edit?.let {record->
        var name by remember(record) {mutableStateOf(record.getString("name"))}
        AlertDialog(onDismissRequest={edit=null},title={Text("Rename session")},text={OutlinedTextField(value=name,onValueChange={name=it.take(120)},singleLine=true,label={Text("Task name")})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={Repository.rename(record.getString("id"),name.trim());edit=null;onAction("refresh")}){Text("Save")}},dismissButton={TextButton(onClick={edit=null}){Text("Cancel")}})
    }
    deletion?.let {record->AlertDialog(onDismissRequest={deletion=null},title={Text("Delete session?")},text={Text(record.getString("name"))},confirmButton={TextButton(onClick={undo=Repository.delete(record.getString("id"));deletion=null;onAction("refresh")}){Text("Delete",color=Coral)}},dismissButton={TextButton(onClick={deletion=null}){Text("Keep")}})}
}

@Composable private fun ActionRow(title:String,subtitle:String,icon:ImageVector,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=Color.White) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=Moss,modifier=Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(subtitle,fontSize=11.sp,color=Muted)}
            Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(18.dp))
        }
    }
}

@Composable private fun SettingsPage(modifier:Modifier,revision:Int,onAction:(String)->Unit,onProfile:()->Unit) {
    val context=LocalContext.current
    var opacity by remember {mutableFloatStateOf(Repository.prefs.getFloat("opacity",1f))}
    val floating=Repository.prefs.getBoolean("showPet",false) && Settings.canDrawOverlays(context)
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ActionRow(if(Cloud.owner==null)"Your Google profile" else Cloud.username,if(Cloud.owner==null)"One account for every grove" else Cloud.status,Icons.Default.AccountCircle,onProfile)}
        item {Text("YOUR COMPANION",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,modifier=Modifier.padding(top=12.dp))}
        item {Surface(shape=RoundedCornerShape(22.dp),color=Color.White) {Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("Floating tree",fontWeight=FontWeight.Medium,fontSize=14.sp);Text("A tiny grove above other apps",fontSize=11.sp,color=Muted)}
                Switch(checked=floating,onCheckedChange={onAction("overlay")})
            }
            Spacer(Modifier.height(8.dp))
            Text("Tree opacity · ${(opacity*100).toInt()}%",fontSize=12.sp,color=Muted)
            Slider(value=opacity,onValueChange={opacity=it;Repository.prefs.edit().putFloat("opacity",it).apply()},valueRange=.3f..1f)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Animate tree & water",fontSize=14.sp,modifier=Modifier.weight(1f))
                Switch(checked=Repository.prefs.getBoolean("animate",true),onCheckedChange={Repository.prefs.edit().putBoolean("animate",it).apply();onAction("refresh")})
            }
        }}}
        item {Text("FOCUS & ACCESS",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,modifier=Modifier.padding(top=12.dp))}
        if(BuildConfig.APP_BLOCKING_AVAILABLE)item {ActionRow("Apps & permissions","Choose what stays outside your focus",Icons.Default.Shield){onAction("apps")}}
        item {ActionRow("Notifications","Focus controls when the app is in the background",Icons.Default.NotificationsNone){onAction("notifications")}}
        item {ActionRow("Tree growth preview","See a whole little lifecycle in seconds",Icons.Default.FastForward){onAction("preview")}}
        item {ActionRow("Export your journal","Keep a local JSON copy of your sessions",Icons.Default.FileDownload){onAction("export")}}
        item {Text("Focanopy ${BuildConfig.VERSION_NAME} · ${if(BuildConfig.APP_BLOCKING_AVAILABLE)"App blocking" else "Standard"}\nMade for small, steady progress.",fontSize=11.sp,color=Muted,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(18.dp))}
    }
}

@Composable private fun ProfileSheet(revision:Int,onProfile:(Boolean)->Unit,onAction:(String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=28.dp).padding(bottom=38.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Your grove, everywhere",fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));IconButton(onClick={onProfile(false)}){Icon(Icons.Default.Close,"Close profile")}}
        Icon(Icons.Default.AccountCircle,null,tint=Moss,modifier=Modifier.size(52.dp))
        if(Cloud.owner!=null) {
            Text(Cloud.username,fontSize=19.sp,fontWeight=FontWeight.Medium)
            Text(Cloud.status,fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
            Text("Use this Google account on every device. Your sessions stay together.",fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {SmallIcon("Sync sessions",Icons.Default.Sync,Cloud.busy.get()){onAction("sync")};TextButton(enabled=Repository.engine.phase=="idle" && !Cloud.busy.get(),onClick={onAction("signout")}){Text("Sign out",color=Muted)}}
            if(Cloud.hasConflicts()) {Text("Session changes need review.");Row {TextButton(onClick={onAction("keepLocal")}){Text("Keep mine")};TextButton(onClick={onAction("keepCloud")}){Text("Use cloud")}}}
        } else {
            Text(if(Cloud.googlePending)"Continue with your Google account" else "One tap. One connected grove.",fontSize=18.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
            Text("No password or registration form. Your browser securely connects you and returns here.",fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
            if(Cloud.busy.get()) { SmallIcon("Connecting",Icons.Default.Sync,true){} }
            else if(Cloud.googlePending) {TextButton(onClick={onAction("cancelGoogle")}){Text("Cancel sign-in")}}
            else Button(onClick={onAction("google")},enabled=Repository.engine.phase=="idle",modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Text("Continue with Google")}
            if(Repository.engine.phase!="idle") Text("Finish your current timer before connecting an account.",fontSize=12.sp,color=Coral,textAlign=TextAlign.Center)
            if(Cloud.status!="Local only")Text(Cloud.status,fontSize=11.sp,color=Muted,textAlign=TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SaveSheet(onDismiss:()->Unit,onSaved:()->Unit) {
    var name by rememberSaveable {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,icon={Icon(Icons.Default.Spa,null,tint=Moss)},title={Text("A little time, well spent.")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("What did you work on?",color=Muted)
        OutlinedTextField(value=name,onValueChange={name=it.take(120)},label={Text("Task name")},singleLine=true,shape=RoundedCornerShape(14.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("Job Apply","Upskill","Interview prep","Work").forEach {suggestion->SuggestionChip(onClick={name=suggestion},label={Text(suggestion,fontSize=11.sp)})}}
        error?.let {Text(it,color=Coral,fontSize=12.sp)}
    }},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={try{Repository.save(name.trim());onSaved()}catch(e:Exception){error=e.localizedMessage}}){Text("Save session")}},dismissButton={TextButton(onClick=onDismiss){Text("Keep for later")}})
}
