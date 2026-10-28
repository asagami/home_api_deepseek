<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Connection,
  CopyDocument,
  Cpu,
  Delete,
  Download,
  Edit,
  Lock,
  MagicStick,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'
import {
  type ApiCodeGenSpec,
  type ApiEndpointSummaryVO,
  type CodeGenPreviewVO,
  type DataSourceOptionVO,
  type FieldSpec,
  getEndpointDetail,
  listCodeGenDataSources,
  listConfiguredEndpoints,
  previewCodeGen,
  updateCodeGenWorkspace,
  writeCodeGenToDisk,
} from '@/api/codegen'
import { copyText } from '@/utils/clipboard'

const activeTab = ref('handler')
const loading = ref(false)
const writing = ref(false)
const dataSources = ref<DataSourceOptionVO[]>([])

// 操作模式：create = 新建接口脚手架, update = 修改已有接口 (仅更新 VO, Req, SQL)
const workMode = ref<'create' | 'update'>('create')
const configuredEndpoints = ref<ApiEndpointSummaryVO[]>([])
const selectedEndpointPath = ref('')
const loadingEndpoint = ref(false)

// JSON 快速解析弹窗
const jsonDialogVisible = ref(false)
const jsonInput = ref('{\n  "username": "postgres",\n  "superuser": true\n}')

// SQL 快速推导弹窗
const sqlDialogVisible = ref(false)
const sqlInput = ref('SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1')

const form = reactive<ApiCodeGenSpec>({
  apiName: 'PostgreSQL用户分页查询',
  domain: 'pg',
  description: '外部 PostgreSQL 数据库用户信息分页查询接口',
  endpointPath: '/api/v1/pg/sample-users',
  costPerCall: 1,
  engineType: 'EXTERNAL_SQL',
  targetDataSourceKey: 'pg-local',
  paged: true,
  customSql: 'SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1',
  esIndexName: 'sample_index',
  upstreamBaseUrl: 'https://api.example.com/v1',
  upstreamHttpMethod: 'GET',
  upstreamHeaders: '{\n  "Accept": "application/json"\n}',
  requestFields: [
    { name: 'username', type: 'String', description: '用户名关键字', example: 'postgres', required: false },
    { name: 'superuser', type: 'Boolean', description: '是否超级管理员', example: 'true', required: false },
  ],
  responseFields: [
    { name: 'userId', type: 'String', description: '用户ID', example: '10' },
    { name: 'username', type: 'String', description: '用户名', example: 'postgres' },
    { name: 'superuser', type: 'Boolean', description: '是否超级用户', example: 'true' },
    { name: 'validUntil', type: 'String', description: '过期时间', example: 'infinity' },
  ],
})

const previewData = ref<CodeGenPreviewVO>({
  reqClassName: 'PgSampleUsersPageReq',
  reqCode: '// 正在生成...',
  voClassName: 'PgSampleUsersOpenVO',
  voCode: '// 正在生成...',
  docClassName: undefined,
  docCode: undefined,
  handlerClassName: 'PgSampleUsersApiHandler',
  handlerCode: '// 正在生成...',
  metaSql: '-- 正在生成...',
})

const isUpdateMode = computed(() => workMode.value === 'update')

const activeCode = computed(() => {
  switch (activeTab.value) {
    case 'req':
      return previewData.value.reqCode
    case 'vo':
      return previewData.value.voCode
    case 'doc':
      return previewData.value.docCode || ''
    case 'handler':
      return previewData.value.handlerCode
    case 'sql':
      return previewData.value.metaSql
    default:
      return ''
  }
})

async function loadDataSources() {
  try {
    const list = await listCodeGenDataSources()
    dataSources.value = list
    if (list.length > 0 && !form.targetDataSourceKey && list[0]) {
      form.targetDataSourceKey = list[0].key
    }
  } catch {
    // 容错处理
  }
}

async function loadConfiguredEndpoints() {
  try {
    const list = await listConfiguredEndpoints()
    configuredEndpoints.value = list
  } catch {
    // 容错处理
  }
}

