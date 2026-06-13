<template>
  <VModal ref="modal" mount-to-body :title="isEdit ? '编辑分组' : '新建分组'" :width="700" @close="emit('close')">
    <div class="space-y-4 md:grid md:grid-cols-4 md:gap-6">
      <div class="md:col-span-1">
        <span class="text-base font-medium text-gray-900">基本信息</span>
      </div>
      <div class="space-y-4 md:col-span-3">
        <div v-if="groups.length > 0">
          <label class="form-label">上级分组</label>
          <select v-model="form.spec.parentName" class="form-input">
            <option value="">无（顶级分组）</option>
            <option v-for="g in groups" :key="g.metadata.name" :value="g.metadata.name">
              {{ g.spec.displayName }}
            </option>
          </select>
          <p v-if="parentForChild" class="text-xs text-gray-400 mt-1">
            默认选中「{{ parentForChild.spec.displayName }}」作为上级分组
          </p>
        </div>

        <div>
          <label class="form-label">名称 <span class="text-red-500">*</span></label>
          <input v-model="form.spec.displayName" class="form-input" placeholder="分组名称" @input="onNameInput" />
        </div>

        <div>
          <label class="form-label">别名 <span class="text-red-500">*</span></label>
          <div class="flex items-center">
            <input v-model="form.spec.slug" class="form-input" placeholder="url-slug" @input="slugManuallyEdited = true" />
            <div class="flex h-full cursor-pointer items-center border-l px-3 hover:bg-gray-100" @click="form.spec.slug = slugify(form.spec.displayName)">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="16" height="16" class="text-gray-500">
                <path fill="currentColor" d="M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z"/>
              </svg>
            </div>
          </div>
        </div>

        <div>
          <label class="form-label">描述</label>
          <textarea v-model="form.spec.description" class="form-input" rows="3" placeholder="分组描述（可选）"></textarea>
        </div>

        <div>
          <label class="form-label">封面图</label>
          <div class="flex gap-2 items-center">
            <input v-model="form.spec.cover" class="form-input flex-1" placeholder="封面图 URL" />
            <VButton size="sm" type="secondary" @click="coverSelectorVisible = true">选择</VButton>
          </div>
          <img v-if="form.spec.cover" :src="form.spec.cover" class="mt-2 w-40 h-28 rounded object-cover" />
        </div>

        <div>
          <label class="flex items-center gap-2">
            <input v-model="form.spec.hideFromList" type="checkbox" />
            <span class="text-sm">隐藏此分组</span>
          </label>
          <p class="text-xs text-gray-400 mt-1">隐藏后不会在前台分组列表中显示，但仍可通过链接访问</p>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-between">
        <VSpace>
          <VButton type="secondary" :loading="saving" @click="handleSave">
            {{ isEdit ? '保存' : '创建' }}
          </VButton>
          <VButton v-if="!isEdit" :loading="saving" @click="handleSave(true)">
            保存并继续
          </VButton>
        </VSpace>
        <VButton @click="modal?.close()">取消</VButton>
      </div>
    </template>
  </VModal>

  <AttachmentSelectorModal
    v-if="coverSelectorVisible"
    :accepts="['image/*']"
    :min="1" :max="1"
    @select="onCoverSelect"
    @close="coverSelectorVisible = false"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { VButton, VModal, VSpace, Toast } from "@halo-dev/components";
import { apiRequest, ApiError } from "../utils/api";

interface AlbumGroup {
  metadata: { name: string; generateName?: string };
  spec: { displayName: string; slug: string; description: string; cover: string; priority: number; children: string[]; parentName: string; hideFromList: boolean };
  apiVersion?: string;
  kind?: string;
}

const props = withDefaults(defineProps<{
  group?: AlbumGroup;
  groups?: AlbumGroup[];
  parentForChild?: AlbumGroup;
}>(), {
  group: undefined,
  groups: () => [],
  parentForChild: undefined,
});
const emit = defineEmits<{ (e: "close"): void; (e: "saved"): void }>();

const modal = ref<InstanceType<typeof VModal> | null>(null);
const saving = ref(false);
const coverSelectorVisible = ref(false);
const isEdit = computed(() => !!props.group);

const apiBase = "/apis/console.api.gallery.halo.run/v1alpha1";

function createForm(): any {
  return {
    apiVersion: "gallery.halo.run/v1alpha1",
    kind: "AlbumGroup",
    metadata: { name: "", generateName: "albumgroup-" },
    spec: {
      displayName: "",
      slug: "",
      description: "",
      cover: "",
      priority: 0,
      children: [],
      parentName: props.parentForChild?.metadata.name || "",
      hideFromList: false,
    },
  };
}

const form = ref(createForm());

function slugify(text: string): string {
  return text.toLowerCase().replace(/[^\w\u4e00-\u9fff]+/g, "-").replace(/^-+|-+$/g, "");
}

let slugManuallyEdited = false;
function onNameInput() {
  if (!slugManuallyEdited) {
    form.value.spec.slug = slugify(form.value.spec.displayName);
  }
}

function onCoverSelect(attachments: any[]) {
  if (!attachments.length) return;
  const att = attachments[0];
  form.value.spec.cover = att.status?.permalink || att.metadata?.name || "";
  coverSelectorVisible.value = false;
}

async function handleSave(keepAdding = false) {
  if (!form.value.spec.displayName || !form.value.spec.slug) {
    Toast.warning("请填写名称和别名");
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await apiRequest(`${apiBase}/albumgroups/${props.group!.metadata.name}`, {
        method: "PUT",
        body: { ...form.value, metadata: props.group!.metadata },
      });
      Toast.success("保存成功");
    } else {
      // Backend handles parent/children maintenance atomically via spec.parentName.
      await apiRequest(`${apiBase}/albumgroups`, {
        method: "POST",
        body: form.value,
      });
      Toast.success("创建成功");
    }

    emit("saved");
    if (keepAdding && !isEdit.value) {
      form.value = createForm();
      slugManuallyEdited = false;
    } else {
      modal.value?.close();
    }
  } catch (e) {
    Toast.error(e instanceof ApiError ? e.message : "操作失败");
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  if (props.group) {
    form.value = JSON.parse(JSON.stringify(props.group));
  }
});
</script>

<style scoped>
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
</style>
