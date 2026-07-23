<template>
  <div class="report-assistant">
    <!-- 顶部标题 -->
    <div class="page-header">
      <h2>报表助手 · 自由查询</h2>
      <span class="subtitle">支持自然语言描述或字段勾选两种方式生成报表</span>
    </div>

    <!-- 模式切换 -->
    <el-tabs v-model="activeMode" class="mode-tabs">
      <!-- 自然语言输入模式 -->
      <el-tab-pane label="自然语言输入" name="NLP">
        <div class="nlp-input-area">
          <el-input
            v-model="nlpQuery"
            type="textarea"
            :rows="3"
            placeholder="示例：看2026年6月USD/CNY远期交易的客户名称、交易金额、远期汇率、分行收益点"
            @keydown.enter.exact.prevent="handleParse"
          />
          <div class="nlp-actions">
            <el-button type="primary" :loading="parseLoading" @click="handleParse">解析</el-button>
            <el-button @click="nlpQuery = ''">清空</el-button>
          </div>
        </div>

        <!-- 解析结果展示 -->
        <el-card v-if="parseResult" class="parse-result-card" shadow="never">
          <template #header>
            <span>解析结果</span>
            <span v-if="parseResult.summary" class="parse-summary">{{ parseResult.summary }}</span>
          </template>
          <div v-if="parseResult.selectFields && parseResult.selectFields.length">
            <div class="parse-section">
              <span class="section-label">展示字段：</span>
              <el-tag
                v-for="f in parseResult.selectFields"
                :key="f"
                closable
                @close="removeParsedField(f)"
                class="field-tag"
              >
                {{ fieldDisplayName(f) }}
              </el-tag>
            </div>
          </div>
          <div v-if="parseResult.filters && parseResult.filters.length" class="parse-section">
            <span class="section-label">过滤条件：</span>
            <el-tag v-for="(fc, idx) in parseResult.filters" :key="idx" class="field-tag">
              {{ describeFilter(fc) }}
            </el-tag>
          </div>

          <!-- 分析类型（NLP 模式） -->
          <div v-if="parseResult.analysisType" class="parse-section">
            <span class="section-label">分析类型：</span>
            <el-tag class="field-tag" type="success">
              {{ parseResult.analysisType === 'YOY' ? '同比分析' : '环比分析' }}
            </el-tag>
            <span class="section-label" style="margin-left: 12px;">基准日期：</span>
            <el-tag class="field-tag" type="info">{{ nlpBaseDate }}</el-tag>
          </div>

          <!-- 排序信息（NLP 模式） -->
          <div class="parse-section order-section">
            <span class="section-label">排序信息：<span class="required-mark">*</span></span>
            <div v-for="(ob, idx) in nlpOrderBy" :key="idx" class="order-row">
              <el-select v-model="ob.fieldCode" placeholder="排序字段" size="small" class="order-field">
                <el-option
                  v-for="f in (parseResult.selectFields || [])"
                  :key="f"
                  :label="fieldDisplayName(f)"
                  :value="f"
                />
              </el-select>
              <el-select v-model="ob.direction" placeholder="排序方向" size="small" class="order-dir">
                <el-option label="升序" value="ASC" />
                <el-option label="降序" value="DESC" />
              </el-select>
              <el-button type="danger" size="small" link @click="nlpOrderBy.splice(idx, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="nlpOrderBy.push({ fieldCode: '', direction: 'ASC' })">+ 添加排序</el-button>
          </div>

          <div class="parse-tip">解析完成，请点击下方"生成报表"按钮</div>
        </el-card>
      </el-tab-pane>

      <!-- 字段勾选面板模式 -->
      <el-tab-pane label="字段勾选面板" name="SELECT">
        <div class="select-panel">
          <!-- 左侧：字段树 -->
          <div class="field-tree-area">
            <div class="panel-title">可选字段</div>
            <el-input v-model="fieldSearchKey" placeholder="搜索字段" size="small" clearable class="field-search" />
            <div class="field-tree-scroll">
              <div v-for="(fields, category) in filteredFieldGroups" :key="category" class="field-group">
                <div class="group-title">
                  <el-checkbox
                    :model-value="isGroupAllSelected(fields)"
                    :indeterminate="isGroupIndeterminate(fields)"
                    @change="toggleGroup(fields, $event)"
                  >
                    {{ category }}
                  </el-checkbox>
                </div>
                <div class="group-items">
                  <el-checkbox
                    v-for="f in fields"
                    :key="f.fieldCode"
                    :model-value="selectedFieldCodes.includes(f.fieldCode)"
                    @change="toggleField(f.fieldCode, $event)"
                    class="field-checkbox"
                  >
                    {{ f.displayName }}
                  </el-checkbox>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：已选字段 + 过滤条件 -->
          <div class="selected-area">
            <div class="panel-title">
              已选字段（{{ selectedFields.length }}）
              <span class="hint">拖拽可调整顺序</span>
            </div>
            <div class="selected-list">
              <div v-if="selectedFields.length === 0" class="empty-tip">请从左侧选择字段</div>
              <div
                v-for="(f, idx) in selectedFields"
                :key="f.fieldCode"
                class="selected-item"
                draggable="true"
                @dragstart="onDragStart(idx)"
                @dragover.prevent
                @drop="onDrop(idx)"
              >
                <span class="drag-handle">≡</span>
                <span class="item-name">{{ f.displayName }}</span>
                <el-select
                  v-if="f.isMetric === 'Y'"
                  v-model="aggMap[f.fieldCode]"
                  placeholder="聚合"
                  size="small"
                  class="agg-select"
                  clearable
                >
                  <el-option label="求和 SUM" value="SUM" />
                  <el-option label="平均 AVG" value="AVG" />
                  <el-option label="计数 COUNT" value="COUNT" />
                  <el-option label="最大 MAX" value="MAX" />
                  <el-option label="最小 MIN" value="MIN" />
                </el-select>
                <el-button
                  v-if="f.isDimension === 'Y'"
                  :type="groupByFields.includes(f.fieldCode) ? 'primary' : 'default'"
                  size="small"
                  @click="toggleGroupBy(f.fieldCode)"
                >
                  {{ groupByFields.includes(f.fieldCode) ? '已分组' : '设为分组' }}
                </el-button>
                <el-button type="danger" size="small" link @click="removeSelected(f.fieldCode)">删除</el-button>
              </div>
            </div>

            <!-- 过滤条件 -->
            <div class="filter-section">
              <div class="panel-title">过滤条件</div>
              <div v-for="(fc, idx) in filterConditions" :key="idx" class="filter-row">
                <el-select v-model="fc.fieldCode" placeholder="字段" size="small" class="filter-field" @change="onFilterFieldChange(fc)">
                  <el-option-group v-for="(fields, cat) in fieldGroups" :key="cat" :label="cat">
                    <el-option
                      v-for="f in fields"
                      :key="f.fieldCode"
                      :label="f.displayName"
                      :value="f.fieldCode"
                      :disabled="!f.isFilterable || f.isFilterable === 'N'"
                    />
                  </el-option-group>
                </el-select>
                <el-select v-model="fc.operator" placeholder="操作" size="small" class="filter-op">
                  <el-option label="等于 =" value="EQ" />
                  <el-option label="不等于 !=" value="NE" />
                  <el-option label="大于 >" value="GT" />
                  <el-option label="大于等于 >=" value="GE" />
                  <el-option label="小于 <" value="LT" />
                  <el-option label="小于等于 <=" value="LE" />
                  <el-option label="包含" value="LIKE" />
                  <el-option label="介于" value="BETWEEN" />
                </el-select>
                <!-- 值输入：有字典则下拉，否则文本输入 -->
                <el-select
                  v-if="hasDict(fc.fieldCode) && fc.operator !== 'BETWEEN'"
                  v-model="fc.value"
                  placeholder="请选择"
                  size="small"
                  class="filter-value"
                  clearable
                  filterable
                >
                  <el-option
                    v-for="opt in dictMap[fc.fieldCode] || []"
                    :key="opt.value"
                    :label="opt.label + ' (' + opt.value + ')'"
                    :value="opt.value"
                  />
                </el-select>
                <el-input v-else v-model="fc.value" placeholder="值" size="small" class="filter-value" />
                <el-input v-if="fc.operator === 'BETWEEN'" v-model="fc.value2" placeholder="至" size="small" class="filter-value2" />
                <el-button type="danger" size="small" link @click="filterConditions.splice(idx, 1)">删除</el-button>
              </div>
              <el-button size="small" @click="addFilter">+ 添加条件</el-button>
            </div>

            <!-- 排序信息（SELECT 模式） -->
            <div class="filter-section">
              <div class="panel-title">排序信息 <span class="required-mark">*</span></div>
              <div v-for="(ob, idx) in selectOrderBy" :key="idx" class="filter-row">
                <el-select v-model="ob.fieldCode" placeholder="排序字段" size="small" class="filter-field">
                  <el-option
                    v-for="f in selectedFields"
                    :key="f.fieldCode"
                    :label="f.displayName"
                    :value="f.fieldCode"
                  />
                </el-select>
                <el-select v-model="ob.direction" placeholder="排序方向" size="small" class="filter-op">
                  <el-option label="升序" value="ASC" />
                  <el-option label="降序" value="DESC" />
                </el-select>
                <el-button type="danger" size="small" link @click="selectOrderBy.splice(idx, 1)">删除</el-button>
              </div>
              <el-button size="small" @click="selectOrderBy.push({ fieldCode: '', direction: 'ASC' })">+ 添加排序</el-button>
            </div>

            <!-- 分析选项（SELECT 模式，仅在勾选分析字段时显示） -->
            <div v-if="hasAnalysisField" class="filter-section">
              <div class="panel-title">分析选项 <span class="required-mark">*</span></div>
              <div class="analysis-options">
                <el-select v-model="analysisType" placeholder="分析类型" size="small" class="analysis-select" disabled>
                  <el-option label="同比分析" value="YOY" />
                  <el-option label="环比分析" value="MOM" />
                </el-select>
                <el-select v-model="timeGranularity" placeholder="时间粒度" size="small" class="analysis-select">
                  <el-option label="日" value="DAY" />
                  <el-option label="周" value="WEEK" />
                  <el-option label="月" value="MONTH" />
                  <el-option label="季度" value="QUARTER" />
                  <el-option label="年" value="YEAR" />
                </el-select>
                <el-date-picker v-model="baseDate" type="date" placeholder="基准日期" size="small" class="analysis-date" />
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 查询与导出按钮 -->
    <div class="action-bar">
      <el-button type="primary" :loading="queryLoading" @click="handleQuery" :disabled="!canQuery">
        生成报表
      </el-button>
      <el-button :loading="exportLoading" @click="handleExport" :disabled="!canQuery">
        导出 Excel
      </el-button>
      <el-button @click="resetAll">重置</el-button>
    </div>

    <!-- 报表展示 -->
    <div v-if="queryResult" class="report-result">
      <div class="result-header">
        <span>查询结果（{{ queryResult.total }} 行）</span>
      </div>
      <el-table :data="queryResult.rows" border stripe size="small" class="report-table" max-height="500">
        <el-table-column
          v-for="col in queryResult.columns"
          :key="col.fieldCode"
          :prop="col.fieldCode"
          :label="col.displayName"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="scope">
            <span v-if="isCurrencyField(col.fieldCode) && scope.row[col.fieldCode] !== null && scope.row[col.fieldCode] !== undefined">
              {{ formatNumber(scope.row[col.fieldCode]) }}
            </span>
            <span v-else-if="isRateField(col.fieldCode) && scope.row[col.fieldCode] !== null && scope.row[col.fieldCode] !== undefined">
              {{ scope.row[col.fieldCode] }}%
            </span>
            <span v-else>
              {{ scope.row[col.fieldCode] }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getReportFields, getReportDict, parseNaturalLanguage, queryReport, exportReport } from '@/api/report'

