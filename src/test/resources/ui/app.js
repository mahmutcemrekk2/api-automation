/* ============================================
   API Test Automation Dashboard — App Logic
   ============================================ */

// ============================================
// STATE
// ============================================
let registry = { endpoints: [] };
let coverage = { summary: {}, covered: [], uncovered: [] };
let features = [];
let flowSteps = [];
let stepIdCounter = 0;
let setupSteps = [];
let setupStepIdCounter = 0;

// ============================================
// INIT
// ============================================
document.addEventListener('DOMContentLoaded', async () => {
    setupNavigation();
    await loadData();
    renderDashboard();
    addFlowStep(); // Start with one step
    loadGitStatus();
});

async function loadData() {
    try {
        const [regRes, covRes, featRes] = await Promise.all([
            fetch('/api/registry'),
            fetch('/api/coverage'),
            fetch('/api/features')
        ]);
        registry = await regRes.json();
        coverage = await covRes.json();
        features = await featRes.json();
        populateFeatureDropdown();
    } catch (e) {
        console.error('Failed to load data:', e);
        toast('Failed to load data from server', 'error');
    }
}

function populateFeatureDropdown() {
    const select = document.getElementById('featureSelect');
    if (!select) return;
    // Keep the "NEW" option, remove others
    select.innerHTML = '<option value="NEW">-- 📄 Create New Feature --</option>';
    features.forEach(f => {
        select.innerHTML += `<option value="${f.name}">📝 Append to: ${f.name}</option>`;
    });
}

function toggleFeatureInput(val) {
    const featInput = document.getElementById('featureFilename');
    const suffix = document.getElementById('featureSuffix');
    if (val === 'NEW') {
        featInput.style.display = "inline-block";
        suffix.style.display = "inline";
    } else {
        featInput.style.display = "none";
        suffix.style.display = "none";
    }
    updatePreview();
}

// ============================================
// NAVIGATION
// ============================================
function setupNavigation() {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', () => {
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
            document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
            item.classList.add('active');
            document.getElementById('page-' + item.dataset.page).classList.add('active');

            if (item.dataset.page === 'existing') renderExisting();
            if (item.dataset.page === 'dashboard') renderDashboard();
        });
    });
}

// ============================================
// DASHBOARD
// ============================================
function renderDashboard() {
    const s = coverage.summary || {};
    document.getElementById('statTotal').textContent = s.totalEndpoints || 0;
    document.getElementById('statCovered').textContent = s.coveredEndpoints || 0;
    document.getElementById('statMissing').textContent = s.uncoveredEndpoints || 0;
    document.getElementById('statPercent').textContent = (s.coveragePercent || 0) + '%';
    document.getElementById('coverageFill').style.width = (s.coveragePercent || 0) + '%';

    // Resource table
    const resources = {};
    (registry.endpoints || []).forEach(ep => {
        if (!resources[ep.resource]) resources[ep.resource] = { total: 0, covered: 0 };
        resources[ep.resource].total++;
    });
    (coverage.covered || []).forEach(c => {
        if (resources[c.resource]) resources[c.resource].covered++;
    });

    const tbody = document.getElementById('resourceTableBody');
    tbody.innerHTML = Object.entries(resources).map(([name, data]) => {
        const missing = data.total - data.covered;
        const pct = data.total > 0 ? Math.round(data.covered / data.total * 100) : 0;
        const color = pct === 100 ? 'var(--success)' : pct > 50 ? 'var(--accent)' : pct > 0 ? 'var(--warning)' : 'var(--danger)';
        return `<tr>
            <td style="text-transform:capitalize;font-weight:600">${name}</td>
            <td>${data.total}</td>
            <td style="color:var(--success)">${data.covered}</td>
            <td style="color:${missing > 0 ? 'var(--warning)' : 'var(--text-muted)'}">${missing}</td>
            <td><span style="color:${color};font-weight:600">${pct}%</span></td>
        </tr>`;
    }).join('');
}

// ============================================
// NEW SCENARIO BUILDER
// ============================================
// PRE-REQUISITE SETUP STEPS
// ============================================
function addSetupStep() {
    const stepId = setupStepIdCounter++;
    const step = {
        id: stepId,
        service: 'dummyjson',
        loginRequired: false,
        method: 'GET',
        path: '',
        requestBody: '',
        expectedStatus: 200,
        storeFields: [{ path: '', key: '' }]
    };
    setupSteps.push(step);
    renderSetupSteps();
    updatePreview();
}

function removeSetupStep(stepId) {
    setupSteps = setupSteps.filter(s => s.id !== stepId);
    renderSetupSteps();
    updatePreview();
}

function updateSetupStep(id, field, value) {
    const step = setupSteps.find(s => s.id === id);
    if (step) {
        step[field] = value;
        if (field === 'method' && !['POST', 'PUT', 'PATCH'].includes(value)) {
            step.requestBody = '';
        }
        renderSetupSteps();
        updatePreview();
    }
}

function selectSetupEndpoint(id, value) {
    if (!value) {
        updateSetupStep(id, 'path', '');
        return;
    }
    const [method, path] = value.split('|');
    const step = setupSteps.find(s => s.id === id);
    if (step) {
        step.method = method;
        step.path = path;

        const allEp = registry.endpoints || [];
        const match = allEp.find(e => e.method === method && e.path === path);
        if (match && match.exampleBody && ['POST', 'PUT', 'PATCH'].includes(method)) {
            step.requestBody = JSON.stringify(match.exampleBody, null, 2);
        } else {
            step.requestBody = '';
        }

        renderSetupSteps();
        updatePreview();
    }
}

function addSetupStoreField(stepId) {
    const step = setupSteps.find(s => s.id === stepId);
    if (step) {
        if (!step.storeFields) step.storeFields = [];
        step.storeFields.push({ path: '', key: '' });
        renderSetupSteps();
        updatePreview();
    }
}

function removeSetupStoreField(stepId, index) {
    const step = setupSteps.find(s => s.id === stepId);
    if (step && step.storeFields) {
        step.storeFields.splice(index, 1);
        renderSetupSteps();
        updatePreview();
    }
}

function updateSetupStoreField(stepId, index, field, value) {
    const step = setupSteps.find(s => s.id === stepId);
    if (step && step.storeFields && step.storeFields[index]) {
        step.storeFields[index][field] = value;
        updatePreview();
    }
}

