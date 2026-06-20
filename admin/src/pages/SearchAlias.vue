<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <h2 class="page-title" style="margin-bottom:0;">搜索别名配置</h2>
      <button class="btn-primary" @click="openAdd">+ 新增映射</button>
    </div>

    <div class="search-bar">
      <input v-model="searchKw" placeholder="搜索别名关键词..." @input="loadList" style="width:260px;" />
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>用户搜索词</th>
          <th>目标分类</th>
          <th>目标关键词</th>
          <th>权重</th>
          <th>来源</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td><span class="tag" style="background:#fef3c7;color:#d97706;">{{ item.alias }}</span></td>
          <td>{{ item.targetCategory || '-' }}</td>
          <td>{{ item.targetKeyword || '-' }}</td>
          <td>{{ ((item.weight || 0.8) * 100).toFixed(0) }}%</td>
          <td>{{ item.source === 0 ? '运营配置' : '自动挖掘' }}</td>
          <td>
            <button class="btn-sm btn-edit" @click="openEdit(item)">编辑</button>
            <button class="btn-sm btn-del" @click="doDelete(item.id)">删除</button>
          </td>
        </tr>
        <tr v-if="list.length === 0"><td colspan="7" class="empty-row">暂无数据</td></tr>
      </tbody>
    </table>

    <div v-if="dialog.show" class="modal-mask" @click.self="dialog.show = false">
      <div class="modal">
        <h3 class="modal-title">{{ dialog.isEdit ? '编辑映射' : '新增映射' }}</h3>
        <div class="form-group">
          <label>用户搜索词 <span style="color:#fe2c55;">*</span></label>
          <input v-model="dialog.form.alias" placeholder="例如: 好吃的" />
        </div>
        <div class="form-group">
          <label>目标分类</label>
          <input v-model="dialog.form.targetCategory" placeholder="例如: 美食" />
        </div>
        <div class="form-group">
          <label>目标关键词</label>
          <input v-model="dialog.form.targetKeyword" placeholder="例如: 美食教程" />
        </div>
        <div class="form-group">
          <label>权重: {{ dialog.form.weight }}%</label>
          <input type="range" v-model.number="dialog.form.weight" min="0" max="100" style="width:100%;" />
        </div>
        <div class="modal-btns">
          <button class="btn-cancel" @click="dialog.show = false">取消</button>
          <button class="btn-primary" :disabled="saving" @click="doSave">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getSearchAliases, createSearchAlias, updateSearchAlias, deleteSearchAlias } from '@/api/admin'
import { toast } from '@/utils/toast'

const list = ref<any[]>([])
const searchKw = ref('')
const saving = ref(false)

const dialog = reactive({
  show: false,
  isEdit: false,
  form: { id: 0, alias: '', targetCategory: '', targetKeyword: '', weight: 80 }
})

async function loadList() {
  try {
    const res: any = await getSearchAliases(searchKw.value || undefined)
    if (res.success) list.value = res.data || []
  } catch (e) { console.error(e) }
}

function openAdd() {
  dialog.isEdit = false
  dialog.form = { id: 0, alias: '', targetCategory: '', targetKeyword: '', weight: 80 }
  dialog.show = true
}

function openEdit(item: any) {
  dialog.isEdit = true
  dialog.form = { ...item, weight: Math.round((item.weight || 0.8) * 100) }
  dialog.show = true
}

async function doSave() {
  const payload = {
    alias: dialog.form.alias.trim(),
    targetCategory: dialog.form.targetCategory.trim() || null,
    targetKeyword: dialog.form.targetKeyword.trim() || null,
    weight: dialog.form.weight / 100,
    source: 0
  }
  saving.value = true
  try {
    const res = dialog.isEdit
      ? await updateSearchAlias(dialog.form.id, payload)
      : await createSearchAlias(payload)
    if (res.success) { dialog.show = false; loadList(); toast.success(dialog.isEdit ? '已更新' : '已创建') }
    else toast.error('保存失败')
  } catch (e) { toast.error('网络错误') } finally { saving.value = false }
}

async function doDelete(id: number) {
  if (!confirm('确定删除？')) return
  try {
    const res: any = await deleteSearchAlias(id)
    if (res.success) { toast.success('已删除'); loadList() }
    else toast.error('删除失败')
  } catch (e) { toast.error('网络错误') }
}

onMounted(() => loadList())
</script>
