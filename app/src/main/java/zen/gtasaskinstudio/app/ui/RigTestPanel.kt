package zen.gtasaskinstudio.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zen.gtasaskinstudio.app.i18n.Lang
import zen.gtasaskinstudio.app.i18n.StrKey
import zen.gtasaskinstudio.app.i18n.fmt
import zen.gtasaskinstudio.app.i18n.str
import zen.gtasaskinstudio.app.ifp.IfpFile
import zen.gtasaskinstudio.app.ifp.IfpReader
import zen.gtasaskinstudio.app.ifp.IfpResult
import zen.gtasaskinstudio.app.ifp.IfpRigCheck
import zen.gtasaskinstudio.app.rig.AutoRig
import zen.gtasaskinstudio.app.rig.AutoRigGeometry
import zen.gtasaskinstudio.app.rig.AutoRigResult
import zen.gtasaskinstudio.app.rig.AutoWeightResult
import zen.gtasaskinstudio.app.rig.AutoWeights
import zen.gtasaskinstudio.app.rig.SkinPreview
import zen.gtasaskinstudio.app.rig.PoseFrame
import zen.gtasaskinstudio.app.rig.PosePlayer
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.saf.SUPPORTED_MIME_TYPES
import zen.gtasaskinstudio.app.saf.SafBytesResult
import zen.gtasaskinstudio.app.saf.SafReadResult
import zen.gtasaskinstudio.app.saf.readSafBytes
import zen.gtasaskinstudio.app.saf.readSafFileInfo
import zen.gtasaskinstudio.app.scene.SceneBuffers

/** Pose siap gambar: garis/titik di ruang model, dan geseran dari rig diam. / A drawable pose: lines/points in model space, plus the shift from the rest rig. */
internal class PoseView(val geometry: AutoRigGeometry, val shift: FloatArray, val vertices: FloatArray? = null)

private sealed class IfpLoad {
    class Ok(val name: String, val file: IfpFile) : IfpLoad()
    class BadFormat(val reason: String) : IfpLoad()
    data object Unreadable : IfpLoad()
}

/** Bobot otomatis dari vertex scene dan tepi wireframe. / Auto weights from the scene vertices and wireframe edges. */
private fun computeWeights(scene: SceneBuffers, rig: AutoRigResult): AutoWeightResult? {
    val n = scene.vertexCount
    val pos = FloatArray(n * 3) { scene.vertices[(it / 3) * 6 + it % 3] }
    val wire = scene.wireIndices
    val edges = IntArray(wire.size) { wire[it].toInt() and 0xFFFF }
    return AutoWeights.compute(pos, edges, rig)
}

private fun loadIfp(context: Context, uri: Uri): IfpLoad {
    val info = (readSafFileInfo(context, uri) as? SafReadResult.Success)?.info ?: return IfpLoad.Unreadable
    val bytes = (readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES) as? SafBytesResult.Success)
        ?.bytes ?: return IfpLoad.Unreadable
    return when (val r = IfpReader.parse(bytes, withKeys = true)) {
        is IfpResult.Success -> IfpLoad.Ok(info.displayName ?: "ped.ifp", r.file)
        is IfpResult.Failure -> IfpLoad.BadFormat(r.reason)
    }
}

/**
 * Panel tes rig: memainkan pose dari ped.ifp pada skeleton hasil auto-rig. Tanpa ped.ifp, menampilkan pose berdiri bawaan.
 * Rig test panel: plays poses from ped.ifp on the auto-rig skeleton. Without a ped.ifp it shows the built-in standing pose.
 */