function renderSetupSteps() {
    const container = document.getElementById('setupSteps');
    if (!container) return;

    if (setupSteps.length === 0) {
        container.innerHTML = '';
        return;
    }

    container.innerHTML = setupSteps.map((step, idx) => {
        const allEndpoints = registry.endpoints || [];

        return `
        <div class="flow-step" style="border-left: 4px solid var(--accent); background: rgba(59, 130, 246, 0.05);" data-setup-step-id="${step.id}">
            <button class="remove-step" onclick="removeSetupStep(${step.id})">✕</button>
            <div style="font-size:12px; font-weight:600; color:var(--accent); margin-bottom:15px; text-transform:uppercase; letter-spacing:0.5px; display:flex; align-items:center; gap:8px;">
                🔌 Pre-requisite Request ${idx + 1}
                <span class="badge" style="background:var(--accent);color:var(--bg)">Always 200 OK</span>
            </div>

            <div class="step-group">
                <div class="step-row" style="margin-top:6px">
                    <select style="width:110px" onchange="updateSetupStep(${step.id},'method',this.value)">
                        <option value="GET" ${step.method === 'GET' ? 'selected' : ''}>GET</option>
                        <option value="POST" ${step.method === 'POST' ? 'selected' : ''}>POST</option>
                        <option value="PUT" ${step.method === 'PUT' ? 'selected' : ''}>PUT</option>
                        <option value="DELETE" ${step.method === 'DELETE' ? 'selected' : ''}>DELETE</option>
                        <option value="PATCH" ${step.method === 'PATCH' ? 'selected' : ''}>PATCH</option>
                    </select>
                    <select onchange="selectSetupEndpoint(${step.id},this.value)">
                        <option value="">-- Allow ANY Endpoint --</option>
                        ${groupEndpoints(allEndpoints).map(g =>
            `<optgroup label="${g.resource.toUpperCase()}">
                                ${g.items.map(ep => `<option value="${ep.method}|${ep.path}" ${step.method === ep.method && step.path === ep.path ? 'selected' : ''}>${ep.method} ${ep.path} — ${ep.name}</option>`).join('')}
                            </optgroup>`
        ).join('')}
                    </select>
                </div>
                <div style="margin-top:6px">
                    <input type="text" placeholder="/custom/path or use dropdown above"
                           value="${step.path}" onchange="updateSetupStep(${step.id},'path',this.value)">
                </div>
            </div>
            
            ${['POST', 'PUT', 'PATCH'].includes(step.method) ? `
            <div class="step-group">
                <div class="step-label"><span>Request Body</span></div>
                <textarea placeholder='{"key": "value"}' onchange="updateSetupStep(${step.id},'requestBody',this.value)">${step.requestBody}</textarea>
            </div>` : ''}

            <div class="step-group">
                <div class="step-label">
                    <span>Store Required Data</span>
                </div>
                <div class="kv-pairs" id="setup-store-${step.id}">
                    ${(step.storeFields || []).map((sf, i) => `
                    <div class="kv-row">
                        <input type="text" placeholder="$.field" value="${sf.path}"
                               onchange="updateSetupStoreField(${step.id},${i},'path',this.value)">
                        <span class="arrow">→</span>
                        <input type="text" placeholder="variableName" value="${sf.key}"
                               onchange="updateSetupStoreField(${step.id},${i},'key',this.value)">
                        ${step.storeFields.length > 1 ? `<button class="remove-btn" onclick="removeSetupStoreField(${step.id},${i})">✕</button>` : ''}
                    </div>`).join('')}
                </div>
                <button class="add-row-btn" onclick="addSetupStoreField(${step.id})">+ Add Store Field</button>
            </div>
        </div>`;
    }).join('');
}

// ============================================
function addFlowStep() {
    const stepId = stepIdCounter++;
    const step = {
        id: stepId,
        service: 'dummyjson',
        loginRequired: stepId === 0,
        method: 'GET',
        path: '',
        requestBody: '',
        expectedStatus: 200,
        loadGlobals: [],
        storeFields: [],
        saveGlobals: [],
        assertions: []
    };
    flowSteps.push(step);
    renderFlowSteps();
}

function removeFlowStep(stepId) {
    flowSteps = flowSteps.filter(s => s.id !== stepId);
    renderFlowSteps();
}

