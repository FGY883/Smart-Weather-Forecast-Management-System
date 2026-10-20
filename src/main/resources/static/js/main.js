/* ============================================
   智能天气预报系统 - 主脚本
   ============================================ */

/* CSRF：为所有非 GET 的同源 fetch 自动附带 X-XSRF-TOKEN（读取 CookieCsrfTokenRepository 写入的 XSRF-TOKEN），
   修复此前收藏增删等 POST 请求缺少 CSRF 令牌被 403 拦截的问题。 */
(function installCsrfHeader() {
    if (!window.fetch) return;
    function getCsrf() {
        var m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : null;
    }
    var _fetch = window.fetch;
    window.fetch = function (input, init) {
        init = init || {};
        var method = (init.method || (input && input.method) || 'GET').toUpperCase();
        if (method !== 'GET' && method !== 'HEAD') {
            var token = getCsrf();
            if (token) {
                var h = init.headers;
                if (h && typeof h.append === 'function') {
                    if (!h.get('X-XSRF-TOKEN')) h.append('X-XSRF-TOKEN', token);
                } else {
                    h = h || {};
                    if (!h['X-XSRF-TOKEN']) h['X-XSRF-TOKEN'] = token;
                    init.headers = h;
                }
            }
        }
        return _fetch.call(this, input, init);
    };
})();

// 天气图标映射
const weatherIcons = {
    '晴': '☀️', '多云': '⛅', '阴': '☁️', '小雨': '🌦️', '中雨': '🌧️',
    '大雨': '🌧️', '暴雨': '⛈️', '雷阵雨': '⛈️', '小雪': '🌨️', '中雪': '🌨️',
    '大雪': '❄️', '雾': '🌫️', '霾': '😷', '沙尘暴': '🌪️', '阵雨': '🌦️',
    '雪': '❄️', '雨': '🌧️', '晴间多云': '🌤️', '阴转晴': '🌤️'
};

/**
 * 获取天气图标
 */
function getWeatherIcon(text) {
    if (!text) return '🌤️';
    for (let key in weatherIcons) {
        if (text.includes(key)) return weatherIcons[key];
    }
    return '🌤️';
}

/* ============================================
   登录态工具：未登录时执行受保护操作，提示登录并引导跳转
   登录态由服务端 fragments::navbar 注入的 #authState[data-auth] 提供
   ============================================ */
function isLoggedIn() {
    const el = document.getElementById('authState');
    return !!(el && el.getAttribute('data-auth') === 'true');
}

function requireLogin(message) {
    if (isLoggedIn()) return true;
    showToast(message || '该操作需要登录后进行', 'warning');
    const el = document.getElementById('authState');
    const login = (el && el.getAttribute('data-login')) || '/login';
    setTimeout(() => {
        window.location.href = login;
    }, 1000);
    return false;
}

function esc(s) {
    if (s === null || s === undefined) return '';
    const d = document.createElement('div');
    d.appendChild(document.createTextNode(String(s)));
    return d.innerHTML;
}

/**
 * 添加收藏
 */
function addFavorite(city, cityCode) {
    if (!requireLogin()) return;
    fetch('/api/favorite/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `city=${encodeURIComponent(city)}&cityCode=${encodeURIComponent(cityCode)}`
    })
    .then(res => res.json())
    .then(data => {
        if (data.code === 200) {
            showToast('收藏成功', 'success');
            const btn = document.getElementById('favoriteBtn');
            if (btn) {
                btn.classList.remove('btn-outline-warning');
                btn.classList.add('btn-warning');
                btn.innerHTML = '<i class="bi bi-star-fill me-1"></i>已收藏';
                btn.onclick = () => toggleFavorite(false, btn);
            }
        } else {
            showToast(data.msg || '收藏失败', 'warning');
        }
    })
    .catch(() => showToast('请求失败', 'danger'));
}

/**
 * 通过城市编码取消收藏
 */
