/* ============================================
   智能天气预报系统 - 主脚本
   ============================================ */

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

/**
 * 添加收藏
 */
function addFavorite(city, cityCode) {
    fetch('/api/favorite/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `city=${encodeURIComponent(city)}&cityCode=${cityCode}`
    })
    .then(res => res.json())
    .then(data => {
        if (data.code === 200) {
            showToast('收藏成功', 'success');
            const btn = document.querySelector('.btn-favorite');
            if (btn) {
                btn.classList.add('active');
                btn.innerHTML = '<i class="bi bi-star-fill"></i> 已收藏';
                btn.onclick = () => removeFavoriteByCityCode(cityCode);
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
    });
}

/**
 * 取消收藏
 */
function removeFavorite(favoriteId) {
    fetch('/api/favorite/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `favoriteId=${favoriteId}`
    })
    .then(res => res.json())
    .then(data => {
        if (data.code === 200) {
            showToast('已取消收藏', 'info');
            const btn = document.querySelector('.btn-favorite');
            if (btn) {
                btn.classList.remove('active');
                btn.innerHTML = '<i class="bi bi-star"></i> 收藏';
                // 重新绑定点击事件（需要cityCode）
                const cityCode = btn.dataset.cityCode;
                const city = btn.dataset.city;
                if (cityCode && city) {
                    btn.onclick = () => addFavorite(city, cityCode);
                }
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
 * 切换收藏状态
 */
function toggleFavorite(isFavorited, btn) {
    const city = btn.dataset.city;
    const cityCode = btn.dataset.cityCode;
    if (isFavorited) {
        addFavorite(city, cityCode);
    } else {
        removeFavoriteByCityCode(cityCode);
    }
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    const toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        const container = document.createElement('div');
        container.id = 'toastContainer';
        container.style.cssText = 'position:fixed;top:80px;right:20px;z-index:9999;';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `alert alert-${type} alert-dismissible fade show`;
    toast.style.cssText = 'min-width:280px;box-shadow:0 4px 12px rgba(0,0,0,0.15);border-radius:8px;animation:fadeIn 0.3s ease;';
    toast.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;
    document.getElementById('toastContainer').appendChild(toast);

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
                            <a href="/weather/detail/${fav.cityCode}?cityName=${encodeURIComponent(fav.city)}" class="text-decoration-none">
                                <strong>${fav.city}</strong>
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
                            <small class="text-muted">${record.createTime || ''}</small>
                        </div>
                        <pre class="mb-0" style="white-space:pre-wrap;font-size:0.9rem;color:#555;">${record.pushContent || ''}</pre>
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
 * 初始化AI预测图表 (ECharts)
 */
function initAiForecastChart(predictions) {
    if (!predictions || predictions.length === 0) return;

    const chartDom = document.getElementById('aiForecastChart');
    if (!chartDom) return;

    const chart = echarts.init(chartDom);
    const dates = predictions.map(p => p.targetDate);
    const tempMax = predictions.map(p => p.tempMaxPred);
    const tempMin = predictions.map(p => p.tempMinPred);

    const option = {
        tooltip: { trigger: 'axis' },
        legend: { data: ['预测最高温', '预测最低温'], top: 10 },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
            type: 'category',
            data: dates,
            axisLabel: { formatter: val => val ? val.substring(5) : '' }
        },
        yAxis: {
            type: 'value',
            name: '温度(℃)',
            axisLabel: { formatter: '{value}℃' }
        },
        series: [
            {
                name: '预测最高温',
                type: 'line',
                data: tempMax,
                smooth: true,
                lineStyle: { width: 3 },
                itemStyle: { color: '#E74C3C' },
                areaStyle: { color: 'rgba(231, 76, 60, 0.1)' },
                markPoint: {
                    data: [
                        { type: 'max', name: '最高值' }
                    ]
                }
            },
            {
                name: '预测最低温',
                type: 'line',
                data: tempMin,
                smooth: true,
                lineStyle: { width: 3 },
                itemStyle: { color: '#3498DB' },
                areaStyle: { color: 'rgba(52, 152, 219, 0.1)' },
                markPoint: {
                    data: [
                        { type: 'min', name: '最低值' }
                    ]
                }
            }
        ]
    };

    chart.setOption(option);
    window.addEventListener('resize', () => chart.resize());
}

/**
 * 初始化统计图表 (ECharts)
 */
function initStatisticsCharts(stats) {
    if (!stats || stats.length === 0) return;

    const months = stats.map(s => s.statMonth + '月');
    const avgTemp = stats.map(s => s.avgTemp);
    const maxTemp = stats.map(s => s.maxTemp);
    const minTemp = stats.map(s => s.minTemp);
    const precip = stats.map(s => s.totalPrecip);
    const humidity = stats.map(s => s.avgHumidity);
    const rainDays = stats.map(s => s.rainDays);
    const sunnyDays = stats.map(s => s.sunnyDays);
    const cloudyDays = stats.map(s => s.cloudyDays);

    // 温度趋势图
    const tempChartDom = document.getElementById('tempChart');
    if (tempChartDom) {
        const tempChart = echarts.init(tempChartDom);
        tempChart.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['平均温度', '最高温度', '最低温度'], top: 10 },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
            xAxis: { type: 'category', data: months },
            yAxis: { type: 'value', name: '温度(℃)' },
            series: [
                { name: '平均温度', type: 'line', data: avgTemp, smooth: true, lineStyle: { width: 3 }, itemStyle: { color: '#4A90D9' } },
                { name: '最高温度', type: 'line', data: maxTemp, smooth: true, lineStyle: { width: 2, type: 'dashed' }, itemStyle: { color: '#E74C3C' } },
                { name: '最低温度', type: 'line', data: minTemp, smooth: true, lineStyle: { width: 2, type: 'dashed' }, itemStyle: { color: '#3498DB' } }
            ]
        });
        window.addEventListener('resize', () => tempChart.resize());
    }

    // 降水量柱状图
    const precipChartDom = document.getElementById('precipChart');
    if (precipChartDom) {
        const precipChart = echarts.init(precipChartDom);
        precipChart.setOption({
            tooltip: { trigger: 'axis' },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
            xAxis: { type: 'category', data: months },
            yAxis: { type: 'value', name: '降水量(mm)' },
            series: [{
                name: '月降水量',
                type: 'bar',
                data: precip,
                itemStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: '#6DB3F2' },
                        { offset: 1, color: '#4A90D9' }
                    ]),
                    borderRadius: [6, 6, 0, 0]
                }
            }]
        });
        window.addEventListener('resize', () => precipChart.resize());
    }

    // 天气类型分布饼图
    const pieChartDom = document.getElementById('weatherPieChart');
    if (pieChartDom) {
        const totalRain = rainDays.reduce((a, b) => a + b, 0);
        const totalSunny = sunnyDays.reduce((a, b) => a + b, 0);
        const totalCloudy = cloudyDays.reduce((a, b) => a + b, 0);

        const pieChart = echarts.init(pieChartDom);
        pieChart.setOption({
            tooltip: { trigger: 'item', formatter: '{b}: {c}天 ({d}%)' },
            legend: { bottom: 10 },
            series: [{
                type: 'pie',
                radius: ['40%', '65%'],
                avoidLabelOverlap: false,
                itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                label: { show: true, formatter: '{b}\n{c}天' },
                data: [
                    { value: totalSunny, name: '晴天', itemStyle: { color: '#FFD700' } },
                    { value: totalCloudy, name: '多云/阴', itemStyle: { color: '#87CEEB' } },
                    { value: totalRain, name: '雨天', itemStyle: { color: '#4682B4' } }
                ]
            }]
        });
        window.addEventListener('resize', () => pieChart.resize());
    }

    // 湿度折线图
    const humidityChartDom = document.getElementById('humidityChart');
    if (humidityChartDom) {
        const humidityChart = echarts.init(humidityChartDom);
        humidityChart.setOption({
            tooltip: { trigger: 'axis' },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
            xAxis: { type: 'category', data: months },
            yAxis: { type: 'value', name: '湿度(%)', max: 100 },
            series: [{
                name: '平均湿度',
                type: 'line',
                data: humidity,
                smooth: true,
                lineStyle: { width: 3, color: '#2ECC71' },
                itemStyle: { color: '#2ECC71' },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(46, 204, 113, 0.3)' },
                        { offset: 1, color: 'rgba(46, 204, 113, 0.05)' }
                    ])
                }
            }]
        });
        window.addEventListener('resize', () => humidityChart.resize());
    }
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