function renderFlowSteps() {
    const container = document.getElementById('builderSteps');
    container.innerHTML = flowSteps.map((step, idx) => {
        const endpoints = getUncoveredEndpoints();
        const allEndpoints = registry.endpoints || [];

        return `
        <div class="flow-step" data-step-id="${step.id}">
            ${flowSteps.length > 1 ? `<button class="remove-step" onclick="removeFlowStep(${step.id})">✕</button>` : ''}

            <!-- Load Global Variables -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}</div>
                    <span>Load Global Variables</span>
                </div>
                <div class="kv-pairs" id="load-global-${step.id}">
                    ${(step.loadGlobals || []).map((lg, i) => `
                    <div class="kv-row">
                        <input type="text" placeholder="Global Variable Name" value="${lg.globalKey}"
                               onchange="updateLoadGlobal(${step.id},${i},'globalKey',this.value)">
                        <span class="arrow">→</span>
                        <input type="text" placeholder="Local Variable Name" value="${lg.localKey}"
                               onchange="updateLoadGlobal(${step.id},${i},'localKey',this.value)">
                        <button class="remove-btn" onclick="removeLoadGlobal(${step.id},${i})">✕</button>
                    </div>`).join('')}
                </div>
                <button class="add-row-btn" onclick="addLoadGlobal(${step.id})">+ Add Load Global Variable</button>
            </div>

            <!-- Service & Login -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.1</div>
                    <span>Configuration</span>
                </div>
                <div class="step-row" style="gap:16px">
                    <div style="flex:1">
                        <select onchange="updateStep(${step.id},'service',this.value)" style="margin-bottom:6px">
                            <option value="dummyjson" selected>dummyjson</option>
                        </select>
                    </div>
                    <div class="checkbox-row">
                        <input type="checkbox" id="login-${step.id}" ${step.loginRequired ? 'checked' : ''}
                               onchange="updateStep(${step.id},'loginRequired',this.checked)">
                        <label for="login-${step.id}">Login Required</label>
                    </div>
                </div>
            </div>

            <!-- Endpoint Selection -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.2</div>
                    <span>Endpoint</span>
                </div>
                <div class="step-row">
                    <select style="width:110px" onchange="updateStep(${step.id},'method',this.value)">
                        <option value="GET" ${step.method === 'GET' ? 'selected' : ''}>GET</option>
                        <option value="POST" ${step.method === 'POST' ? 'selected' : ''}>POST</option>
                        <option value="PUT" ${step.method === 'PUT' ? 'selected' : ''}>PUT</option>
                        <option value="DELETE" ${step.method === 'DELETE' ? 'selected' : ''}>DELETE</option>
                        <option value="PATCH" ${step.method === 'PATCH' ? 'selected' : ''}>PATCH</option>
                    </select>
                    <select onchange="selectEndpoint(${step.id},this.value)">
                        <option value="">-- Select Endpoint --</option>
                        ${groupEndpoints(endpoints).map(g =>
            `<optgroup label="${g.resource.toUpperCase()}">
                                ${g.items.map(ep =>
                `<option value="${ep.method}|${ep.path}" ${step.method === ep.method && step.path === ep.path ? 'selected' : ''}>
                                        ${ep.method} ${ep.path} — ${ep.name}
                                    </option>`
            ).join('')}
                            </optgroup>`
        ).join('')}
                    </select>
                </div>
                <div style="margin-top:6px">
                    <input type="text" placeholder="/custom/path or use dropdown above"
                           value="${step.path}" onchange="updateStep(${step.id},'path',this.value)">
                </div>
                <div class="step-label" style="margin-top:10px;">
                    <div class="step-number">${idx + 1}.3</div>
                    <span>Expected Status</span>
                </div>
                <div class="step-row" style="margin-top:6px;">
                    <input type="number" value="${step.expectedStatus || 200}" onchange="updateStep(${step.id},'expectedStatus', parseInt(this.value, 10))" style="width:100px;">
                </div>
            </div>

            <!-- Request Body -->
            ${['POST', 'PUT', 'PATCH'].includes(step.method) ? `
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.4</div>
                    <span>Request Body</span>
                </div>
                <textarea placeholder='{"key": "value"}'
                          onchange="updateStep(${step.id},'requestBody',this.value)">${step.requestBody}</textarea>
            </div>` : ''}

            <!-- Store Response -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.5</div>
                    <span>Store Response Values</span>
                </div>
                <div class="kv-pairs" id="store-${step.id}">
                    ${(step.storeFields || []).map((sf, i) => `
                    <div class="kv-row">
                        <input type="text" placeholder="$.field" value="${sf.path}"
                               onchange="updateStoreField(${step.id},${i},'path',this.value)">
                        <span class="arrow">→</span>
                        <input type="text" placeholder="variableName" value="${sf.key}"
                               onchange="updateStoreField(${step.id},${i},'key',this.value)">
                        <button class="remove-btn" onclick="removeStoreField(${step.id},${i})">✕</button>
                    </div>`).join('')}
                </div>
                <button class="add-row-btn" onclick="addStoreField(${step.id})">+ Add Store Field</button>
            </div>

            <!-- Save Global Variables -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.6</div>
                    <span>Save Global Variables</span>
                </div>
                <div class="kv-pairs" id="save-global-${step.id}">
                    ${(step.saveGlobals || []).map((sg, i) => `
                    <div class="kv-row">
                        <input type="text" placeholder="$.field" value="${sg.path}"
                               onchange="updateSaveGlobal(${step.id},${i},'path',this.value)">
                        <span class="arrow">→</span>
                        <input type="text" placeholder="Global Variable Name" value="${sg.globalKey}"
                               onchange="updateSaveGlobal(${step.id},${i},'globalKey',this.value)">
                        <button class="remove-btn" onclick="removeSaveGlobal(${step.id},${i})">✕</button>
                    </div>`).join('')}
                </div>
                <button class="add-row-btn" onclick="addSaveGlobal(${step.id})">+ Add Save Global Variable</button>
            </div>

            <!-- Assertions -->
            <div class="step-group">
                <div class="step-label">
                    <div class="step-number">${idx + 1}.7</div>
                    <span>Validate Response</span>
                </div>
                <div id="assert-${step.id}">
                    ${(step.assertions || []).map((a, i) => `
                    <div class="assertion-row">
                        <input type="text" placeholder="$.field" value="${a.path}"
                               onchange="updateAssertion(${step.id},${i},'path',this.value)">
                        <select onchange="updateAssertion(${step.id},${i},'condition',this.value)">
                            <option value="exists" ${a.condition === 'exists' ? 'selected' : ''}>exists</option>
                            <option value="not empty" ${a.condition === 'not empty' ? 'selected' : ''}>not empty</option>
                            <option value="null" ${a.condition === 'null' ? 'selected' : ''}>null</option>
                            <option value="equals" ${a.condition === 'equals' ? 'selected' : ''}>equals</option>
                            <option value="contains" ${a.condition === 'contains' ? 'selected' : ''}>contains</option>
                            <option value="matches" ${a.condition === 'matches' ? 'selected' : ''}>matches regex</option>
                        </select>
                        <input type="text" placeholder="Expected Value" value="${escHtml(a.value || '')}"
                               onchange="updateAssertion(${step.id},${i},'value',this.value)"
                               style="display: ${['equals', 'contains', 'matches'].includes(a.condition) ? 'inline-block' : 'none'}; flex:1; min-width:80px;">
                        <button class="remove-btn" onclick="removeAssertion(${step.id},${i})">✕</button>
                    </div>`).join('')}
                </div>
                <button class="add-row-btn" onclick="addAssertion(${step.id})">+ Add Assertion</button>
            </div>
        </div>
        ${idx < flowSteps.length - 1 ? '<div class="step-connector">↓</div>' : ''}
        `;
    }).join('');

    updatePreview();
}

function groupEndpoints(endpoints) {
    const groups = {};
    endpoints.forEach(ep => {
        if (!groups[ep.resource]) groups[ep.resource] = { resource: ep.resource, items: [] };
        groups[ep.resource].items.push(ep);
    });
    return Object.values(groups);
}