function removeFavoriteByCityCode(cityCode) {
    if (!requireLogin()) return;
    // 先获取收藏列表找到对应的ID
    fetch('/api/favorite/list')
    .then(res => res.json())
    .then(data => {
        if (data.code === 200 && data.data) {
            const fav = data.data.find(f => f.cityCode === cityCode);
            if (fav) {
                removeFavorite(fav.id);
            }
        }
    })
    .catch(() => showToast('请求失败', 'danger'));
}

/**
 * 取消收藏
 */
function removeFavorite(favoriteId) {
    if (!requireLogin()) return;
    fetch('/api/favorite/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `favoriteId=${favoriteId}`
    })
    .then(res => res.json())
    .then(data => {
        if (data.code === 200) {
            showToast('已取消收藏', 'info');
            const btn = document.getElementById('favoriteBtn');
            if (btn) {
                btn.classList.remove('btn-warning');
                btn.classList.add('btn-outline-warning');
                btn.innerHTML = '<i class="bi bi-star me-1"></i>收藏';
                btn.onclick = () => toggleFavorite(true, btn);
            }
            // 如果在收藏列表页面，移除对应元素
            const item = document.querySelector(`[data-fav-id="${favoriteId}"]`);
            if (item) item.remove();
        } else {
            showToast(data.msg || '取消收藏失败', 'warning');
        }
    })
    .catch(() => showToast('请求失败', 'danger'));
}

/**
 * 切换收藏状态：isFavorited=true 表示"当前未收藏、点击添加"
 */
function toggleFavorite(isFavorited, btn) {
    if (!requireLogin()) return;
    const city = btn.dataset.city;
    const cityCode = btn.dataset.cityCode;
    if (isFavorited) {
        addFavorite(city, cityCode);
    } else {
        removeFavoriteByCityCode(cityCode);
    }
}

/**
 * 显示提示消息（消息按纯文本写入，避免 XSS）
 */
function showToast(message, type = 'info') {
    let toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.id = 'toastContainer';
        toastContainer.style.cssText = 'position:fixed;top:80px;right:20px;z-index:9999;';
        document.body.appendChild(toastContainer);
    }

    const toast = document.createElement('div');
    toast.className = `alert alert-${type} alert-dismissible fade show`;
    toast.style.cssText = 'min-width:280px;box-shadow:0 4px 12px rgba(0,0,0,0.15);border-radius:8px;animation:fadeIn 0.3s ease;';

    const msgNode = document.createElement('span');
    msgNode.textContent = message;               // 文本节点，杜绝 HTML 注入
    toast.appendChild(msgNode);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close';
    closeBtn.setAttribute('data-bs-dismiss', 'alert');
    closeBtn.setAttribute('aria-label', 'Close');
    toast.appendChild(closeBtn);

    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

/**
 * 加载用户收藏列表
 */
function loadFavorites() {
    const container = document.getElementById('favoritesList');
    if (!container) return;
    if (!isLoggedIn()) return;

    fetch('/api/favorite/list')
    .then(res => res.json())
    .then(data => {
        if (data.code === 200 && data.data && data.data.length > 0) {
            let html = '<div class="list-group">';
            data.data.forEach(fav => {
                html += `
                    <div class="list-group-item d-flex justify-content-between align-items-center" data-fav-id="${fav.id}">
                        <div>
                            <i class="bi bi-geo-alt-fill text-primary me-2"></i>
                            <a href="/weather/detail/${encodeURIComponent(fav.cityCode)}?cityName=${encodeURIComponent(fav.city)}" class="text-decoration-none">
                                <strong>${esc(fav.city)}</strong>
                            </a>
                        </div>
                        <button class="btn btn-sm btn-outline-danger" onclick="removeFavorite(${fav.id})">
                            <i class="bi bi-trash"></i> 取消收藏
                        </button>
                    </div>
                `;
            });
            html += '</div>';
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bookmark"></i><p>暂无收藏城市</p></div>';
        }
    })
    .catch(() => {
        container.innerHTML = '<div class="text-danger">加载失败</div>';
    });
}

/**
 * 加载推送记录
 */
