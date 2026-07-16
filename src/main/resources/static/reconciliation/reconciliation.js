(() => {
    'use strict';

    const API = {
        museums: '/system/museum/findAll',
        reconciliation: '/trip/union-pay-data/reconciliation/abnormal'
    };

    const state = {
        result: null,
        activeGroup: null,
        detailIndex: new Map(),
        museumNames: new Map(),
        demoMode: false
    };

    const el = id => document.getElementById(id);
    const form = el('query-form');
    const museumSelect = el('museum-select');
    const startDateInput = el('start-date');
    const endDateInput = el('end-date');
    const queryButton = el('query-button');
    const queryMessage = el('query-message');
    const resultContent = el('result-content');
    const initialState = el('initial-state');
    const loadingState = el('loading-state');
    const showEmptyToggle = el('show-empty-toggle');

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function toDateInputValue(date) {
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    function initializeDates() {
        const today = new Date();
        startDateInput.value = toDateInputValue(new Date(today.getFullYear(), today.getMonth(), 1));
        endDateInput.value = toDateInputValue(today);
        startDateInput.max = endDateInput.value;
    }

    function requestHeaders() {
        const headers = { 'Content-Type': 'application/json' };
        // 兼容项目常见的Sa-Token本地存储方式；Cookie登录时浏览器会自动携带凭证。
        const token = localStorage.getItem('sa-token') || sessionStorage.getItem('sa-token');
        if (token) headers['sa-token'] = token;
        return headers;
    }

    async function request(url, options = {}) {
        const response = await fetch(url, {
            credentials: 'same-origin',
            ...options,
            headers: { ...requestHeaders(), ...(options.headers || {}) }
        });
        let payload;
        try {
            payload = await response.json();
        } catch (_) {
            throw new Error(`服务返回了无法识别的内容（HTTP ${response.status}）`);
        }
        if (!response.ok || payload.code !== 200) {
            throw new Error(payload.msg || `请求失败（HTTP ${response.status}）`);
        }
        return payload.data;
    }

    async function loadMuseums() {
        try {
            const museums = await request(API.museums, { method: 'GET' });
            const list = Array.isArray(museums) ? museums : [];
            state.museumNames.clear();
            museumSelect.innerHTML = '<option value="">请选择博物馆</option>';
            museumSelect.disabled = false;
            list.forEach(museum => {
                const option = document.createElement('option');
                option.value = museum.id;
                option.textContent = museum.name || `博物馆 ${museum.id}`;
                museumSelect.appendChild(option);
                state.museumNames.set(String(museum.id), option.textContent);
            });
            if (list.length === 1) museumSelect.value = String(list[0].id);
            if (!list.length) museumSelect.disabled = true;
        } catch (error) {
            museumSelect.innerHTML = '<option value="">博物馆列表加载失败</option>';
            museumSelect.disabled = true;
        }
    }

    function selectedMuseumId() {
        return museumSelect.value;
    }

    function validateQuery() {
        const museumId = selectedMuseumId();
        const startDate = startDateInput.value;
        const endDate = endDateInput.value;
        if (!museumId) return '请选择博物馆。';
        if (!startDate || !endDate) return '请选择完整的开始和结束日期。';
        if (endDate < startDate) return '结束日期不能早于开始日期。';
        return '';
    }

    function showQueryMessage(message) {
        queryMessage.textContent = message;
        queryMessage.classList.toggle('hidden', !message);
    }

    function setLoading(loading) {
        queryButton.disabled = loading;
        queryButton.innerHTML = loading
            ? '<span class="loader-mini">·</span> 正在核对'
            : '<span class="button-icon" aria-hidden="true">↗</span> 开始核对';
        initialState.classList.add('hidden');
        resultContent.classList.add('hidden');
        loadingState.classList.toggle('hidden', !loading);
    }

    async function submitQuery(event) {
        event.preventDefault();
        const validationMessage = validateQuery();
        if (validationMessage) {
            showQueryMessage(validationMessage);
            return;
        }
        showQueryMessage('');
        setLoading(true);
        state.demoMode = false;
        try {
            const result = await request(API.reconciliation, {
                method: 'POST',
                body: JSON.stringify({
                    museumId: selectedMuseumId(),
                    startDate: startDateInput.value,
                    endDate: endDateInput.value
                })
            });
            renderResult(result);
        } catch (error) {
            loadingState.classList.add('hidden');
            initialState.classList.remove('hidden');
            showQueryMessage(error.message || '查询失败，请稍后重试。');
        } finally {
            queryButton.disabled = false;
            queryButton.innerHTML = '<span class="button-icon" aria-hidden="true">↗</span> 开始核对';
        }
    }

    function formatMoney(cents, signed = false) {
        const value = Number(cents || 0);
        const prefix = value < 0 ? '-' : signed && value > 0 ? '+' : '';
        return `${prefix}¥${(Math.abs(value) / 100).toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        })}`;
    }

    function moneyClass(value) {
        const number = Number(value || 0);
        return number > 0 ? 'positive' : number < 0 ? 'negative' : '';
    }

    function formatDate(value) {
        return value ? String(value).replace('T', ' ') : '—';
    }

    function orderStatusText(status) {
        const statusMap = {
            1: '支付中',
            10: '支付成功',
            '-1': '放弃支付',
            '-2': '部分退款',
            '-10': '全额退款',
            '-11': '退款中'
        };
        if (status === null || status === undefined || status === '') return '未设置';
        return statusMap[String(status)] || `未知状态（${status}）`;
    }

    function verificationStatusText(isUsed) {
        if (Number(isUsed) === 1) return '已核销';
        if (Number(isUsed) === 0) return '未核销';
        if (isUsed === null || isUsed === undefined || isUsed === '') return '未设置';
        return `未知状态（${isUsed}）`;
    }

    function orderTypeText(orderType) {
        if (Number(orderType) === 1) return '个人订单';
        if (Number(orderType) === 2) return '团队订单';
        if (orderType === null || orderType === undefined || orderType === '') return '未设置';
        return `未知类型（${orderType}）`;
    }

    function orderDetailStatusText(status) {
        const statusMap = {
            '-1': '放弃支付',
            0: '初始状态',
            10: '支付成功',
            '-2': '已退款',
            '-11': '退款中'
        };
        if (status === null || status === undefined || status === '') return '未设置';
        return statusMap[String(status)] || `未知状态（${status}）`;
    }

    function escapeHtml(value) {
        return String(value ?? '—')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function anomalyStats(groups) {
        const categories = (groups || []).flatMap(group => group.categories || []);
        const details = categories.reduce((sum, category) => sum + Number(category.orderCount || 0), 0);
        const activeCategories = categories.filter(category => Number(category.orderCount || 0) > 0).length;
        const tradeNos = new Set();
        categories.forEach(category => (category.details || []).forEach(detail => {
            if (detail.tradeNo) tradeNos.add(detail.tradeNo);
        }));
        return { details, activeCategories, uniqueOrders: tradeNos.size };
    }

    function renderResult(result) {
        state.result = result || {};
        state.detailIndex.clear();
        loadingState.classList.add('hidden');
        initialState.classList.add('hidden');
        resultContent.classList.remove('hidden');

        const balance = state.result.balance || {};
        const stats = anomalyStats(state.result.groups);
        const museumId = String(state.result.museumId || selectedMuseumId() || '—');
        const museumName = state.museumNames.get(museumId) || `博物馆 ${museumId}`;
        el('result-period').textContent = `${state.result.startDate || '—'}  至  ${state.result.endDate || '—'}`;
        el('museum-badge').textContent = museumName;
        el('result-summary').textContent = stats.uniqueOrders
            ? `发现 ${stats.uniqueOrders} 个异常订单，分布在 ${stats.activeCategories} 个异常分类中。`
            : '所选核对区间没有发现异常订单。';

        renderBalance(balance);
        renderMetrics(balance);
        renderCoverage(state.result.sourceControl || {});
        renderBilling(state.result.billing || {});
        renderGroups(state.result.groups || []);
        resultContent.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function renderBalance(balance) {
        const balanced = Boolean(balance.balanced);
        const banner = el('balance-banner');
        banner.classList.toggle('unbalanced', !balanced);
        banner.querySelector('.balance-symbol').textContent = balanced ? '✓' : '!';
        el('balance-title').textContent = balanced ? '账目已完全平衡' : '账目尚未完全平衡';
        el('balance-description').textContent = balanced
            ? '系统有效核销金额与调整后银联净额一致，原始数据已完整处理。'
            : '仍存在金额差额、未归类数据或数据覆盖问题，请检查下方异常明细。';
        el('balance-difference').textContent = formatMoney(balance.balanceDifference, true);

        el('formula-pay').textContent = formatMoney(balance.unionPayAmount);
        el('formula-refund').textContent = formatMoney(balance.unionRefundAmount);
        el('formula-adjustment').textContent = formatMoney(balance.abnormalAdjustmentAmount, true);
        el('formula-system').textContent = formatMoney(balance.systemVerificationAmount);
        const formulaStatus = el('formula-status');
        formulaStatus.textContent = balanced ? '已闭环' : '未闭环';
        formulaStatus.className = `status-pill ${balanced ? 'success' : 'danger'}`;
    }

    function renderMetrics(balance) {
        el('system-amount').textContent = formatMoney(balance.systemVerificationAmount);
        el('system-quantity').textContent = Number(balance.systemVerificationQuantity || 0).toLocaleString('zh-CN');
        el('union-pay-amount').textContent = formatMoney(balance.unionPayAmount);
        el('union-pay-count').textContent = Number(balance.unionPayCount || 0).toLocaleString('zh-CN');
        el('union-refund-amount').textContent = formatMoney(balance.unionRefundAmount);
        el('union-refund-count').textContent = Number(balance.unionRefundCount || 0).toLocaleString('zh-CN');
        el('adjustment-amount').textContent = formatMoney(balance.abnormalAdjustmentAmount, true);
    }

    function renderCoverage(source) {
        const rows = [
            ['系统有效核销', source.systemVerificationDetailCount, source.classifiedSystemVerificationDetailCount],
            ['系统退款明细', source.systemRefundDetailCount, source.classifiedSystemRefundDetailCount],
            ['银联消费流水', source.unionPayRowCount, source.classifiedUnionPayRowCount],
            ['银联退款流水', source.unionRefundRowCount, source.classifiedUnionRefundRowCount]
        ];
        el('coverage-grid').innerHTML = rows.map(([label, total, handled]) => {
            const covered = Number(total || 0) === Number(handled || 0);
            return `<article class="coverage-item">
                <div class="coverage-label"><span>${escapeHtml(label)}</span><span class="coverage-check">${covered ? '✓' : '!'}</span></div>
                <strong>${Number(handled || 0).toLocaleString('zh-CN')} / ${Number(total || 0).toLocaleString('zh-CN')}</strong>
                <small>${covered ? '全部进入对账计算' : '存在未处理数据'}</small>
            </article>`;
        }).join('');
        const complete = rows.every(([, total, handled]) => Number(total || 0) === Number(handled || 0))
            && Number(source.unclassifiedCount || 0) === 0
            && Number(source.duplicateClassificationCount || 0) === 0;
        const status = el('coverage-status');
        status.textContent = complete ? '全部处理' : '需要检查';
        status.className = `status-pill ${complete ? 'success' : 'danger'}`;
    }

    function renderBilling(billing) {
        const details = Array.isArray(billing.details) ? billing.details : [];
        el('billing-total-quantity').textContent =
            Number(billing.totalQuantity || 0).toLocaleString('zh-CN');
        el('billing-total-amount').textContent = formatMoney(billing.totalAmount);
        el('billing-table-wrap').classList.toggle('hidden', details.length === 0);
        el('billing-empty').classList.toggle('hidden', details.length !== 0);
        el('billing-table-body').innerHTML = details.map(item => `<tr>
            <td>${escapeHtml(item.verificationMonth)}</td>
            <td>${escapeHtml(item.activityName)}</td>
            <td>${escapeHtml(item.activityId)}</td>
            <td>${formatMoney(item.activityPrice)}</td>
            <td>${Number(item.quantity || 0).toLocaleString('zh-CN')}</td>
            <td class="money">${formatMoney(item.amount)}</td>
        </tr>`).join('');
    }

    function renderGroups(groups) {
        const visibleGroups = groups.filter(group => showEmptyToggle.checked
            || (group.categories || []).some(category => Number(category.orderCount || 0) > 0));
        const stats = anomalyStats(groups);
        el('anomaly-summary').textContent = stats.uniqueOrders
            ? `${stats.uniqueOrders} 个异常订单 · ${stats.details} 条分类记录（同一订单可命中多个标签）`
            : '当前核对区间未发现异常数据';
        el('no-anomaly-state').classList.toggle('hidden', stats.details !== 0 || showEmptyToggle.checked);

        if (!visibleGroups.length) {
            el('group-tabs').innerHTML = '';
            el('anomaly-groups').innerHTML = '';
            return;
        }
        if (!visibleGroups.some(group => group.groupCode === state.activeGroup)) {
            state.activeGroup = visibleGroups[0].groupCode;
        }
        el('group-tabs').innerHTML = visibleGroups.map(group => {
            const count = (group.categories || []).reduce((sum, item) => sum + Number(item.orderCount || 0), 0);
            return `<button class="group-tab ${group.groupCode === state.activeGroup ? 'active' : ''}"
                type="button" data-group-code="${escapeHtml(group.groupCode)}">
                ${escapeHtml(group.groupName)}<span class="group-tab-count">${count}</span>
            </button>`;
        }).join('');
        el('anomaly-groups').innerHTML = visibleGroups.map(group => renderGroup(group)).join('');
    }

    function renderGroup(group) {
        const categories = (group.categories || []).filter(category => showEmptyToggle.checked
            || Number(category.orderCount || 0) > 0);
        return `<div class="anomaly-group ${group.groupCode === state.activeGroup ? 'active' : ''}"
            data-group-panel="${escapeHtml(group.groupCode)}">
            ${categories.map(category => renderCategory(group, category)).join('')}
        </div>`;
    }

    function renderCategory(group, category) {
        const details = category.details || [];
        const empty = details.length === 0;
        return `<article class="category-card ${empty ? 'empty-category' : ''}">
            <button class="category-header" type="button" data-toggle-category ${empty ? 'disabled' : ''}>
                <span class="category-accent"></span>
                <span class="category-title">
                    <strong>${escapeHtml(category.anomalyName)}</strong>
                    <span>${escapeHtml(category.anomalyCode)}</span>
                </span>
                <span class="category-count"><strong>${Number(category.orderCount || 0)}</strong> 条</span>
                <span class="category-adjustment">
                    <span>分类调整</span>
                    <strong class="${moneyClass(category.adjustmentAmount)}">${formatMoney(category.adjustmentAmount, true)}</strong>
                </span>
                <span class="chevron">⌄</span>
            </button>
            ${empty ? '<div class="empty-category-copy">当前区间无此类异常</div>' : `
            <div class="category-body">
                <table class="detail-table">
                    <thead><tr>
                        <th>银联交易号</th><th>异常原因</th><th>系统有效核销</th>
                        <th>银联净额</th><th>调整金额</th><th>退款核对</th><th></th>
                    </tr></thead>
                    <tbody>${details.map((detail, index) => renderDetailRow(group, category, detail, index)).join('')}</tbody>
                </table>
            </div>`}
        </article>`;
    }

    function renderDetailRow(group, category, detail, index) {
        const key = `${group.groupCode}:${category.anomalyCode}:${index}`;
        state.detailIndex.set(key, { group, category, detail });
        const system = detail.system || {};
        const union = detail.union || {};
        const unionNet = Number(union.payAmount || 0) - Number(union.refundAmount || 0);
        const refundCheck = detail.refundCheck || {};
        return `<tr>
            <td><span class="trade-no">${escapeHtml(detail.tradeNo || '无交易号')}</span></td>
            <td class="reason-cell">${escapeHtml(detail.explanation)}</td>
            <td class="money">${formatMoney(system.validVerificationAmount)}</td>
            <td class="money">${formatMoney(unionNet)}</td>
            <td class="money ${moneyClass(detail.adjustmentAmount)}">${formatMoney(detail.adjustmentAmount, true)}</td>
            <td><span class="match-mark ${refundCheck.matched ? '' : 'mismatch'}">${refundCheck.matched ? '✓ 一致' : '！不一致'}</span></td>
            <td><button class="detail-button" type="button" data-detail-key="${escapeHtml(key)}">查看详情</button></td>
        </tr>`;
    }

    function renderSubtable(title, rows, columns) {
        if (!rows || !rows.length) return `<p class="section-help">${escapeHtml(title)}：无</p>`;
        return `<h4>${escapeHtml(title)}</h4><div class="subtable-wrap"><table class="subtable"><thead><tr>${columns.map(column => `<th>${escapeHtml(column.label)}</th>`).join('')}</tr></thead>
            <tbody>${rows.map(row => `<tr>${columns.map(column => `<td>${escapeHtml(column.format ? column.format(row[column.key]) : row[column.key])}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
    }

    function openDetail(key) {
        const entry = state.detailIndex.get(key);
        if (!entry) return;
        const { category, detail } = entry;
        const system = detail.system || {};
        const union = detail.union || {};
        const refund = detail.refundCheck || {};
        el('modal-code').textContent = category.anomalyCode;
        el('modal-title').textContent = category.anomalyName;
        el('modal-body').innerHTML = `
            <section class="detail-hero">
                <div class="detail-hero-line">
                    <div><span>银联交易号</span><strong>${escapeHtml(detail.tradeNo || '无交易号')}</strong></div>
                    <div><span>本分类调整金额</span><strong>${formatMoney(detail.adjustmentAmount, true)}</strong></div>
                </div>
                <p>${escapeHtml(detail.explanation)}</p>
                ${(detail.relatedAnomalyCodes || []).length ? `<div class="tag-list">${detail.relatedAnomalyCodes.map(code => `<span class="code-tag">${escapeHtml(code)}</span>`).join('')}</div>` : ''}
            </section>
            <section class="detail-section">
                <h3>系统订单</h3>
                <div class="detail-grid">
                    ${detailField('系统订单', system.orderExists ? '已找到' : '不存在')}
                    ${detailField('订单号', system.orderNo)}
                    ${detailField('订单ID', system.orderId)}
                    ${detailField('博物馆ID', system.museumId)}
                    ${detailField('订单类型', orderTypeText(system.orderType))}
                    ${detailField('购买数量', system.orderQuantity)}
                    ${detailField('支付金额', formatMoney(system.payAmount))}
                    ${detailField('支付时间', formatDate(system.paySuccessTime), true)}
                    ${detailField('预约日期', system.appointmentDate)}
                    ${detailField('核销时间', formatDate(system.verificationTime), true)}
                    ${detailField('有效核销数量', system.validVerificationQuantity)}
                    ${detailField('有效核销金额', formatMoney(system.validVerificationAmount))}
                    ${detailField('核对期退款', `${system.periodRefundCount || 0} 笔 / ${formatMoney(system.periodRefundAmount)}`)}
                    ${detailField('累计退款金额', formatMoney(system.cumulativeRefundAmount))}
                    ${detailField('主订单退款', `${system.refundQuantity || 0} 笔 / ${formatMoney(system.orderRefundAmount)}`)}
                    ${detailField('订单状态 / 核销状态',
                        `${orderStatusText(system.orderStatus)} / ${verificationStatusText(system.isUsed)}`)}
                    ${detailField('团队ID', system.teamId)}
                    ${detailField('游客ID', system.visitorId)}
                    ${detailField('游客批次号', system.batchNo)}
                    ${detailField('创建时间', formatDate(system.createTime), true)}
                    ${detailField('更新时间', formatDate(system.updateTime), true)}
                </div>
                ${renderSubtable('全部子订单', system.orderDetails, [
                    { key: 'orderDetailId', label: '子订单ID' },
                    { key: 'orderNo', label: '子订单号' },
                    { key: 'activityName', label: '活动名称' },
                    { key: 'activityPrice', label: '活动单价', format: formatMoney },
                    { key: 'activityScheduleId', label: '场次ID' },
                    { key: 'orderAmount', label: '订单金额', format: formatMoney },
                    { key: 'orderStatus', label: '状态', format: orderDetailStatusText },
                    { key: 'refundAmount', label: '退款金额', format: formatMoney },
                    { key: 'refundTime', label: '退款时间', format: formatDate }
                ])}
                ${renderSubtable('系统退款明细', system.refundDetails, [
                    { key: 'orderDetailId', label: '子订单ID' },
                    { key: 'refundId', label: '退款编号' },
                    { key: 'refundTime', label: '退款时间', format: formatDate },
                    { key: 'refundAmount', label: '退款金额', format: formatMoney }
                ])}
            </section>
            <section class="detail-section">
                <h3>银联流水</h3>
                <div class="detail-grid">
                    ${detailField('核对期消费', `${union.payCount || 0} 笔 / ${formatMoney(union.payAmount)}`)}
                    ${detailField('区间外消费', `${union.historicalPayCount || 0} 笔 / ${formatMoney(union.historicalPayAmount)}`)}
                    ${detailField('核对期退款', `${union.refundCount || 0} 笔 / ${formatMoney(union.refundAmount)}`)}
                    ${detailField('区间外退款', `${union.historicalRefundCount || 0} 笔 / ${formatMoney(union.historicalRefundAmount)}`)}
                </div>
                ${renderSubtable('核对期消费明细', union.paymentDetails, [
                    { key: 'id', label: '流水ID' },
                    { key: 'transactionTime', label: '交易时间', format: formatDate },
                    { key: 'amount', label: '消费金额', format: formatMoney },
                    { key: 'handlingCharge', label: '手续费', format: formatMoney },
                    { key: 'netAmount', label: '净额', format: formatMoney },
                    { key: 'referenceNumber', label: '参考号' },
                    { key: 'settlementDate', label: '结算日期' },
                    { key: 'channel', label: '交易渠道' },
                    { key: 'cardNo', label: '卡号' },
                    { key: 'mid', label: '商户号' },
                    { key: 'tid', label: '终端号' },
                    { key: 'unionOrderNo', label: '银商订单号' }
                ])}
                ${renderSubtable('区间外消费明细', union.historicalPaymentDetails, [
                    { key: 'id', label: '流水ID' },
                    { key: 'transactionTime', label: '交易时间', format: formatDate },
                    { key: 'amount', label: '消费金额', format: formatMoney },
                    { key: 'handlingCharge', label: '手续费', format: formatMoney },
                    { key: 'netAmount', label: '净额', format: formatMoney },
                    { key: 'referenceNumber', label: '参考号' },
                    { key: 'settlementDate', label: '结算日期' },
                    { key: 'channel', label: '交易渠道' },
                    { key: 'cardNo', label: '卡号' },
                    { key: 'mid', label: '商户号' },
                    { key: 'tid', label: '终端号' },
                    { key: 'unionOrderNo', label: '银商订单号' }
                ])}
                ${renderSubtable('银联退款明细', union.refundDetails, [
                    { key: 'id', label: '流水ID' },
                    { key: 'transactionTime', label: '退款时间', format: formatDate },
                    { key: 'refundAmount', label: '退款金额', format: formatMoney },
                    { key: 'handlingCharge', label: '手续费', format: formatMoney },
                    { key: 'netAmount', label: '净额', format: formatMoney },
                    { key: 'referenceNumber', label: '参考号' },
                    { key: 'settlementDate', label: '结算日期' },
                    { key: 'channel', label: '交易渠道' },
                    { key: 'cardNo', label: '卡号' },
                    { key: 'mid', label: '商户号' },
                    { key: 'tid', label: '终端号' },
                    { key: 'unionOrderNo', label: '银商订单号' }
                ])}
                ${renderSubtable('区间外退款明细', union.historicalRefundDetails, [
                    { key: 'id', label: '流水ID' },
                    { key: 'transactionTime', label: '退款时间', format: formatDate },
                    { key: 'refundAmount', label: '退款金额', format: formatMoney },
                    { key: 'handlingCharge', label: '手续费', format: formatMoney },
                    { key: 'netAmount', label: '净额', format: formatMoney },
                    { key: 'referenceNumber', label: '参考号' },
                    { key: 'settlementDate', label: '结算日期' },
                    { key: 'channel', label: '交易渠道' },
                    { key: 'cardNo', label: '卡号' },
                    { key: 'mid', label: '商户号' },
                    { key: 'tid', label: '终端号' },
                    { key: 'unionOrderNo', label: '银商订单号' }
                ])}
            </section>
            <section class="detail-section">
                <h3>退款金额核对</h3>
                <div class="detail-grid">
                    ${detailField('系统退款', formatMoney(refund.systemRefundAmount))}
                    ${detailField('银联退款', formatMoney(refund.unionRefundAmount))}
                    ${detailField('退款差额', formatMoney(refund.differenceAmount, true))}
                </div>
                <p class="section-help">核对结果：${refund.matched ? '两边退款金额一致' : '两边退款金额不一致，需要进一步处理'}</p>
            </section>`;
        el('detail-modal').classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    }

    function detailField(label, value, wide = false) {
        return `<div class="detail-field ${wide ? 'wide' : ''}"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
    }

    function closeModal() {
        el('detail-modal').classList.add('hidden');
        document.body.style.overflow = '';
    }

    function downloadJson() {
        if (!state.result) return;
        const blob = new Blob([JSON.stringify(state.result, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `异常对账_${state.result.startDate}_${state.result.endDate}.json`;
        anchor.click();
        URL.revokeObjectURL(url);
        showToast('JSON 文件已生成');
    }

    let toastTimer;
    function showToast(message) {
        const toast = el('toast');
        toast.textContent = message;
        toast.classList.remove('hidden');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toast.classList.add('hidden'), 2200);
    }

    function renderDemo() {
        state.demoMode = true;
        renderResult(buildDemoResult());
        showToast('当前展示的是界面示例数据');
    }

    function buildDemoResult() {
        const emptyCategory = (code, name) => ({ anomalyCode: code, anomalyName: name, orderCount: 0, adjustmentAmount: 0, details: [] });
        const detail = {
            tradeNo: '202601081735002184',
            explanation: '订单在核对区间前支付，核对期完成核销',
            adjustmentAmount: 12800,
            relatedAnomalyCodes: [],
            system: {
                orderExists: true, orderId: 28351, orderNo: 'ET202512180038', orderStatus: 10, isUsed: 1,
                museumId: 1, orderType: 1, orderQuantity: 2, payAmount: 12800, orderRefundAmount: 0,
                refundQuantity: 0, visitorId: 5192, batchNo: 'VIS20251218001',
                createTime: '2025-12-18 14:31:10', updateTime: '2026-01-08 09:42:11',
                paySuccessTime: '2025-12-18 14:31:22', appointmentDate: '2026-01-08',
                verificationTime: '2026-01-08 09:42:11', validVerificationQuantity: 2,
                validVerificationAmount: 12800, periodRefundCount: 0, periodRefundAmount: 0,
                cumulativeRefundAmount: 0, refundDetails: [],
                orderDetails: [
                    { orderDetailId: 77101, orderId: 28351, orderNo: 'ET202512180038-1',
                        museumId: 1, activityId: 13, activityName: '博物馆研学课程', activityPrice: 6400,
                        activityScheduleId: 106, orderAmount: 6400, refundAmount: 0, orderStatus: 10 },
                    { orderDetailId: 77102, orderId: 28351, orderNo: 'ET202512180038-2',
                        museumId: 1, activityId: 13, activityName: '博物馆研学课程', activityPrice: 6400,
                        activityScheduleId: 106, orderAmount: 6400, refundAmount: 0, orderStatus: 10 }
                ]
            },
            union: {
                paymentExists: false, payCount: 0, payAmount: 0, paymentDetails: [], historicalPayCount: 1,
                historicalPayAmount: 12800, historicalPaymentDetails: [
                    { id: 9801, transactionTime: '2025-12-18 14:31:26', amount: 12800,
                        handlingCharge: 30, netAmount: 12770, referenceNumber: 'REF251218001',
                        settlementDate: '2025-12-18', channel: '微信', cardNo: '****',
                        mid: '898102100002649', tid: 'KF994K4C', unionOrderNo: 'UMS251218001' }
                ], refundCount: 0, refundAmount: 0,
                refundDetails: [], historicalRefundCount: 0, historicalRefundAmount: 0,
                historicalRefundDetails: []
            },
            refundCheck: { systemRefundAmount: 0, unionRefundAmount: 0, differenceAmount: 0, matched: true }
        };
        const refundDetail = {
            tradeNo: '202601160928004812',
            explanation: '核对区间前支付或核销的订单在核对期发生退款',
            adjustmentAmount: 3000,
            relatedAnomalyCodes: [],
            system: {
                orderExists: true, orderId: 28110, orderNo: 'ET202512060115', orderStatus: -2, isUsed: 1,
                museumId: 1, orderType: 1, orderQuantity: 2, payAmount: 10000, orderRefundAmount: 3000,
                refundQuantity: 2, visitorId: 5098, createTime: '2025-12-06 10:20:02',
                updateTime: '2026-01-16 09:30:42', paySuccessTime: '2025-12-06 10:20:18',
                appointmentDate: '2025-12-20',
                verificationTime: '2025-12-20 15:08:09', validVerificationQuantity: 0,
                validVerificationAmount: 0, periodRefundCount: 2, periodRefundAmount: 3000,
                cumulativeRefundAmount: 3000,
                orderDetails: [
                    { orderDetailId: 77103, orderId: 28110, orderNo: 'ET202512060115-1',
                        activityName: '专题讲解活动', activityPrice: 5000, activityScheduleId: 88,
                        orderAmount: 5000, refundAmount: 1000, orderStatus: -2,
                        refundTime: '2026-01-16 09:28:10' },
                    { orderDetailId: 77104, orderId: 28110, orderNo: 'ET202512060115-2',
                        activityName: '专题讲解活动', activityPrice: 5000, activityScheduleId: 88,
                        orderAmount: 5000, refundAmount: 2000, orderStatus: -2,
                        refundTime: '2026-01-16 09:30:42' }
                ],
                refundDetails: [
                    { orderDetailId: 77103, refundId: 'R260116001', refundTime: '2026-01-16 09:28:10', refundAmount: 1000 },
                    { orderDetailId: 77104, refundId: 'R260116002', refundTime: '2026-01-16 09:30:42', refundAmount: 2000 }
                ]
            },
            union: {
                paymentExists: false, payCount: 0, payAmount: 0, paymentDetails: [], historicalPayCount: 1,
                historicalPayAmount: 10000, historicalPaymentDetails: [], refundCount: 2, refundAmount: 3000,
                refundDetails: [
                    { id: 9911, transactionTime: '2026-01-16 09:28:14', refundAmount: 1000, referenceNumber: 'REF260116001' },
                    { id: 9912, transactionTime: '2026-01-16 09:30:48', refundAmount: 2000, referenceNumber: 'REF260116002' }
                ], historicalRefundCount: 0, historicalRefundAmount: 0, historicalRefundDetails: []
            },
            refundCheck: { systemRefundAmount: 3000, unionRefundAmount: 3000, differenceAmount: 0, matched: true }
        };
        return {
            museumId: '1', startDate: '2026-01-01', endDate: '2026-01-18', amountUnit: 'CENT',
            balance: {
                systemVerificationQuantity: 126, systemVerificationAmount: 849600, systemRefundCount: 2,
                systemRefundAmount: 3000, unionPayCount: 119, unionPayAmount: 836800, unionRefundCount: 2,
                unionRefundAmount: 3000, unionNetAmount: 833800, abnormalAdjustmentAmount: 15800,
                adjustedUnionNetAmount: 849600, balanceDifference: 0, balanced: true
            },
            sourceControl: {
                systemVerificationDetailCount: 126, classifiedSystemVerificationDetailCount: 126,
                systemRefundDetailCount: 2, classifiedSystemRefundDetailCount: 2,
                unionPayRowCount: 119, classifiedUnionPayRowCount: 119,
                unionRefundRowCount: 2, classifiedUnionRefundRowCount: 2,
                unclassifiedCount: 0, duplicateClassificationCount: 0
            },
            billing: {
                totalQuantity: 126,
                totalAmount: 849600,
                details: [
                    { verificationMonth: '2026-01', activityId: 13, activityName: '博物馆研学课程',
                        activityPrice: 6400, quantity: 54, amount: 345600 },
                    { verificationMonth: '2026-01', activityId: 19, activityName: '专题讲解活动',
                        activityPrice: 7000, quantity: 72, amount: 504000 }
                ]
            },
            groups: [
                { groupCode: 'PAYMENT_VERIFICATION', groupName: '支付与核销异常', categories: [
                    { anomalyCode: 'HISTORICAL_PAY_CURRENT_VERIFICATION', anomalyName: '区间前支付、核对期核销', orderCount: 1, adjustmentAmount: 12800, details: [detail] },
                    emptyCategory('CROSS_MONTH_APPOINTMENT', '核对期支付、跨期预约'),
                    emptyCategory('PAY_AMOUNT_MISMATCH', '支付金额不一致')
                ]},
                { groupCode: 'REFUND', groupName: '退款异常', categories: [
                    { anomalyCode: 'CROSS_MONTH_REFUND', anomalyName: '区间前支付或核销、核对期退款', orderCount: 1, adjustmentAmount: 3000, details: [refundDetail] },
                    emptyCategory('REFUND_AMOUNT_MISMATCH', '退款金额不一致'),
                    emptyCategory('UNION_REFUND_ONLY', '银联有退款、系统无退款')
                ]},
                { groupCode: 'DATA_INTEGRITY', groupName: '订单数据异常', categories: [
                    emptyCategory('SYSTEM_TRADE_NO_MISSING', '系统订单缺少银联订单号'),
                    emptyCategory('ORDER_REFUND_SUM_MISMATCH', '主子订单退款汇总不一致')
                ]},
                { groupCode: 'IMPORT_QUALITY', groupName: '银联账单数据异常', categories: [
                    emptyCategory('DUPLICATE_UNION_REFERENCE', '银联参考号重复')
                ]}
            ]
        };
    }

    form.addEventListener('submit', submitQuery);
    startDateInput.addEventListener('change', () => {
        endDateInput.min = startDateInput.value;
        showQueryMessage('');
    });
    endDateInput.addEventListener('change', () => {
        startDateInput.max = endDateInput.value;
        showQueryMessage('');
    });
    el('demo-button').addEventListener('click', renderDemo);
    el('refresh-button').addEventListener('click', () => form.requestSubmit());
    el('export-button').addEventListener('click', downloadJson);
    showEmptyToggle.addEventListener('change', () => state.result && renderGroups(state.result.groups || []));
    el('group-tabs').addEventListener('click', event => {
        const button = event.target.closest('[data-group-code]');
        if (!button) return;
        state.activeGroup = button.dataset.groupCode;
        renderGroups(state.result.groups || []);
    });
    el('anomaly-groups').addEventListener('click', event => {
        const detailButton = event.target.closest('[data-detail-key]');
        if (detailButton) {
            openDetail(detailButton.dataset.detailKey);
            return;
        }
        const header = event.target.closest('[data-toggle-category]');
        if (header && !header.disabled) header.closest('.category-card').classList.toggle('open');
    });
    document.querySelectorAll('[data-close-modal]').forEach(node => node.addEventListener('click', closeModal));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeModal();
    });

    initializeDates();
    loadMuseums();
})();
