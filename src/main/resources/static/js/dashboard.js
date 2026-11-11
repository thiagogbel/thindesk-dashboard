/*
 * Thindesk - Dashboard Administrativo
 * Consome a API REST /api/v1/dashboard/* (RF06), aplica os filtros (RF05)
 * e atualiza os dados automaticamente a cada 60s (RF07).
 */
(function () {
    'use strict';

    const API = '/api/v1';
    const INTERVALO_MS = 60000;
    const COR = { s1: '#2a78d6', s2: '#eb6834', grade: '#e9e8e4', texto: '#52514e' };
    const nf = new Intl.NumberFormat('pt-BR');
    const nf1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

    const form = document.getElementById('filtros');
    const graficos = {};
    let timer = null;
    let carregando = false;

    /* ------------------------------------------------------------ */
    /* Filtros                                                      */
    /* ------------------------------------------------------------ */

    function iso(d) {
        const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
        return z.toISOString().slice(0, 10);
    }

    function aplicarPreset(dias) {
        const fim = new Date();
        const inicio = new Date();
        inicio.setDate(fim.getDate() - (dias - 1));
        form.inicio.value = iso(inicio);
        form.fim.value = iso(fim);
        document.querySelectorAll('#presets button').forEach(b =>
            b.classList.toggle('active', Number(b.dataset.dias) === dias));
    }

    /** Monta a query string a partir do formulario (campos vazios sao ignorados). */
    function parametros(extra) {
        const p = new URLSearchParams();
        ['inicio', 'fim', 'status', 'tipo', 'prioridade', 'tecnico', 'setor'].forEach(campo => {
            const v = form[campo].value.trim();
            if (v) p.set(campo, v);
        });
        Object.entries(extra || {}).forEach(([k, v]) => { if (v !== undefined && v !== '') p.set(k, v); });
        return p;
    }

    /** Restaura filtros da URL: o link do dashboard filtrado pode ser compartilhado. */
    function lerUrl() {
        const u = new URLSearchParams(location.search);
        let temData = false;
        ['inicio', 'fim', 'status', 'tipo', 'prioridade', 'tecnico', 'setor', 'granularidade'].forEach(campo => {
            if (u.has(campo) && form[campo]) {
                form[campo].value = u.get(campo);
                if (campo === 'inicio' || campo === 'fim') temData = true;
            }
        });
        if (!temData) aplicarPreset(90);
        else document.querySelectorAll('#presets button').forEach(b => b.classList.remove('active'));
    }

    function gravarUrl() {
        const p = parametros({ granularidade: form.granularidade.value === 'AUTO' ? '' : form.granularidade.value });
        history.replaceState(null, '', location.pathname + (p.toString() ? '?' + p : ''));
    }

    /* ------------------------------------------------------------ */
    /* Acesso a API                                                 */
    /* ------------------------------------------------------------ */

    async function getJson(caminho, params) {
        const url = API + caminho + (params && params.toString() ? '?' + params : '');
        const resp = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
        if (resp.status === 401) {
            location.href = '/login';
            throw new Error('Sessão expirada');
        }
        if (!resp.ok) {
            let msg = 'Erro ' + resp.status;
            try { const e = await resp.json(); if (e && e.mensagem) msg = e.mensagem; } catch (_) { /* corpo nao-JSON */ }
            throw new Error(msg);
        }
        return resp.json();
    }

    async function carregar() {
        if (carregando) return;
        if (form.inicio.value && form.fim.value && form.inicio.value > form.fim.value) {
            mostrarAlerta(['A data inicial não pode ser maior que a data final.']);
            return;
        }
        carregando = true;
        document.querySelector('.content').classList.add('carregando');
        esconderAlerta();
        gravarUrl();

        const p = parametros();
        const pSerie = parametros({ granularidade: form.granularidade.value });
        const pRecentes = parametros({ tamanho: 10, ordenarPor: 'dataAbertura', direcao: 'desc' });
        pRecentes.delete('prioridade');
        pRecentes.delete('setor');

        const tarefas = [
            ['Indicadores', getJson('/dashboard/resumo', p).then(renderResumo)],
            ['Série temporal', getJson('/dashboard/chamados-por-periodo', pSerie).then(renderSerie)],
            ['Status', getJson('/dashboard/status', p).then(renderStatus)],
            ['Tipos', getJson('/dashboard/tipos', parametros({ limite: 10 })).then(renderTipos)],
            ['Técnicos', getJson('/dashboard/tecnicos', parametros({ limite: 10 })).then(renderTecnicos)],
            ['Prioridade', getJson('/dashboard/distribuicao/prioridade', p).then(d => renderBarras('chartPrioridade', d, false))],
            ['Setor', getJson('/dashboard/distribuicao/setor', p).then(d => renderBarras('chartSetor', d, true))],
            ['Recentes', getJson('/chamados', pRecentes).then(renderRecentes)]
        ];
        const resultados = await Promise.allSettled(tarefas.map(t => t[1]));
        // agrupa blocos que falharam pelo mesmo motivo (ex.: filtro invalido afeta todos)
        const porMotivo = new Map();
        resultados.forEach((r, i) => {
            if (r.status !== 'rejected') return;
            const msg = r.reason.message;
            porMotivo.set(msg, (porMotivo.get(msg) || []).concat(tarefas[i][0]));
        });
        const falhas = [...porMotivo].map(([msg, blocos]) =>
            blocos.length === tarefas.length ? msg : blocos.join(', ') + ': ' + msg);
        if (falhas.length) mostrarAlerta(falhas);

        document.getElementById('ultimaAtualizacao').textContent =
            'às ' + new Date().toLocaleTimeString('pt-BR');
        document.querySelector('.content').classList.remove('carregando');
        carregando = false;
    }

    /* ------------------------------------------------------------ */
    /* Renderizacao                                                 */
    /* ------------------------------------------------------------ */

    const txt = (id, v) => { document.getElementById(id).textContent = v; };
    const num = v => v === null || v === undefined ? '—' : nf.format(v);
    const dec = v => v === null || v === undefined ? '—' : nf1.format(v);
    const esc = s => String(s ?? '').replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    function horas(h) {
        if (h === null || h === undefined) return '—';
        if (h < 1) return Math.round(h * 60) + ' min';
        if (h < 48) return nf1.format(h) + ' h';
        return nf1.format(h / 24) + ' dias';
    }

    function renderResumo(r) {
        txt('kpiTotal', num(r.totalAbertosNoPeriodo));
        txt('kpiAtendidos', num(r.atendidosNoPeriodo));
        txt('kpiBacklog', num(r.backlog));
        txt('kpiBacklogDet', `${num(r.abertos)} na fila · ${num(r.emAndamento)} em andamento · ${num(r.aguardandoCliente)} aguardando`);
        txt('kpiAtrasados', num(r.atrasados));
        txt('kpiSla', r.taxaSla === null ? '—' : dec(r.taxaSla) + '%');
        txt('kpiTempo', horas(r.tempoMedioResolucaoHoras));
        txt('kpiAvaliacao', r.avaliacaoMedia === null ? 'sem avaliações' : 'avaliação média ' + dec(r.avaliacaoMedia) + ' / 5');

        const v = r.variacaoPercentual;
        const el = document.getElementById('kpiVariacao');
        if (v === null || v === undefined) {
            el.textContent = 'sem dados no período anterior';
        } else {
            const seta = v > 0 ? '▲' : v < 0 ? '▼' : '■';
            el.innerHTML = `<span class="up">${seta} ${dec(Math.abs(v))}%</span> vs. período anterior (${num(r.totalPeriodoAnterior)})`;
        }
    }

    function baseOpcoes(extra) {
        return Object.assign({
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 250 },
            plugins: {
                legend: { display: false },
                tooltip: { backgroundColor: '#0b0b0b', padding: 10, cornerRadius: 6, boxPadding: 4 }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: COR.texto, maxRotation: 0, autoSkip: true } },
                y: { beginAtZero: true, grid: { color: COR.grade }, border: { display: false },
                     ticks: { color: COR.texto, callback: v => nf.format(v) } }
            }
        }, extra || {});
    }

    function desenhar(id, config) {
        if (graficos[id]) {
            graficos[id].data = config.data;
            graficos[id].options = config.options;
            graficos[id].update();
        } else {
            graficos[id] = new Chart(document.getElementById(id), config);
        }
    }

    function renderSerie(s) {
        const nomes = { DIA: 'por dia', SEMANA: 'por semana', MES: 'por mês' };
        txt('serieSub', `${nomes[s.granularidade]} · ${num(s.totalAbertos)} abertos, ${num(s.totalAtendidos)} atendidos`);
        desenhar('chartSerie', {
            type: 'line',
            data: {
                labels: s.pontos.map(p => p.rotulo),
                datasets: [
                    { label: 'Abertos', data: s.pontos.map(p => p.abertos), borderColor: COR.s1, backgroundColor: COR.s1,
                      borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, tension: 0.25 },
                    { label: 'Atendidos', data: s.pontos.map(p => p.atendidos), borderColor: COR.s2, backgroundColor: COR.s2,
                      borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, tension: 0.25 }
                ]
            },
            options: baseOpcoes({
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { display: true, position: 'top', align: 'end',
                              labels: { usePointStyle: true, pointStyle: 'line', color: COR.texto, boxWidth: 18 } },
                    tooltip: { backgroundColor: '#0b0b0b', padding: 10, cornerRadius: 6,
                               callbacks: { label: c => ` ${c.dataset.label}: ${nf.format(c.parsed.y)}` } }
                }
            })
        });
        tabela('tabSerie', ['Período', 'Abertos', 'Atendidos'],
            s.pontos.map(p => [p.rotulo, num(p.abertos), num(p.atendidos)]));
    }

    function renderStatus(lista) {
        renderBarras('chartStatus', lista, true);
        tabela('tabStatus', ['Status', 'Chamados', '%'],
            lista.map(i => [i.rotulo, num(i.total), dec(i.percentual) + '%']));
    }

    /** Barras de uma so cor (magnitude); horizontais quando os rotulos sao longos. */
    function renderBarras(id, lista, horizontal) {
        const opcoes = baseOpcoes({ indexAxis: horizontal ? 'y' : 'x' });
        opcoes.plugins.tooltip.callbacks = {
            label: c => ` ${nf.format(c.raw)} chamados (${dec(lista[c.dataIndex].percentual)}%)`
        };
        if (horizontal) {
            opcoes.scales = {
                x: { beginAtZero: true, grid: { color: COR.grade }, border: { display: false },
                     ticks: { color: COR.texto, callback: v => nf.format(v) } },
                y: { grid: { display: false }, ticks: { color: COR.texto, autoSkip: false } }
            };
        }
        desenhar(id, {
            type: 'bar',
            data: {
                labels: lista.map(i => i.rotulo),
                datasets: [{ data: lista.map(i => i.total), backgroundColor: COR.s1, borderRadius: 4,
                             borderSkipped: 'start', maxBarThickness: 26 }]
            },
            options: opcoes
        });
    }

    function renderTipos(lista) {
        const max = Math.max(1, ...lista.map(t => t.total));
        linhas('rankTipos', lista.map(t => `
            <tr>
                <td class="text-secondary">${t.posicao}</td>
                <td>${esc(t.tipo)}</td>
                <td><div class="barra-wrap"><span>${num(t.total)}</span>
                    <div class="barra" style="width:${(t.total / max * 100).toFixed(1)}%"></div></div></td>
                <td class="text-end">${horas(t.tempoMedioResolucaoHoras)}</td>
                <td class="text-end">${t.taxaSla === null ? '—' : dec(t.taxaSla) + '%'}</td>
            </tr>`), 5);
    }

    function renderTecnicos(lista) {
        linhas('rankTecnicos', lista.map(t => `
            <tr>
                <td class="text-secondary">${t.posicao}</td>
                <td>${esc(t.tecnico)}</td>
                <td class="text-end">${num(t.atendidos)}</td>
                <td class="text-end">${horas(t.tempoMedioResolucaoHoras)}</td>
                <td class="text-end">${t.taxaSla === null ? '—' : dec(t.taxaSla) + '%'}</td>
                <td class="text-end">${t.avaliacaoMedia === null ? '—' : dec(t.avaliacaoMedia)}</td>
            </tr>`), 6);
    }

    const ROTULO_STATUS = { ABERTO: 'Aberto', EM_ANDAMENTO: 'Em andamento', AGUARDANDO_CLIENTE: 'Aguardando cliente',
                            RESOLVIDO: 'Resolvido', FECHADO: 'Fechado', CANCELADO: 'Cancelado' };
    const ROTULO_PRIORIDADE = { BAIXA: 'Baixa', MEDIA: 'Média', ALTA: 'Alta', CRITICA: 'Crítica' };

    function renderRecentes(pagina) {
        linhas('tabRecentes', pagina.conteudo.map(c => `
            <tr>
                <td class="font-monospace small">${esc(c.protocolo)}</td>
                <td>${esc(c.titulo)}</td>
                <td><span class="badge-status st-${esc(c.status)}">${ROTULO_STATUS[c.status] || esc(c.status)}</span></td>
                <td class="small">${ROTULO_PRIORIDADE[c.prioridade] || '—'}</td>
                <td class="small">${esc(c.tecnico || 'Não atribuído')}</td>
                <td class="small text-secondary">${esc(c.consideracaoTecnica || '—')}</td>
                <td class="small text-end text-nowrap">${c.dataAbertura ? new Date(c.dataAbertura).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : ''}</td>
            </tr>`), 7);
    }

    function linhas(idTabela, html, colunas) {
        const tbody = document.querySelector('#' + idTabela + ' tbody');
        tbody.innerHTML = html.length ? html.join('')
            : `<tr><td colspan="${colunas}" class="text-secondary">Nenhum dado para os filtros selecionados.</td></tr>`;
    }

    function tabela(id, cabecalho, dados) {
        document.getElementById(id).innerHTML =
            '<table class="table table-sm"><thead><tr>' + cabecalho.map(h => `<th>${h}</th>`).join('') +
            '</tr></thead><tbody>' + dados.map(l => '<tr>' + l.map(c => `<td>${esc(c)}</td>`).join('') + '</tr>').join('') +
            '</tbody></table>';
    }

    function mostrarAlerta(falhas) {
        const el = document.getElementById('alerta');
        el.innerHTML = '<strong>Não foi possível carregar parte do dashboard.</strong><br>' + falhas.map(esc).join('<br>');
        el.classList.remove('d-none');
    }

    function esconderAlerta() {
        document.getElementById('alerta').classList.add('d-none');
    }

    /* ------------------------------------------------------------ */
    /* Atualizacao automatica (RF07)                                */
    /* ------------------------------------------------------------ */

    function agendar() {
        clearInterval(timer);
        if (document.getElementById('autoRefresh').checked) {
            timer = setInterval(() => { if (!document.hidden) carregar(); }, INTERVALO_MS);
        }
    }

    /* ------------------------------------------------------------ */
    /* Eventos                                                      */
    /* ------------------------------------------------------------ */

    form.addEventListener('submit', e => { e.preventDefault(); carregar(); });
    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('autoRefresh').addEventListener('change', agendar);
    document.getElementById('btnLimpar').addEventListener('click', () => {
        form.reset();
        aplicarPreset(90);
        carregar();
    });
    document.querySelectorAll('#presets button').forEach(b => b.addEventListener('click', () => {
        aplicarPreset(Number(b.dataset.dias));
        carregar();
    }));
    ['inicio', 'fim'].forEach(c => form[c].addEventListener('change', () =>
        document.querySelectorAll('#presets button').forEach(b => b.classList.remove('active'))));
    document.querySelectorAll('[data-tabela]').forEach(b => b.addEventListener('click', () => {
        const alvo = document.getElementById(b.dataset.tabela);
        const canvas = alvo.previousElementSibling;
        const mostrar = alvo.classList.contains('d-none');
        alvo.classList.toggle('d-none', !mostrar);
        canvas.classList.toggle('d-none', mostrar);
        b.textContent = mostrar ? 'Ver gráfico' : 'Ver tabela';
    }));
    document.addEventListener('visibilitychange', () => { if (!document.hidden) carregar(); });

    lerUrl();
    carregar();
    agendar();
})();
