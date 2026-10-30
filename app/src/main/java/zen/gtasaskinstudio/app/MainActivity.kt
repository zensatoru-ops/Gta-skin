package zen.gtasaskinstudio.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zen.gtasaskinstudio.app.i18n.Lang
import zen.gtasaskinstudio.app.i18n.LocalLang
import zen.gtasaskinstudio.app.i18n.StrKey
import zen.gtasaskinstudio.app.i18n.str
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.Severity
import zen.gtasaskinstudio.app.rw.TxdParseResult
import zen.gtasaskinstudio.app.rw.TxdParser
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.saf.AutoTextureResult
import zen.gtasaskinstudio.app.saf.AutoTextures
import zen.gtasaskinstudio.app.saf.SUPPORTED_MIME_TYPES
import zen.gtasaskinstudio.app.saf.SafBytesResult
import zen.gtasaskinstudio.app.saf.SafFailureReason
import zen.gtasaskinstudio.app.saf.SafFileInfo
import zen.gtasaskinstudio.app.saf.SafReadResult
import zen.gtasaskinstudio.app.saf.readSafBytes
import zen.gtasaskinstudio.app.saf.readSafFileInfo
import zen.gtasaskinstudio.app.fbx.FbxLoader
import zen.gtasaskinstudio.app.obj.ObjLoadOutcome
import zen.gtasaskinstudio.app.obj.ObjLoader
import zen.gtasaskinstudio.app.scene.LoadOutcome
import zen.gtasaskinstudio.app.scene.SceneBuffers
import zen.gtasaskinstudio.app.scene.LoadedModel
import zen.gtasaskinstudio.app.scene.ModelLoader
import zen.gtasaskinstudio.app.ui.IssuesList
import zen.gtasaskinstudio.app.ui.ModelViewerScreen
import zen.gtasaskinstudio.app.ui.ObjViewerScreen
import zen.gtasaskinstudio.app.util.formatBytes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

/** State layar utama. / Main screen state. */
private sealed class ScreenState {
    data object Idle : ScreenState()
    data class Loading(val info: SafFileInfo) : ScreenState()
    data class Loaded(val value: LoadedModel) : ScreenState()
    data class ObjLoaded(val value: ObjLoadOutcome.Loaded, val objBytes: ByteArray, val isObj: Boolean) : ScreenState()
    data class Failed(val info: SafFileInfo, val issues: List<Issue>) : ScreenState()
    data class NotDff(val info: SafFileInfo) : ScreenState()
    data class Error(val reason: SafFailureReason) : ScreenState()
}

@Composable
private fun AppRoot() {
    var lang by remember { mutableStateOf(Lang.ID) }

    CompositionLocalProvider(LocalLang provides lang) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                MainScreen(
                    lang = lang,
                    onToggleLang = { lang = if (lang == Lang.ID) Lang.EN else Lang.ID },
                )
            }
        }
    }
}

/** Hasil muat TXD: nama berkas, tekstur yang dipakai, dan jumlah yang dilewati. / TXD load result. */
private class TxdLoad(val name: String?, val textures: List<TxdTexture>, val skipped: Int)

/** Baca dan parse TXD di thread latar. Kegagalan hanya menghasilkan daftar kosong. / Read and parse a TXD off the main thread. */
private fun loadTxd(context: android.content.Context, uri: Uri): TxdLoad {
    val info = when (val r = readSafFileInfo(context, uri)) {
        is SafReadResult.Success -> r.info
        is SafReadResult.Failure -> return TxdLoad(null, emptyList(), 0)
    }
    return when (val bytes = readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES)) {
        is SafBytesResult.Success -> when (val p = TxdParser.parse(bytes.bytes)) {
            is TxdParseResult.Success -> TxdLoad(
                info.displayName,
                p.textures,
                p.issues.count { it.code.name.startsWith("TXD_") },
            )
            is TxdParseResult.Failure -> TxdLoad(info.displayName, emptyList(), p.issues.size)
        }
        else -> TxdLoad(info.displayName, emptyList(), 0)
    }
}