function getUncoveredEndpoints() {
    return coverage.uncovered || [];
}

function selectEndpoint(stepId, value) {
    if (!value) return;
    const [method, path] = value.split('|');
    const step = flowSteps.find(s => s.id === stepId);
    if (step) {
        step.method = method;
        step.path = path;

        // Auto-fill request body from registry
        const ep = (registry.endpoints || []).find(e => e.method === method && e.path === path);
        if (ep && ep.requestBody) {
            step.requestBody = JSON.stringify(ep.requestBody, null, 4);
        }

        // Auto-fill response fields as assertions
        if (ep && ep.responseFields && ep.responseFields.length > 0) {
            step.assertions = ep.responseFields.slice(0, 4).map(f => ({
                path: '$.' + f, condition: 'exists'
            }));
        }
    }
    renderFlowSteps();
}

function updateStep(stepId, field, value) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) {
        step[field] = value;
        if (field === 'method') renderFlowSteps();
        else updatePreview();
    }
}

// Load Global Variables
function addLoadGlobal(stepId) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) {
        if (!step.loadGlobals) step.loadGlobals = [];
        step.loadGlobals.push({ globalKey: '', localKey: '' });
        renderFlowSteps();
    }
}
function removeLoadGlobal(stepId, index) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.loadGlobals) { step.loadGlobals.splice(index, 1); renderFlowSteps(); }
}
function updateLoadGlobal(stepId, index, field, value) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.loadGlobals && step.loadGlobals[index]) { step.loadGlobals[index][field] = value; updatePreview(); }
}

// Store fields
function addStoreField(stepId) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) { step.storeFields.push({ path: '', key: '' }); renderFlowSteps(); }
}
function removeStoreField(stepId, index) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) { step.storeFields.splice(index, 1); renderFlowSteps(); }
}
function updateStoreField(stepId, index, field, value) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.storeFields[index]) { step.storeFields[index][field] = value; updatePreview(); }
}

// Save Global Variables
function addSaveGlobal(stepId) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) {
        if (!step.saveGlobals) step.saveGlobals = [];
        step.saveGlobals.push({ path: '', globalKey: '' });
        renderFlowSteps();
    }
}
function removeSaveGlobal(stepId, index) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.saveGlobals) { step.saveGlobals.splice(index, 1); renderFlowSteps(); }
}
function updateSaveGlobal(stepId, index, field, value) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.saveGlobals && step.saveGlobals[index]) { step.saveGlobals[index][field] = value; updatePreview(); }
}

// Assertions
function addAssertion(stepId) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) { step.assertions.push({ path: '', condition: 'exists' }); renderFlowSteps(); }
}
function removeAssertion(stepId, index) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step) { step.assertions.splice(index, 1); renderFlowSteps(); }
}
function updateAssertion(stepId, index, field, value) {
    const step = flowSteps.find(s => s.id === stepId);
    if (step && step.assertions[index]) {
        step.assertions[index][field] = value;
        if (field === 'condition' && ['equals', 'contains', 'matches'].includes(value) && !step.assertions[index].value) {
            step.assertions[index].value = ''; // Initialize if needed
        }
        renderFlowSteps();
    }
}

// ============================================
// GHERKIN GENERATOR
// ============================================
function generateGherkin() {
    const isAppend = document.getElementById('featureSelect').value !== 'NEW';
    let baseFeat = document.getElementById('featureFilename').value.trim() || 'generated_feature';
    let baseScene = document.getElementById('scenarioNameInput').value.trim() || 'Generated Scenario';
    let rawTags = document.getElementById('scenarioTags') ? document.getElementById('scenarioTags').value.trim() : '@smoke';

    const scenarioName = baseScene;

    const hasLogin = flowSteps.some(s => s.loginRequired);
    const isFlow = flowSteps.length > 1;
    const tag = rawTags;

    let lines = [];

    if (!isAppend) {
        lines.push(`${tag}`);
        lines.push(`Feature: ${baseFeat.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}`);
        lines.push(`    Auto-generated test scenario`);
        lines.push('');
    } else {
        lines.push(`    ${tag}`);
    }

    if (!isAppend) {
        lines.push(`    ${tag}`);
    }
    lines.push(`    Scenario: ${scenarioName}`);
    lines.push(`        Given system uses "dummyjson" service`);

    if (hasLogin) {
        lines.push(`        And user is logged in as default`);
    }

    setupSteps.forEach((step, idx) => {
        if (!step.path) return;
        lines.push('');
        lines.push(`        # Pre-requisite Setup ${idx + 1}`);

        const resolvedPath = step.path;

        if (step.requestBody && ['POST', 'PUT', 'PATCH'].includes(step.method)) {
            lines.push(`        When user sends "${step.method}" request to "${resolvedPath}" with body:`);
            lines.push('            """');
            step.requestBody.split('\n').forEach(l => lines.push('            ' + l));
            lines.push('            """');
        } else {
            lines.push(`        When user sends "${step.method}" request to "${resolvedPath}"`);
        }

        lines.push(`        Then response status code should be 200`);

        step.storeFields.forEach(sf => {
            if (sf.path && sf.key) {
                lines.push(`        And user stores response "${sf.path}" as "${sf.key}"`);
            }
        });
    });

    flowSteps.forEach((step, idx) => {
        if (!step.path) return;
        lines.push('');
        if (isFlow) lines.push(`        # Step ${idx + 1}`);

        (step.loadGlobals || []).forEach(lg => {
            if (lg.globalKey && lg.localKey) {
                lines.push(`        And system loads global variable "${lg.globalKey}" as "${lg.localKey}"`);
            }
        });

        const resolvedPath = step.path;

        if (step.requestBody && ['POST', 'PUT', 'PATCH'].includes(step.method)) {
            lines.push(`        When I send "${step.method}" request to "${resolvedPath}" with body:`);
            lines.push('            """');
            step.requestBody.split('\n').forEach(l => lines.push('            ' + l));
            lines.push('            """');
        } else {
            lines.push(`        When I send "${step.method}" request to "${resolvedPath}"`);
        }

        lines.push(`        Then response status code should be ${step.expectedStatus || 200}`);

        // Assertions
        if (step.assertions.length > 0) {
            lines.push(`        And response should match:`);
            step.assertions.forEach(a => {
                if (a.path) {
                    const padded = a.path.padEnd(20);
                    let displayCond = a.condition;
                    if (['equals', 'contains', 'matches'].includes(a.condition)) {
                        if (a.condition === 'equals') displayCond = a.value || '';
                        if (a.condition === 'contains') displayCond = 'contains ' + (a.value || '');
                        if (a.condition === 'matches') displayCond = 'matches ' + (a.value || '');
                    }
                    lines.push(`            | ${padded} | ${displayCond.padEnd(10)} |`);
                }
            });
        }

        // Store fields
        step.storeFields.forEach(sf => {
            if (sf.path && sf.key) {
                lines.push(`        And user stores response "${sf.path}" as "${sf.key}"`);
            }
        });

        // Save Global fields
        (step.saveGlobals || []).forEach(sg => {
            if (sg.path && sg.globalKey) {
                lines.push(`        And user stores response "${sg.path}" as global variable "${sg.globalKey}"`);
            }
        });
    });

    lines.push('');
    return lines.join('\n');
}