// 模式
const activeMode = ref('NLP')

// 字段元数据
const fieldGroups = ref({})
const fieldMap = ref({})

// 码值字典
const dictMap = ref({})

// 自然语言模式
const nlpQuery = ref('')
const parseLoading = ref(false)
const parseResult = ref(null)

// 字段勾选模式
const fieldSearchKey = ref('')
const selectedFieldCodes = ref([])
const selectedFields = ref([])
const aggMap = ref({})
const groupByFields = ref([])
const filterConditions = ref([])

// 排序条件
const nlpOrderBy = ref([])
const selectOrderBy = ref([])

// 查询结果
const queryLoading = ref(false)
const exportLoading = ref(false)
const queryResult = ref(null)

// 分析选项
const analysisType = ref('')
const timeGranularity = ref('MONTH')
const baseDate = ref('')

// 拖拽
let dragIndex = null

onMounted(() => {
  loadFields()
  loadDict()
})

async function loadFields() {
  try {
    const res = await getReportFields()
    fieldGroups.value = res.data || {}
    const map = {}
    Object.values(fieldGroups.value).forEach(arr => {
      arr.forEach(f => { map[f.fieldCode] = f })
    })
    fieldMap.value = map
  } catch (e) {
    ElMessage.error('加载字段失败')
  }
}