function loadPushRecords() {
    const container = document.getElementById('pushRecordsList');
    if (!container) return;
    if (!isLoggedIn()) return;

    fetch('/api/push/records')
    .then(res => res.json())
    .then(data => {
        if (data.code === 200 && data.data && data.data.length > 0) {
            let html = '<div class="list-group">';
            data.data.forEach(record => {
                const typeLabel = record.pushType === 'WARNING' ? '预警推送' : '每日天气';
                const typeBadge = record.pushType === 'WARNING' ? 'danger' : 'info';
                html += `
                    <div class="list-group-item">
                        <div class="d-flex justify-content-between align-items-center mb-1">
                            <span class="badge bg-${typeBadge}">${typeLabel}</span>
                            <small class="text-muted">${esc(record.createTime)}</small>
                        </div>
                        <pre class="mb-0" style="white-space:pre-wrap;font-size:0.9rem;color:#555;">${esc(record.pushContent)}</pre>
                    </div>
                `;
            });
            html += '</div>';
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bell"></i><p>暂无推送记录</p></div>';
        }
    })
    .catch(() => {
        container.innerHTML = '<div class="text-danger">加载失败</div>';
    });
}

/**
 * 页面加载完成后初始化
 */
document.addEventListener('DOMContentLoaded', function() {
    // 为天气图标添加emoji
    document.querySelectorAll('[data-weather-text]').forEach(el => {
        const text = el.getAttribute('data-weather-text');
        el.textContent = getWeatherIcon(text);
    });

    // 初始化Bootstrap工具提示
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(el => new bootstrap.Tooltip(el));

    // 如果在个人中心页面，加载收藏和推送记录
    if (document.getElementById('favoritesList')) {
        loadFavorites();
    }
    if (document.getElementById('pushRecordsList')) {
        loadPushRecords();
    }
});

/* ============================================================
   “我的城市”网格（首页 / 详情页 Skies & Cities 板块）
   数据源=用户收藏（DB 持久、随账号，重新登录不丢）；支持增删。
   依赖 AetherCard（aether-card.js）与 showToast。
   ============================================================ */
