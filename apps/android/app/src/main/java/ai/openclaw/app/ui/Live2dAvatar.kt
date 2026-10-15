package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.openclaw.app.ui.design.ClawTheme
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 使っている Live2D モデルの情報（ゲートウェイの avatar.model.list の1件）。 */
internal data class Live2dInfo(val id: String, val name: String, val entry: String, val bytes: Long, val kind: String = "live2d")

private const val LIVE2D_HOST = "https://live2d.local/"

/** このキャラで、いま使うことになっている Live2D モデル。なければ null（絵のアバターを使う）。 */
@Composable
internal fun rememberActiveLive2d(viewModel: MainViewModel, ownerId: String): Live2dInfo? {
  var info by remember(ownerId) { mutableStateOf<Live2dInfo?>(null) }
  val version = AvatarModelBus.version
  LaunchedEffect(ownerId, version) {
    info =
      runCatching {
        val rows = JSONObject(viewModel.runGatewayRpc(GatewayMethod.AvatarModelList, JSONObject().put("ownerId", ownerId).toString(), timeoutMs = 20_000)).optJSONArray("models")
        (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
          .firstOrNull { it.optBoolean("active") && it.optString("kind") in setOf("live2d", "vrm", "glb") }
          ?.let { Live2dInfo(it.optString("id"), it.optString("name"), it.optString("entry"), it.optLong("bytes"), it.optString("kind")) }
      }.getOrNull()
  }
  return info
}

/** モデルのファイルを、ゲートウェイからこの端末へ取っておく（取ってあれば何もしない）。 */
private suspend fun syncLive2d(viewModel: MainViewModel, context: android.content.Context, ownerId: String, model: Live2dInfo): File {
  val root = File(context.filesDir, "live2d/$ownerId/${model.id}")
  val marker = File(root, ".synced-${model.bytes}")
  if (marker.exists()) return root
  root.deleteRecursively()
  root.mkdirs()
  val files = JSONObject(viewModel.runGatewayRpc(GatewayMethod.AvatarModelFiles, JSONObject().put("ownerId", ownerId).put("id", model.id).toString(), timeoutMs = 30_000)).optJSONArray("files")
  for (i in 0 until (files?.length() ?: 0)) {
    val f = files!!.getJSONObject(i)
    val rel = f.optString("path")
    val size = f.optLong("bytes")
    val target = File(root, rel)
    if (!target.canonicalPath.startsWith(root.canonicalPath)) continue
    target.parentFile?.mkdirs()
    withContext(Dispatchers.IO) { target.outputStream().use { out ->
      var offset = 0L
      while (offset < size) {
        val params = JSONObject().put("ownerId", ownerId).put("id", model.id).put("path", rel).put("offset", offset).put("length", 1_000_000)
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.AvatarModelRead, params.toString(), timeoutMs = 60_000))
        val bytes = Base64.decode(res.optString("dataBase64"), Base64.DEFAULT)
        if (bytes.isEmpty()) break
        out.write(bytes)
        offset += bytes.size
      }
    } }
  }
  marker.writeText("ok")
  return root
}

private fun mimeOf(name: String): String =
  when (name.substringAfterLast('.', "").lowercase()) {
    "json" -> "application/json"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "wav" -> "audio/wav"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
  }

private val LIVE2D_HTML = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}canvas{display:block;width:100%;height:100%}</style>
<script src="https://cubism.live2d.com/sdk-web/cubismcore/live2dcubismcore.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/pixi.js@6.5.10/dist/browser/pixi.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/pixi-live2d-display@0.4.0/dist/cubism4.min.js"></script>
</head><body><canvas id="c"></canvas><script>
var model=null,app=null,speaking=false,expressions=[],motionGroups=[];
function report(t){try{Android.onStatus(t)}catch(e){}}
function norm(s){return String(s||'').toLowerCase()}
var KEYS={happy:['happy','smile','joy','笑','嬉','喜','にこ','わら'],sad:['sad','cry','tear','blue','泣','悲','涙','青','しょぼ'],angry:['angry','mad','dark','怒','ぷん','黒'],
 shy:['shy','blush','cheek','red','赤','照','恥','はず'],surprised:['surprise','shock','sweat','驚','びっくり','汗'],thinking:['think','sweat','考','困','hmm','汗'],
 aroused:['blush','heart','excite','love','red','赤','照','ハート','興奮','とろ','恋']};