async function loadDict() {
  try {
    const res = await getReportDict()
    dictMap.value = res.data || {}
  } catch (e) {
    // 字典加载失败不影响主流程
  }
}

// 是否有字典
function hasDict(fieldCode) {
  return fieldCode && dictMap.value[fieldCode] && dictMap.value[fieldCode].length > 0
}

// 过滤字段变化时清空值
function onFilterFieldChange(fc) {
  fc.value = ''
  fc.value2 = ''
}

// 过滤后的字段分组（搜索）
const filteredFieldGroups = computed(() => {
  if (!fieldSearchKey.value) return fieldGroups.value
  const key = fieldSearchKey.value.toLowerCase()
  const result = {}
  for (const [cat, fields] of Object.entries(fieldGroups.value)) {
    const filtered = fields.filter(f =>
      f.displayName.toLowerCase().includes(key) || f.fieldCode.toLowerCase().includes(key)
    )
    if (filtered.length) result[cat] = filtered
  }
  return result
})

// 字段显示名
function fieldDisplayName(code) {
  return fieldMap.value[code]?.displayName || code
}

// 是否可以查询：NLP 模式需要解析结果有字段；SELECT 模式需要已选字段
const canQuery = computed(() => {
  if (activeMode.value === 'NLP') {
    return parseResult.value && parseResult.value.selectFields && parseResult.value.selectFields.length > 0
  }
  return selectedFields.value.length > 0
})