function updatePreview() {
    const gherkin = generateGherkin();
    const preview = document.getElementById('previewBody');
    preview.innerHTML = highlightGherkin(gherkin);

    // Changing any field requires a re-test
    const genBtn = document.getElementById('generateFeatureBtn');
    if (genBtn) {
        genBtn.disabled = true;
        genBtn.title = "You must test the scenario successfully before generating";
    }
}

function highlightGherkin(text) {
    // Replace strings first so HTML classes don't get caught in the regex later
    return text.replace(/"([^"]*)"/g, '"<span class="string">$1</span>"')
        .replace(/^(Feature:|Scenario:|Scenario Outline:|Background:)/gm, '<span class="keyword">$1</span>')
        .replace(/^(\s*)(Given|When|Then|And|But)(\s)/gm, '$1<span class="keyword">$2</span>$3')
        .replace(/^(\s*#.*)/gm, '<span class="comment">$1</span>')
        .replace(/^(\s*)(@\S+)/gm, '$1<span class="keyword">$2</span>');
}

function copyPreview() {
    const gherkin = generateGherkin();
    navigator.clipboard.writeText(gherkin);
    toast('Copied to clipboard!', 'success');
}

// ============================================
// TEST & GENERATE FEATURE
// ============================================
function validateSetupVariables() {
    for (let setup of setupSteps) {
        if (!setup.path) continue; // Skip empty setups
        if (!setup.storeFields || setup.storeFields.length === 0 || !setup.storeFields[0].key || !setup.storeFields[0].path) {
            toast('Pre-requisite Requests must store at least one response value.', 'warning');
            return false;
        }

        for (let store of setup.storeFields) {
            if (store.key && store.key.trim() !== '') {
                const keyName = store.key.trim();
                const isUsed = flowSteps.some(flow => {
                    return (flow.path && (flow.path.includes(`{{${keyName}}}`) || flow.path.includes(`\${${keyName}}`))) ||
                        (flow.requestBody && (flow.requestBody.includes(`{{${keyName}}}`) || flow.requestBody.includes(`\${${keyName}}`)));
                });

                if (!isUsed) {
                    toast(`Setup error: Variable '${keyName}' is stored but never used in any scenario step.`, 'error');
                    return false;
                }
            }
        }
    }
    return true;
}

async function testUnsavedScenario() {
    const btn = document.getElementById('testScenarioBtn');
    const genBtn = document.getElementById('generateFeatureBtn');

    const gherkin = generateGherkin();
    if (!gherkin.includes('When I send')) {
        toast('Please add at least one endpoint step', 'warning');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Testing...';
    toast('⏳ Running scenario...', 'info');

    try {
        const res = await fetch('/api/run-temp', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: gherkin })
        });
        const data = await res.json();

        const outBox = document.getElementById('testResponseBox');
        if (outBox && data.output) {
            outBox.innerHTML = '<pre style="margin:0; font-family:\'JetBrains Mono\', monospace; font-size:11px; white-space:pre-wrap; color:var(--text);">' + escHtml(data.output).replace(/\[main\] INFO /g, '') + '</pre>';
        } else if (outBox) {
            outBox.innerHTML = '<span class="comment">No output returned</span>';
        }

        if (data.success) {
            toast('✅ Scenario passed! You can now generate it.', 'success');
            genBtn.disabled = false;
            genBtn.title = "Ready to generate";
            if (outBox) outBox.style.borderTop = "3px solid var(--success)";
        } else {
            toast('❌ Scenario failed. Check console or logic.', 'error');
            genBtn.disabled = true;
            genBtn.title = "You must fix the scenario and run successfully before generating";
            if (outBox) outBox.style.borderTop = "3px solid var(--danger)";
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }

    btn.disabled = false;
    btn.innerHTML = '🧪 Test Scenario';
}

async function generateFeature() {
    const isAppend = document.getElementById('featureSelect').value !== 'NEW';
    const featName = document.getElementById('featureFilename').value.trim();
    const sceneName = document.getElementById('scenarioNameInput').value.trim();

    if (!isAppend && !featName) { toast('Please enter a feature name', 'warning'); return; }
    if (!sceneName) { toast('Please enter a scenario name', 'warning'); return; }

    const hasEndpoint = flowSteps.some(s => s.path);
    if (!hasEndpoint) { toast('Please select at least one endpoint', 'warning'); return; }

    if (!validateSetupVariables()) return;

    const gherkin = generateGherkin();
    const fullFilename = isAppend ? document.getElementById('featureSelect').value : featName.replace(/\.feature$/, '') + '.feature';

    try {
        const res = await fetch('/api/save-feature', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename: fullFilename, content: gherkin, append: isAppend })
        });
        const data = await res.json();

        if (data.success) {
            toast(`✅ Feature file saved: ${fullFilename}`, 'success');

            // Client-side state update: move used endpoints to covered
            flowSteps.forEach(step => {
                if (!step.path) return;
                const uncIdx = (coverage.uncovered || []).findIndex(
                    u => u.method === step.method && u.path === step.path);
                if (uncIdx >= 0) {
                    const ep = coverage.uncovered.splice(uncIdx, 1)[0];
                    coverage.covered.push({
                        resource: ep.resource, name: ep.name,
                        method: ep.method, path: ep.path,
                        featureFile: fullFilename,
                        scenarios: [document.getElementById('featureFilename').value.replace(/_/g, ' ')]
                    });
                }
            });

            // Update summary
            coverage.summary.coveredEndpoints = coverage.covered.length;
            coverage.summary.uncoveredEndpoints = coverage.uncovered.length;
            coverage.summary.coveragePercent = Math.round(
                coverage.covered.length / coverage.summary.totalEndpoints * 1000) / 10;

            // Reload features and update dropdown
            const featRes = await fetch('/api/features');
            features = await featRes.json();
            populateFeatureDropdown();

            // Invalidate test state
            document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
            document.getElementById('commitPushBtn').disabled = true;

            renderDashboard();
            resetBuilder();
        } else {
            toast('Failed to save: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (e) {
        toast('Server error: ' + e.message, 'error');
    }
}

