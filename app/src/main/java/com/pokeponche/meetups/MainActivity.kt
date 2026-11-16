package com.pokeponche.meetups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import java.time.Instant
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val builtInTypes=listOf("Community Day", "Community Day Clásico", "Día de Eclosiones", "Evento de Captura", "Día de incursiones", "Investigación limitada", "Evento temático", "Evento de temporada", "Evento principal", "Hora destacada", "Hora de incursiones", "Lunes Max", "GO Fest", "GO Tour", "Wild Area", "Viernes de Amigos", "Social")
private val bonusOptions=listOf("Polvo estelar", "Experiencia (PX)", "Caramelos", "Caramelos XL", "Distancia de eclosión", "Duración del incienso", "Duración de módulos cebo", "Probabilidad shiny", "Intercambios especiales", "Costo de intercambio", "Daño en incursiones", "Pases de incursión", "Fotobombas", "Investigaciones", "Apariciones salvajes", "Otro")
private val amountOptions=listOf("x2", "x3", "x4", "x1.5", "1/2", "1/4", "+25%", "+50%", "3 horas", "4 horas", "5", "Otro")
private data class BonusItem(val name:String="Polvo estelar", val amount:String="x2", val customName:String="", val customAmount:String="") {
    val displayName get()=if(name=="Otro") customName.trim() else name
    val displayAmount get()=if(amount=="Otro") customAmount.trim() else amount
    fun formatted() = "${displayName} — ${displayAmount}".trim()
}
private fun decodeBonuses(source:String):List<BonusItem> = source.lines().filter {it.isNotBlank()}.map { line ->
    val parts=line.split(" — ",limit=2)
    val name=parts[0].trim()
    val amount=parts.getOrElse(1){"x2"}.trim()
    BonusItem(name=if(name in bonusOptions) name else "Otro", amount=if(amount in amountOptions) amount else "Otro", customName=if(name in bonusOptions) "" else name,customAmount=if(amount in amountOptions) "" else amount)
}
private fun suggestedTitle(event:Meetup, clubName:String):String {
    val base=when(event.category) {
        "Viernes de Amigos", "Social" -> "${event.category} · $clubName"
        "Lunes Max" -> "Lunes Max de ${event.species.ifBlank {"Pokémon"}}"
        "Día de Eclosiones" -> "Día de Eclosiones de ${event.species.ifBlank {"Pokémon"}}"
        "Evento de Captura" -> "Evento de Captura de ${event.species.ifBlank {"Pokémon"}}"
        else -> "${event.category} de ${event.species.ifBlank {"Pokémon"}}"
    }
    return base + if(event.shinyAvailable && event.category !in listOf("Viernes de Amigos","Social")) " [Shiny Disponible ✨]" else ""
}

