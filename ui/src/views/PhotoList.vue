<template>
  <VPageHeader :title="albumDisplayName + ' - 照片管理'">
    <template #icon>
      <IconGallery />
    </template>
    <template #actions>
      <VButton type="default" @click="$router.push({ name: 'PhotoGallery' })">
        返回列表
      </VButton>
      <VButton type="secondary" @click="openAddModal">
        <template #icon>
          <IconAddCircle class="h-full w-full" />
        </template>
        添加照片
      </VButton>
    </template>
  </VPageHeader>

  <div class="m-4">
    <VCard :body-class="['!p-0']">
      <VLoading v-if="loading" />
      <VEmpty v-else-if="photos.length === 0" title="暂无照片" message="点击「添加照片」开始上传" />
      <div v-else class="photo-grid">
        <div v-for="photo in photos" :key="photo.metadata.name" class="photo-card">
          <img :src="photo.spec.thumbnail || photo.spec.url" :alt="photo.spec.title" />
          <div class="photo-card-overlay">
            <div class="photo-card-actions">
              <VButton size="sm" type="secondary" @click="openEditModal(photo)">编辑</VButton>
              <VButton size="sm" type="danger" @click="handleDelete(photo)">删除</VButton>
            </div>
          </div>
          <div class="photo-card-info">
            <span>{{ photo.spec.title || '未命名' }}</span>
          </div>
        </div>
      </div>
    </VCard>
  </div>

  <!-- 添加照片弹窗 -->
  <VModal v-if="showAddModal" ref="addModalRef" mount-to-body title="添加照片" :width="600" @close="showAddModal = false">
    <div class="space-y-4">
      <div>
        <label class="form-label">标题</label>
        <input v-model="newPhoto.spec.title" class="form-input" placeholder="照片标题（可选）" />
      </div>
      <div>
        <label class="form-label">图片 URL <span class="text-red-500">*</span></label>
        <div class="flex gap-2">
          <input v-model="newPhoto.spec.url" class="form-input flex-1" placeholder="图片地址" />
          <VButton size="sm" type="secondary" @click="addUrlSelectorVisible = true">选择附件</VButton>
        </div>
        <img v-if="newPhoto.spec.url" :src="newPhoto.spec.url" class="mt-2 max-w-full max-h-48 rounded" />
      </div>
      <div>
        <label class="form-label">描述</label>
        <textarea v-model="newPhoto.spec.description" class="form-input" rows="2" placeholder="照片描述（可选）"></textarea>
      </div>
      <div>
        <label class="form-label">缩略图 URL</label>
        <div class="flex gap-2">
          <input v-model="newPhoto.spec.thumbnail" class="form-input flex-1" placeholder="缩略图地址（留空使用原图）" />
          <VButton size="sm" type="secondary" @click="addThumbSelectorVisible = true">选择附件</VButton>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="flex justify-between">
        <VButton type="secondary" :loading="adding" @click="handleAdd">添加</VButton>
        <VButton @click="showAddModal = false">取消</VButton>
      </div>
    </template>
  </VModal>

  <!-- 编辑照片弹窗 -->
  <VModal v-if="showEditModal" ref="editModalRef" mount-to-body title="编辑照片" :width="600" @close="showEditModal = false">
    <div class="space-y-4">
      <div>
        <label class="form-label">标题</label>
        <input v-model="editingPhoto.spec.title" class="form-input" />
      </div>
      <div>
        <label class="form-label">图片 URL</label>
        <div class="flex gap-2">
          <input v-model="editingPhoto.spec.url" class="form-input flex-1" />
          <VButton size="sm" type="secondary" @click="editUrlSelectorVisible = true">选择附件</VButton>
        </div>
      </div>
      <div>
        <label class="form-label">描述</label>
        <textarea v-model="editingPhoto.spec.description" class="form-input" rows="2"></textarea>
      </div>
      <div>
        <label class="form-label">排序</label>
        <input v-model.number="editingPhoto.spec.priority" type="number" class="form-input" />
      </div>
    </div>
    <template #footer>
      <div class="flex justify-between">
        <VButton type="secondary" :loading="saving" @click="handleUpdate">保存</VButton>
        <VButton @click="showEditModal = false">取消</VButton>
      </div>
    </template>
  </VModal>

  <!-- 附件选择器（添加-图片URL） -->
  <AttachmentSelectorModal
    v-if="addUrlSelectorVisible"
    :accepts="['image/*']"
    :min="1" :max="1"
    @select="(atts: any[]) => onSelectAttachment(atts, 'newUrl')"
    @close="addUrlSelectorVisible = false"
  />
  <!-- 附件选择器（添加-缩略图） -->
  <AttachmentSelectorModal
    v-if="addThumbSelectorVisible"
    :accepts="['image/*']"
    :min="1" :max="1"
    @select="(atts: any[]) => onSelectAttachment(atts, 'newThumb')"
    @close="addThumbSelectorVisible = false"
  />
  <!-- 附件选择器（编辑-图片URL） -->
  <AttachmentSelectorModal
    v-if="editUrlSelectorVisible"
    :accepts="['image/*']"
    :min="1" :max="1"
    @select="(atts: any[]) => onSelectAttachment(atts, 'editUrl')"
    @close="editUrlSelectorVisible = false"
  />
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { VButton, VCard, VEmpty, VLoading, VModal, VPageHeader, Dialog, Toast } from "@halo-dev/components";
import { markRaw, h } from "vue";