async function handleModeChange(mode: 'create' | 'update') {
  workMode.value = mode
  if (mode === 'update') {
    await loadConfiguredEndpoints()
    if (configuredEndpoints.value.length > 0) {
      if (!selectedEndpointPath.value || !configuredEndpoints.value.some(e => e.path === selectedEndpointPath.value)) {
        selectedEndpointPath.value = configuredEndpoints.value[0]?.path || ''
      }
      handleSelectEndpoint(selectedEndpointPath.value)
    }
  } else {
    // 切换回新建模式时重置表单为默认模板
    form.apiName = 'PostgreSQL用户分页查询'
    form.domain = 'pg'
    form.description = '外部 PostgreSQL 数据库用户信息分页查询接口'
    form.endpointPath = '/api/v1/pg/sample-users'
    form.costPerCall = 1
    form.engineType = 'EXTERNAL_SQL'
    form.targetDataSourceKey = 'pg-local'
    form.paged = true
    form.customSql = 'SELECT usesysid, usename, usesuper, valuntil FROM pg_catalog.pg_user WHERE 1=1'
    handlePreview()
  }
}

async function handleSelectEndpoint(path: string) {
  if (!path) return
  loadingEndpoint.value = true
  try {
    const detail = await getEndpointDetail(path)
    form.apiName = detail.apiName
    form.domain = detail.domain
    form.description = detail.description || ''
    form.endpointPath = detail.endpointPath
    form.costPerCall = detail.costPerCall
    form.engineType = detail.engineType
    form.targetDataSourceKey = detail.targetDataSourceKey || 'pg-local'
    form.paged = detail.paged ?? true
    form.customSql = detail.customSql || ''
    form.esIndexName = detail.esIndexName || ''
    form.upstreamBaseUrl = detail.upstreamBaseUrl || ''
    form.upstreamHttpMethod = detail.upstreamHttpMethod || 'GET'
    form.upstreamHeaders = detail.upstreamHeaders || ''
    form.requestFields = detail.requestFields || []
    form.responseFields = detail.responseFields || []

    ElMessage.success(`已加载接口 [${detail.apiName}] 的配置规格`)
    await handlePreview()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载接口配置失败')
  } finally {
    loadingEndpoint.value = false
  }
}

async function handlePreview() {
  loading.value = true
  try {
    const res = await previewCodeGen(form)
    previewData.value = res
  } catch {
    // 由统一拦截器提示
  } finally {
    loading.value = false
  }
}

async function handlePrimaryAction() {
  if (isUpdateMode.value) {
    await handleUpdateToDisk()
  } else {
    await handleWriteToDisk()
  }
}

async function handleWriteToDisk() {
  try {
    const docPrompt = form.engineType === 'ES' && previewData.value.docClassName
      ? `, ${previewData.value.docClassName}.java`
      : ''
    await ElMessageBox.confirm(
      `确定将新接口安全落盘至本地项目吗？\n将创建：${previewData.value.reqClassName}.java, ${previewData.value.voClassName}.java${docPrompt}, ${previewData.value.handlerClassName}.java\n（若目标文件已存在将受工程防覆盖规则保护拒绝覆盖）`,
      '确认创建接口代码',
      {
        confirmButtonText: '确定写入',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )

    writing.value = true
    const savedPath = await writeCodeGenToDisk(form)
    ElMessage.success({
      message: `代码已成功写入: ${savedPath || '后端工程 handlers 目录'}`,
      duration: 6000,
      showClose: true,
    })
    loadConfiguredEndpoints()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '写入失败')
    }
  } finally {
    writing.value = false
  }
}

async function handleUpdateToDisk() {
  try {
    await ElMessageBox.confirm(
      `确定原地更新该接口的代码与契约吗？\n\n【变更范围受限声明】：\n1. 覆写更新入参 ${previewData.value.reqClassName}.java\n2. 覆写更新出参 ${previewData.value.voClassName}.java\n3. 通过 SQL 锚点精准更新业务 SQL\n4. 原 Handler 中其他业务逻辑与自定义扩展方法 100% 完整保留\n\n是否继续？`,
      '确认原地更新接口契约',
      {
        confirmButtonText: '确认更新',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )

    writing.value = true
    const savedPath = await updateCodeGenWorkspace(form)
    ElMessage.success({
      message: `接口契约与代码已成功原地更新: ${savedPath}`,
      duration: 6000,
      showClose: true,
    })
    loadConfiguredEndpoints()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '更新失败')
    }
  } finally {
    writing.value = false
  }
}