function resetBuilder() {
    flowSteps = [];
    setupSteps = [];
    // Explicitly remove all step cards from the UI container
    const stepsContainer = document.getElementById('builderSteps');
    if (stepsContainer) stepsContainer.innerHTML = '';
    const setupContainer = document.getElementById('setupSteps');
    if (setupContainer) setupContainer.innerHTML = '';

    stepIdCounter = 0;
    setupStepIdCounter = 0;
    document.getElementById('featureFilename').value = '';
    document.getElementById('scenarioNameInput').value = '';
    const tagsElem = document.getElementById('scenarioTags');
    if (tagsElem) tagsElem.value = '@smoke';
    document.getElementById('featureSelect').value = 'NEW';
    toggleFeatureInput('NEW');

    // Add one fresh empty step
    addFlowStep();
    updatePreview();
}

// ============================================
// EXISTING SCENARIOS
// ============================================
function renderExisting() {
    const container = document.getElementById('existingList');

    if (features.length === 0) {
        container.innerHTML = '<div style="color:var(--text-muted);padding:40px;text-align:center">No feature files found</div>';
        return;
    }

    container.innerHTML = features.map(f => {
        const scenarios = parseScenarios(f.content);
        return `
        <div style="margin-bottom:20px">
            <h3 style="font-size:14px;color:var(--accent);margin-bottom:10px;font-family:'JetBrains Mono',monospace; display:flex; justify-content:space-between; align-items:center;">
                <span>📄 ${f.name}</span>
                <div style="display:flex; gap:6px;">
                    <button class="secondary-btn" style="padding:4px 8px; font-size:11px; cursor:pointer;" onclick="openEditModal('${f.name}')">✏️ Edit File</button>
                    <button class="primary-btn" style="padding:4px 8px; font-size:11px; cursor:pointer;" onclick="runSingleFile('${f.name}')">▶ Run File</button>
                </div>
            </h3>
            ${scenarios.map(sc => `
            <div class="scenario-card ${sc.commented ? 'commented' : ''}" id="sc-${sc.hash}" draggable="true" ondragstart="dragStart(event, '${f.name}', '${escHtml(sc.name)}')" ondragover="dragOver(event)" ondrop="dragDrop(event, '${f.name}', '${escHtml(sc.name)}')" ondragend="dragEnd(event)">
                <div class="scenario-card-header" onclick="toggleScenarioCard('sc-${sc.hash}')">
                    <h4 style="cursor: grab;">
                        <span style="opacity: 0.5; margin-right: 8px;">☰</span>
                        <span class="badge ${sc.commented ? '' : 'get'}">${sc.commented ? '💬 Commented' : '✅ Active'}</span>
                        ${sc.name}
                    </h4>
                    <div class="scenario-card-actions" style="display:flex; gap:6px;">
                        <button style="border:1px solid #555; background:transparent; color:#ccc; padding:2px 8px; font-size:11px; border-radius:4px; cursor:pointer;" onclick="event.stopPropagation();runSingleScenario('${f.name}','${escHtml(sc.name)}')">
                            ▶ Run
                        </button>
                        <button style="border:1px solid #555; background:transparent; color:#ccc; padding:2px 8px; font-size:11px; border-radius:4px; cursor:pointer;" onclick="event.stopPropagation();openScenarioEditModal('${f.name}','${escHtml(sc.name)}')">
                            ✏️ Edit
                        </button>
                        ${sc.commented ?
                `<button style="border:1px solid #555; background:transparent; color:#ccc; padding:2px 8px; font-size:11px; border-radius:4px; cursor:pointer;" onclick="event.stopPropagation();commentScenario('${f.name}','${escHtml(sc.name)}','uncomment')">
                                ↩ Uncomment</button>` :
                `<button style="border:1px solid #555; background:transparent; color:#ccc; padding:2px 8px; font-size:11px; border-radius:4px; cursor:pointer;" onclick="event.stopPropagation();commentScenario('${f.name}','${escHtml(sc.name)}','comment')">
                                💬 Comment Out</button>`
            }
                        <button style="border:1px solid var(--danger); background:transparent; color:var(--danger); padding:2px 8px; font-size:11px; border-radius:4px; cursor:pointer;" onclick="event.stopPropagation();deleteScenario('${f.name}','${escHtml(sc.name)}')">
                            🗑 Delete
                        </button>
                    </div>
                </div>
                <div class="scenario-card-body"><pre>${escHtml(sc.body)}</pre></div>
            </div>`).join('')}
        </div>`;
    }).join('');
}

function parseScenarios(content) {
    const lines = content.split('\n');
    const scenarios = [];
    let current = null;
    let gapBuffer = [];

    for (const line of lines) {
        const trimmed = line.trim();
        const clean = trimmed.startsWith('#') ? trimmed.substring(1).trim() : trimmed;
        const isScenario = clean.startsWith('Scenario:') || clean.startsWith('Scenario Outline:');

        if (isScenario) {
            if (current) {
                current.body = current.body.replace(/\s+$/, '') + '\n';
                scenarios.push(current);
            }
            const name = clean.replace(/^Scenario(\s+Outline)?:\s*/, '').trim();
            const commented = trimmed.startsWith('#');

            let body = gapBuffer.length > 0 ? gapBuffer.join('\n') + '\n' + line + '\n' : line + '\n';
            current = { name, commented, body: body, hash: hashCode(name) };
            gapBuffer = [];
        } else {
            if (trimmed === '' || trimmed.startsWith('@') || trimmed.startsWith('#')) {
                gapBuffer.push(line);
            } else {
                if (current) {
                    if (gapBuffer.length > 0) {
                        current.body += gapBuffer.join('\n') + '\n';
                        gapBuffer = [];
                    }
                    current.body += line + '\n';
                } else {
                    // It's header text, throw away the gap buffer so it doesn't leak into the first scenario
                    gapBuffer = [];
                }
            }
        }
    }

    if (current) {
        if (gapBuffer.length > 0) {
            current.body += '\n' + gapBuffer.join('\n');
        }
        current.body = current.body.replace(/\s+$/, '') + '\n';
        scenarios.push(current);
    }

    return scenarios;
}

