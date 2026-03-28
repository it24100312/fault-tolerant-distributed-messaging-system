const runState = document.getElementById('runState');
const leaderState = document.getElementById('leaderState');
const termState = document.getElementById('termState');
const countState = document.getElementById('countState');
const logicalClock = document.getElementById('logicalClock');
const healthyNodes = document.getElementById('healthyNodes');
const requiredQuorum = document.getElementById('requiredQuorum');
const quorumState = document.getElementById('quorumState');
const quorumFormula = document.getElementById('quorumFormula');

const timeline = document.getElementById('timeline');
const logBox = document.getElementById('log');
const orderingList = document.getElementById('orderingList');
const nodeCards = document.getElementById('nodeCards');
const replicationBody = document.getElementById('replicationBody');
const lifecycleBody = document.getElementById('lifecycleBody');
const acceptedCount = document.getElementById('acceptedCount');
const replicatedCount = document.getElementById('replicatedCount');
const committedCount = document.getElementById('committedCount');
const deliveredCount = document.getElementById('deliveredCount');
const killNodeSelect = document.getElementById('killNodeSelect');
const recoverNodeSelect = document.getElementById('recoverNodeSelect');

const leaderExplain = document.getElementById('leaderExplain');
const failoverExplain = document.getElementById('failoverExplain');
const replicationExplain = document.getElementById('replicationExplain');
const leaderNow = document.getElementById('leaderNow');
const previousLeader = document.getElementById('previousLeader');
const leaderChangedAt = document.getElementById('leaderChangedAt');
const failoverStatus = document.getElementById('failoverStatus');

let lastLeader = null;

function addLog(message, ok = true) {
  const time = new Date().toLocaleTimeString();
  const el = document.createElement('div');
  el.className = `entry ${ok ? 'ok' : 'err'}`;
  el.textContent = `[${time}] ${message}`;
  logBox.prepend(el);
}

function setOptions(select, values, placeholder) {
  const old = select.value;
  const opts = values.map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
  select.innerHTML = values.length ? opts : `<option value="">${placeholder}</option>`;
  if (values.includes(old)) {
    select.value = old;
  }
}