function setEmotion(key){
  if(!model)return;
  var words=KEYS[key]||[];
  var hit=null;
  for(var i=0;i<expressions.length&&!hit;i++){var n=norm(expressions[i]);for(var j=0;j<words.length;j++){if(n.indexOf(words[j])>=0){hit=expressions[i];break}}}
  try{ if(hit){model.expression(hit)} else if(key==='normal'){model.internalModel.motionManager.expressionManager&&model.internalModel.motionManager.expressionManager.resetExpression()} }catch(e){}
}
function tapMotion(){
  if(!model)return;
  var pick=motionGroups.filter(function(g){return /tap|touch|body|flick/i.test(g)})[0]||motionGroups.filter(function(g){return !/idle/i.test(g)})[0];
  try{ if(pick){model.motion(pick)} }catch(e){}
}
function setSpeaking(v){speaking=!!v}
function fit(){
  if(!model||!app)return;
  var w=app.renderer.width,h=app.renderer.height;
  model.scale.set(1);
  var s=Math.min(w/model.width,h/model.height)*1.15;
  model.scale.set(s);
  model.x=(w-model.width)/2;
  model.y=0;
}
(async function(){
  try{
    window.PIXI=PIXI;
    app=new PIXI.Application({view:document.getElementById('c'),autoStart:true,backgroundAlpha:0,resizeTo:window,antialias:true,resolution:window.devicePixelRatio||1,autoDensity:true});
    model=await PIXI.live2d.Live2DModel.from('__MODEL_URL__',{autoInteract:false});
    app.stage.addChild(model);
    fit();
    window.addEventListener('resize',fit);
    var mm=model.internalModel.motionManager;
    motionGroups=Object.keys(mm.definitions||{});
    var ex=mm.expressionManager&&mm.expressionManager.definitions||[];
    expressions=ex.map(function(e){return e.Name||e.name||e.File||''});
    app.ticker.add(function(){
      if(!model)return;
      try{
        var core=model.internalModel.coreModel;
        var t=performance.now()/1000;
        var v=speaking?(0.25+0.55*Math.abs(Math.sin(t*11))):0;
        core.setParameterValueById('ParamMouthOpenY',v);
      }catch(e){}
    });
    app.view.addEventListener('pointerdown',function(){tapMotion()});
    report('ready:'+JSON.stringify({motions:motionGroups,expressions:expressions}));
  }catch(e){report('error:'+(e&&e.message||e))}
})();
</script></body></html>
""".trimIndent()

/**
 * VRM（人型の3D）と glb（ふつうの3D）を出すページ。three.js と three-vrm をネットから読みます。
 * VRM は、まばたき・表情・口パク・ゆっくりした首の動きが付き、glb はゆっくり回ります。
 */
private val VRM_HTML = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}canvas{display:block}</style>
<script type="importmap">{"imports":{"three":"https://cdn.jsdelivr.net/npm/three@0.169.0/build/three.module.js","three/addons/":"https://cdn.jsdelivr.net/npm/three@0.169.0/examples/jsm/","@pixiv/three-vrm":"https://cdn.jsdelivr.net/npm/@pixiv/three-vrm@3.3.3/lib/three-vrm.module.min.js"}}</script>
</head><body><script type="module">
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { HumanoidMotion } from './humanoid-motion.js';
import { HumanoidVideoExport } from './humanoid-export.js';
import { HumanoidAutoRig } from './humanoid-auto-rig.js';
var nativeVrm=false,autoRig=null,rigProfileKey='';
var videoExporter=null;
window.startVideoExport=function(seconds){
  if(!videoExporter){report('export-error:モデルの読み込み完了を待ってください');return}
  videoExporter.start(seconds).catch(function(e){report('export-error:'+e.message)});
};
window.cancelVideoExport=function(){if(videoExporter)videoExporter.cancel()};
var runtimeOptions=__RUNTIME_OPTIONS__, motionController=null, bvhRevision=-1;
window.setRuntimeOptions=function(options){
  if(videoExporter&&videoExporter.running)return;
  runtimeOptions=options;
  try{
    if(root&&!nativeVrm){
      var key=JSON.stringify(options.rigProfile);
      if(options.autoRig&&(!autoRig||key!==rigProfileKey)){
        if(motionController)motionController.dispose();
        motionController=null;videoExporter=null;
        if(autoRig)autoRig.dispose();
        autoRig=null;vrm=null;
        autoRig=new HumanoidAutoRig(root,options.rigProfile);vrm=autoRig;
        motionController=new HumanoidMotion(vrm);rigProfileKey=key;
        videoExporter=new HumanoidVideoExport(renderer,scene,camera,motionController,renderFrame,frameModel,report);
        frameModel();
        report('ready:'+JSON.stringify({rig:true}));
        report('motion:骨付けを推定しました。関節の動きと形を確認し、補正してください');
      }else if(!options.autoRig&&autoRig){
        motionController.dispose();autoRig.dispose();autoRig=null;vrm=null;motionController=null;videoExporter=null;
        report('ready:'+JSON.stringify({rig:false}));
        report('motion:元の静止モデルに戻しました');
      }
    }
    if(!motionController)return;
    if(autoRig&&autoRig.helper)autoRig.helper.visible=!!options.showBones;
    motionController.configure(options);
    if(options.motion==='bvh'&&options.bvhRevision!==bvhRevision){
      bvhRevision=options.bvhRevision;
      motionController.loadBvh('./motion.bvh?r='+bvhRevision).then(function(count){if(count)report('motion:BVH '+count+'部位を対応付けました')}).catch(function(e){report('motion-error:'+e.message)});
    }
  }catch(e){if(!nativeVrm)report('ready:'+JSON.stringify({rig:false}));report('motion-error:'+e.message)}
};
function report(t){try{Android.onStatus(t)}catch(e){}}
var renderer=new THREE.WebGLRenderer({alpha:true,antialias:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
renderer.setSize(window.innerWidth,window.innerHeight);
document.body.appendChild(renderer.domElement);
var scene=new THREE.Scene();
var camera=new THREE.PerspectiveCamera(30,window.innerWidth/window.innerHeight,0.1,100);
scene.add(new THREE.AmbientLight(0xffffff,1.3));
var sun=new THREE.DirectionalLight(0xffffff,1.6);sun.position.set(1,2,2);scene.add(sun);
var viewerDisposed=false,animationHandle=0;
var vrm=null,root=null,speaking=false,emotion='normal',nextBlink=2,blinkAt=-1;
window.setSpeaking=function(v){if(!videoExporter||!videoExporter.running)speaking=!!v};
window.setEmotion=function(k){if(!videoExporter||!videoExporter.running)emotion=k};
function frameModel(){
  var box=new THREE.Box3().setFromObject(root);
  var size=box.getSize(new THREE.Vector3());var center=box.getCenter(new THREE.Vector3());
  var h=vrm&&!runtimeOptions?size.y*0.55:size.y;
  var y=vrm&&!runtimeOptions?box.max.y-size.y*0.28:center.y;
  var dist=(h/2)/Math.tan(THREE.MathUtils.degToRad(15))*1.15;
  camera.position.set(center.x,y,center.z+dist);camera.lookAt(center.x,y,center.z);
}
var loader=new GLTFLoader();
loader.register(function(parser){return new VRMLoaderPlugin(parser)});
loader.load('__MODEL_URL__',function(gltf){
  if(viewerDisposed){VRMUtils.deepDispose(gltf.scene);return}
  vrm=gltf.userData.vrm||null;nativeVrm=!!vrm;
  if(vrm){VRMUtils.rotateVRM0(vrm);root=vrm.scene}else{root=gltf.scene}
  scene.add(root);frameModel();
  if(runtimeOptions){
    try{if(vrm)motionController=new HumanoidMotion(vrm);window.setRuntimeOptions(runtimeOptions)}catch(e){report('error:'+e.message);return}
  }
  if(motionController)videoExporter=new HumanoidVideoExport(renderer,scene,camera,motionController,renderFrame,frameModel,report);
  report('ready:'+JSON.stringify({vrm:nativeVrm,rig:!!motionController}));
  if(motionController)report('motion:共通骨格 '+Object.keys(motionController.bones).length+'部位・揺れ骨 '+motionController.springDefaults.size+'本');
},undefined,function(e){report('error:'+(e&&e.message||e))});
var clock=new THREE.Clock();
var MAP={happy:'happy',angry:'angry',sad:'sad',shy:'relaxed',surprised:'surprised',thinking:'neutral',aroused:'relaxed',normal:'neutral'};
function animate(){
  animationHandle=requestAnimationFrame(animate);
  var dt=clock.getDelta();var t=clock.elapsedTime;
  if(!videoExporter||!videoExporter.running)renderFrame(dt,t,false);
}
function renderFrame(dt,t,recording){
  if(vrm){
    var em=vrm.expressionManager;
    if(em){
      ['happy','angry','sad','relaxed','surprised'].forEach(function(n){em.setValue(n,0)});
      var n=MAP[emotion]||'neutral';
      if(n!=='neutral')em.setValue(n,0.8);
      em.setValue('aa',speaking?Math.abs(Math.sin(t*11))*0.7:0);
      if(t>nextBlink){blinkAt=t;nextBlink=t+2+Math.random()*4}
      var b=blinkAt>0?Math.max(0,1-Math.abs((t-blinkAt)-0.08)/0.08):0;
      em.setValue('blink',recording?Math.max(0,1-Math.abs((t%4)-0.08)/0.08):b);
    }
    if(motionController){motionController.update(dt)}else{
    var hb=vrm.humanoid&&vrm.humanoid.getNormalizedBoneNode('head');
    if(hb)hb.rotation.y=Math.sin(t*0.6)*0.08;
    var sb=vrm.humanoid&&vrm.humanoid.getNormalizedBoneNode('spine');
    if(sb)sb.rotation.z=Math.sin(t*0.9)*0.02;
    vrm.update(Math.min(dt,0.1));
    }
  }else if(root){root.rotation.y+=dt*0.4}
  renderer.render(scene,camera);
}
animate();
window.addEventListener('pagehide',function(){viewerDisposed=true;cancelAnimationFrame(animationHandle);if(videoExporter)videoExporter.cancel();if(motionController)motionController.dispose();if(autoRig)autoRig.dispose();if(root)VRMUtils.deepDispose(root);renderer.dispose()});
window.addEventListener('resize',function(){if(videoExporter&&videoExporter.running)return;renderer.setSize(window.innerWidth,window.innerHeight);camera.aspect=window.innerWidth/window.innerHeight;camera.updateProjectionMatrix()});
</script></body></html>
""".trimIndent()