// NLP 模式下从过滤条件中提取基准日期（取日期范围的结束日期）
const nlpBaseDate = computed(() => {
  if (!parseResult.value || !parseResult.value.analysisType) return ''
  const filters = parseResult.value.filters || []
  const dateFilter = filters.find(fc => fc.fieldCode === 'trade_date')
  if (dateFilter) {
    let dateStr = ''
    if (dateFilter.operator === 'BETWEEN' && dateFilter.value2) {
      dateStr = dateFilter.value2
    } else if (dateFilter.operator === 'LE' && dateFilter.value) {
      dateStr = dateFilter.value
    } else if (dateFilter.operator === 'GE' && dateFilter.value) {
      dateStr = dateFilter.value
    }
    // 转换 yyyyMMdd -> yyyy-MM-dd
    if (dateStr && dateStr.length === 8 && !dateStr.includes('-')) {
      return dateStr.substring(0, 4) + '-' + dateStr.substring(4, 6) + '-' + dateStr.substring(6, 8)
    }
    return dateStr
  }
  return ''
})

// 格式化日期为 yyyy-MM-dd
function formatDateStr(dateStr) {
  if (!dateStr) return null
  if (dateStr.includes('-')) return dateStr
  if (dateStr.length === 8) {
    return dateStr.substring(0, 4) + '-' + dateStr.substring(4, 6) + '-' + dateStr.substring(6, 8)
  }
  return dateStr
}

// 字段勾选模式：是否勾选了分析计算字段
const hasAnalysisField = computed(() => {
  return selectedFieldCodes.value.some(code => code.startsWith('yoy_') || code.startsWith('mom_'))
})

// 字段勾选模式：根据勾选的分析字段自动设置分析类型（只读）
const autoAnalysisType = computed(() => {
  const hasYoy = selectedFieldCodes.value.some(code => code.startsWith('yoy_'))
  const hasMom = selectedFieldCodes.value.some(code => code.startsWith('mom_'))
  if (hasYoy) return 'YOY'
  if (hasMom) return 'MOM'
  return ''
})

// 字段勾选模式：分析选项是否完整（必填校验）
const analysisOptionsValid = computed(() => {
  if (!hasAnalysisField.value) return true
  return !!analysisType.value && !!timeGranularity.value && !!baseDate.value
})

// 监听勾选的分析字段，自动设置分析类型（不可修改）
watch(autoAnalysisType, (newVal) => {
  analysisType.value = newVal
})

// 当取消勾选所有分析字段时，清空分析选项
watch(hasAnalysisField, (newVal) => {
  if (!newVal) {
    analysisType.value = ''
    baseDate.value = ''
  }
})