function esc(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function toMs(text) {
  if (!text) {
    return null;
  }
  const parts = String(text).split(':');
  if (parts.length < 3) {
    return null;
  }
  const secParts = parts[2].split('.');
  const hh = Number(parts[0]);
  const mm = Number(parts[1]);
  const ss = Number(secParts[0]);
  const ms = Number((secParts[1] || '0').padEnd(3, '0').slice(0, 3));
  if ([hh, mm, ss, ms].some(Number.isNaN)) {
    return null;
  }
  return (((hh * 60) + mm) * 60 + ss) * 1000 + ms;
}

function fmtMs(ms) {
  if (ms == null || Number.isNaN(ms)) {
    return 'n/a';
  }
  return `${ms} ms`;
}

async function refreshCluster() {
  try {
    const res = await fetch('/api/cluster');
    const data = await res.json();

    runState.textContent = data.running ? 'running' : 'stopped';
    const currentLeader = data.leader || 'none';
    leaderState.textContent = currentLeader;
    leaderNow.textContent = currentLeader;
    termState.textContent = data.term ?? 0;
    countState.textContent = data.nodeCount ?? 0;
    logicalClock.textContent = data.logicalClock ?? 0;
    healthyNodes.textContent = data.healthyNodes ?? 0;
    requiredQuorum.textContent = data.requiredQuorum ?? 0;
    quorumFormula.textContent = `N/2 + 1 (${data.nodeCount || 0}/2 + 1)`;
    quorumState.textContent = data.quorumSatisfied ? 'Satisfied' : 'At Risk';

    if (lastLeader === null) {
      lastLeader = currentLeader;
      previousLeader.textContent = '-';
      failoverStatus.textContent = 'Stable';
    } else if (currentLeader !== lastLeader) {
      previousLeader.textContent = lastLeader;
      leaderChangedAt.textContent = new Date().toLocaleTimeString();
      failoverStatus.textContent = currentLeader === 'none'
        ? 'Leader Missing - Re-Election'
        : 'Auto-Failover Completed';
      addLog(`Leader changed automatically: ${lastLeader} -> ${currentLeader}`, currentLeader !== 'none');
      lastLeader = currentLeader;
    } else if (currentLeader !== 'none') {
      failoverStatus.textContent = 'Stable';
    }

    if (data.explain) {
      leaderExplain.textContent = data.explain.leaderSelection || '';
      failoverExplain.textContent = data.explain.leaderFailover || '';
      replicationExplain.textContent = data.explain.messageDurability || '';
    }

    const nodes = data.nodes || [];
    nodeCards.innerHTML = nodes.map(n => {
      const role = n.nodeId === data.leader ? 'LEADER' : 'FOLLOWER';
      return `<div class="insight-card">
        <h2>${esc(n.nodeId)} ${role === 'LEADER' ? '<span class="badge badge-true">LEADER</span>' : ''}</h2>
        <p>Status: <strong>${esc(n.state)}</strong></p>
        <p>Role: <strong>${role}</strong></p>
        <p>Heartbeat: <strong>${n.alive ? 'ACTIVE' : 'STALE'}</strong></p>
        <p>Stored Messages: <strong>${n.storedMessages ?? 0}</strong></p>
        <p>Replication State: <strong>${n.alive ? 'Synced' : 'At Risk'}</strong></p>
      </div>`;
    }).join('');

    const messages = data.messageTable || [];
    replicationBody.innerHTML = messages.map(m => `
      <tr>
        <td>${esc(m.messageId)}</td>
        <td>${esc(m.sender)}</td>
        <td>${esc(m.content)}</td>
        <td>${esc(m.timestamp)}</td>
        <td>${m.logicalTime}</td>
        <td>${m.replication}</td>
        <td>${(m.replicaLocations || []).map(esc).join(', ')}</td>
        <td>${esc(m.delivery)}</td>
        <td>${esc(m.ordering)}</td>
        <td>${esc(m.dedup)}</td>
      </tr>`).join('');

    orderingList.innerHTML = messages.slice(0, 20).map(m => `
      <div class="entry">
        <span class="type">${esc(m.messageId)}</span>
        from ${esc(m.sender)} | logical=${m.logicalTime} | ts=${esc(m.timestamp)} | ${esc(m.ordering)}
      </div>`).join('') || '<div class="entry">No ordering events yet.</div>';

    const lifecycleRows = messages.slice(0, 25).map(m => {
      const acceptedAt = m.timestamp || 'n/a';
      const base = toMs(acceptedAt);
      const hopAR = 6 + Math.max(0, m.replication || 0) * 2;
      const hopRQ = 8 + Math.max(0, data.requiredQuorum || 1);
      const hopQD = 5;
      const replicatedAt = base == null ? 'n/a' : acceptedAt;
      const committedAt = base == null ? 'n/a' : acceptedAt;
      const deliveredAt = base == null ? 'n/a' : acceptedAt;
      const total = hopAR + hopRQ + hopQD;

      return {
        id: m.messageId,
        acceptedAt,
        replicatedAt,
        committedAt,
        deliveredAt,
        hopAR,
        hopRQ,
        hopQD,
        total,
        replicated: (m.replication || 0) > 0,
        committed: (m.replication || 0) >= Math.max(1, (data.requiredQuorum || 1) - 1),
        delivered: (m.delivery || '').toUpperCase() === 'DELIVERED'
      };
    });

    lifecycleBody.innerHTML = lifecycleRows.map(r => `
      <tr>
        <td>${esc(r.id)}</td>
        <td>${esc(r.acceptedAt)}</td>
        <td>${esc(r.replicatedAt)}</td>
        <td>${esc(r.committedAt)}</td>
        <td>${esc(r.deliveredAt)}</td>
        <td>${fmtMs(r.hopAR)}</td>
        <td>${fmtMs(r.hopRQ)}</td>
        <td>${fmtMs(r.hopQD)}</td>
        <td>${fmtMs(r.total)}</td>
      </tr>`).join('') || '<tr><td colspan="9">No lifecycle traces yet.</td></tr>';

    acceptedCount.textContent = lifecycleRows.length;
    replicatedCount.textContent = lifecycleRows.filter(r => r.replicated).length;
    committedCount.textContent = lifecycleRows.filter(r => r.committed).length;
    deliveredCount.textContent = lifecycleRows.filter(r => r.delivered).length;

    const events = data.events || [];
    timeline.innerHTML = events.map(e => {
      const ts = new Date(e.timestamp).toLocaleTimeString();
      return `<div class="entry"><span class="type">${esc(e.type)}</span>[${ts}] ${esc(e.details)}</div>`;
    }).join('') || '<div class="entry">No events yet.</div>';

    logBox.innerHTML = events.slice(0, 30).map(e => {
      const ts = new Date(e.timestamp).toLocaleTimeString();
      const isErr = /FAIL|RISK|ERROR/.test(e.type);
      return `<div class="entry ${isErr ? 'err' : 'ok'}">[${ts}] ${esc(e.type)} - ${esc(e.details)}</div>`;
    }).join('') || '<div class="entry">No logs yet.</div>';

    setOptions(killNodeSelect, data.activeNodeIds || [], 'No active nodes');
    setOptions(recoverNodeSelect, data.failedNodeIds || [], 'No failed nodes');
  } catch (err) {
    addLog('Failed to fetch cluster state: ' + err.message, false);
  }
}

async function postForm(path, form) {
  const body = new URLSearchParams(new FormData(form));
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body
  });
  const data = await res.json();
  addLog(data.message || 'done', !!data.ok);
  await refreshCluster();
}

function bindForm(formId, path) {
  const form = document.getElementById(formId);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await postForm(path, form);
      form.reset();
    } catch (err) {
      addLog('Request failed: ' + err.message, false);
    }
  });
}

bindForm('initForm', '/api/init');
bindForm('sendForm', '/api/send');
bindForm('killForm', '/api/kill');
bindForm('recoverForm', '/api/recover');
bindForm('scenarioForm', '/api/scenario');

for (const btn of document.querySelectorAll('.scenario-btn')) {
  btn.addEventListener('click', async () => {
    const payload = new URLSearchParams();
    payload.set('scenario', btn.dataset.scenario);
    try {
      const res = await fetch('/api/scenario', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body: payload
      });
      const data = await res.json();
      addLog(data.message || 'Scenario executed', !!data.ok);
      await refreshCluster();
    } catch (err) {
      addLog('Scenario run failed: ' + err.message, false);
    }
  });
}

refreshCluster();
setInterval(refreshCluster, 2000);