class MainActivity: ComponentActivity() {
    private fun say(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    private fun copy(label:String, text:String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label,text))
        say("$label copiado")
    }
    private fun share(event:Meetup, message:String) {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="image/png"
            putExtra(Intent.EXTRA_TEXT,message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if(event.imagePath.isNotBlank() && File(event.imagePath).exists()) {
                val uri=FileProvider.getUriForFile(this@MainActivity,"$packageName.files",File(event.imagePath))
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newUri(contentResolver,"Imagen de quedada",uri)
            } else type="text/plain"
        }
        startActivity(Intent.createChooser(intent,"Compartir invitación"))
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val repo=LocalRepository(this)
        val catalog=runCatching { repo.loadPokemon() }.getOrElse { emptyList() }
        setContent {
            MaterialTheme(colorScheme=meetupLightColors(), shapes=Shapes(
                extraSmall=RoundedCornerShape(8.dp), small=RoundedCornerShape(8.dp), medium=RoundedCornerShape(12.dp),
                large=RoundedCornerShape(16.dp), extraLarge=RoundedCornerShape(24.dp)
            )) {
                var page by remember { mutableStateOf("image") }
                var historyQuery by remember { mutableStateOf("") }
                var historyFilter by remember { mutableStateOf("Todas") }
                var showBonuses by remember { mutableStateOf(false) }
                var showDescription by remember { mutableStateOf(true) }
                var event by remember { mutableStateOf(Meetup(location=repo.defaultPlace)) }
                var refresh by remember { mutableIntStateOf(0) }
                var selectedCount by remember { mutableIntStateOf(1) }
                var isSocial by remember { mutableStateOf(false) }
                var customTypes by remember { mutableStateOf(repo.customEventTypes) }
                var showAddType by remember { mutableStateOf(false) }
                var pendingDelete by remember { mutableStateOf<Meetup?>(null) }
                var newTypeName by remember { mutableStateOf("") }
                val availableTypes = (builtInTypes + customTypes).distinct()
                var titleCustomized by remember { mutableStateOf(false) }
                val bonusRows=remember { mutableStateListOf<BonusItem>() }
                LaunchedEffect(page,event.id) {
                    if(page=="form") {
                        bonusRows.clear(); bonusRows.addAll(decodeBonuses(event.bonuses))
                        titleCustomized=event.title.isNotBlank() && event.title!=suggestedTitle(event,repo.clubName)
                        if(!titleCustomized) event=event.copy(title=suggestedTitle(event,repo.clubName))
                    }
                }
                LaunchedEffect(page,event.category,event.species,event.shinyAvailable) {
                    if(page=="form" && !titleCustomized) event=event.copy(title=suggestedTitle(event,repo.clubName))
                }
                val queries=remember { mutableStateListOf("", "", "") }
                var preview by remember { mutableStateOf<Bitmap?>(null) }
                var club by remember { mutableStateOf(repo.clubName) }
                var place by remember { mutableStateOf(repo.defaultPlace) }
                var winner by remember { mutableStateOf("") }
                var raffleCount by remember { mutableStateOf("1") }
                // Una rifa es temporal: al salir y volver, no aparecen ganadores anteriores.
                LaunchedEffect(page, event.id) {
                    if (page == "raffle") {
                        winner = ""
                        raffleCount = "1"
                    }
                }
                var currentBackgroundTarget by remember { mutableStateOf("active") }
                val backgroundLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) runCatching {
                        repo.importBackground(uri, currentBackgroundTarget).also { path ->
                            if(currentBackgroundTarget=="social") repo.socialBackground=path
                            else { repo.activeBackground=path }
                            preview=null
                        }
                    }.onSuccess {say("Imagen actualizada")}.onFailure {say("Error: ${it.message}")}
                }
                val importAttendees=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) runCatching {
                        val raw=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("CSV vacío")
                        val lines=raw.lines().filter { it.isNotBlank() }
                        val header=lines.firstOrNull()?.let(::parseCsvLine)?.map {it.trim().lowercase()} ?: error("CSV vacío")
                        val column=header.indexOfFirst { it in listOf("trainer", "username", "user_name", "nombre", "player_name", "display_name", "participant", "asistente") }
                        if(column<0) error("El CSV requiere una columna de usuario: username, trainer o nombre")
                        val unique=lines.drop(1).mapNotNull { line -> parseCsvLine(line).getOrNull(column)?.trim()?.takeIf { it.isNotBlank() } }.distinctBy {it.lowercase()}
                        if(unique.isEmpty()) error("No se encontraron nombres")
                        val updated=event.copy(attendees=unique.toMutableList(),checkedAt=java.time.Instant.now().toString(),
                            checkInCount=unique.size, acceptedCount=null, rsvpRecords=mutableListOf(), statsUpdatedAt="")
                        event=updated; repo.upsert(updated); refresh++
                        say("${unique.size} asistentes guardados")
                    }.onFailure {say("No se importó: ${it.message}")}
                }
                // Android Storage Access Framework: users select where exports are saved.
                var exportMode by remember { mutableStateOf("all_csv") }
                val exportLauncher=rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    if(uri!=null) runCatching {
                        val all=repo.readAll()
                        when(exportMode) {
                            "all_csv" -> MeetupExporter.exportEvents(this@MainActivity,uri,all)
                            "one_zip" -> MeetupExporter.exportZip(this@MainActivity,uri,listOf(event))
                            else -> MeetupExporter.exportZip(this@MainActivity,uri,all)
                        }
                    }.onSuccess {say("Exportación guardada")}.onFailure {say("Error: ${it.message}")}
                }
                var downloading by remember { mutableStateOf(false) }
                var downloadMessage by remember { mutableStateOf("") }
                // Compara fecha y hora de fin usando la zona horaria local del teléfono.
                // No basta con que sea el mismo día: la quedada debe haber terminado.
                fun finished(e: Meetup): Boolean = runCatching {
                    val end = LocalDate.parse(e.endDate.trim())
                        .atTime(LocalTime.parse(e.endTime.trim()))
                    !end.isAfter(java.time.LocalDateTime.now())
                }.getOrDefault(false)

                fun downloadEvents(targets: List<Meetup>) {
                    if (downloading) return
                    val pending = targets.filter { e ->
                        e.campfireUrl.isNotBlank() && e.checkedAt.isBlank() && finished(e)
                    }
                    if (pending.isEmpty()) {
                        downloadMessage = "No hay quedadas terminadas pendientes de descarga"
                        return
                    }
                    downloading = true
                    downloadMessage = "Consultando ${pending.size} quedada(s)…"
                    Thread {
                        var updated = 0
                        var waiting = 0
                        var failures = 0
                        val notes = mutableListOf<String>()
                        pending.forEach { e ->
                            try {
                                val result = CampfireClient.fetch(e.campfireUrl)
                                if (result.attendees != null) {
                                    // Conserva los registros que puedan haberse importado mientras consultamos.
                                    synchronized(repo) {
                                        val saved = repo.readAll().firstOrNull { it.id == e.id }
                                        if (saved != null && saved.checkedAt.isBlank()) {
                                            repo.upsert(saved.copy(
                                                attendees = result.attendees.toMutableList(),
                                                checkedAt = Instant.now().toString(),
                                                acceptedCount = result.acceptedCount,
                                                checkInCount = result.checkInCount,
                                                rsvpRecords = result.records.toMutableList(),
                                                statsUpdatedAt = Instant.now().toString()
                                            ))
                                            updated++
                                        }
                                    }
                                } else {
                                    waiting++
                                    notes.add("${e.title}: ${result.note}")
                                }
                            } catch (ex: Exception) {
                                failures++
                                notes.add("${e.title}: ${ex.message ?: "Error de conexión"}")
                            }
                        }
                        runOnUiThread {
                            downloading = false
                            refresh++
                            // Actualiza el detalle visible sin cambiar de quedada.
                            event = repo.readAll().firstOrNull { it.id == event.id } ?: event
                            downloadMessage = "Descargadas: $updated · En espera: $waiting · Errores: $failures" +
                                if (notes.isEmpty()) "" else "\n" + notes.take(4).joinToString("\n")
                        }
                    }.start()
                }
                fun downloadMissing() = downloadEvents(repo.readAll())
                fun startFresh() {
                    event=Meetup(location=repo.defaultPlace)
                    queries.indices.forEach {queries[it]=""}; selectedCount=1; isSocial=false; preview=null; titleCustomized=false; bonusRows.clear()
                    page="image"
                }
                fun whatsappText(e:Meetup):String = buildString {
                    append("📢 ").append(e.title).append("\n\n")
                    append("📆 ").append(e.startDate).append(" · ").append(e.startTime)
                        .append(" a ").append(e.endTime).append("\n")
                    append("📍 ").append(e.location).append("\n")
                    if(e.bonuses.isNotBlank()) append("🎁 Bonos: ").append(e.bonuses.replace("\n"," · ")).append("\n")
                    append("\n🔗 Únete a la quedada: ").append(e.campfireUrl).append("\n")
                    append("¡Te esperamos! · ").append(repo.clubName)
                }
                if(pendingDelete != null) {
                    AlertDialog(
                        onDismissRequest={pendingDelete=null},
                        title={Text("Eliminar quedada")},
                        text={Text("¿Eliminar «${pendingDelete?.title.orEmpty()}» del historial? Esta acción no se puede deshacer. La foto en la galería se conservará.")},
                        confirmButton={TextButton(onClick={
                            val target=pendingDelete
                            if(target!=null) {
                                repo.delete(target.id)
                                if(event.id==target.id) event=Meetup(location=repo.defaultPlace)
                                refresh++
                                page="history"
                            }
                            pendingDelete=null
                        }) { Text("Eliminar",color=MaterialTheme.colorScheme.error) }},
                        dismissButton={TextButton(onClick={pendingDelete=null}) {Text("Cancelar")}}
                    )
                }
                val mainTab = when(page) {
                    "history", "detail", "raffle", "sync" -> "history"
                    "settings" -> "settings"
                    else -> "image"
                }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Surface(modifier=Modifier.statusBarsPadding(), color=MaterialTheme.colorScheme.surface,
                            border=BorderStroke(0.5.dp,MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                if(page in listOf("form","link","share","detail","raffle","sync")) {
                                    IconButton(onClick={page=when(page) {"form"->"image";"link"->"form";"share"->"link";"raffle"->"detail";else->"history"}}) {
                                        Icon(Icons.Outlined.ArrowBack,contentDescription="Volver")
                                    }
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    Text("CREADOR DE QUEDADAS",style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                                    Text(when(page){"image"->"Crear imagen";"form"->"Datos de la quedada";
                                        "link"->"Enlace Campfire";"share"->"Compartir";
                                        "history"->"Mis quedadas";"detail"->"Detalle de quedada";
                                        "raffle"->"Rifa";"sync"->"Descargar datos";
                                        else->"Ajustes"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                                        maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                                if(page in listOf("form","link","share")) {
                                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(50)) {
                                        Text("${listOf("image","form","link","share").indexOf(page)+1} / 4",
                                            modifier=Modifier.padding(horizontal=11.dp,vertical=6.dp),
                                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor=MaterialTheme.colorScheme.surface,
                            modifier=Modifier.navigationBarsPadding(),tonalElevation=0.dp) {
                            NavigationBarItem(selected=mainTab=="image",onClick={if(page!="image") startFresh()},
                                icon={Icon(Icons.Outlined.AddCircleOutline,contentDescription=null)},label={Text("Crear")})
                            NavigationBarItem(selected=mainTab=="history",onClick={page="history"},
                                icon={Icon(Icons.Outlined.CalendarMonth,contentDescription=null)},label={Text("Quedadas")})
                            NavigationBarItem(selected=mainTab=="settings",onClick={page="settings"},
                                icon={Icon(Icons.Outlined.Settings,contentDescription=null)},label={Text("Ajustes")})
                        }
                    }
                ) { insets ->
                    Column(Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal=16.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        if(page in listOf("image","form","link","share")) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                val index=listOf("image","form","link","share").indexOf(page)
                                repeat(4) { i ->
                                    Box(Modifier.weight(1f).height(4.dp)
                                        .background(if(i<=index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(50)))
                                }
                            }
                        }
                        when(page) {

                            "image" -> {
                                PolishSectionTitle("Imagen del evento", "Selecciona hasta tres Pokémon")
                                
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Checkbox(checked=isSocial,onCheckedChange={ checked -> isSocial=checked; preview=null })
                                    Text("Evento social",modifier=Modifier.clickable { isSocial=!isSocial;preview=null })
                                }
                                if(isSocial) {
                                    Text("Se utilizará la foto social configurada en Ajustes.",
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    for(i in 0 until selectedCount) {
                                        val results=remember(queries[i],catalog) {
                                            if(queries[i].isBlank()) emptyList() else catalog.filter {it.name.contains(queries[i],true)}.take(6)
                                        }
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(value=queries[i],onValueChange={queries[i]=it;preview=null},modifier=Modifier.weight(1f),label={Text(if(i==0) "Nombre del Pokémon" else "Pokémon ${i+1}")},singleLine=true)
                                            if(i==selectedCount-1 && selectedCount<3) {
                                                FilledTonalButton(shape=RoundedCornerShape(10.dp),onClick={selectedCount++;preview=null},contentPadding=PaddingValues(horizontal=14.dp)) {Text("+",style=MaterialTheme.typography.titleLarge)}
                                            } else if(i>0 && i==selectedCount-1) {
                                                TextButton(onClick={queries[i]="";selectedCount--;preview=null}) {Text("−")}
                                            }
                                        }
                                        if(results.isNotEmpty() && results.none { it.name.equals(queries[i],true) }) {
                                            Column { results.forEach { p -> Text(p.name,modifier=Modifier.fillMaxWidth().clickable { queries[i]=p.name;preview=null }.padding(7.dp)) } }
                                        }
                                    }
                                    
                                    if(selectedCount==2) TextButton(onClick={queries[1]="";selectedCount=1;preview=null}) {Text("Quitar segundo Pokémon")}
                                }
                                Button(shape=RoundedCornerShape(10.dp),onClick={
                                    runCatching {
                                        val selected=(0 until if(isSocial) 0 else selectedCount).map { index -> catalog.firstOrNull {it.name.equals(queries[index].trim(),true)} ?: error("Selecciona un Pokémon válido: ${queries[index]}") }
                                        val background=if(isSocial) repo.socialBackground else repo.activeBackground
                                        preview?.recycle()
                                        preview=ImageEngine.render(this@MainActivity,background,selected)
                                        event=event.copy(species=selected.joinToString(", ") {it.name},category=if(isSocial) "Viernes de Amigos" else if(event.category in listOf("Viernes de Amigos","Social")) "Community Day" else event.category)
                                    }.onFailure {say("No se pudo generar: ${it.message}")}
                                }){Text("Previsualizar")}
                                preview?.let { bitmap ->
                                    Image(bitmap.asImageBitmap(),"Vista previa de invitación",Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Fit)
                                    Button(shape=RoundedCornerShape(10.dp),onClick={
                                        runCatching {
                                            val path=ImageEngine.save(this@MainActivity,bitmap)
                                            event=event.copy(imagePath=path)
                                            repo.upsert(event); page="form";refresh++
                                            say("Imagen guardada en la galería")
                                        }.onFailure {say("Error al guardar: ${it.message}")}
                                    },modifier=Modifier.fillMaxWidth()) {Text("Guardar y continuar  →")}
                                }

                            }
                            "form" -> {
                                PolishSectionTitle("Información del evento")
                                
                                DropdownChoice("Tipo de evento",event.category,availableTypes + "＋ Añadir nuevo tipo de evento") { choice ->
                                    if(choice=="＋ Añadir nuevo tipo de evento") { newTypeName="";showAddType=true }
                                    else event=event.copy(category=choice)
                                }
                                if(showAddType) {
                                    AlertDialog(
                                        onDismissRequest={showAddType=false},
                                        title={Text("Nuevo tipo de evento")},
                                        text={Column {Field("Nombre del evento",newTypeName){newTypeName=it}}},
                                        confirmButton={TextButton(onClick={
                                            val name=newTypeName.trim()
                                            if(name.isNotBlank()) {
                                                if(name !in availableTypes) { repo.customEventTypes=customTypes+name;customTypes=repo.customEventTypes }
                                                event=event.copy(category=name)
                                                showAddType=false
                                            } else say("Escribe un nombre")
                                        }) {Text("Guardar")}},
                                        dismissButton={TextButton(onClick={showAddType=false}){Text("Cancelar")}}
                                    )
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("Shiny disponible ✨",modifier=Modifier.weight(1f))
                                    Switch(checked=event.shinyAvailable,onCheckedChange={event=event.copy(shinyAvailable=it)})
                                }
                                Field("Título sugerido (editable)",event.title){titleCustomized=true;event=event.copy(title=it)}
                                TextButton(onClick={titleCustomized=false;event=event.copy(title=suggestedTitle(event,repo.clubName))}) {Text("↺ Sugerir título")}
                                Field("Pokémon / protagonistas",event.species){event=event.copy(species=it)}
                                Text("Bonos · ${bonusRows.size}",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={showBonuses=!showBonuses}) {
                                    Text(if(showBonuses) "Ocultar bonos  ⌃" else "Editar bonos  ⌄")
                                }
                                if(showBonuses) bonusRows.forEachIndexed { index, row ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                            DropdownChoice("Bono ${index+1}",row.name,bonusOptions) { selected ->
                                                bonusRows[index]=row.copy(name=selected)
                                                event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                            }
                                            if(row.name=="Otro") Field("Nombre del bono",row.customName){ updated ->
                                                bonusRows[index]=bonusRows[index].copy(customName=updated)
                                                event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                            }
                                            DropdownChoice("¿Cuánto?",row.amount,amountOptions) { selected ->
                                                bonusRows[index]=bonusRows[index].copy(amount=selected)
                                                event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                            }
                                            if(row.amount=="Otro") Field("Cantidad o duración",row.customAmount){ updated ->
                                                bonusRows[index]=bonusRows[index].copy(customAmount=updated)
                                                event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                            }
                                            TextButton(onClick={
                                                bonusRows.removeAt(index)
                                                event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                            }) {Text("Quitar bono")}
                                        }
                                    }
                                }
                                OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={
                                    showBonuses=true
                                    bonusRows.add(BonusItem())
                                    event=event.copy(bonuses=bonusRows.joinToString("\n") {it.formatted()})
                                }) {Text("+ Añadir bono")}
                                Text("Fecha y hora",style=MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f)) {DateChoice("Inicio",event.startDate){event=event.copy(startDate=it,endDate=if(event.endDate.isBlank()) it else event.endDate)}}
                                    Box(Modifier.weight(1f)) {TimeChoice("Hora",event.startTime){event=event.copy(startTime=it)}}
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f)) {DateChoice("Fin",event.endDate){event=event.copy(endDate=it)}}
                                    Box(Modifier.weight(1f)) {TimeChoice("Hora",event.endTime){event=event.copy(endTime=it)}}
                                }
                                Field("Lugar",event.location){event=event.copy(location=it)}
                                OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={
                                    val text=buildString {
                                        append("✨ ¡Acompáñanos a ").append(event.category).append("!")
                                        if(event.species.isNotBlank()) append("\n🎮 Protagonistas: ").append(event.species)
                                        append("\n📅 ").append(event.startDate).append(" de ").append(event.startTime).append(" a ").append(event.endTime)
                                        append("\n📍 ").append(event.location)
                                        if(event.bonuses.isNotBlank()) append("\n\n🎁 Bonos del evento:\n").append(event.bonuses)
                                        append("\n\n💙 ¡Te esperamos!")
                                    }
                                    event=event.copy(description=text)
                                }){Text("Generar descripción")}
                                TextButton(onClick={showDescription=!showDescription}) {Text(if(showDescription) "Ocultar descripción  ⌃" else "Editar descripción  ⌄")}
                                if(showDescription) Field("Descripción",event.description,lines=6){event=event.copy(description=it)}
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={copy("Título",event.title)}){Text("Copiar título")}
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={copy("Descripción",event.description)}){Text("Copiar descripción")}
                                }
                                Button(shape=RoundedCornerShape(10.dp),onClick={
                                    runCatching {
                                        require(event.title.isNotBlank() && event.description.isNotBlank()){"Completa título y descripción"}
                                        require(event.startDate.isNotBlank() && event.endDate.isNotBlank()){"Completa las fechas"}
                                        val d1=LocalDate.parse(event.startDate); val d2=LocalDate.parse(event.endDate)
                                        val t1=LocalTime.parse(event.startTime.trim()); val t2=LocalTime.parse(event.endTime.trim())
                                        require(d2.atTime(t2).isAfter(d1.atTime(t1))){"La fecha final debe ser posterior al inicio"}
                                        repo.upsert(event);refresh++;page="link"
                                    }.onFailure {say(it.message ?: "Revisa fechas y horarios")}
                                },modifier=Modifier.fillMaxWidth()){Text("Continuar  →")}
                            }
                            "link" -> {
                                Text("Copia los datos en Campfire y pega el enlace",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={copy("Título",event.title)}){Text("Título")}
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={copy("Descripción",event.description)}){Text("Descripción")}
                                }
                                Field("Enlace de la quedada",event.campfireUrl){event=event.copy(campfireUrl=it.trim())}
                                Button(shape=RoundedCornerShape(10.dp),onClick={
                                    if(!event.campfireUrl.startsWith("https://"))say("Introduce un enlace https válido")
                                    else {repo.upsert(event);refresh++;page="share"}
                                },modifier=Modifier.fillMaxWidth()){Text("Guardar y continuar  →")}
                            }
                            "share" -> {
                                PolishSectionTitle("Invitación lista")
                                Text(event.title,style=MaterialTheme.typography.titleLarge)
                                Text(whatsappText(event))
                                Button(shape=RoundedCornerShape(10.dp),onClick={copy("Mensaje de WhatsApp",whatsappText(event))},modifier=Modifier.fillMaxWidth()){Text("Copiar mensaje")}
                                Button(shape=RoundedCornerShape(10.dp),onClick={share(event,whatsappText(event))},modifier=Modifier.fillMaxWidth()){Text("Compartir imagen y mensaje")}
                                OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={repo.upsert(event);refresh++;page="detail"},modifier=Modifier.fillMaxWidth()){Text("Ver quedada")}
                                TextButton(onClick={startFresh()}){Text("Crear otra quedada")}
                            }
                            "history" -> {
                                val events=remember(refresh) {repo.readAll().sortedByDescending {it.createdAt}}
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                    PolishSectionTitle("Tus eventos", "${events.size} quedadas guardadas")
                                    FilledIconButton(onClick={startFresh()}) { Icon(Icons.Outlined.Add,contentDescription="Nueva quedada") }
                                }
                                Field("Buscar por nombre o tipo",historyQuery){historyQuery=it}
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf("Todas","Pendientes","Con datos").forEach { filter ->
                                        FilterChip(selected=historyFilter==filter,onClick={historyFilter=filter},label={Text(filter)})
                                    }
                                }
                                PolishCard {
                                    PolishSectionTitle("Datos y exportación")
                                    ExportActionRow("Descargar check-ins",Icons.Outlined.Download) { page="sync" }
                                    ExportActionRow("Exportar solo quedadas",Icons.Outlined.IosShare) {
                                        exportMode="all_csv";exportLauncher.launch("quedadas.csv")
                                    }
                                    ExportActionRow("Exportar todos los datos",Icons.Outlined.IosShare) {
                                        exportMode="all_zip";exportLauncher.launch("todas_las_quedadas.zip")
                                    }
                                }
                                val visible = events.filter { e ->
                                    (historyQuery.isBlank() || e.title.contains(historyQuery,true) || e.category.contains(historyQuery,true)) &&
                                    when(historyFilter){"Pendientes"->e.checkedAt.isBlank();"Con datos"->e.checkedAt.isNotBlank();else->true}
                                }
                                if(visible.isEmpty()) {
                                    PolishCard {
                                        Text("No hay quedadas para mostrar",style=MaterialTheme.typography.titleMedium)
                                        Text("Crea una nueva o cambia los filtros",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                visible.forEach { e ->
                                    MeetupHistoryCard(e) {event=e;downloadMessage="";page="detail"}
                                }
                            }
                            "detail" -> {
                                if(event.imagePath.isNotBlank() && File(event.imagePath).exists()) {
                                    val image=remember(event.imagePath){BitmapFactory.decodeFile(event.imagePath)}
                                    image?.let {
                                        Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                                            Image(it.asImageBitmap(),"Imagen de la quedada",Modifier.fillMaxWidth().aspectRatio(1.7f),contentScale=ContentScale.Crop)
                                        }
                                    }
                                }
                                PolishCard {
                                    Text(event.title.ifBlank {"Borrador"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                                    Text(event.category,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                                    Text("${event.startDate} · ${event.startTime} – ${event.endTime}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${event.location}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    StatusChip(if(event.checkedAt.isNotBlank()) "Con check-ins" else if(event.campfireUrl.isBlank()) "Sin enlace" else "Pendiente")
                                    var detailDescriptionExpanded by remember(event.id) {mutableStateOf(false)}
                                    TextButton(onClick={detailDescriptionExpanded=!detailDescriptionExpanded}) {
                                        Text(if(detailDescriptionExpanded) "Ocultar descripción" else "Ver descripción")
                                    }
                                    if(detailDescriptionExpanded) Text(event.description,style=MaterialTheme.typography.bodyMedium)
                                }
                                PolishCard {
                                    PolishSectionTitle("Asistencia")
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                                        MetricTile("Check-ins",(event.checkInCount ?: event.attendees.size).toString(),Modifier.weight(1f))
                                        MetricTile("Aceptaron",event.acceptedCount?.toString() ?: "—",Modifier.weight(1f))
                                        val rate=if((event.acceptedCount ?: 0)>0 && event.checkInCount!=null)
                                            "%.0f%%".format(Locale.ROOT,100.0*event.checkInCount!!/event.acceptedCount!!) else "—"
                                        MetricTile("Tasa",rate,Modifier.weight(1f))
                                    }
                                    Button(shape=RoundedCornerShape(10.dp),onClick={downloadEvents(listOf(event))},enabled=!downloading && event.campfireUrl.isNotBlank() &&
                                        finished(event) && event.checkedAt.isBlank(),modifier=Modifier.fillMaxWidth()) {
                                        Icon(Icons.Outlined.Download,contentDescription=null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if(downloading) "Descargando…" else "Descargar check-ins")
                                    }
                                    if(downloadMessage.isNotBlank()) Text(downloadMessage,style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(event.checkedAt.isNotBlank()) Text("Datos guardados localmente",style=MaterialTheme.typography.labelMedium,
                                        color=Color(0xFF166534))
                                }
                                PolishCard {
                                    PolishSectionTitle("Acciones")
                                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
                                        ActionTile("Exportar ZIP",Icons.Outlined.FileDownload,Modifier.weight(1f)) {
                                            exportMode="one_zip";exportLauncher.launch("quedada_${event.id.take(8)}.zip")
                                        }
                                        ActionTile("Compartir",Icons.Outlined.IosShare,Modifier.weight(1f)) {page="share"}
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
                                        ActionTile("Hacer rifa",Icons.Outlined.Casino,Modifier.weight(1f)) {page="raffle"}
                                        ActionTile("Editar datos",Icons.Outlined.Edit,Modifier.weight(1f)) {page="form"}
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
                                        ActionTile("Editar enlace",Icons.Outlined.InsertLink,Modifier.weight(1f)) {page="link"}
                                        ActionTile("Importar CSV",Icons.Outlined.UploadFile,Modifier.weight(1f)) {
                                            importAttendees.launch(arrayOf("text/*","application/csv","application/octet-stream"))
                                        }
                                    }
                                }
                                TextButton(onClick={pendingDelete=event},modifier=Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.DeleteOutline,contentDescription=null,tint=MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Eliminar quedada",color=MaterialTheme.colorScheme.error)
                                }
                            }
                            "raffle" -> {
                                PolishCard {
                                    PolishSectionTitle("Sorteo",event.title)
                                    Text("${event.attendees.size} participantes",style=MaterialTheme.typography.titleLarge)
                                    Field("Número de ganadores",raffleCount){raffleCount=it}
                                    Button(shape=RoundedCornerShape(10.dp),enabled=event.attendees.isNotEmpty(),modifier=Modifier.fillMaxWidth(),onClick={
                                        val n=raffleCount.toIntOrNull()?.coerceIn(1,event.attendees.size) ?: 1
                                        winner=event.attendees.shuffled().take(n).joinToString("\n🏆 ",prefix="🏆 ")
                                    }){
                                        Icon(Icons.Outlined.Casino,contentDescription=null)
                                        Spacer(Modifier.width(8.dp));Text("Sortear")
                                    }
                                }
                                if(winner.isNotBlank()) {
                                    PolishCard {
                                        PolishSectionTitle("Ganadores")
                                        Text(winner,style=MaterialTheme.typography.titleMedium)
                                        OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={copy("Ganadores",winner)},modifier=Modifier.fillMaxWidth()) {
                                            Icon(Icons.Outlined.ContentCopy,contentDescription=null)
                                            Spacer(Modifier.width(8.dp));Text("Copiar ganadores")
                                        }
                                    }
                                }
                            }
                            "sync" -> {
                                Text("Descargar check-ins",style=MaterialTheme.typography.titleLarge)
                                val pending=repo.readAll().filter {it.campfireUrl.isNotBlank() && it.checkedAt.isBlank() && finished(it)}
                                Text("${pending.size} quedadas terminadas sin check-ins locales")
                                Button(shape=RoundedCornerShape(10.dp),onClick={downloadMissing()},enabled=!downloading,modifier=Modifier.fillMaxWidth()) {
                                    Text(if(downloading) "Descargando…" else "Descargar datos pendientes")
                                }
                                if(downloadMessage.isNotBlank()) Text(downloadMessage, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("También puedes importar un CSV",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                pending.forEach { e ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(e.title,fontWeight=FontWeight.SemiBold)
                                            Text(e.endDate)
                                            TextButton(onClick={event=e;importAttendees.launch(arrayOf("text/*","application/csv","application/octet-stream"))}) {Text("Importar CSV de asistentes")}
                                        }
                                    }
                                }
                                TextButton(onClick={page="history"}) {Text("Volver")}
                            }
                            "settings" -> {
                                PolishCard {
                                    PolishSectionTitle("Comunidad")
                                    Field("Nombre de la comunidad",club){club=it}
                                    Field("Lugar habitual",place){place=it}
                                    Button(shape=RoundedCornerShape(10.dp),onClick={repo.clubName=club;repo.defaultPlace=place;say("Configuración guardada")},
                                        modifier=Modifier.fillMaxWidth()) {Text("Guardar cambios")}
                                }
                                PolishCard {
                                    PolishSectionTitle("Imágenes predeterminadas")
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={currentBackgroundTarget="active";backgroundLauncher.launch(arrayOf("image/*"))},
                                        modifier=Modifier.fillMaxWidth()) {
                                        Icon(Icons.Outlined.Image,contentDescription=null)
                                        Spacer(Modifier.width(8.dp));Text("Cambiar fondo de temporada")
                                    }
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={currentBackgroundTarget="social";backgroundLauncher.launch(arrayOf("image/*"))},
                                        modifier=Modifier.fillMaxWidth()) {Text("Cambiar foto social")}
                                }
                                PolishCard {
                                    PolishSectionTitle("Almacenamiento")
                                    Text("Las quedadas y los registros se guardan en este dispositivo.",
                                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={page="history"},modifier=Modifier.fillMaxWidth()) {Text("Ir a mis quedadas")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label:String, value:String, lines:Int=1, onChange:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(10.dp),minLines=lines,maxLines=if(lines>1) 12 else 1,singleLine=lines==1,
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            focusedContainerColor=MaterialTheme.colorScheme.surface))
}


@Composable
private fun DropdownChoice(label:String, selected:String, items:List<String>, onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
            Text("$label: $selected  ▾",modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            items.forEach { item ->
                DropdownMenuItem(text={Text(item)},onClick={onSelect(item);expanded=false})
            }
        }
    }
}