function initCityGrid(gridId, addBtnId) {
    const grid = document.getElementById(gridId);
    if (!grid) return;
    const addBtn = document.getElementById(addBtnId);

    function toast(m, t) { if (window.showToast) showToast(m, t || 'info'); }

    function fetchCard(city) {
        return fetch('/api/aether/weather?city=' + encodeURIComponent(city))
            .then(r => r.json())
            .then(res => (res && res.code === 200 && res.data) ? res.data : null)
            .catch(() => null);
    }

    function renderEmpty() {
        grid.innerHTML = '<div class="empty"><span class="big">还没有城市</span>点“＋ 添加城市”，把常看的城市永久收藏到这里。</div>';
    }

    function decorateRemove() {
        grid.querySelectorAll('.aether-card').forEach(card => {
            if (card.querySelector('.aether-card-remove')) return;
            const code = card.getAttribute('data-code');
            const city = card.getAttribute('data-city');
            const x = document.createElement('button');
            x.type = 'button';
            x.className = 'aether-card-remove';
            x.title = '从我的城市移除';
            x.setAttribute('aria-label', '移除 ' + city);
            x.innerHTML = '<i class="bi bi-x-lg"></i>';
            x.addEventListener('click', e => {
                e.stopPropagation();
                if (!code) { toast('缺少城市编码，无法移除', 'warning'); return; }
                fetch('/api/favorite/remove', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: 'cityCode=' + encodeURIComponent(code)
                }).then(r => r.json()).then(res => {
                    if (res && res.code === 200) {
                        card.remove();
                        toast('已移除 ' + city, 'info');
                        if (!grid.querySelector('.aether-card')) renderEmpty();
                    } else {
                        toast((res && res.msg) || '移除失败', 'danger');
                    }
                }).catch(() => toast('移除失败', 'danger'));
            });
            card.appendChild(x);
        });
    }

    function loadGrid() {
        fetch('/api/favorite/list')
            .then(r => r.json())
            .then(res => {
                const favs = (res && res.code === 200 && res.data) ? res.data : [];
                if (!favs.length) { renderEmpty(); return; }
                Promise.all(favs.map(f => fetchCard(f.city))).then(cards => {
                    const valid = cards.filter(Boolean);
                    if (!valid.length) { renderEmpty(); return; }
                    AetherCard.render(grid, JSON.stringify(valid));
                    decorateRemove();
                });
            })
            .catch(renderEmpty);
    }

    function addCity(name, onDone) {
        name = (name || '').trim();
        if (!name) return;
        fetchCard(name).then(card => {
            if (!card) { toast('未找到城市「' + name + '」，请换个名称', 'warning'); return; }
            fetch('/api/favorite/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'city=' + encodeURIComponent(card.city || name) + '&cityCode=' + encodeURIComponent(card.code)
            }).then(r => r.json()).then(res => {
                if (res && res.code === 200) { toast('已添加 ' + name, 'success'); loadGrid(); if (onDone) onDone(); }
                else if (res && res.code === 401) { toast('请先登录', 'warning'); }
                else { toast((res && res.msg) || '添加失败', 'warning'); loadGrid(); if (onDone) onDone(); }
            }).catch(() => toast('添加失败', 'danger'));
        });
    }

    // —— Aether 风格内嵌搜索框（替代原生 prompt）——
    const box = document.getElementById('cityAddBox');
    const input = document.getElementById('cityAddInput');
    const goBtn = document.getElementById('cityAddGo');
    const sug = document.getElementById('cityAddSug');
    let CITY_LIST = ['北京', '上海', '广州', '深圳', '成都', '杭州', '西安', '武汉', '南京', '重庆',
        '天津', '苏州', '青岛', '厦门', '三亚', '哈尔滨', '长春', '沈阳', '大连', '昆明',
        '丽江', '桂林', '拉萨', '乌鲁木齐', '呼和浩特', '长沙', '郑州'];

    function loadCityList() {
        fetch('/api/aether/cities').then(r => r.json()).then(res => {
            if (res && res.code === 200 && Array.isArray(res.data) && res.data.length) CITY_LIST = res.data;
        }).catch(() => {});
    }
    function showSug(q) {
        if (!sug) return;
        const kw = (q || '').trim();
        const list = (kw ? CITY_LIST.filter(c => c.indexOf(kw) >= 0) : CITY_LIST).slice(0, 12);
        sug.innerHTML = '';
        if (!list.length) { sug.style.display = 'none'; return; }
        const tip = document.createElement('div');
        tip.className = 'sug-tip';
        tip.textContent = '点击或按回车添加';
        sug.appendChild(tip);
        list.forEach(c => {
            const el = document.createElement('div');
            el.className = 'sug';
            el.setAttribute('data-city', c);      // 用 DOM 赋值，避免属性/内容注入
            el.textContent = c;
            el.addEventListener('click', () => addCity(el.getAttribute('data-city'), closeBox));
            sug.appendChild(el);
        });
        sug.style.display = 'block';
    }
    function openBox() {
        if (!box) return;
        box.classList.add('open');
        if (addBtn) addBtn.style.display = 'none';
        if (input) { input.focus(); showSug(input.value); }
    }
    function closeBox() {
        if (!box) return;
        box.classList.remove('open');
        if (addBtn) addBtn.style.display = '';
        if (sug) sug.style.display = 'none';
        if (input) input.value = '';
    }

    if (addBtn) addBtn.addEventListener('click', openBox);
    if (goBtn) goBtn.addEventListener('click', () => { if (input && input.value.trim()) addCity(input.value, closeBox); });
    if (input) {
        input.addEventListener('input', () => showSug(input.value));
        input.addEventListener('keydown', e => {
            if (e.key === 'Enter' && input.value.trim()) addCity(input.value, closeBox);
            else if (e.key === 'Escape') closeBox();
        });
    }
    document.addEventListener('click', e => {
        const wrap = document.querySelector('.city-add-wrap');
        if (wrap && !wrap.contains(e.target)) closeBox();
    });
    loadCityList();
    loadGrid();
}