/** Baca byte, parse, validasi, dan bangun scene di thread latar. */
private fun loadFile(context: android.content.Context, info: SafFileInfo): ScreenState {
    return when (val bytes = readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES)) {
        is SafBytesResult.TooLarge -> ScreenState.Failed(
            info,
            listOf(Issue(Severity.ERROR, IssueCode.FILE_TOO_LARGE, -1, "max ${Limits.MAX_FILE_BYTES / (1024 * 1024)} MB")),
        )
        is SafBytesResult.Failure -> ScreenState.Error(SafFailureReason.CANNOT_READ)
        is SafBytesResult.Success -> when (val o = ModelLoader.load(info.displayName, info.sizeBytes, bytes.bytes)) {
            is LoadOutcome.Loaded -> ScreenState.Loaded(o.value)
            is LoadOutcome.Failed -> ScreenState.Failed(info, o.issues)
        }
    }
}

/** Baca berkas .mtl di thread latar; null bila gagal. / Read a .mtl file off the main thread; null on failure. */
private fun loadMtlBytes(context: android.content.Context, uri: Uri): Pair<String?, ByteArray>? {
    val info = (readSafFileInfo(context, uri) as? SafReadResult.Success)?.info ?: return null
    val bytes = readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES) as? SafBytesResult.Success
        ?: return null
    return info.displayName to bytes.bytes
}