// ===== 自然语言解析 =====
async function handleParse() {
  if (!nlpQuery.value.trim()) {
    ElMessage.warning('请输入查询描述')
    return
  }
  parseLoading.value = true
  try {
    const res = await parseNaturalLanguage(nlpQuery.value)
    parseResult.value = res.data
    // 重置排序内容，避免旧的排序信息用在新的报表查询中
    nlpOrderBy.value = []
    // 如果解析结果中包含排序信息，使用解析结果的排序
    if (res.data.orderBy && res.data.orderBy.length > 0) {
      nlpOrderBy.value = res.data.orderBy.map(ob => ({ fieldCode: ob.fieldCode, direction: ob.direction }))
    }
    if (!res.data.selectFields || res.data.selectFields.length === 0) {
      ElMessage.warning('未识别到有效字段，建议使用字段勾选面板')
    } else {
      ElMessage.success('解析完成，请点击"生成报表"')
    }
  } catch (e) {
    ElMessage.error('解析失败')
  } finally {
    parseLoading.value = false
  }
}

function removeParsedField(code) {
  if (parseResult.value && parseResult.value.selectFields) {
    parseResult.value.selectFields = parseResult.value.selectFields.filter(f => f !== code)
  }
}

function describeFilter(fc) {
  const name = fieldDisplayName(fc.fieldCode)
  const opMap = { EQ: '=', NE: '!=', LT: '<', LE: '<=', GT: '>', GE: '>=', LIKE: '包含', BETWEEN: '介于', IN: '属于' }
  const op = opMap[fc.operator] || fc.operator
  // 字典值显示 label
  const valueLabel = dictLabel(fc.fieldCode, fc.value)
  if (fc.operator === 'BETWEEN') return `${name} ${op} ${valueLabel}~${dictLabel(fc.fieldCode, fc.value2)}`
  return `${name} ${op} ${valueLabel}`
}

function dictLabel(fieldCode, value) {
  if (!value) return value
  if (hasDict(fieldCode)) {
    const opt = dictMap.value[fieldCode].find(o => o.value === value)
    if (opt) return opt.label
  }
  return value
}

// ===== 字段勾选 =====
function isGroupAllSelected(fields) {
  return fields.every(f => selectedFieldCodes.value.includes(f.fieldCode))
}
function isGroupIndeterminate(fields) {
  const selected = fields.filter(f => selectedFieldCodes.value.includes(f.fieldCode))
  return selected.length > 0 && selected.length < fields.length
}
function toggleGroup(fields, checked) {
  const codes = fields.map(f => f.fieldCode)
  if (checked) {
    const newCodes = codes.filter(c => !selectedFieldCodes.value.includes(c))
    selectedFieldCodes.value.push(...newCodes)
    newCodes.forEach(c => {
      if (fieldMap.value[c]) selectedFields.value.push(fieldMap.value[c])
    })
  } else {
    selectedFieldCodes.value = selectedFieldCodes.value.filter(c => !codes.includes(c))
    selectedFields.value = selectedFields.value.filter(f => !codes.includes(f.fieldCode))
  }
}
function toggleField(code, checked) {
  if (checked) {
    if (!selectedFieldCodes.value.includes(code)) {
      selectedFieldCodes.value.push(code)
      if (fieldMap.value[code]) selectedFields.value.push(fieldMap.value[code])
    }
  } else {
    selectedFieldCodes.value = selectedFieldCodes.value.filter(c => c !== code)
    selectedFields.value = selectedFields.value.filter(f => f.fieldCode !== code)
    groupByFields.value = groupByFields.value.filter(c => c !== code)
  }
}
function removeSelected(code) {
  selectedFieldCodes.value = selectedFieldCodes.value.filter(c => c !== code)
  selectedFields.value = selectedFields.value.filter(f => f.fieldCode !== code)
  groupByFields.value = groupByFields.value.filter(c => c !== code)
}
function toggleGroupBy(code) {
  const idx = groupByFields.value.indexOf(code)
  if (idx >= 0) {
    groupByFields.value.splice(idx, 1)
  } else {
    groupByFields.value.push(code)
  }
}
function addFilter() {
  filterConditions.value.push({ fieldCode: '', operator: 'EQ', value: '', value2: '' })
}