/** Live2D モデルでアバターを出す。モデルの表示には Live2D 公式の Cubism Core と pixi を、ネットから読みます。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun Live2dAvatar(viewModel: MainViewModel, ownerId: String, model: Live2dInfo, speaking: Boolean, lastReply: String, height: Dp, modifier: Modifier = Modifier, studioOptions: String? = null, bvhText: String? = null, expressionKey: String? = null, exportRequest: Int = 0, exportSeconds: Int = 5, exportAudio: File? = null, onVideoExport: (File?, String) -> Unit = { _, _ -> }, onVideoStatus: (String) -> Unit = {}, onViewerReady: (Boolean) -> Unit = {}) {
  val context = LocalContext.current
  val currentBvh by rememberUpdatedState(bvhText)
  val currentAudio by rememberUpdatedState(exportAudio)
  val currentExportCallback by rememberUpdatedState(onVideoExport)
  val currentVideoStatus by rememberUpdatedState(onVideoStatus)
  val currentReadyCallback by rememberUpdatedState(onViewerReady)
  val encoder = remember(model.id) { HumanoidVideoEncoder(context.cacheDir) }
  val exportAuthorized = remember(model.id) { java.util.concurrent.atomic.AtomicBoolean(false) }
  val viewerReleased = remember(model.id) { java.util.concurrent.atomic.AtomicBoolean(false) }
  var motionStatus by remember(model.id) { mutableStateOf("") }
  var root by remember(model.id) { mutableStateOf<File?>(null) }
  var status by remember(model.id) { mutableStateOf("Live2D モデルを読み込んでいます…") }
  var webView by remember { mutableStateOf<WebView?>(null) }
  var ready by remember(model.id) { mutableStateOf(false) }

  LaunchedEffect(model.id, model.bytes) {
    runCatching { syncLive2d(viewModel, context, ownerId, model) }
      .onSuccess { root = it }
      .onFailure { status = "モデルを取得できませんでした：${it.message}" }
  }
  LaunchedEffect(exportRequest, ready) {
    if (ready && exportRequest > 0 && studioOptions != null) {
      exportAuthorized.set(true)
      webView?.evaluateJavascript("window.startVideoExport($exportSeconds)", null)
    } else if (ready && exportRequest == 0 && studioOptions != null) {
      exportAuthorized.set(false)
      webView?.evaluateJavascript("window.cancelVideoExport()", null)
    }
  }
  LaunchedEffect(studioOptions, ready) { if (ready && studioOptions != null) webView?.evaluateJavascript("window.setRuntimeOptions($studioOptions)", null) }
  LaunchedEffect(speaking, ready) { if (ready) webView?.evaluateJavascript("setSpeaking($speaking)", null) }
  LaunchedEffect(lastReply, expressionKey, ready) {
    if (ready) webView?.evaluateJavascript("setEmotion(${JSONObject.quote(expressionKey ?: avatarEmotionOf(lastReply).key)})", null)
  }

  Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
    val dir = root
    if (dir == null || status.startsWith("モデルを取得できません")) {
      Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    } else {
      AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { ctx ->
          WebView(ctx).apply {
            setBackgroundColor(0)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            addJavascriptInterface(
              object {
                @JavascriptInterface
                fun beginVideo(seconds: Int): String = runCatching {
                  check(!viewerReleased.get() && exportAuthorized.compareAndSet(true, false)) { "書き出し操作が必要です" }
                  encoder.begin(seconds, currentAudio)
                }.exceptionOrNull()?.let { it.message ?: "動画の書き出しに失敗しました" }.orEmpty()

                @JavascriptInterface
                fun videoFrame(png: String, index: Int): String = runCatching {
                  check(!viewerReleased.get()) { "画面を閉じたため書き出しを中止しました" }
                  encoder.frame(png, index)
                }.exceptionOrNull()?.let { it.message ?: "動画の書き出しに失敗しました" }.orEmpty()

                @JavascriptInterface
                fun finishVideo(): String = runCatching {
                  check(!viewerReleased.get()) { "画面を閉じたため書き出しを中止しました" }
                  val file = encoder.finish()
                  android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (viewerReleased.get()) file.delete()
                    else currentExportCallback(file, "動画が完成しました。保存先を選んでください。")
                  }
                }.exceptionOrNull()?.let { it.message ?: "動画の書き出しに失敗しました" }.orEmpty()

                @JavascriptInterface
                fun cancelVideo() { encoder.cancel() }

                @JavascriptInterface
                fun onStatus(text: String) {
                  if (viewerReleased.get()) return
                  post {
                    if (viewerReleased.get()) return@post
                    if (text.startsWith("ready:")) { ready = true; status = ""; currentReadyCallback(JSONObject(text.removePrefix("ready:")).optBoolean("rig", studioOptions == null)) }
                    else if (text.startsWith("export-progress:")) currentVideoStatus("動画を書き出しています：${text.removePrefix("export-progress:")}%")
                    else if (text.startsWith("export-error:")) currentExportCallback(null, text.removePrefix("export-error:"))
                    else if (text.startsWith("motion:")) motionStatus = text.removePrefix("motion:")
                    else if (text.startsWith("motion-error:")) motionStatus = text.removePrefix("motion-error:")
                    else status = "モデルを表示できませんでした：${text.removePrefix("error:")}"
                  }
                }
              },
              "Android",
            )
            webViewClient =
              object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                  val url = request.url.toString()
                  if (url in setOf(LIVE2D_HOST + "humanoid-motion.js", LIVE2D_HOST + "humanoid-export.js", LIVE2D_HOST + "humanoid-auto-rig.js")) {
                    return WebResourceResponse("text/javascript", "utf-8", context.assets.open(url.removePrefix(LIVE2D_HOST)))
                  }
                  if (url.substringBefore('?') == LIVE2D_HOST + "motion.bvh") {
                    val bytes = currentBvh?.toByteArray(Charsets.UTF_8)
                    return WebResourceResponse("text/plain", "utf-8", if (bytes == null) 404 else 200, if (bytes == null) "Not Found" else "OK", emptyMap(), ByteArrayInputStream(bytes ?: ByteArray(0)))
                  }
                  if (!url.startsWith(LIVE2D_HOST + "model/")) return null
                  val rel = java.net.URLDecoder.decode(url.removePrefix(LIVE2D_HOST + "model/").substringBefore('?'), "UTF-8")
                  val file = File(dir, rel)
                  if (!file.canonicalPath.startsWith(dir.canonicalPath) || !file.isFile) {
                    return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))
                  }
                  return WebResourceResponse(mimeOf(file.name), null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), file.inputStream())
                }
              }
            val modelUrl = LIVE2D_HOST + "model/" + model.entry.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            loadDataWithBaseURL(LIVE2D_HOST, (if (model.kind == "live2d") LIVE2D_HTML else VRM_HTML).replace("__MODEL_URL__", modelUrl).replace("__RUNTIME_OPTIONS__", studioOptions ?: "null"), "text/html", "utf-8", null)
            webView = this
          }
        },
        onRelease = {
          viewerReleased.set(true)
          exportAuthorized.set(false)
          kotlin.concurrent.thread(name = "humanoid-export-cleanup") { encoder.cancel() }
          it.destroy(); webView = null
        },
      )
      if (motionStatus.isNotBlank()) Text(text = motionStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      if (status.isNotBlank()) Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    }
  }
}
