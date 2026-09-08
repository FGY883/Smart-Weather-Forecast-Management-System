/* ============================================================
   城市卡渲染器（Aether 风 · 七种场景 · 复用模块）
   由 aether.html / index.html / detail.html 共用
   ============================================================ */

window.AetherCard = (function() {
  const SCENES = {
    clear:  { zh: '晴朗',   en: 'Sunny',        sky: 'sky-clear' },
    cloudy: { zh: '多云',   en: 'Cloudy',       sky: 'sky-cloudy' },
    rain:   { zh: '小雨',   en: 'Rainy',        sky: 'sky-rain' },
    snow:   { zh: '飘雪',   en: 'Snowy',        sky: 'sky-snow' },
    storm:  { zh: '雷阵雨', en: 'Thunderstorm', sky: 'sky-storm' },
    fog:    { zh: '薄雾',   en: 'Foggy',        sky: 'sky-fog' },
    night:  { zh: '晴夜',   en: 'Clear Night',  sky: 'sky-night' }
  };

  const rnd = (a, b) => a + Math.random() * (b - a);

  function cloudSVG(w) {
    const h = Math.round(w * 0.56);
    return `<svg class="cloudart" width="${w}" height="${h}" viewBox="0 0 160 90" aria-hidden="true">
      <path d="M34 76 C16 76 6 64 12 52 C17 33 40 28 50 40 C58 22 84 20 94 34 C112 22 138 32 138 50 C152 52 158 70 142 76 Z"
            fill="#ffffff" stroke="#9fb3d0" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>
    </svg>`;
  }

  // 云朵分区：把上方区域切成 n 条水平带，每朵云独占一条，并用负延迟错开相位，
  // 保证任意时刻都不会叠在一起。
  function cloudGroup(n, opacity, minW, maxW) {
    let s = '';
    const bandTop = 3, bandSpan = 48;
    for (let i = 0; i < n; i++) {
      const band = bandSpan / n;
      const top  = bandTop + band * i + rnd(0, band * 0.4);
      const w    = Math.round(rnd(minW, maxW));
      const dur  = rnd(54, 88);
      const delay = -(dur * i / n) - rnd(0, 4);
      const op   = (opacity * rnd(.78, 1)).toFixed(2);
      s += `<div class="cloudgrp" style="top:${top.toFixed(1)}%;opacity:${op};
              animation-duration:${dur.toFixed(1)}s;animation-delay:${delay.toFixed(1)}s">
              <div class="bobb" style="animation-duration:${rnd(4.5,7.5).toFixed(1)}s;
                   animation-delay:-${rnd(0,4).toFixed(1)}s">${cloudSVG(w)}</div>
            </div>`;
    }
    return s;
  }

  function rainLayer(n, minH, maxH) {
    let s = '';
    for (let i = 0; i < n; i++) {
      s += `<div class="drop" style="left:${rnd(0,100).toFixed(1)}%;height:${rnd(minH,maxH).toFixed(0)}px;
              animation-duration:${rnd(.55,1.15).toFixed(2)}s;animation-delay:-${rnd(0,1.2).toFixed(2)}s;
              opacity:${rnd(.35,.85).toFixed(2)}"></div>`;
    }
    return s;
  }

  function snowLayer(n) {
    let s = '';
    for (let i = 0; i < n; i++) {
      s += `<div class="flake" style="left:${rnd(0,100).toFixed(1)}%;font-size:${rnd(9,19).toFixed(0)}px;
              animation-duration:${rnd(5,10).toFixed(1)}s;animation-delay:-${rnd(0,9).toFixed(1)}s;
              opacity:${rnd(.5,1).toFixed(2)}">❄</div>`;
    }
    return s;
  }

  function starLayer(n) {
    let s = '';
    for (let i = 0; i < n; i++) {
      s += `<div class="star" style="left:${rnd(2,97).toFixed(1)}%;top:${rnd(3,66).toFixed(1)}%;
              animation-duration:${rnd(1.6,4.2).toFixed(1)}s;animation-delay:-${rnd(0,4).toFixed(1)}s"></div>`;
    }
    return s;
  }

  function birdLayer(n) {
    let s = '';
    for (let i = 0; i < n; i++) {
      const top = 30 + i * 13 + rnd(-4, 4);
      s += `<div class="bird" style="top:${top.toFixed(1)}%;left:0;
              animation-duration:${rnd(16,26).toFixed(1)}s;animation-delay:-${rnd(0,14).toFixed(1)}s">
              <svg width="26" height="12" viewBox="0 0 26 12" fill="none"
                   stroke="rgba(60,80,120,.55)" stroke-width="2.2" stroke-linecap="round">
                <path d="M1 8 C5 2, 9 2, 13 8 C17 2, 21 2, 25 8"/>
              </svg>
            </div>`;
    }
    return s;
  }

  function buildScene(key) {
    let inner = '';
    if (key === 'clear') {
      inner += `<div class="sunrays">${
        Array.from({length: 12}, (_, i) => `<div class="ray" style="transform:rotate(${i * 30}deg)"></div>`).join('')
      }</div>`;
      inner += `<div class="sun"></div>`;
      inner += cloudGroup(2, .7, 76, 108);
      inner += birdLayer(2);
    } else if (key === 'cloudy') {
      inner += `<div class="sun" style="width:86px;height:86px;top:-14px;right:16px;opacity:.5;
                  box-shadow:0 0 26px 6px rgba(255,209,90,.32)"></div>`;
      inner += cloudGroup(3, 1, 84, 128);
    } else if (key === 'rain') {
      inner += cloudGroup(2, 1, 92, 132);
      inner += rainLayer(36, 12, 22);
    } else if (key === 'snow') {
      inner += cloudGroup(2, .9, 88, 124);
      inner += snowLayer(26);
    } else if (key === 'storm') {
      inner += cloudGroup(2, 1, 96, 140);
      inner += rainLayer(44, 14, 26);
      inner += `<div class="boltline">
                  <svg width="46" height="96" viewBox="0 0 46 96" fill="none" stroke="#fff"
                       stroke-width="3.4" stroke-linejoin="round" stroke-linecap="round"
                       style="filter:drop-shadow(0 0 10px #aee)">
                    <path d="M26 2 L10 50 H26 L18 94 L38 40 H23 L34 2 Z" fill="#fff" fill-opacity=".92"/>
                  </svg>
                </div>
                <div class="bolt"></div>`;
    } else if (key === 'fog') {
      inner += cloudGroup(2, .5, 92, 130);
      inner += Array.from({length: 5}, (_, i) =>
        `<div class="fogband" style="top:${18 + i * 15}%;animation-duration:${(9 + i * 3).toFixed(0)}s;
           opacity:${(.5 - i * .055).toFixed(2)}"></div>`).join('');
    } else if (key === 'night') {
      inner += `<div class="moon"></div>`;
      inner += starLayer(30);
      inner += cloudGroup(1, .35, 90, 124);
      inner += `<div class="shoot" style="top:14%;left:8%;animation-delay:${rnd(1,7).toFixed(1)}s"></div>`;
    }
    inner += `<div class="sheen"></div>`;
    return `<div class="scene ${SCENES[key].sky}">${inner}</div>`;
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function cardHTML(c, i) {
    const s = SCENES[c.scene] || SCENES.cloudy;
    const hi    = c.hi == null ? '—' : c.hi;
    const lo    = c.lo == null ? '—' : c.lo;
    const wind  = c.windSpeed == null ? '—' : c.windSpeed;
    const hum   = c.humidity == null ? '—' : c.humidity;
    return `<div class="aether-card" data-city="${esc(c.city)}" data-code="${esc(c.code || '')}" style="--i:${i}" tabindex="0"
                 role="link" aria-label="${esc(c.city)} 天气详情">
      ${buildScene(c.scene)}
      <div class="content">
        <div class="top">
          <div class="city">${esc(c.city)}</div>
        </div>
        <div class="spacer"></div>
        <div class="temp">${c.temp == null ? '—' : c.temp}<sup>°C</sup></div>
        <div class="desc">${esc(c.text)}<span class="en">${s.en}</span></div>
        <div class="range">↑ ${hi}° &nbsp; ↓ ${lo}°</div>
        <div class="meta">
          <div>${hum}%<span>湿度</span></div>
          <div>${wind} km/h<span>${esc(c.windDir || '风速')}</span></div>
        </div>
        ${c.warning ? `<div class="warn" title="${esc(c.warning)}">⚠ ${esc(c.warning)}</div>` : ''}
      </div>
      <div class="stamp">${esc(c.updatedAt || '')} 更新</div>
    </div>`;
  }

  function bindGrid(grid, onCardClick) {
    grid.querySelectorAll('.aether-card').forEach(card => {
      card.addEventListener('click', e => {
        // 水波纹
        const r = document.createElement('span');
        r.className = 'ripple';
        const b = card.getBoundingClientRect();
        r.style.width = r.style.height = '70px';
        r.style.left = (e.clientX - b.left - 35) + 'px';
        r.style.top  = (e.clientY - b.top  - 35) + 'px';
        card.appendChild(r);
        setTimeout(() => r.remove(), 640);
        // 跳转
        if (onCardClick) {
          setTimeout(() => onCardClick(card.dataset), 240);
        }
      });
      card.addEventListener('keydown', e => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          if (onCardClick) onCardClick(card.dataset);
        }
      });
    });
  }

  /**
   * 渲染城市卡网格到指定容器
   * @param gridEl .aether-grid 容器
   * @param cardsJson 后端传来的 JSON 字符串
   * @param onCardClick 点击回调（默认跳到详情页）
   */
  function render(gridEl, cardsJson, onCardClick) {
    if (!gridEl) return;
    let cards = [];
    try { cards = JSON.parse(cardsJson || '[]'); } catch (e) { cards = []; }
    if (!cards.length) {
      gridEl.innerHTML = '<div class="empty"><span class="big">这里还空着</span>天气数据加载中...</div>';
      return;
    }
    gridEl.innerHTML = cards.map(cardHTML).join('');
    const nav = onCardClick || (data => {
      if (data.code) {
        location.href = '/weather/detail/' + encodeURIComponent(data.code)
                       + '?cityName=' + encodeURIComponent(data.city || '');
      }
    });
    bindGrid(gridEl, nav);
  }

  return { SCENES, render, cardHTML, buildScene };
})();