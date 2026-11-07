package io.github.pcdv.hackertv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Full-screen WebView on https://hackertv.org. The page already reacts to arrow keys, digits and
 * Enter, so the only job here is to turn remote keys it doesn't know into the keys it does.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var lastBack = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Switch the TV on (the page shows a power button until it is clicked), then
                    // enter theater mode, the page's full-window layout. Its own fullscreen button
                    // needs a native custom view, which this WebView does not provide.
                    view.evaluateJavascript(LET_DPAD_NAVIGATE, null)
                    view.evaluateJavascript(GUIDE_NAVIGATION, null)
                    view.evaluateJavascript(
                        """setTimeout(()=>{
                            document.getElementById('powerCta')?.click();
                            setTimeout(()=>{
                              if(!document.body.classList.contains('theater'))
                                document.dispatchEvent(new KeyboardEvent('keydown',{key:'t',bubbles:true}));
                            },1500);
                        },1500)""", null
                    )
                }
            }
            setBackgroundColor(0xFF000000.toInt())
            isFocusable = true
            requestFocus()
        }
        setContentView(web)
        hideSystemBars()

        if (savedInstanceState == null) web.loadUrl(URL) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        val key = REMOTE_KEYS[event.keyCode]
        if (key != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) sendKey(key)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back closes the guide (Escape); a second press within two seconds leaves the app. */
    private fun onBack() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack < 2000) finish() else sendKey("Escape")
        lastBack = now
    }

    private fun sendKey(key: String) {
        web.evaluateJavascript(
            "document.dispatchEvent(new KeyboardEvent('keydown',{key:'$key',bubbles:true}))", null
        )
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        web.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private companion object {
        const val URL = "https://hackertv.org/"

        /**
         * The page turns ArrowUp/ArrowDown into channel changes and cancels the key, which stops
         * the D-pad from moving focus between its buttons. Real key presses (isTrusted) are kept
         * from reaching the page, so the D-pad navigates; the Channel keys are re-sent from
         * [REMOTE_KEYS] as synthetic events, which pass through. A mousemove wakes the theater-mode
         * controls, which otherwise appear only on a key the page has seen.
         */
        const val LET_DPAD_NAVIGATE = """(()=>{
            if(window.__tvDpad) return; window.__tvDpad=true;
            addEventListener('keydown',e=>{
              if(e.isTrusted&&(e.key==='ArrowUp'||e.key==='ArrowDown')){
                e.stopPropagation();
                document.dispatchEvent(new Event('mousemove'));
              }
            },true);
        })()"""

        /**
         * D-pad navigation inside the program guide. Opening the guide leaves focus on a control
         * behind it, so the arrows have nowhere to go: focus the on-air program of the current
         * channel when the guide appears, and move focus on each arrow press: up and down to the
         * adjacent channel row, left and right along the row. OK activates the focused button,
         * which tunes that channel. Also draws a focus ring, which the page lacks.
         */
        const val GUIDE_NAVIGATION = """(()=>{
            if(window.__tvGuide) return; window.__tvGuide=true;
            const st=document.createElement('style');
            st.textContent='button:focus{outline:3px solid #ffe35a;outline-offset:1px}';
            document.head.appendChild(st);
            const guide=document.getElementById('guide'), grid=document.getElementById('guideGrid');
            const isOpen=()=>!guide.hidden;
            // Focus without the browser's own scroll, then scroll so the button clears the sticky
            // time bar, which scrollIntoView ignores.
            const focusOn=b=>{
              b.focus({preventScroll:true});
              if(!grid.contains(b)) return;
              const g=grid.getBoundingClientRect(), r=b.getBoundingClientRect();
              const head=grid.querySelector('.g-head');
              const top=g.top+(head?head.getBoundingClientRect().height:0);
              if(r.top<top) grid.scrollTop-=top-r.top;
              else if(r.bottom>g.bottom) grid.scrollTop+=r.bottom-g.bottom;
            };
            const focusStart=()=>{
              const b=grid.querySelector('.g-row.current .g-prog.live')
                ||grid.querySelector('.g-row.current .g-ch')||grid.querySelector('button');
              if(b) focusOn(b);
            };
            new MutationObserver(()=>{ if(isOpen()&&grid.querySelector('.g-row')) focusStart(); })
              .observe(grid,{childList:true});
            new MutationObserver(()=>{
              if(!isOpen()){ if(document.activeElement) document.activeElement.blur(); }
              else if(grid.querySelector('.g-row')) focusStart();
            }).observe(guide,{attributes:true,attributeFilter:['hidden']});
            const DIRS={ArrowUp:[0,-1],ArrowDown:[0,1],ArrowLeft:[-1,0],ArrowRight:[1,0]};
            addEventListener('keydown',e=>{
              const dir=DIRS[e.key];
              if(!dir||!e.isTrusted||!isOpen()) return;
              e.stopPropagation(); e.preventDefault();
              const cur=document.activeElement;
              if(!cur||cur===guide||!guide.contains(cur)){ focusStart(); return; }
              const rows=[...grid.querySelectorAll('.g-row:not(.g-head)')];
              const row=cur.closest('.g-row');
              const center=b=>{ const q=b.getBoundingClientRect(); return q.left+q.width/2; };
              const nearest=(list,x)=>list.reduce((a,b)=>
                Math.abs(center(b)-x)<Math.abs(center(a)-x)?b:a);
              const close=document.getElementById('guideClose');
              if(!row){                                   // on the close button
                if(dir[1]>0&&rows.length) focusOn(nearest([...rows[0].querySelectorAll('button')],center(cur)));
                return;
              }
              if(dir[1]){                                 // other row, nearest column
                const next=rows[rows.indexOf(row)+dir[1]];
                if(next) focusOn(nearest([...next.querySelectorAll('button')],center(cur)));
                else if(dir[1]<0) focusOn(close);
              }else{                                      // same row, next program
                const btns=[...row.querySelectorAll('button')].sort((a,b)=>center(a)-center(b));
                const next=btns[btns.indexOf(cur)+dir[0]];
                if(next) focusOn(next);
              }
            },true);
        })()"""

        /** Remote keys the page has no handler for, mapped to the keys it has. */
        val REMOTE_KEYS = mapOf(
            KeyEvent.KEYCODE_CHANNEL_UP to "ArrowUp",
            KeyEvent.KEYCODE_CHANNEL_DOWN to "ArrowDown",
            KeyEvent.KEYCODE_PROG_RED to "g",     // guide
            KeyEvent.KEYCODE_PROG_GREEN to "f",   // picture filter
            KeyEvent.KEYCODE_PROG_YELLOW to "c",  // captions
            KeyEvent.KEYCODE_PROG_BLUE to "i",    // info
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "t", // theater (full window) on or off
            KeyEvent.KEYCODE_GUIDE to "g",
            KeyEvent.KEYCODE_INFO to "i",
            KeyEvent.KEYCODE_CAPTIONS to "c",
        )
    }
}