/** Baca byte OBJ, parse, dan bangun scene di thread latar. / Read, parse and build an OBJ scene off the main thread. */
private fun loadObjFile(context: android.content.Context, info: SafFileInfo, isFbx: Boolean): ScreenState {
    return when (val bytes = readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES)) {
        is SafBytesResult.TooLarge -> ScreenState.Failed(
            info,
            listOf(Issue(Severity.ERROR, IssueCode.FILE_TOO_LARGE, -1, "max ${Limits.MAX_FILE_BYTES / (1024 * 1024)} MB")),
        )
        is SafBytesResult.Failure -> ScreenState.Error(SafFailureReason.CANNOT_READ)
        is SafBytesResult.Success -> when (val o = if (isFbx) FbxLoader.load(info.displayName, info.sizeBytes, bytes.bytes) else ObjLoader.load(info.displayName, info.sizeBytes, bytes.bytes)) {
            is ObjLoadOutcome.Loaded -> ScreenState.ObjLoaded(o, bytes.bytes, isObj = !isFbx)
            is ObjLoadOutcome.Failed -> ScreenState.Failed(info, o.issues)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(lang: Lang, onToggleLang: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScreenState>(ScreenState.Idle) }

    // PNG otomatis: dicari di folder yang sama dengan berkas model, memakai nama material.
    // Auto PNG: searched in the model file's folder, matched by material names.
    var modelUri by remember { mutableStateOf<Uri?>(null) }
    var autoResult by remember { mutableStateOf<AutoTextureResult?>(null) }
    suspend fun autoSearch(uri: Uri, s: ScreenState) {
        val scene: SceneBuffers = when (s) {
            is ScreenState.Loaded -> s.value.scene
            is ScreenState.ObjLoaded -> s.value.scene
            else -> null
        } ?: return
        val names = scene.materialNames.filterNotNull().distinct()
        autoResult = withContext(Dispatchers.Default) { AutoTextures.find(context, uri, names) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = readSafFileInfo(context, uri)) {
            is SafReadResult.Failure -> state = ScreenState.Error(result.reason)
            is SafReadResult.Success -> {
                val info = result.info
                val name = (info.displayName ?: "").lowercase()
                val isDff = name.endsWith(".dff")
                val isObj = name.endsWith(".obj")
                val isFbx = name.endsWith(".fbx")
                if (!isDff && !isObj && !isFbx) {
                    state = ScreenState.NotDff(info)
                } else {
                    state = ScreenState.Loading(info)
                    autoResult = null
                    modelUri = info.uri
                    scope.launch {
                        val next = withContext(Dispatchers.Default) {
                            if (isDff) loadFile(context, info) else loadObjFile(context, info, isFbx)
                        }
                        state = next
                        autoSearch(info.uri, next)
                    }
                }
            }
        }
    }
    val pick = { openDocumentLauncher.launch(SUPPORTED_MIME_TYPES) }

    var textures by remember { mutableStateOf<List<TxdTexture>>(emptyList()) }
    var txdName by remember { mutableStateOf<String?>(null) }
    var txdSkipped by remember { mutableIntStateOf(0) }
    val txdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.Default) { loadTxd(context, uri) }
            textures = loaded.textures
            txdName = loaded.name
            txdSkipped = loaded.skipped
        }
    }
    var mtlName by remember { mutableStateOf<String?>(null) }
    val mtlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = state as? ScreenState.ObjLoaded ?: return@rememberLauncherForActivityResult
        if (!current.isObj) return@rememberLauncherForActivityResult
        scope.launch {
            val mtl = withContext(Dispatchers.Default) { loadMtlBytes(context, uri) } ?: return@launch
            val o = withContext(Dispatchers.Default) {
                ObjLoader.load(
                    current.value.displayName,
                    current.value.sizeBytes,
                    current.objBytes,
                    mtl.second,
                )
            }
            if (o is ObjLoadOutcome.Loaded) {
                state = ScreenState.ObjLoaded(o, current.objBytes, isObj = true)
                mtlName = mtl.first
            }
        }
    }
    val txdLabel = txdName?.let {
        val base = "${lang.str(StrKey.TxdLoaded)} $it · ${textures.size} ${lang.str(StrKey.TxdTextures)}"
        if (txdSkipped > 0) "$base · $txdSkipped ${lang.str(StrKey.TxdSkipped)}" else base
    }

    // TXD menang bila nama sama; PNG otomatis mengisi sisanya.
    // TXD wins on equal names; auto PNGs fill the rest.
    val autoTextures = autoResult?.textures.orEmpty()
    val allTextures = textures + autoTextures.filter { a -> textures.none { it.name.equals(a.name, ignoreCase = true) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lang.str(StrKey.AppTitle)) },
                actions = {
                    OutlinedButton(onClick = onToggleLang, modifier = Modifier.padding(end = 12.dp)) {
                        Text(lang.str(StrKey.LangToggle))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val current = state
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = pick) { Text(lang.str(StrKey.OpenFile)) }
                val name = when (current) {
                    is ScreenState.Loaded -> current.value.displayName
                    is ScreenState.ObjLoaded -> current.value.displayName
                    is ScreenState.Loading -> current.info.displayName
                    is ScreenState.Failed -> current.info.displayName
                    is ScreenState.NotDff -> current.info.displayName
                    else -> null
                }
                if (name != null) Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }

            // Android 10 ke bawah: izin baca penyimpanan diminta langsung (dialog sistem), bukan lewat Pengaturan.
            // Android 10 and below: the storage read permission is requested directly (system dialog), not via Settings.
            val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    val u = modelUri
                    if (u != null) scope.launch { autoSearch(u, current) }
                } else {
                    openStorageSettings(context)
                }
            }

            when (current) {
                is ScreenState.Idle -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(lang.str(StrKey.PhaseNotice), style = MaterialTheme.typography.bodySmall)
                    Text(lang.str(StrKey.OpenFileHint), style = MaterialTheme.typography.bodySmall)
                    Text(lang.str(StrKey.NoFileSelected))
                }
                is ScreenState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(lang.str(StrKey.Parsing))
                }
                is ScreenState.Loaded -> ModelViewerScreen(
                    lang = lang,
                    loaded = current.value,
                    textures = allTextures,
                    txdLabel = txdLabel,
                    onPickTxd = { txdLauncher.launch(SUPPORTED_MIME_TYPES) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                is ScreenState.ObjLoaded -> ObjViewerScreen(
                    lang = lang,
                    scene = current.value.scene,
                    issues = current.value.issues,
                    vertexCount = current.value.vertexCount,
                    triangleCount = current.value.triangleCount,
                    mtlName = mtlName,
                    showMtl = current.isObj,
                    onPickMtl = { mtlLauncher.launch(SUPPORTED_MIME_TYPES) },
                    textures = allTextures,
                    txdLabel = txdLabel,
                    onPickTxd = { txdLauncher.launch(SUPPORTED_MIME_TYPES) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                is ScreenState.Failed -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(lang.str(StrKey.ParseFailedTitle), style = MaterialTheme.typography.titleMedium)
                    IssuesList(lang, current.issues, Modifier.fillMaxWidth())
                }
                is ScreenState.NotDff -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileInfoCard(lang, current.info)
                    Text(lang.str(StrKey.OnlyDffNow))
                }
                is ScreenState.Error -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ErrorCard(lang = lang, reason = current.reason, onRetry = pick)
                }
            }

            // Status PNG ditaruh di BAWAH viewer. Kalau di atas, kemunculannya (setelah pencarian selesai) menggeser
            // viewer ke bawah, dan SurfaceView di beberapa perangkat (vivo Android 10) tidak ikut bergeser: muncul
            // pita hitam yang menutupi tombol Buka berkas. Di bawah, ia hanya mengubah tinggi viewer.
            // The PNG status sits BELOW the viewer. Above it, appearing after the search finishes pushes the viewer
            // down, and the SurfaceView on some devices (vivo, Android 10) does not follow: a black band hides the
            // Open file button. Below, it only changes the viewer's height.
            val autoInfo = autoResult
            if (autoInfo != null && (current is ScreenState.Loaded || current is ScreenState.ObjLoaded)) {
                AutoPngStatus(
                    lang = lang,
                    result = autoInfo,
                    onGrant = {
                        if (Build.VERSION.SDK_INT < 30) {
                            storagePermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            openStorageSettings(context)
                        }
                    },
                    onRecheck = {
                        val u = modelUri
                        if (u != null) scope.launch { autoSearch(u, current) }
                    },
                )
            }
        }
    }
}