// 拖拽排序
function onDragStart(idx) { dragIndex = idx }
function onDrop(idx) {
  if (dragIndex === null || dragIndex === idx) return
  const item = selectedFields.value.splice(dragIndex, 1)[0]
  selectedFields.value.splice(idx, 0, item)
  selectedFieldCodes.value = selectedFields.value.map(f => f.fieldCode)
  dragIndex = null
}

// ===== 查询与导出 =====
function getValidOrderBy() {
  const list = activeMode.value === 'NLP' ? nlpOrderBy.value : selectOrderBy.value
  return list.filter(ob => ob.fieldCode && ob.direction)
}

function validateOrderBy() {
  const list = activeMode.value === 'NLP' ? nlpOrderBy.value : selectOrderBy.value
  if (list.length === 0) {
    ElMessage.warning('请至少添加一个排序条件')
    return false
  }
  for (const ob of list) {
    if (!ob.fieldCode) {
      ElMessage.warning('请选择排序字段')
      return false
    }
    if (!ob.direction) {
      ElMessage.warning('请选择排序方向')
      return false
    }
  }
  return true
}

function buildRequest(source) {
  const orderBy = getValidOrderBy()
  const req = {}
  if (activeMode.value === 'NLP' && parseResult.value) {
    req.selectFields = parseResult.value.selectFields || []
    req.aggregations = null
    req.groupBy = parseResult.value.groupBy || null
    req.filters = (parseResult.value.filters || []).filter(fc => fc.fieldCode && fc.operator)
    req.analysisType = parseResult.value.analysisType || null
    req.timeGranularity = parseResult.value.timeGranularity || 'MONTH'
    // NLP 模式下：如果是分析查询，从 trade_date 过滤条件中提取基准日期，并移除 trade_date 过滤条件
    if (req.analysisType) {
      const dateFilter = (req.filters || []).find(fc => fc.fieldCode === 'trade_date')
      if (dateFilter) {
        // 基准日期使用日期范围的结束日期（对于本周、本月等相对时间，结束日期才是基准日期）
        req.baseDate = formatDateStr(dateFilter.value2 || dateFilter.value)
      }
      req.filters = (req.filters || []).filter(fc => fc.fieldCode !== 'trade_date')
    } else {
      req.baseDate = null
    }
  } else {
    req.selectFields = selectedFieldCodes.value
    req.aggregations = { ...aggMap.value }
    req.groupBy = groupByFields.value.length ? groupByFields.value : null
    req.filters = filterConditions.value.filter(fc => fc.fieldCode && fc.operator)
    req.analysisType = analysisType.value || null
    req.timeGranularity = timeGranularity.value || 'MONTH'
    req.baseDate = baseDate.value || null
  }
  req.orderBy = orderBy.length ? orderBy : null
  req.querySource = source
  req.limit = source === 'EXPORT' ? 100000 : 1000
  return req
}

async function handleQuery() {
  if (activeMode.value === 'NLP') {
    if (!parseResult.value || !parseResult.value.selectFields || parseResult.value.selectFields.length === 0) {
      ElMessage.warning('请先输入并解析查询描述')
      return
    }
  } else {
    if (selectedFields.value.length === 0) {
      ElMessage.warning('请至少选择一个字段')
      return
    }
    // 字段勾选模式：如果勾选了分析字段，校验分析选项是否完整
    if (hasAnalysisField.value && !analysisOptionsValid.value) {
      ElMessage.warning('请完整填写分析选项（时间粒度和基准日期）')
      return
    }
  }
  if (!validateOrderBy()) return
  queryLoading.value = true
  try {
    const req = buildRequest(activeMode.value === 'NLP' ? 'NLP' : 'SELECT')
    const res = await queryReport(req)
    queryResult.value = res.data
    if (res.data.total === 0) ElMessage.info('查询无数据')
  } catch (e) {
    ElMessage.error('查询失败：' + (e.message || ''))
  } finally {
    queryLoading.value = false
  }
}