@Composable
internal fun RigTestPanel(
    lang: Lang,
    rig: AutoRigResult,
    scene: SceneBuffers,
    mirror: Boolean,
    onMirror: (Boolean) -> Unit,
    onPose: (PoseView?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ifp by remember { mutableStateOf<IfpFile?>(null) }
    var ifpName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var matched by remember { mutableIntStateOf(0) }
    var lacking by remember { mutableStateOf<List<String>>(emptyList()) }
    var animIndex by remember { mutableIntStateOf(-1) }
    var time by remember { mutableStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var weights by remember(scene, rig) { mutableStateOf<AutoWeightResult?>(null) }
    var weighting by remember { mutableStateOf(false) }

    LaunchedEffect(scene, rig, follow) {
        if (follow && weights == null) {
            weighting = true
            weights = withContext(Dispatchers.Default) { computeWeights(scene, rig) }
            weighting = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = withContext(Dispatchers.Default) { loadIfp(context, uri) }) {
                is IfpLoad.Ok -> {
                    val report = withContext(Dispatchers.Default) { IfpRigCheck.check(r.file) }
                    ifp = r.file
                    ifpName = r.name
                    matched = report.bones.count { it.exact > 0 }
                    lacking = report.missing.map { it.name.trim() }
                    animIndex = r.file.animations.indexOfFirst { it.name.equals("IDLE_stance", ignoreCase = true) }
                        .let { if (it >= 0) it else if (r.file.animations.isNotEmpty()) 0 else -1 }
                    time = 0f
                    playing = false
                    message = null
                }
                is IfpLoad.BadFormat -> message = lang.fmt(StrKey.RigTestBadIfp, r.reason)
                IfpLoad.Unreadable -> message = lang.str(StrKey.RigTestReadFail)
            }
        }
    }

    val file = ifp
    val stance = remember { PosePlayer.stanceAnimation() }
    val anim = if (file != null && animIndex in file.animations.indices) file.animations[animIndex] else stance
    val duration = remember(anim) { PosePlayer.duration(anim) }
    // Waktu dibulatkan ke 1/30 dtk supaya gambar tidak dihitung ulang tanpa perlu. / Time is rounded to 1/30 s so we do not recompute needlessly.
    val stepTime = Math.round(time * 30f) / 30f
    val frame: PoseFrame? = remember(rig, anim, stepTime, follow) { PosePlayer.pose(rig, anim, stepTime, beside = !follow) }

    LaunchedEffect(frame, weights, follow) {
        val f = frame
        if (f == null) {
            onPose(null)
        } else {
            val wts = weights
            val verts: FloatArray? = if (follow && wts != null) {
                withContext(Dispatchers.Default) {
                    val xf = SkinPreview.transforms(rig.joints, f.joints, wts.height)
                    if (xf != null) SkinPreview.skin(scene.vertices, wts, xf) else null
                }
            } else {
                null
            }
            val g = AutoRig.geometry(AutoRigResult(rig.up, f.joints))
            onPose(PoseView(g, FloatArray(3) { f.shift[it].toFloat() }, verts))
        }
    }
    DisposableEffect(Unit) { onDispose { onPose(null) } }

    LaunchedEffect(playing, anim) {
        while (playing && duration > 0f) {
            delay(70)
            time += 0.07f
            if (time > duration) time = 0f
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        val status = when {
            message != null -> message!!
            file == null -> lang.str(StrKey.RigTestStance)
            else -> lang.fmt(StrKey.RigTestIfp, ifpName, file.animations.size, matched)
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (file != null && lacking.isNotEmpty()) {
            Text(lang.fmt(StrKey.RigTestLacking, lacking.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        }
        if (frame == null) {
            Text(lang.str(StrKey.RigTestIncomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (file != null) {
            Text(
                lang.fmt(StrKey.RigTestAnim, anim.name, frame.animatedBones, stepTime, duration),
                style = MaterialTheme.typography.bodySmall,
            )
            if (frame.missing.isNotEmpty()) {
                Text(
                    lang.fmt(StrKey.RigTestMissing, frame.missing.joinToString(", ") { it.trim() }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            if (follow) {
                if (weighting) lang.str(StrKey.RigTestWeighting) else lang.str(StrKey.RigTestFollowNote)
            } else {
                lang.str(StrKey.RigTestNote)
            },
            style = MaterialTheme.typography.bodySmall,
        )

        if (file != null && duration > 0f) {
            Slider(
                value = time.coerceIn(0f, duration),
                onValueChange = { time = it; playing = false },
                valueRange = 0f..duration,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { follow = !follow },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = follow, onCheckedChange = null)
            Text(lang.str(StrKey.RigTestFollow), modifier = Modifier.padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onMirror(!mirror) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = mirror, onCheckedChange = null)
            Text(lang.str(StrKey.RigTestMirror), modifier = Modifier.padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(SUPPORTED_MIME_TYPES) }, modifier = Modifier.weight(1f)) {
                Text(lang.str(StrKey.RigTestOpenIfp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { pickerOpen = true },
                enabled = file != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(lang.str(StrKey.RigTestPick), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { playing = !playing },
                enabled = file != null && duration > 0f,
                modifier = Modifier.weight(1f),
            ) {
                Text(lang.str(if (playing) StrKey.RigTestPause else StrKey.RigTestPlay), maxLines = 1)
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text(lang.str(StrKey.RigTestClose), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (pickerOpen && file != null) {
        var query by remember { mutableStateOf("") }
        val indices = remember(file, query) {
            file.animations.indices.filter { query.isBlank() || file.animations[it].name.contains(query.trim(), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(lang.str(StrKey.RigTestPick)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(lang.str(StrKey.RigTestSearch)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(indices) { i ->
                            Text(
                                text = file.animations[i].name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        animIndex = i
                                        time = 0f
                                        playing = false
                                        pickerOpen = false
                                    }
                                    .padding(vertical = 10.dp),
                                color = if (i == animIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text(lang.str(StrKey.SlotCancel)) } },
        )
    }
}
