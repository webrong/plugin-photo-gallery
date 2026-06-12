<template>
  <VPageHeader title="相册管理">
    <template #icon>
      <IconGallery />
    </template>
    <template #actions>
      <VButton type="default" @click="$router.push({ name: 'AlbumGroupList' })">
        分组管理
      </VButton>
      <VButton type="secondary" @click="openCreateModal">
        <template #icon>
          <IconAddCircle class="h-full w-full" />
        </template>
        新建
      </VButton>
    </template>
  </VPageHeader>

  <div class="m-4">
    <VCard :body-class="['!p-0']">
      <VLoading v-if="loading" />
      <VEmpty v-else-if="albums.length === 0" title="暂无相册" message="点击上方按钮创建你的第一个相册" />
      <table v-else class="w-full text-sm">
        <thead>
          <tr class="border-b bg-gray-50">
            <th class="px-4 py-3 text-left font-medium">封面</th>
            <th class="px-4 py-3 text-left font-medium">名称</th>
            <th class="px-4 py-3 text-left font-medium">别名</th>
            <th class="px-4 py-3 text-left font-medium">照片数</th>
            <th class="px-4 py-3 text-left font-medium">排序</th>
            <th class="px-4 py-3 text-left font-medium">状态</th>
            <th class="px-4 py-3 text-right font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="album in albums" :key="album.metadata.name" class="border-b hover:bg-gray-50">
            <td class="px-4 py-3">
              <img v-if="album.spec.cover" :src="album.spec.cover" class="w-16 h-12 rounded object-cover" />
              <div v-else class="w-16 h-12 rounded bg-gray-200 flex items-center justify-center text-gray-400">
                无
              </div>
            </td>
            <td class="px-4 py-3 font-medium">{{ album.spec.displayName }}</td>
            <td class="px-4 py-3 text-gray-500">{{ album.spec.slug }}</td>
            <td class="px-4 py-3">{{ album.status?.photoCount ?? 0 }}</td>
            <td class="px-4 py-3">{{ album.spec.priority ?? 0 }}</td>
            <td class="px-4 py-3">
              <VTag v-if="album.spec.visible !== false">可见</VTag>
              <VTag v-else class="bg-gray-100 text-gray-500">隐藏</VTag>
            </td>
            <td class="px-4 py-3 text-right">
              <VSpace>
                <VButton size="sm" type="secondary"
                  @click="$router.push({ name: 'PhotoList', params: { name: album.metadata.name } })">
                  照片
                </VButton>
                <VButton size="sm" type="secondary" @click="openEditModal(album)">
                  编辑
                </VButton>
                <VButton size="sm" type="danger" @click="handleDelete(album)">删除</VButton>
              </VSpace>
            </td>
          </tr>
        </tbody>
      </table>
    </VCard>
  </div>

  <AlbumEditingModal
    v-if="editingModal"
    :album="selectedAlbum"
    @close="onEditingModalClose"
    @saved="onEditingModalSaved"
  />
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { VButton, VCard, VEmpty, VLoading, VPageHeader, VSpace, VTag, Dialog, Toast } from "@halo-dev/components";
import { markRaw, h } from "vue";
import AlbumEditingModal from "../components/AlbumEditingModal.vue";

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

interface Album {
  metadata: { name: string; creationTimestamp?: string };
  spec: { displayName: string; slug: string; description?: string; cover?: string; priority?: number; visible?: boolean };
  status?: { photoCount?: number; permalink?: string };
}

const albums = ref<Album[]>([]);
const loading = ref(true);
const editingModal = ref(false);
const selectedAlbum = ref<Album | undefined>(undefined);

const apiBase = "/apis/console.api.gallery.halo.run/v1alpha1";

async function fetchAlbums() {
  loading.value = true;
  try {
    const res = await fetch(`${apiBase}/albums`, {
      headers: { "Content-Type": "application/json" },
    });
    if (res.ok) {
      const data = await res.json();
      albums.value = data.items || [];
    }
  } catch (e) {
    console.error("获取相册列表失败", e);
  } finally {
    loading.value = false;
  }
}

function openCreateModal() {
  selectedAlbum.value = undefined;
  editingModal.value = true;
}

function openEditModal(album: Album) {
  selectedAlbum.value = album;
  editingModal.value = true;
}

function onEditingModalClose() {
  editingModal.value = false;
  selectedAlbum.value = undefined;
}

async function onEditingModalSaved() {
  await fetchAlbums();
}

function handleDelete(album: Album) {
  Dialog.warning({
    title: "删除确认",
    description: `确定要删除相册「${album.spec.displayName}」吗？相册内的照片也会被删除。`,
    confirmType: "danger",
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await fetch(`${apiBase}/albums/${album.metadata.name}`, { method: "DELETE" });
        Toast.success("删除成功");
        await fetchAlbums();
      } catch (e) {
        Toast.error("删除失败");
      }
    },
  });
}

onMounted(fetchAlbums);
</script>