function toggleScenarioCard(id) {
    document.getElementById(id).classList.toggle('expanded');
}

async function commentScenario(filename, scenarioName, action) {
    try {
        const res = await fetch('/api/comment-scenario', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename, scenario: scenarioName, action })
        });
        const data = await res.json();
        if (data.success) {
            toast(`${action === 'comment' ? '💬 Commented out' : '✅ Uncommented'}: ${scenarioName}`, 'success');
            // Reload features and re-render
            const featRes = await fetch('/api/features');
            features = await featRes.json();
            renderExisting();

            // Invalidate test state
            document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
            document.getElementById('commitPushBtn').disabled = true;
        } else {
            toast('Failed: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

async function deleteScenario(filename, scenarioName) {
    if (!confirm(`Are you sure you want to delete scenario: '${scenarioName}'? This action cannot be undone.`)) {
        return;
    }

    try {
        const res = await fetch('/api/delete-scenario', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename, scenario: scenarioName })
        });
        const data = await res.json();
        if (data.success) {
            toast(`🗑 Deleted scenario: ${scenarioName}`, 'success');
            // Reload features and re-render
            const featRes = await fetch('/api/features');
            features = await featRes.json();
            renderExisting();

            // Invalidate test state
            document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
            document.getElementById('commitPushBtn').disabled = true;
        } else {
            toast('Failed to delete: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

// ============================================
// DRAG AND DROP REORDERING
// ============================================

let dragSrcEl = null;
let dragFilename = null;
let dragScenarioName = null;

function dragStart(e, filename, scenarioName) {
    dragSrcEl = e.currentTarget;
    dragFilename = filename;
    dragScenarioName = scenarioName;
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', scenarioName);
    e.currentTarget.style.opacity = '0.4';
}

function dragOver(e) {
    if (e.preventDefault) {
        e.preventDefault();
    }
    e.dataTransfer.dropEffect = 'move';
    return false;
}

async function dragDrop(e, targetFilename, targetScenarioName) {
    if (e.stopPropagation) {
        e.stopPropagation();
    }

    if (dragSrcEl !== e.currentTarget && dragFilename === targetFilename) {
        // We are dropping a scenario onto another scenario in the same file
        try {
            const res = await fetch('/api/reorder-scenario', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    filename: dragFilename,
                    sourceScenario: dragScenarioName,
                    targetScenario: targetScenarioName
                })
            });
            const data = await res.json();
            if (data.success) {
                // Reload features and re-render
                const featRes = await fetch('/api/features');
                features = await featRes.json();
                renderExisting();

                document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
                document.getElementById('commitPushBtn').disabled = true;
            } else {
                toast('Failed to reorder: ' + (data.error || 'Unknown'), 'error');
            }
        } catch (err) {
            toast('Error reordering: ' + err.message, 'error');
        }
    }
    return false;
}

function dragEnd(e) {
    e.currentTarget.style.opacity = '1';
    // Remove all over styling if we had any
}

async function openEditModal(filename) {
    const feature = features.find(f => f.name === filename);
    if (!feature) return;
    document.getElementById('editFilename').value = filename;
    document.getElementById('editContent').value = feature.content;
    document.getElementById('editModalSubtitle').textContent = "Warning: Invalid Gherkin syntax may break the tests.";
    showModal('edit-modal');
}