@Composable
private fun DateChoice(label:String,value:String,onChange:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(shape=RoundedCornerShape(10.dp),onClick={
        val date=runCatching {LocalDate.parse(value)}.getOrElse {LocalDate.now()}
        DatePickerDialog(context,{ _,year,month,day ->
            onChange(LocalDate.of(year,month+1,day).toString())
        },date.year,date.monthValue-1,date.dayOfMonth).show()
    },modifier=Modifier.fillMaxWidth()) {
        Text("📅 ${value.ifBlank {label}}",modifier=Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TimeChoice(label:String,value:String,onChange:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val parsed=remember(value) {
        runCatching { LocalTime.parse(value.trim()) }.getOrDefault(LocalTime.of(18,0))
    }
    val display=remember(value) {
        if(value.isBlank()) "Seleccionar"
        else runCatching {
            LocalTime.parse(value.trim()).format(DateTimeFormatter.ofPattern("h:mm a",Locale("es","MX")))
        }.getOrDefault(value)
    }
    OutlinedButton(shape=RoundedCornerShape(10.dp),
        onClick={
            TimePickerDialog(context,{_,hour,minute ->
                // Keep persisted time in HH:mm so old records and LocalTime.parse still work.
                onChange(String.format(Locale.ROOT,"%02d:%02d",hour,minute))
            },parsed.hour,parsed.minute,false).show()
        },modifier=Modifier.fillMaxWidth()
    ) { Text("◷ $display",modifier=Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.labelMedium) }
}



@Composable
private fun PolishSectionTitle(title:String,subtitle:String="") {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PolishCard(content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
private fun StatusChip(status:String) {
    val bg=when(status) {
        "Con check-ins" -> Color(0xFFDCFCE7)
        "Sin enlace" -> Color(0xFFFEE2E2)
        else -> Color(0xFFFEF3C7)
    }
    val fg=when(status) {
        "Con check-ins" -> Color(0xFF166534)
        "Sin enlace" -> Color(0xFF991B1B)
        else -> Color(0xFF92400E)
    }
    Surface(color=bg,shape=RoundedCornerShape(50)) {
        Text(status,Modifier.padding(horizontal=11.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,
            color=fg,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun MetricTile(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier=modifier,shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(vertical=14.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                color=MaterialTheme.colorScheme.onSurface,maxLines=1)
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportActionRow(label:String,icon:ImageVector,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
        shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,contentDescription=null,tint=MaterialTheme.colorScheme.primary,
                modifier=Modifier.size(22.dp))
            Text(label,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionTile(label:String,icon:ImageVector,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=84.dp),shape=RoundedCornerShape(12.dp),
        color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal=6.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Icon(icon,contentDescription=null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
            Text(label,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,
                maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MeetupHistoryCard(e:Meetup,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(e.imagePath.isNotBlank() && File(e.imagePath).exists()) {
                val thumbnail=remember(e.imagePath){
                    BitmapFactory.Options().run {inSampleSize=8;BitmapFactory.decodeFile(e.imagePath,this)}
                }
                if(thumbnail!=null) Image(thumbnail.asImageBitmap(),contentDescription=null,
                    modifier=Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                else EventIcon()
            } else EventIcon()
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(e.title.ifBlank {"Borrador"},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${e.startDate} · ${e.startTime}",style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.category,style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                StatusChip(if(e.checkedAt.isNotBlank()) "Con check-ins" else if(e.campfireUrl.isBlank()) "Sin enlace" else "Pendiente")
            }
        }
    }
}

@Composable
private fun EventIcon() {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(88.dp),contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.CalendarMonth,contentDescription=null,modifier=Modifier.size(36.dp),
                tint=MaterialTheme.colorScheme.primary)
        }
    }
}

private fun meetupLightColors() = lightColorScheme(
    primary = Color(0xFFF05A28),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF0EB),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF1F5),
    onSecondaryContainer = Color(0xFF374151),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1B22),
    surface = Color.White,
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)
