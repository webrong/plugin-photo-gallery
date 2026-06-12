<template>
  <div class="page-container">
    <div class="page-header">
      <h1>{{ isEdit ? '编辑相册' : '新建相册' }}</h1>
      <VButton type="default" @click="$router.back()">返回</VButton>
    </div>

    <VCard>
      <div class="form-body">
        <div class="form-group">
          <label class="form-label">名称 <span class="text-red-500">*</span></label>
          <input v-model="form.spec.displayName" class="form-input" placeholder="相册名称" @input="onNameInput" />
        </div>

        <div class="form-group">
          <label class="form-label">Slug <span class="text-red-500">*</span></label>
          <input v-model="form.spec.slug" class="form-input" placeholder="url-slug" />
        </div>

        <div class="form-group">
          <label class="form-label">描述</label>
          <textarea v-model="form.spec.description" class="form-input" rows="3" placeholder="相册描述（可选）"></textarea>
        </div>

        <div class="form-group">
          <label class="form-label">封面图</label>
          <div class="flex gap-2 items-center">
            <input v-model="form.spec.cover" class="form-input flex-1" placeholder="封面图 URL" />
            <VButton size="sm" type="secondary" @click="selectAttachment">选择附件</VButton>
          </div>
          <img v-if="form.spec.cover" :src="form.spec.cover" class="mt-2 w-40 h-28 rounded object-cover" />
        </div>

        <div class="form-row">
          <div class="form-group flex-1">
            <label class="form-label">排序</label>
            <input v-model.number="form.spec.priority" type="number" class="form-input" placeholder="0" />
          </div>
          <div class="form-group flex-1">
            <label class="form-label">可见性</label>
            <select v-model="form.spec.visible" class="form-input">
              <option :value="true">可见</option>
              <option :value="false">隐藏</option>
            </select>
          </div>
        </div>

        <div class="form-actions">
          <VButton type="default" @click="$router.back()">取消</VButton>
          <VButton type="primary" :loading="saving" @click="handleSave">
            {{ isEdit ? '保存' : '创建' }}
          </VButton>
        </div>
      </div>
    </VCard>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { VButton, VCard, Toast } from "@halo-dev/components";

const route = useRoute();
const isEdit = computed(() => !!route.params.name);
const saving = ref(false);

const apiBase = "/apis/console.api.gallery.halo.run/v1alpha1";

function createForm(): any {
  return {
    apiVersion: "gallery.halo.run/v1alpha1",
    kind: "Album",
    metadata: { generateName: "album-" },
    spec: {
      displayName: "",
      slug: "",
      description: "",
      cover: "",
      priority: 0,
      visible: true,
    },
  };
}

const form = ref(createForm());

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fff]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

let slugManuallyEdited = false;
function onNameInput() {
  if (!slugManuallyEdited) {
    form.value.spec.slug = slugify(form.value.spec.displayName);
  }
}

async function fetchAlbum(name: string) {
  try {
    const res = await fetch(`${apiBase}/albums/${name}`);
    if (res.ok) {
      form.value = await res.json();
    }
  } catch (e) {
    Toast.error("获取相册失败");
  }
}

function selectAttachment() {
  // 使用 Halo 内置附件选择器
  const win = window as any;
  if (win.hsDesktop?.attachmentSelector) {
    win.hsDesktop.attachmentSelector.open((attachment: any) => {
      form.value.spec.cover = attachment.status?.permalink || attachment.spec?.displayName;
    });
  } else {
    Toast.info("请直接输入图片 URL");
  }
}

async function handleSave() {
  if (!form.value.spec.displayName || !form.value.spec.slug) {
    Toast.warning("请填写名称和 Slug");
    return;
  }
  saving.value = true;
  try {
    const url = isEdit.value
      ? `${apiBase}/albums/${route.params.name}`
      : `${apiBase}/albums`;
    const method = isEdit.value ? "PUT" : "POST";
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(form.value),
    });
    if (res.ok) {
      Toast.success(isEdit.value ? "保存成功" : "创建成功");
      history.back();
    } else {
      const err = await res.text();
      Toast.error(`操作失败: ${err}`);
    }
  } catch (e) {
    Toast.error("操作失败");
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  if (route.params.name) {
    fetchAlbum(route.params.name as string);
  }
});
</script>

<style scoped>
.page-container {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 20px;
  font-weight: 600;
}
.form-body {
  padding: 24px;
}
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
}
.form-input:focus {
  outline: none;
  border-color: #4f46e5;
}
.form-row {
  display: flex;
  gap: 16px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}
</style>
