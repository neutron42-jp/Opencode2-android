package ai.opencode.app

/**
 * Built-in userscript: appends 更新 / アプリ設定 buttons below the
 * Settings|Help row in the WebUI mobile drawer. Styled with the same
 * classes as the stock buttons so light/dark themes match automatically.
 */
object BuiltInScript {
    val JS = """
    (function(){
      function btnClass(){
        return 'flex h-7 min-w-0 flex-1 items-center gap-2 rounded-[6px] px-2 ' +
          'text-[13px] leading-4 text-v2-text-text-faint ' +
          'hover:bg-v2-background-bg-layer-02 focus-visible:outline-none ' +
          'focus-visible:bg-v2-background-bg-layer-02';
      }
      function icon(name){
        return '<svg data-slot="icon-svg" width="14" height="14" viewBox="0 0 16 16" ' +
          'fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
          '<use href="#' + name + '"></use></svg>';
      }
      function ensure(){
        if (document.getElementById('oc-app-menu-row')) return;
        var settings = document.querySelector('[data-action="mobile-tabs-settings"]');
        if (!settings || !settings.parentElement) return;
        var row = document.createElement('div');
        row.id = 'oc-app-menu-row';
        row.className = 'flex items-center gap-1';
        row.style.marginTop = '2px';
        row.innerHTML =
          '<button type="button" class="' + btnClass() + '">' +
            icon('opencode-v2-icon-refresh') + '<span>更新</span></button>' +
          '<button type="button" class="' + btnClass() + '">' +
            icon('opencode-v2-icon-sliders') + '<span>アプリ設定</span></button>';
        var btns = row.querySelectorAll('button');
        btns[0].addEventListener('click', function(){
          if (window.OpenCodeApp) OpenCodeApp.reload();
        });
        btns[1].addEventListener('click', function(){
          if (window.OpenCodeApp) OpenCodeApp.openSettings();
        });
        settings.parentElement.after(row);
      }
      new MutationObserver(ensure).observe(document.documentElement, {
        childList: true, subtree: true
      });
      ensure();
    })();
    """.trimIndent()
}