async function handleExport() {
  if (activeMode.value === 'NLP') {
    if (!parseResult.value || !parseResult.value.selectFields || parseResult.value.selectFields.length === 0) {
      ElMessage.warning('请先输入并解析查询描述')
      return
    }
  } else {
    if (selectedFields.value.length === 0) {
      ElMessage.warning('请至少选择一个字段')
      return
    }
  }
  if (!validateOrderBy()) return
  exportLoading.value = true
  try {
    const req = buildRequest('EXPORT')
    const res = await exportReport(req)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
    link.download = `导出报表_${ts}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || ''))
  } finally {
    exportLoading.value = false
  }
}

function resetAll() {
  nlpQuery.value = ''
  parseResult.value = null
  selectedFieldCodes.value = []
  selectedFields.value = []
  aggMap.value = {}
  groupByFields.value = []
  filterConditions.value = []
  nlpOrderBy.value = []
  selectOrderBy.value = []
  queryResult.value = null
  analysisType.value = ''
  timeGranularity.value = 'MONTH'
  baseDate.value = ''
}

function isCurrencyField(fieldCode) {
  return fieldCode === 'counter_amount' || 
         fieldCode.includes('_counter') || 
         fieldCode.includes('business_amount') ||
         fieldCode.includes('_current') ||
         fieldCode.includes('_last')
}

function isRateField(fieldCode) {
  return fieldCode.includes('_rate') || 
         fieldCode.includes('rate') && !fieldCode.includes('exchange')
}

function formatNumber(value) {
  if (typeof value === 'number') {
    return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
  const num = parseFloat(value)
  if (!isNaN(num)) {
    return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
  return value
}
</script>

<style scoped>
.report-assistant {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 18px;
}
.page-header .subtitle {
  color: #909399;
  font-size: 13px;
}
.mode-tabs {
  margin-bottom: 16px;
}
.nlp-input-area {
  max-width: 900px;
}
.nlp-actions {
  margin-top: 8px;
}
.parse-result-card {
  margin-top: 16px;
  max-width: 900px;
}
.parse-result-card :deep(.el-card__header) {
  display: flex;
  align-items: center;
  gap: 12px;
}
.parse-summary {
  color: #606266;
  font-size: 13px;
  font-weight: normal;
}
.parse-section {
  margin-bottom: 12px;
}
.section-label {
  font-weight: bold;
  margin-right: 8px;
}
.field-tag {
  margin: 0 6px 6px 0;
}
.parse-tip {
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}

.select-panel {
  display: flex;
  gap: 16px;
  min-height: 400px;
}
.field-tree-area {
  width: 320px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}
.panel-title {
  padding: 10px 12px;
  font-weight: bold;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  font-size: 13px;
}
.panel-title .hint {
  font-weight: normal;
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.field-search {
  margin: 8px;
  width: calc(100% - 16px);
}
.field-tree-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.field-group {
  margin-bottom: 8px;
}
.group-title {
  font-weight: bold;
  padding: 4px 0;
}
.group-items {
  padding-left: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.field-checkbox {
  margin-right: 8px;
}

.selected-area {
  flex: 1;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}
.selected-list {
  padding: 8px 12px;
  min-height: 120px;
  max-height: 260px;
  overflow-y: auto;
}
.empty-tip {
  color: #c0c4cc;
  text-align: center;
  padding: 20px;
}
.selected-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 6px;
  background: #fff;
}
.drag-handle {
  cursor: move;
  color: #c0c4cc;
  user-select: none;
}
.item-name {
  flex: 1;
  font-size: 13px;
}
.agg-select {
  width: 110px;
}
.filter-section {
  border-top: 1px solid #ebeef5;
  padding: 8px 12px;
}
.filter-row {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
  align-items: center;
}
.filter-field {
  width: 160px;
}
.filter-op {
  width: 110px;
}
.filter-value {
  flex: 1;
  min-width: 100px;
}
.filter-value2 {
  flex: 1;
  min-width: 100px;
}

.analysis-section {
  margin: 16px 0;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}
.analysis-options {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 8px;
}
.analysis-select {
  width: 120px;
}
.analysis-date {
  width: 180px;
}

.action-bar {
  margin: 16px 0;
  display: flex;
  gap: 8px;
}
.report-result {
  margin-top: 16px;
}
.result-header {
  font-weight: bold;
  margin-bottom: 8px;
  font-size: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.currency-info {
  font-weight: normal;
  font-size: 12px;
  color: #909399;
  background: #f0f9ff;
  padding: 2px 8px;
  border-radius: 4px;
}
.report-table {
  width: 100%;
}
.required-mark {
  color: #f56c6c;
  margin-left: 2px;
}
.order-section {
  border-top: 1px dashed #ebeef5;
  padding-top: 12px;
}
.order-row {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
  align-items: center;
}
.order-field {
  width: 200px;
}
.order-dir {
  width: 120px;
}
</style>