async function handleCopy() {
  try {
    await copyText(activeCode.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e: any) {
    ElMessage.error(e?.message || '复制失败')
  }
}

function addRequestField() {
  form.requestFields.push({
    name: '',
    type: 'String',
    description: '',
    example: '',
    required: false,
  })
}

function addResponseField() {
  form.responseFields.push({
    name: '',
    type: 'String',
    description: '',
    example: '',
  })
}

function parseJsonToReqFields() {
  try {
    const parsed = JSON.parse(jsonInput.value)
    if (typeof parsed !== 'object' || parsed === null) {
      ElMessage.error('请输入有效的 JSON 对象')
      return
    }
    const fields: FieldSpec[] = []
    for (const [key, val] of Object.entries(parsed)) {
      let type = 'String'
      if (typeof val === 'number') {
        type = Number.isInteger(val) ? 'Integer' : 'BigDecimal'
      } else if (typeof val === 'boolean') {
        type = 'Boolean'
      }
      fields.push({
        name: key,
        type,
        description: key,
        example: String(val),
        required: false,
      })
    }
    form.requestFields = fields
    jsonDialogVisible.value = false
    ElMessage.success(`成功导入 ${fields.length} 个入参字段`)
  } catch (e: any) {
    ElMessage.error(`JSON 解析失败: ${e.message}`)
  }
}

function parseSqlToVoFields() {
  try {
    const sql = sqlInput.value.trim()
    const selectMatch = sql.match(/SELECT\s+([\s\S]+?)\s+FROM/i)
    if (!selectMatch || !selectMatch[1]) {
      ElMessage.error('无法解析 SELECT 字段，请确保包含 SELECT ... FROM 结构')
      return
    }
    const colsStr = selectMatch[1]
    const rawCols = colsStr.split(',')
    const fields: FieldSpec[] = []

    for (let col of rawCols) {
      col = col.trim()
      if (!col) continue
      const asMatch = col.match(/\s+(?:AS\s+)?([a-zA-Z0-9_]+)$/i)
      let fieldName: string = (asMatch && asMatch[1]) ? asMatch[1] : col
      if (fieldName.includes('.')) {
        fieldName = fieldName.split('.').pop() || fieldName
      }
      fieldName = fieldName.replace(/[`"'[\]]/g, '').trim()
      if (!fieldName) continue
      const camelName = fieldName.replace(/_([a-z0-9])/g, (_, letter) => letter.toUpperCase())
      fields.push({
        name: camelName,
        type: 'String',
        description: fieldName,
        example: '',
      })
    }

    form.responseFields = fields
    sqlDialogVisible.value = false
    ElMessage.success(`成功推导 ${fields.length} 个出参字段`)
  } catch (e: any) {
    ElMessage.error(`SQL 推导失败: ${e.message}`)
  }
}

function getEngineTagType(type: string) {
  switch (type) {
    case 'EXTERNAL_SQL': return 'primary'
    case 'INTERNAL_SQL': return 'info'
    case 'ES': return 'success'
    case 'HTTP_PROXY': return 'warning'
    default: return 'info'
  }
}

let previewTimer: any = null
watch(
  form,
  () => {
    clearTimeout(previewTimer)
    previewTimer = setTimeout(handlePreview, 400)
  },
  { deep: true },
)

onMounted(() => {
  loadDataSources()
  loadConfiguredEndpoints()
  handlePreview()
})
</script>

<template>
  <div class="codegen-workbench">
    <!-- 1. 顶部操作工具栏：现代工作台样式 -->
    <div class="workbench-header">
      <div class="header-left">
        <div class="brand-icon">
          <el-icon :size="22"><Cpu /></el-icon>
        </div>
        <div class="header-copy">
          <h2 class="title">接口脚手架代码生成器</h2>
          <p class="subtitle">
            可视化定义 DTO、VO、Handler 与平台元数据，实时生成规范工程代码并一键安全落盘
          </p>
        </div>
      </div>

      <!-- 居中/右侧：模式切换胶囊与统一动作按钮 -->
      <div class="header-right">
        <!-- 模式切换分段控制器 -->
        <div class="segmented-control">
          <button
            type="button"
            class="seg-item"
            :class="{ active: !isUpdateMode }"
            @click="handleModeChange('create')"
          >
            <el-icon><Plus /></el-icon>
            <span>创建新接口</span>
          </button>
          <button
            type="button"
            class="seg-item"
            :class="{ active: isUpdateMode }"
            @click="handleModeChange('update')"
          >
            <el-icon><Edit /></el-icon>
            <span>修改已有接口</span>
          </button>
        </div>

        <div class="action-buttons">
          <el-button
            :loading="loading"
            :icon="Refresh"
            class="secondary-btn"
            @click="handlePreview"
          >
            刷新预览
          </el-button>
          <el-button
            type="primary"
            :loading="writing"
            :icon="isUpdateMode ? Check : Download"
            class="primary-btn"
            @click="handlePrimaryAction"
          >
            {{ isUpdateMode ? '原地更新接口代码' : '写入本地项目工程' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 2. 修改模式专用：精致的接口选择与上下文工具条 -->
    <transition name="fade">
      <div v-if="isUpdateMode" class="endpoint-picker-bar">
        <div class="picker-top-row">
          <div class="picker-main">
            <div class="picker-label">
              <el-icon class="picker-icon"><Connection /></el-icon>
              <span>选择目标接口：</span>
            </div>
            <el-select
              v-model="selectedEndpointPath"
              filterable
              placeholder="搜索已有接口名称或路径..."
              class="endpoint-select"
              :loading="loadingEndpoint"
              @change="handleSelectEndpoint"
            >
              <el-option
                v-for="ep in configuredEndpoints"
                :key="ep.path"
                :label="`${ep.name} (${ep.path})`"
                :value="ep.path"
              >
                <div class="select-option-row">
                  <span class="option-name">{{ ep.name }}</span>
                  <div class="option-meta">
                    <el-tag size="small" :type="getEngineTagType(ep.engineType)" effect="light">
                      {{ ep.engineType }}
                    </el-tag>
                    <span class="option-path">{{ ep.path }}</span>
                  </div>
                </div>
              </el-option>
            </el-select>
          </div>

          <div class="rule-badge">
            <el-icon><Lock /></el-icon>
            <span>接口路由与包名已保护锁定</span>
          </div>
        </div>

        <div class="picker-notice-row">
          <span class="notice-icon">💡</span>
          <span class="notice-text">
            <b>安全契约保护规则</b>：仅支持修改入参 (Req)、出参 (VO) 与业务 SQL；原有 Handler 中的自定义业务逻辑与扩展方法受代码锚点保护严格保留。
          </span>
        </div>
      </div>
    </transition>

    <!-- 3. 主体工作区分栏：左右协同 -->
    <div class="workbench-layout">
      <!-- 左侧：表单配置区 -->
      <div class="form-column">
        <!-- 1. 基础契约 -->
        <div class="panel-card">
          <div class="panel-header">
            <div class="panel-title-wrap">
              <span class="step-num">1</span>
              <h3 class="panel-title">基础契约配置</h3>
            </div>
            <el-tag v-if="isUpdateMode" size="small" type="warning" effect="plain" class="status-tag">
              基础路由锁定中
            </el-tag>
          </div>

          <div class="panel-body">
            <el-form :model="form" label-position="top">
              <el-form-item label="接口中文名称" required>
                <el-input v-model="form.apiName" placeholder="如：PostgreSQL用户分页查询" />
              </el-form-item>

              <div class="grid grid-cols-2 gap-3">
                <el-form-item label="业务领域包名 (domain)" required>
                  <el-input
                    v-model="form.domain"
                    :disabled="isUpdateMode"
                    placeholder="如：pg, order, finance"
                  >
                    <template v-if="isUpdateMode" #suffix>
                      <el-icon class="lock-icon"><Lock /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item label="单次调用单价 (点数)" required>
                  <el-input-number v-model="form.costPerCall" :min="0" :max="100" class="w-full" />
                </el-form-item>
              </div>

              <el-form-item label="对外访问路径 (需以 /api/v1/ 开头)" required>
                <el-input
                  v-model="form.endpointPath"
                  :disabled="isUpdateMode"
                  placeholder="/api/v1/pg/sample-users"
                >
                  <template v-if="isUpdateMode" #suffix>
                    <el-icon class="lock-icon"><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="底层引擎实现方式" required>
                <el-radio-group
                  v-model="form.engineType"
                  :disabled="isUpdateMode"
                  class="engine-radios"
                >
                  <el-radio-button value="EXTERNAL_SQL">外部 SQL</el-radio-button>
                  <el-radio-button value="INTERNAL_SQL">内部 MySQL (MP)</el-radio-button>
                  <el-radio-button value="ES">ES 检索</el-radio-button>
                  <el-radio-button value="HTTP_PROXY">第三方代理</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <!-- 外部数据源选择 -->
              <template v-if="form.engineType === 'EXTERNAL_SQL'">
                <el-form-item label="目标外部数据源" required>
                  <el-select
                    v-model="form.targetDataSourceKey"
                    :disabled="isUpdateMode"
                    placeholder="请选择已登记的数据源"
                    class="w-full"
                  >
                    <el-option
                      v-for="ds in dataSources"
                      :key="ds.key"
                      :label="`${ds.name} (${ds.dbType}) - [${ds.key}]`"
                      :value="ds.key"
                    />
                    <el-option label="本地 PostgreSQL (默认) - [pg-local]" value="pg-local" />
                  </el-select>
                </el-form-item>

                <el-form-item label="核心业务只读 SQL 模板 (支持直接修改)">
                  <el-input
                    v-model="form.customSql"
                    type="textarea"
                    :rows="3"
                    class="sql-editor"
                    placeholder="SELECT id, usesysid, usename FROM pg_catalog.pg_user WHERE 1=1"
                  />
                  <div class="field-hint">
                    💡 原地更新时将通过代码锚点精确替换 Handler 中的 SQL，业务逻辑与映射完全保留。
                  </div>
                </el-form-item>
              </template>

              <!-- 内部 SQL 引擎 -->
              <template v-else-if="form.engineType === 'INTERNAL_SQL'">
                <el-form-item label="核心业务只读 SQL 模板 (可选)">
                  <el-input
                    v-model="form.customSql"
                    type="textarea"
                    :rows="2"
                    class="sql-editor"
                    placeholder="SELECT id, name FROM sys_user WHERE 1=1"
                  />
                </el-form-item>
              </template>

              <!-- ES 检索引擎 -->
              <template v-else-if="form.engineType === 'ES'">
                <el-form-item label="Elasticsearch 目标索引名称" required>
                  <el-input
                    v-model="form.esIndexName"
                    :disabled="isUpdateMode"
                    placeholder="如：news, user_logs, order_index"
                  />
                </el-form-item>
                <div class="engine-tip es-tip">
                  🔍 <b>ES 检索特性</b>：自动生成专属 <code>*EsDoc.java</code> 实体，自动适配防深分页（窗口 10,000 条限制）、<code>trackTotalHits</code> 及对象映射。
                </div>
              </template>

              <!-- HTTP Proxy 代理引擎 -->
              <template v-else-if="form.engineType === 'HTTP_PROXY'">
                <el-form-item label="第三方 API 目标基础 URL" required>
                  <el-input
                    v-model="form.upstreamBaseUrl"
                    :disabled="isUpdateMode"
                    placeholder="https://api.example.com/v1"
                  />
                </el-form-item>

                <div class="grid grid-cols-2 gap-3">
                  <el-form-item label="上游 HTTP 请求方法">
                    <el-select v-model="form.upstreamHttpMethod" :disabled="isUpdateMode" class="w-full">
                      <el-option label="GET" value="GET" />
                      <el-option label="POST" value="POST" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="请求头注入 (可选)">
                    <el-input v-model="form.upstreamHeaders" placeholder='{"Accept":"application/json"}' />
                  </el-form-item>
                </div>
                <div class="engine-tip http-tip">
                  🌐 <b>HTTP 代理特性</b>：内置 2s 连接超时与 5s 读取超时，预置 <code>transformResponse</code> 清洗出参，防止第三方敏感信息外泄。
                </div>
              </template>

              <div class="paged-toggle-row">
                <span class="text-sm font-medium text-slate-700">是否分页接口 (自动适配分页阶梯扣费)</span>
                <el-switch v-model="form.paged" :disabled="form.engineType === 'ES' || isUpdateMode" />
              </div>
            </el-form>
          </div>
        </div>

        <!-- 2. 请求入参 (DTO) -->
        <div class="panel-card">
          <div class="panel-header">
            <div class="panel-title-wrap">
              <span class="step-num">2</span>
              <h3 class="panel-title">请求入参定义 (DTO)</h3>
              <el-tag v-if="isUpdateMode" size="small" type="success" effect="plain" class="status-tag">
                支持自由增删改
              </el-tag>
            </div>
            <div class="panel-actions">
              <el-button size="small" :icon="MagicStick" class="sub-btn" @click="jsonDialogVisible = true">
                从 JSON 导入
              </el-button>
              <el-button size="small" type="primary" :icon="Plus" class="sub-primary-btn" @click="addRequestField">
                添加参数
              </el-button>
            </div>
          </div>

          <div class="panel-body">
            <el-table :data="form.requestFields" size="small" border class="contract-table">
              <el-table-column label="字段名" min-width="110">
                <template #default="{ row }">
                  <el-input v-model="row.name" size="small" placeholder="username" />
                </template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small">
                    <el-option label="String" value="String" />
                    <el-option label="Integer" value="Integer" />
                    <el-option label="Long" value="Long" />
                    <el-option label="Boolean" value="Boolean" />
                    <el-option label="BigDecimal" value="BigDecimal" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.description" size="small" placeholder="参数含义" />
                </template>
              </el-table-column>
              <el-table-column label="必填" width="55" align="center">
                <template #default="{ row }">
                  <el-checkbox v-model="row.required" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="50" align="center">
                <template #default="{ $index }">
                  <el-button
                    type="danger"
                    link
                    :icon="Delete"
                    @click="form.requestFields.splice($index, 1)"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 3. 响应出参 (VO) -->
        <div class="panel-card">
          <div class="panel-header">
            <div class="panel-title-wrap">
              <span class="step-num">3</span>
              <h3 class="panel-title">响应出参定义 (VO)</h3>
              <el-tag v-if="isUpdateMode" size="small" type="success" effect="plain" class="status-tag">
                支持自由增删改
              </el-tag>
            </div>
            <div class="panel-actions">
              <el-button size="small" :icon="MagicStick" class="sub-btn" @click="sqlDialogVisible = true">
                从 SQL 推导
              </el-button>
              <el-button size="small" type="primary" :icon="Plus" class="sub-primary-btn" @click="addResponseField">
                添加字段
              </el-button>
            </div>
          </div>

          <div class="panel-body">
            <el-table :data="form.responseFields" size="small" border class="contract-table">
              <el-table-column label="字段名" min-width="110">
                <template #default="{ row }">
                  <el-input v-model="row.name" size="small" placeholder="userId" />
                </template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small">
                    <el-option label="String" value="String" />
                    <el-option label="Integer" value="Integer" />
                    <el-option label="Long" value="Long" />
                    <el-option label="Boolean" value="Boolean" />
                    <el-option label="BigDecimal" value="BigDecimal" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="130">
                <template #default="{ row }">
                  <el-input v-model="row.description" size="small" placeholder="字段说明" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="50" align="center">
                <template #default="{ $index }">
                  <el-button
                    type="danger"
                    link
                    :icon="Delete"
                    @click="form.responseFields.splice($index, 1)"
                  />
                </template>
              </el-table-column>
            </el-table>

            <!-- 底部提交栏快捷同步 -->
            <div class="form-bottom-bar">
              <el-button :loading="loading" :icon="Refresh" @click="handlePreview">
                刷新代码预览
              </el-button>
              <el-button
                type="primary"
                :loading="writing"
                :icon="isUpdateMode ? Check : Download"
                class="bottom-submit-btn"
                @click="handlePrimaryAction"
              >
                {{ isUpdateMode ? '保存并原地更新接口代码' : '生成并写入本地项目' }}
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：代码实时预览（粘性浮动，与左侧无缝联动） -->
      <div class="preview-column">
        <div class="code-editor-card">
          <!-- 仿真 IDE 标题栏 -->
          <div class="editor-titlebar">
            <div class="window-controls">
              <span class="control-dot red"></span>
              <span class="control-dot yellow"></span>
              <span class="control-dot green"></span>
              <span class="editor-heading">CODE PREVIEW</span>
            </div>

            <div class="editor-meta">
              <span class="badge mode-badge" :class="isUpdateMode ? 'update-mode' : 'create-mode'">
                {{ isUpdateMode ? '原地修改' : '全新生成' }}
              </span>
              <button type="button" class="copy-action-btn" title="复制代码" @click="handleCopy">
                <el-icon><CopyDocument /></el-icon>
                <span class="copy-text">复制代码</span>
              </button>
            </div>
          </div>

          <!-- 文件标签页切换栏 -->
          <div class="editor-tabs">
            <button
              type="button"
              class="tab-item"
              :class="{ active: activeTab === 'req' }"
              @click="activeTab = 'req'"
            >
              {{ previewData.reqClassName }}.java
            </button>
            <button
              type="button"
              class="tab-item"
              :class="{ active: activeTab === 'vo' }"
              @click="activeTab = 'vo'"
            >
              {{ previewData.voClassName }}.java
            </button>
            <button
              v-if="form.engineType === 'ES' && previewData.docClassName"
              type="button"
              class="tab-item"
              :class="{ active: activeTab === 'doc' }"
              @click="activeTab = 'doc'"
            >
              {{ previewData.docClassName }}.java
            </button>
            <button
              type="button"
              class="tab-item"
              :class="{ active: activeTab === 'handler' }"
              @click="activeTab = 'handler'"
            >
              {{ previewData.handlerClassName }}.java
            </button>
            <button
              type="button"
              class="tab-item"
              :class="{ active: activeTab === 'sql' }"
              @click="activeTab = 'sql'"
            >
              init_meta.sql
            </button>
          </div>

          <!-- 源码阅读器展示区 -->
          <div class="editor-content">
            <pre class="code-text"><code>{{ activeCode }}</code></pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗 1：从 JSON 快速导入入参 -->
    <el-dialog v-model="jsonDialogVisible" title="从 JSON 快速解析入参字段" width="500px">
      <p class="text-xs text-slate-500 mb-2">粘贴入参示例 JSON，系统将自动分析字段名及其对应类型：</p>
      <el-input
        v-model="jsonInput"
        type="textarea"
        :rows="8"
        class="sql-editor"
        placeholder='{\n  "keyword": "test",\n  "status": 1\n}'
      />
      <template #footer>
        <el-button @click="jsonDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="parseJsonToReqFields">确认解析并填入</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：从 SQL 快速推导出参 -->
    <el-dialog v-model="sqlDialogVisible" title="从 SQL 快速推导出参字段" width="540px">
      <p class="text-xs text-slate-500 mb-2">粘贴 SELECT 查询语句，系统将自动提取查询列并转换为驼峰出参：</p>
      <el-input
        v-model="sqlInput"
        type="textarea"
        :rows="6"
        class="sql-editor"
        placeholder="SELECT id, user_name, total_amount FROM orders WHERE 1=1"
      />
      <template #footer>
        <el-button @click="sqlDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="parseSqlToVoFields">确认推导并填入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.codegen-workbench {
  padding: 1.25rem clamp(1rem, 2vw, 2rem);
  max-width: 1560px;
  margin: 0 auto;
}

/* 1. 顶部工具栏 */
.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 1.25rem 1.5rem;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.brand-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4f46e5;
  flex: none;
}

.header-copy .title {
  margin: 0;
  font-size: 1.18rem;
  font-weight: 700;
  color: #0f172a;
}

.header-copy .subtitle {
  margin: 0.25rem 0 0;
  font-size: 0.8rem;
  color: #64748b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  flex: none;
}

/* 模式分段控制器 (Segmented Switcher) */
.segmented-control {
  display: flex;
  background: #f1f5f9;
  padding: 3px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.95rem;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 0.82rem;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.seg-item:hover {
  color: #0f172a;
}

.seg-item.active {
  background: #ffffff;
  color: #4f46e5;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.primary-btn {
  background: #4f46e5;
  border-color: #4f46e5;
  font-weight: 600;
  padding: 0.5rem 1.15rem;
}

.primary-btn:hover {
  background: #4338ca;
  border-color: #4338ca;
}

/* 2. 修改模式选择工具条 */
.endpoint-picker-bar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
  padding: 1rem 1.25rem;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-left: 4px solid #4f46e5;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

.picker-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.picker-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.picker-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
}

.picker-icon {
  color: #4f46e5;
  font-size: 1.15rem;
}

.endpoint-select {
  width: 400px;
  max-width: 100%;
}

.select-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.option-name {
  font-weight: 600;
  color: #1e293b;
}

.option-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.option-path {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.75rem;
  color: #94a3b8;
}

.rule-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
  padding: 0.3rem 0.65rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.picker-notice-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  padding: 0.6rem 0.95rem;
  color: #475569;
  font-size: 0.8rem;
  line-height: 1.6;
}

.notice-icon {
  flex-shrink: 0;
  font-size: 0.95rem;
}

.notice-text {
  flex: 1;
}

/* 3. 主体分栏布局 */
.workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.25fr);
  gap: 1.25rem;
  margin-top: 1.25rem;
  align-items: start;
}

.form-column {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* 卡片样式 */
.panel-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.95rem 1.25rem;
  border-bottom: 1px solid #f1f5f9;
  background: #fafafa;
}

.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.step-num {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 0.75rem;
  font-weight: 700;
}

.panel-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: #0f172a;
}

.status-tag {
  font-weight: 500;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.panel-body {
  padding: 1.25rem;
}

.lock-icon {
  color: #f59e0b;
}

.engine-radios {
  display: flex;
  width: 100%;
}

.engine-radios :deep(.el-radio-button) {
  flex: 1;
}

.engine-radios :deep(.el-radio-button__inner) {
  width: 100%;
  font-size: 0.78rem;
  padding: 0.55rem 0.25rem;
}

.field-hint {
  font-size: 0.74rem;
  color: #64748b;
  margin-top: 0.35rem;
}

.engine-tip {
  padding: 0.6rem 0.85rem;
  margin-bottom: 0.85rem;
  border-radius: 8px;
  font-size: 0.76rem;
  line-height: 1.5;
}

.es-tip {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.http-tip {
  background: #faf5ff;
  border: 1px solid #e9d5ff;
  color: #6b21a8;
}

.paged-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.35rem;
}

.contract-table {
  width: 100%;
  border-radius: 6px;
}

.contract-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
}

.form-bottom-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px dashed #e2e8f0;
}

.bottom-submit-btn {
  background: #4f46e5;
  border-color: #4f46e5;
  font-weight: 600;
}

.bottom-submit-btn:hover {
  background: #4338ca;
  border-color: #4338ca;
}

/* 4. 右侧代码预览卡片（粘性浮动与 IDE 质感） */
.preview-column {
  position: sticky;
  top: 1.25rem;
  height: calc(100vh - 10.5rem);
}

.code-editor-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.editor-titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.65rem 0.95rem;
  background: #0b132b;
  border-bottom: 1px solid #1e293b;
  flex: none;
}

.window-controls {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-shrink: 0;
}

.control-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.control-dot.red { background: #ef4444; }
.control-dot.yellow { background: #f59e0b; }
.control-dot.green { background: #10b981; }

.editor-heading {
  margin-left: 0.35rem;
  font-size: 0.68rem;
  letter-spacing: 0.14em;
  font-weight: 700;
  color: #64748b;
  white-space: nowrap;
}

.editor-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.badge {
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.mode-badge.create-mode {
  background: rgba(99, 102, 241, 0.2);
  color: #818cf8;
}

.mode-badge.update-mode {
  background: rgba(245, 158, 11, 0.2);
  color: #fbbf24;
}

.copy-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #e2e8f0;
  font-size: 0.74rem;
  font-weight: 500;
  padding: 0.3rem 0.65rem;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  min-width: 76px;
  justify-content: center;
  transition: all 0.15s ease;
}

.copy-action-btn:hover {
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
}

.copy-text {
  white-space: nowrap;
  word-break: keep-all;
}

/* 标签切换栏 */
.editor-tabs {
  display: flex;
  align-items: center;
  background: #0b132b;
  padding: 0 0.5rem;
  border-bottom: 1px solid #1e293b;
  overflow-x: auto;
  flex: none;
}

.tab-item {
  padding: 0.6rem 0.95rem;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.74rem;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.tab-item:hover {
  color: #e2e8f0;
}

.tab-item.active {
  color: #38bdf8;
  background: #0f172a;
  border-bottom-color: #38bdf8;
  font-weight: 600;
}

/* 源码展示区 */
.editor-content {
  flex: 1;
  overflow: auto;
  background: #0f172a;
  padding: 1.15rem;
}

.code-text {
  margin: 0;
  font-family: 'JetBrains Mono', SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.76rem;
  line-height: 1.65;
  color: #e2e8f0;
  white-space: pre;
}

.sql-editor :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.78rem;
  line-height: 1.6;
  background: #f8fafc;
}

/* 动画过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