/** Buka layar izin penyimpanan aplikasi. / Open this app's storage permission screen. */
private fun openStorageSettings(context: android.content.Context) {
    val uri = Uri.parse("package:${context.packageName}")
    val intent = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
    }
    context.startActivity(intent)
}

@Composable
private fun AutoPngStatus(
    lang: Lang,
    result: AutoTextureResult,
    onGrant: () -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            result.needsPermission -> {
                Text(lang.str(StrKey.AutoPngNeedPermission), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onGrant) { Text(lang.str(StrKey.AutoPngGrant)) }
                    OutlinedButton(onClick = onRecheck) { Text(lang.str(StrKey.AutoPngRecheck)) }
                }
            }
            result.noPath -> Text(lang.str(StrKey.AutoPngNoPath), style = MaterialTheme.typography.bodySmall)
            result.textures.isEmpty() -> Text(lang.str(StrKey.AutoPngNone), style = MaterialTheme.typography.bodySmall)
            else -> Text(
                "${lang.str(StrKey.AutoPngLoaded)} ${result.textures.size} PNG: " +
                    result.textures.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun FileInfoCard(lang: Lang, info: SafFileInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lang.str(StrKey.FileSelected), style = MaterialTheme.typography.titleMedium)
            Text("${lang.str(StrKey.FileName)}: ${info.displayName ?: "-"}")
            Text("${lang.str(StrKey.FileSize)}: ${formatBytes(info.sizeBytes)}")
            Text("${lang.str(StrKey.FileUri)}: ${info.uri}")
        }
    }
}

@Composable
private fun ErrorCard(lang: Lang, reason: SafFailureReason, onRetry: () -> Unit) {
    val message = when (reason) {
        SafFailureReason.CANNOT_OPEN -> lang.str(StrKey.ErrorCannotOpen)
        SafFailureReason.CANNOT_READ -> lang.str(StrKey.ErrorCannotRead)
        SafFailureReason.UNKNOWN -> lang.str(StrKey.ErrorUnknown)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lang.str(StrKey.ErrorTitle), style = MaterialTheme.typography.titleMedium)
            Text(message)
            Button(onClick = onRetry) {
                Text(lang.str(StrKey.ErrorRetry))
            }
        }
    }
}