const IconGallery = markRaw({
  name: "IconGallery",
  render() {
    return h("svg", {
      xmlns: "http://www.w3.org/2000/svg",
      viewBox: "0 0 24 24",
      width: "1em",
      height: "1em",
      innerHTML: '<path fill="currentColor" d="M20 13c0 1.1-.9 2-2 2h-2v2c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2V9c0-1.1.9-2 2-2h2V5c0-1.1.9-2 2-2h8c1.1 0 2 .9 2 2v6zm-4-2h2V5h-8v2h4c1.1 0 2 .9 2 2v2zm-2 2V9H6v8h8v-4zm-2 4H8v-1h2v1zm0-2H8v-1h2v1zm2 2h-1v-1h1v1zm0-2h-1v-1h1v1z"/>',
    });
  },
});

const IconAddCircle = markRaw({
  name: "IconAddCircle",
  render() {
    return h("svg", {
      xmlns: "http://www.w3.org/2000/svg",
      viewBox: "0 0 24 24",
      width: "1em",
      height: "1em",
      innerHTML: '<path fill="currentColor" d="M11 19v-6H5v-2h6V5h2v6h6v2h-6v6z"/>',
    });
  },
});

const route = useRoute();
const albumName = route.params.name as string;
const albumDisplayName = ref(albumName);
const apiBase = "/apis/console.api.gallery.halo.run/v1alpha1";

interface Photo {
  metadata: { name: string };
  spec: { title: string; description: string; url: string; thumbnail: string; albumName: string; priority: number; visible: boolean };
}

const photos = ref<Photo[]>([]);
const loading = ref(true);
const showAddModal = ref(false);
const showEditModal = ref(false);
const adding = ref(false);
const saving = ref(false);

const addUrlSelectorVisible = ref(false);
const addThumbSelectorVisible = ref(false);
const editUrlSelectorVisible = ref(false);

const newPhoto = ref<Photo>(createEmptyPhoto());
const editingPhoto = ref<Photo>(createEmptyPhoto());

function createEmptyPhoto(): Photo {
  return {
    metadata: { name: "" },
    spec: { title: "", description: "", url: "", thumbnail: "", albumName: albumName, priority: 0, visible: true },
  };
}

async function fetchAlbumInfo() {
  try {
    const res = await fetch(`${apiBase}/albums/${albumName}`);
    if (res.ok) {
      const data = await res.json();
      albumDisplayName.value = data.spec?.displayName || albumName;
    }
  } catch (_) {}
}

async function fetchPhotos() {
  loading.value = true;
  try {
    const res = await fetch(`${apiBase}/photos?albumName=${albumName}&size=100`, {
      headers: { "Content-Type": "application/json" },
    });
    if (res.ok) {
      const data = await res.json();
      photos.value = data.items || [];
    }
  } catch (e) {
    console.error("获取照片失败", e);
  } finally {
    loading.value = false;
  }
}

function onSelectAttachment(attachments: any[], target: string) {
  if (!attachments.length) return;
  const att = attachments[0];
  const url = att.status?.permalink || att.metadata?.name || "";
  if (target === "newUrl") {
    newPhoto.value.spec.url = url;
    addUrlSelectorVisible.value = false;
  } else if (target === "newThumb") {
    newPhoto.value.spec.thumbnail = url;
    addThumbSelectorVisible.value = false;
  } else if (target === "editUrl") {
    editingPhoto.value.spec.url = url;
    editUrlSelectorVisible.value = false;
  }
}

function openAddModal() {
  newPhoto.value = createEmptyPhoto();
  showAddModal.value = true;
}

function openEditModal(photo: Photo) {
  editingPhoto.value = JSON.parse(JSON.stringify(photo));
  showEditModal.value = true;
}

async function handleAdd() {
  if (!newPhoto.value.spec.url) {
    Toast.warning("请填写图片 URL");
    return;
  }
  adding.value = true;
  try {
    const body = {
      apiVersion: "gallery.halo.run/v1alpha1",
      kind: "Photo",
      metadata: { generateName: "photo-" },
      spec: { ...newPhoto.value.spec, albumName: albumName },
    };
    const res = await fetch(`${apiBase}/photos`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (res.ok) {
      Toast.success("添加成功");
      showAddModal.value = false;
      newPhoto.value = createEmptyPhoto();
      await fetchPhotos();
    } else {
      Toast.error("添加失败");
    }
  } catch (e) {
    Toast.error("添加失败");
  } finally {
    adding.value = false;
  }
}

async function handleUpdate() {
  saving.value = true;
  try {
    const res = await fetch(`${apiBase}/photos/${editingPhoto.value.metadata.name}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(editingPhoto.value),
    });
    if (res.ok) {
      Toast.success("保存成功");
      showEditModal.value = false;
      await fetchPhotos();
    } else {
      Toast.error("保存失败");
    }
  } catch (e) {
    Toast.error("保存失败");
  } finally {
    saving.value = false;
  }
}

function handleDelete(photo: Photo) {
  Dialog.warning({
    title: "删除确认",
    description: "确定要删除这张照片吗？",
    confirmType: "danger",
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await fetch(`${apiBase}/photos/${photo.metadata.name}`, { method: "DELETE" });
        Toast.success("删除成功");
        await fetchPhotos();
      } catch (e) {
        Toast.error("删除失败");
      }
    },
  });
}

onMounted(() => {
  fetchAlbumInfo();
  fetchPhotos();
});
</script>

<style scoped>
.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  padding: 16px;
}
.photo-card {
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  background: #f9fafb;
}
.photo-card img {
  width: 100%;
  height: 160px;
  object-fit: cover;
}
.photo-card-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}
.photo-card:hover .photo-card-overlay {
  opacity: 1;
}
.photo-card-actions {
  display: flex;
  gap: 8px;
}
.photo-card-info {
  padding: 8px;
  font-size: 13px;
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
</style>
