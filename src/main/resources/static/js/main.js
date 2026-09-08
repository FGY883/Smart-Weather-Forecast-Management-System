/* 智能天气预报系统 - 前端公共脚本
 * 职责：
 *  1. 提供 fetch 封装，自动附带 CSRF token 与统一错误提示
 *  2. 提供 window.WeatherApp 工具：收藏/取消/列表/Toast
 *  3. 页面无侵入式挂载：data-favorite-btn 元素自动绑定
 */
(function () {
    'use strict';

    function getCookie(name) {
        var m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return m ? decodeURIComponent(m[1]) : null;
    }
    function getMeta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute('content') : null;
    }
    function getCsrf() {
        return getMeta('_csrf') || getCookie('XSRF-TOKEN') || getCookie('X-CSRF-TOKEN');
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function showToast(message, type) {
        type = type || 'info';
        var container = document.getElementById('toastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toastContainer';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        var el = document.createElement('div');
        el.className = 'toast-msg toast-msg--' + (type === 'error' ? 'danger' : type);
        el.textContent = message;
        container.appendChild(el);
        setTimeout(function () {
            el.style.transition = 'opacity .25s';
            el.style.opacity = '0';
            setTimeout(function () { el.remove(); }, 260);
        }, 2400);
    }

    function post(url, body) {
        var csrf = getCsrf();
        return fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'X-CSRF-TOKEN': csrf || ''
            },
            body: body
        }).then(function (r) { return r.json(); });
    }

    function get(url) {
        return fetch(url, { credentials: 'same-origin' }).then(function (r) { return r.json(); });
    }

    /* ----- 收藏 ----- */
    function addFavorite(city, cityCode, btn) {
        post('/api/favorite/add', 'city=' + encodeURIComponent(city) + '&cityCode=' + encodeURIComponent(cityCode))
            .then(function (data) {
                if (data.code === 200) {
                    if (btn) {
                        btn.classList.remove('btn-outline-primary');
                        btn.classList.add('btn-primary');
                        btn.setAttribute('data-favorited', '1');
                        btn.innerHTML = '<i class="bi bi-star-fill"></i> 已收藏';
                    }
                    showToast('已收藏', 'success');
                } else {
                    showToast(data.msg || '收藏失败', 'warning');
                }
            })
            .catch(function () { showToast('请求失败', 'danger'); });
    }
    function removeFavorite(cityCode, btn) {
        post('/api/favorite/remove', 'cityCode=' + encodeURIComponent(cityCode))
            .then(function (data) {
                if (data.code === 200) {
                    if (btn) {
                        btn.classList.remove('btn-primary');
                        btn.classList.add('btn-outline-primary');
                        btn.removeAttribute('data-favorited');
                        btn.innerHTML = '<i class="bi bi-star"></i> 收藏';
                    }
                    showToast('已取消收藏', 'info');
                } else {
                    showToast(data.msg || '取消收藏失败', 'warning');
                }
            })
            .catch(function () { showToast('请求失败', 'danger'); });
    }
    function toggleFavorite(btn) {
        var city = btn.getAttribute('data-city');
        var cityCode = btn.getAttribute('data-city-code');
        if (!city || !cityCode) return;
        var favorited = btn.getAttribute('data-favorited') === '1';
        if (favorited) {
            removeFavorite(cityCode, btn);
        } else {
            addFavorite(city, cityCode, btn);
        }
    }

    function listFavorites() {
        return get('/api/favorite/list').then(function (data) {
            if (data.code === 200) return data.data || [];
            if (data.code === 401) return null;
            showToast(data.msg || '获取收藏失败', 'warning');
            return [];
        });
    }

    function listPushRecords(limit) {
        return get('/api/push/records?limit=' + (limit || 20)).then(function (data) {
            if (data.code === 200) return data.data || [];
            if (data.code === 401) return null;
            return [];
        });
    }

    /* ----- 自动绑定 data-favorite-btn ----- */
    function bindFavoriteButtons() {
        var btns = document.querySelectorAll('[data-favorite-btn]');
        for (var i = 0; i < btns.length; i++) {
            (function (btn) {
                btn.addEventListener('click', function (e) {
                    e.preventDefault();
                    var loggedIn = btn.getAttribute('data-logged-in') === '1';
                    if (!loggedIn) {
                        showToast('请先登录', 'warning');
                        setTimeout(function () { location.href = '/login'; }, 600);
                        return;
                    }
                    toggleFavorite(btn);
                });
            })(btns[i]);
        }
    }

    /* ----- 个人中心列表渲染 ----- */
    function renderFavorites(container, items) {
        if (!items) {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bookmark"></i>登录后可查看收藏</div>';
            return;
        }
        if (!items.length) {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bookmark"></i>暂无收藏城市</div>';
            return;
        }
        var html = '<ul class="list-clean">';
        for (var i = 0; i < items.length; i++) {
            var f = items[i];
            html += '<li>'
                + '<span><i class="bi bi-geo-alt text-soft"></i> '
                + escapeHtml(f.city) + ' <span class="text-muted small">(' + escapeHtml(f.cityCode) + ')</span></span>'
                + '<span class="d-flex gap-2">'
                + '<a class="btn btn-sm btn-outline-primary" href="/weather/detail/' + encodeURIComponent(f.cityCode) + '?cityName=' + encodeURIComponent(f.city) + '">查看</a>'
                + '<button class="btn btn-sm btn-outline-danger" data-remove-fav="' + f.id + '" data-city-code="' + escapeHtml(f.cityCode) + '">移除</button>'
                + '</span></li>';
        }
        html += '</ul>';
        container.innerHTML = html;
        var removeBtns = container.querySelectorAll('[data-remove-fav]');
        for (var j = 0; j < removeBtns.length; j++) {
            removeBtns[j].addEventListener('click', function () {
                var code = this.getAttribute('data-city-code');
                if (!confirm('确定要取消收藏吗？')) return;
                post('/api/favorite/remove', 'cityCode=' + encodeURIComponent(code))
                    .then(function (data) {
                        if (data.code === 200) loadFavoritesInto(container);
                        else showToast(data.msg || '操作失败', 'warning');
                    });
            });
        }
    }

    function loadFavoritesInto(container) {
        listFavorites().then(function (items) { renderFavorites(container, items); });
    }

    function renderPushRecords(container, items) {
        if (!items) {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bell"></i>登录后可查看推送记录</div>';
            return;
        }
        if (!items.length) {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-bell"></i>暂无推送记录</div>';
            return;
        }
        var html = '<table class="data-table"><thead><tr><th>类型</th><th>内容</th><th>状态</th><th>时间</th></tr></thead><tbody>';
        for (var i = 0; i < items.length; i++) {
            var r = items[i];
            var statusBadge = r.pushStatus === 1
                ? '<span class="badge badge--active">已发送</span>'
                : '<span class="badge badge--closed">未发送</span>';
            var type = r.pushType === 'WARNING' ? '预警' : '每日';
            var time = r.pushTime || r.createTime || '';
            html += '<tr>'
                + '<td><span class="badge">' + escapeHtml(type) + '</span></td>'
                + '<td class="text-soft">' + escapeHtml((r.pushContent || '').slice(0, 60)) + '</td>'
                + '<td>' + statusBadge + '</td>'
                + '<td class="text-muted">' + escapeHtml(time) + '</td>'
                + '</tr>';
        }
        html += '</tbody></table>';
        container.innerHTML = html;
    }

    function loadPushInto(container) {
        listPushRecords(20).then(function (items) { renderPushRecords(container, items); });
    }

    /* ----- 城市卡网格 ----- */
    function iconOf(scene) {
        switch (scene) {
            case 'clear': return '<i class="bi bi-sun"></i>';
            case 'cloudy': return '<i class="bi bi-cloud-sun"></i>';
            case 'rain':   return '<i class="bi bi-cloud-rain"></i>';
            case 'snow':   return '<i class="bi bi-snow"></i>';
            case 'storm':  return '<i class="bi bi-cloud-lightning-rain"></i>';
            case 'fog':    return '<i class="bi bi-cloud-fog2"></i>';
            case 'night':  return '<i class="bi bi-moon-stars"></i>';
            default:       return '<i class="bi bi-cloud"></i>';
        }
    }

    function cardHtml(c) {
        var hi = c.hi == null ? '—' : c.hi + '°';
        var lo = c.lo == null ? '—' : c.lo + '°';
        var wind = c.windSpeed == null || c.windSpeed === '' ? '—' : c.windSpeed + ' km/h';
        var hum = c.humidity == null || c.humidity === '' ? '—' : c.humidity + '%';
        var temp = c.temp == null ? '—' : c.temp + '°';
        var sceneClass = c.scene ? ' aether-card--' + c.scene : '';
        var removeBtn = c.removable
            ? '<button class="del" data-remove-card="' + escapeHtml(c.code || c.city) + '" aria-label="移除" type="button">&times;</button>'
            : '';
        return '<div class="aether-card' + sceneClass + '" data-city="' + escapeHtml(c.city) + '" data-code="' + escapeHtml(c.code || '') + '" tabindex="0" role="link" aria-label="' + escapeHtml(c.city) + ' 天气详情">'
            + removeBtn
            + '<div class="aether-card__icon">' + iconOf(c.scene) + '</div>'
            + '<div class="content">'
            + '<div class="city">' + escapeHtml(c.city) + '</div>'
            + '<div class="temp">' + temp + '</div>'
            + '<div class="desc">' + escapeHtml(c.text || '—') + '</div>'
            + '<div class="range">↑ ' + hi + '　↓ ' + lo + '</div>'
            + '<div class="meta"><span>' + hum + '</span><span>' + escapeHtml(c.windDir || '') + ' ' + wind + '</span></div>'
            + (c.warning ? '<div class="warn"><i class="bi bi-exclamation-triangle"></i> ' + escapeHtml(c.warning) + '</div>' : '')
            + '<div class="stamp">' + escapeHtml(c.updatedAt || '') + ' 更新</div>'
            + '</div></div>';
    }

    function renderCityGrid(gridEl, json) {
        if (!gridEl) return;
        var list = [];
        try { list = JSON.parse(json || '[]'); } catch (e) { list = []; }
        if (!list.length) {
            gridEl.innerHTML = '<div class="empty-state"><i class="bi bi-cloud"></i>天气数据加载中</div>';
            return;
        }
        gridEl.innerHTML = list.map(cardHtml).join('');
        Array.prototype.forEach.call(gridEl.querySelectorAll('.aether-card'), function (card) {
            card.addEventListener('click', function () {
                var code = card.dataset.code, city = card.dataset.city;
                if (code) location.href = '/weather/detail/' + encodeURIComponent(code) + '?cityName=' + encodeURIComponent(city || '');
            });
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    var code = card.dataset.code, city = card.dataset.city;
                    if (code) location.href = '/weather/detail/' + encodeURIComponent(code) + '?cityName=' + encodeURIComponent(city || '');
                }
            });
        });
        Array.prototype.forEach.call(gridEl.querySelectorAll('[data-remove-card]'), function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                var code = btn.getAttribute('data-remove-card');
                if (!confirm('确定要移除 ' + code + ' 吗？')) return;
                post('/api/favorite/remove', 'cityCode=' + encodeURIComponent(code)).then(function (data) {
                    if (data && data.code === 200) {
                        var card = btn.closest('.aether-card');
                        if (card) card.remove();
                        showToast('已移除', 'info');
                    } else {
                        showToast((data && data.msg) || '操作失败', 'warning');
                    }
                });
            });
        });
    }

    /* ----- 移动端导航 ----- */
    function bindNavToggle() {
        var btn = document.querySelector('[data-nav-toggle]');
        var target = document.querySelector('[data-nav-target]');
        if (!btn || !target) return;
        btn.addEventListener('click', function () {
            target.classList.toggle('show');
        });
    }
    function bindUserDropdown() {
        var trigger = document.querySelector('[data-user-dropdown]');
        var menu = document.querySelector('[data-user-menu]');
        if (!trigger || !menu) return;
        trigger.addEventListener('click', function (e) {
            e.preventDefault();
            menu.classList.toggle('show');
        });
        document.addEventListener('click', function (e) {
            if (!trigger.contains(e.target) && !menu.contains(e.target)) {
                menu.classList.remove('show');
            }
        });
    }

    /* ----- 暴露给页面 ----- */
    window.WeatherApp = {
        showToast: showToast,
        escapeHtml: escapeHtml,
        post: post,
        get: get,
        addFavorite: addFavorite,
        removeFavorite: removeFavorite,
        toggleFavorite: toggleFavorite,
        listFavorites: listFavorites,
        listPushRecords: listPushRecords,
        loadFavoritesInto: loadFavoritesInto,
        loadPushInto: loadPushInto,
        renderFavorites: renderFavorites,
        renderPushRecords: renderPushRecords,
        renderCityGrid: renderCityGrid,
        cardHtml: cardHtml
    };

    document.addEventListener('DOMContentLoaded', function () {
        bindNavToggle();
        bindUserDropdown();
        bindFavoriteButtons();
    });
})();
