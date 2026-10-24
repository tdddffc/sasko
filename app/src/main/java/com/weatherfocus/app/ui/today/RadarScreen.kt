package com.weatherfocus.app.ui.today

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.weatherfocus.app.ui.WeatherViewModel
import java.util.Locale

/** Animated rain radar (RainViewer tiles on OpenStreetMap) centred on the active place. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val s = state.settings
    val context = LocalContext.current
    val html = radarHtml(s.latitude, s.longitude, s.cityName)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rain radar – ${s.cityName}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.yr.no/en/map/radar")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Icon(Icons.Filled.Share, contentDescription = "Open yr.no radar in browser") }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.padding(padding).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL("https://weatherfocus.local/", html, "text/html", "utf-8", null)
                }
            }
        )
    }
}

private fun radarHtml(lat: Double, lon: Double, name: String): String {
    val la = String.format(Locale.US, "%.4f", lat)
    val lo = String.format(Locale.US, "%.4f", lon)
    val safe = name.replace("'", "").replace("<", "").replace(">", "")
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{height:100%;margin:0;background:#111}
#bar{position:absolute;z-index:999;left:8px;right:8px;bottom:10px;background:rgba(20,20,24,.85);color:#fff;font:14px sans-serif;border-radius:12px;padding:8px 12px;display:flex;gap:10px;align-items:center}
button{background:#f2c94c;border:0;border-radius:8px;padding:6px 12px;font-weight:bold}</style></head><body>
<div id="m"></div><div id="bar"><button id="pp">Pause</button><span id="t">Loading radar...</span></div>
<script>
var map=L.map('m',{zoomControl:true}).setView([$la,$lo],7);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:12,attribution:'&copy; OpenStreetMap'}).addTo(map);
L.marker([$la,$lo]).addTo(map).bindPopup('$safe');
var layers=[],times=[],idx=0,playing=true,timer=null,host='';
function show(i){layers.forEach(function(l,k){l.setOpacity(k===i?0.7:0)});
 var d=new Date(times[i]*1000);document.getElementById('t').textContent=(d.getTime()>Date.now()?'Forecast ':'Radar ')+d.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});}
function tick(){idx=(idx+1)%layers.length;show(idx);}
document.getElementById('pp').onclick=function(){playing=!playing;this.textContent=playing?'Pause':'Play';if(playing){timer=setInterval(tick,700)}else{clearInterval(timer)}};
fetch('https://api.rainviewer.com/public/weather-maps.json').then(function(r){return r.json()}).then(function(j){
 host=j.host;var f=(j.radar.past||[]).concat(j.radar.nowcast||[]);
 f.forEach(function(fr){times.push(fr.time);var l=L.tileLayer(host+fr.path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity:0,maxNativeZoom:7,maxZoom:12});l.addTo(map);layers.push(l)});
 idx=Math.max(0,(j.radar.past||[]).length-1);show(idx);timer=setInterval(tick,700);
}).catch(function(){document.getElementById('t').textContent='Radar unavailable right now'});
</script></body></html>
""".trimIndent()
}