async function saveEditedFeature() {
    const filename = document.getElementById('editFilename').value;
    const content = document.getElementById('editContent').value;
    try {
        const res = await fetch('/api/save-feature', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename, content, append: false })
        });
        const data = await res.json();
        if (data.success) {
            toast('✅ Feature file updated', 'success');
            closeModals();
            const featRes = await fetch('/api/features');
            features = await featRes.json();
            renderExisting();
            document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
            document.getElementById('commitPushBtn').disabled = true;
        } else {
            toast('Failed to save: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

async function openScenarioEditModal(filename, scenarioName) {
    const feature = features.find(f => f.name === filename);
    if (!feature) return;
    const scenarios = parseScenarios(feature.content);
    const scenario = scenarios.find(s => s.name === scenarioName);
    if (!scenario) return;

    document.getElementById('editScenarioFilename').value = filename;
    document.getElementById('editScenarioOriginalName').value = scenarioName;
    document.getElementById('editScenarioContent').value = scenario.body.trim();

    // Reset test state for modal
    const saveBtn = document.getElementById('saveEditScenarioBtn');
    const testBtn = document.getElementById('testEditScenarioBtn');
    const outBox = document.getElementById('editScenarioTestResult');

    saveBtn.disabled = true;
    saveBtn.title = "You must test the scenario successfully before saving";
    testBtn.innerHTML = '🧪 Test Scenario';
    outBox.style.display = 'none';
    outBox.textContent = '';

    showModal('edit-scenario-modal');
}

async function testEditedScenario() {
    const btn = document.getElementById('testEditScenarioBtn');
    const saveBtn = document.getElementById('saveEditScenarioBtn');
    const outBox = document.getElementById('editScenarioTestResult');
    const content = document.getElementById('editScenarioContent').value;

    if (!content.trim()) {
        toast('Scenario content cannot be empty', 'warning');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Testing...';
    outBox.style.display = 'block';
    outBox.style.borderTop = "3px solid var(--accent)";
    outBox.innerHTML = '<span style="color:var(--accent)">Running test...</span>';
    saveBtn.disabled = true;

    try {
        const res = await fetch('/api/run-temp', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: content })
        });
        const data = await res.json();

        if (data.output) {
            outBox.textContent = data.output;
        }

        if (data.success) {
            toast('✅ Edited scenario passed! You can now save it.', 'success');
            saveBtn.disabled = false;
            saveBtn.title = "Ready to save";
            outBox.style.borderTop = "3px solid var(--success)";
        } else {
            toast('❌ Edited scenario failed. Fix errors before saving.', 'error');
            saveBtn.disabled = true;
            saveBtn.title = "You must fix the scenario and run successfully before saving";
            outBox.style.borderTop = "3px solid var(--danger)";
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
        outBox.textContent = 'Connection error or timeout.';
    }

    btn.disabled = false;
    btn.innerHTML = '🧪 Test Scenario';
}

async function saveEditedScenario() {
    const filename = document.getElementById('editScenarioFilename').value;
    const originalName = document.getElementById('editScenarioOriginalName').value;
    const newContentFragment = document.getElementById('editScenarioContent').value;

    const feature = features.find(f => f.name === filename);
    if (!feature) return;

    const scenarios = parseScenarios(feature.content);
    const scenario = scenarios.find(s => s.name === originalName);
    if (!scenario) return;

    const updatedFullContent = feature.content.replace(scenario.body, newContentFragment + '\n\n');

    try {
        const res = await fetch('/api/save-feature', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename, content: updatedFullContent, append: false })
        });
        const data = await res.json();
        if (data.success) {
            toast('✅ Scenario updated', 'success');
            closeModals();
            const featRes = await fetch('/api/features');
            features = await featRes.json();
            renderExisting();
            document.getElementById('testStatus').textContent = '⚠ Tests need re-run';
            document.getElementById('commitPushBtn').disabled = true;
        } else {
            toast('Failed to save: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

async function runSingleFile(filename) {
    runTestApiCall({ filename });
}

async function runSingleScenario(filename, scenarioName) {
    runTestApiCall({ filename, scenario: scenarioName });
}

async function runTestApiCall(payload) {
    const btn = document.getElementById('runTestsBtn');
    const status = document.getElementById('testStatus');
    btn.disabled = true;
    toast('⏳ Running specific test...', 'info');
    status.innerHTML = '<span style="color:var(--accent)">⏳ Test running...</span>';

    try {
        const res = await fetch('/api/run-single', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (data.success) {
            toast('✅ Test passed!', 'success');
            document.getElementById('commitPushBtn').disabled = false;
            status.innerHTML = `<span style="color:var(--success)">✅ Test Passed</span>`;

            const covRes = await fetch('/api/coverage');
            coverage = await covRes.json();
            renderDashboard();
        } else {
            toast('❌ Test failed', 'error');
            document.getElementById('commitPushBtn').disabled = true;
            status.innerHTML = `<span style="color:var(--danger)">❌ Test Failed</span>`;
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
    btn.disabled = false;
}

// ============================================
// GIT OPERATIONS
// ============================================
async function loadGitStatus() {
    try {
        const res = await fetch('/api/git/status');
        const data = await res.json();
        document.getElementById('gitBranch').textContent = data.branch || 'unknown';

        if (data.testsRan && data.allPassed) {
            document.getElementById('commitPushBtn').disabled = false;
            document.getElementById('testStatus').innerHTML =
                '<span style="color:var(--success)">✅ All tests passed</span>';
        }
    } catch (e) {
        document.getElementById('gitBranch').textContent = 'not available';
    }
}

async function createBranch() {
    const name = document.getElementById('branchNameInput').value;
    if (!name) { toast('Branch name required', 'warning'); return; }

    try {
        const res = await fetch('/api/git/new-branch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ branchName: name })
        });
        const data = await res.json();
        if (data.success) {
            toast(`🌿 Branch created: ${name}`, 'success');
            document.getElementById('gitBranch').textContent = name;
            closeModals();
        } else {
            toast('Failed: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

async function runTests() {
    const btn = document.getElementById('runTestsBtn');
    const status = document.getElementById('testStatus');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Running...';
    status.innerHTML = '<span style="color:var(--accent)">⏳ Tests running...</span>';

    try {
        const res = await fetch('/api/run-tests', { method: 'POST' });
        const data = await res.json();

        if (data.success) {
            status.innerHTML = `<span style="color:var(--success)">✅ ${data.summary}</span>`;
            document.getElementById('commitPushBtn').disabled = false;
            toast('✅ All tests passed!', 'success');

            // Reload coverage data
            const covRes = await fetch('/api/coverage');
            coverage = await covRes.json();
            renderDashboard();
        } else {
            status.innerHTML = `<span style="color:var(--danger)">❌ ${data.summary || 'Tests failed'}</span>`;
            document.getElementById('commitPushBtn').disabled = true;
            toast('❌ Tests failed', 'error');
        }
    } catch (e) {
        status.innerHTML = '<span style="color:var(--danger)">❌ Error running tests</span>';
        toast('Error: ' + e.message, 'error');
    }

    btn.disabled = false;
    btn.innerHTML = '▶ Run Tests';
}

async function commitAndPush() {
    const message = document.getElementById('commitMessageInput').value || 'feat: add new API test scenarios';

    try {
        const res = await fetch('/api/git/commit-push', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
        const data = await res.json();
        if (data.success) {
            toast(`🚀 Pushed to ${data.branch}: "${message}"`, 'success');
            closeModals();
        } else {
            toast('Failed: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (e) {
        toast('Error: ' + e.message, 'error');
    }
}

// ============================================
// MODALS
// ============================================
function showModal(id) {
    document.getElementById(id).classList.add('active');
}

function closeModals() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('active'));
}

// Close modal on overlay click
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal-overlay')) closeModals();
});

// ============================================
// TOAST NOTIFICATIONS
// ============================================
function toast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const div = document.createElement('div');
    div.className = `toast ${type}`;
    div.textContent = message;
    container.appendChild(div);
    setTimeout(() => { div.style.opacity = '0'; setTimeout(() => div.remove(), 300); }, 4000);
}

// ============================================
// UTILS
// ============================================
function escHtml(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function hashCode(s) {
    let h = 0;
    for (let i = 0; i < s.length; i++) h = ((h << 5) - h + s.charCodeAt(i)) | 0;
    return Math.abs(h).toString(36);
}
