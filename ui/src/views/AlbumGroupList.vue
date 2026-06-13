<template>
  <VPageHeader title="分组管理">
    <template #icon>
      <IconGallery />
    </template>
    <template #actions>
      <VButton type="default" @click="$router.push({ name: 'PhotoGallery' })">
        返回相册
      </VButton>
      <VButton type="secondary" @click="openCreateModal">
        <template #icon>
          <IconAddCircle class="h-full w-full" />
        </template>
        新建分组
      </VButton>
    </template>
  </VPageHeader>

  <div class="m-4">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="block w-full bg-gray-50 px-4 py-3">
          <span class="text-base font-medium">共 {{ groups.length }} 个分组</span>
        </div>
      </template>
      <VLoading v-if="loading" />
      <VEmpty v-else-if="groups.length === 0" title="暂无分组" message="点击「新建分组」创建相册分组">
        <template #actions>
          <VSpace>
            <VButton @click="fetchGroups">刷新</VButton>
            <VButton type="secondary" @click="openCreateModal">
              <template #icon><IconAddCircle class="h-full w-full" /></template>
              新建分组
            </VButton>
          </VSpace>
        </template>
      </VEmpty>
      <div v-else>
        <GroupListItem
          v-for="group in rootGroups"
          :key="group.metadata.name"
          :group="group"
          :all-groups="groups"
          :level="0"
          @edit="openEditModal"
          @add-child="openAddChildModal"
          @delete="handleDelete"
        />
      </div>
    </VCard>
  </div>

  <AlbumGroupEditingModal
    v-if="editingModal"
    :group="selectedGroup"
    :groups="availableParents"
    :parent-for-child="parentForChild"
    @close="onEditingModalClose"
    @saved="onEditingModalSaved"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { VButton, VCard, VEmpty, VLoading, VPageHeader, VSpace, Dialog, Toast } from "@halo-dev/components";
import { markRaw, h } from "vue";
import AlbumGroupEditingModal from "../components/AlbumGroupEditingModal.vue";
import GroupListItem from "../components/GroupListItem.vue";
import { apiRequest, ApiError } from "../utils/api";

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

interface AlbumGroup {
  metadata: { name: string; creationTimestamp?: string };
  spec: { displayName: string; slug: string; description?: string; cover?: string; priority?: number; children?: string[]; parentName?: string; hideFromList?: boolean };
  status?: { albumCount?: number; permalink?: string };
}

const groups = ref<AlbumGroup[]>([]);
const loading = ref(true);
const editingModal = ref(false);
const selectedGroup = ref<AlbumGroup | undefined>(undefined);
const parentForChild = ref<AlbumGroup | undefined>(undefined);

const apiBase = "/apis/console.api.gallery.halo.run/v1alpha1";

const childNames = computed(() => {
  const names = new Set<string>();
  groups.value.forEach(g => {
    (g.spec.children || []).forEach(c => names.add(c));
  });
  return names;
});

const rootGroups = computed(() => groups.value.filter(g => !childNames.value.has(g.metadata.name)));

const descendantNames = computed(() => {
  if (!selectedGroup.value) return new Set<string>();
  const result = new Set<string>();
  const byName = new Map(groups.value.map(g => [g.metadata.name, g]));
  function walk(name: string) {
    const g = byName.get(name);
    if (!g) return;
    (g.spec.children || []).forEach(c => {
      if (!result.has(c)) {
        result.add(c);
        walk(c);
      }
    });
  }
  walk(selectedGroup.value.metadata.name);
  return result;
});

const availableParents = computed(() => {
  return groups.value.filter(g => {
    if (selectedGroup.value && g.metadata.name === selectedGroup.value.metadata.name) return false;
    if (selectedGroup.value && descendantNames.value.has(g.metadata.name)) return false;
    return true;
  });
});

async function fetchGroups() {
  loading.value = true;
  try {
    const data = await apiRequest<{ items: AlbumGroup[] }>(`${apiBase}/albumgroups`);
    groups.value = data.items || [];
  } catch (e) {
    Toast.error(e instanceof ApiError ? e.message : "获取分组列表失败");
  } finally {
    loading.value = false;
  }
}

function openCreateModal() {
  selectedGroup.value = undefined;
  parentForChild.value = undefined;
  editingModal.value = true;
}

function openEditModal(group: AlbumGroup) {
  selectedGroup.value = group;
  parentForChild.value = undefined;
  editingModal.value = true;
}

function openAddChildModal(parentGroup: AlbumGroup) {
  selectedGroup.value = undefined;
  parentForChild.value = parentGroup;
  editingModal.value = true;
}

function onEditingModalClose() {
  editingModal.value = false;
  selectedGroup.value = undefined;
  parentForChild.value = undefined;
}

async function onEditingModalSaved() {
  await fetchGroups();
}

function handleDelete(group: AlbumGroup) {
  Dialog.warning({
    title: "删除确认",
    description: `确定要删除分组「${group.spec.displayName}」吗？子分组不会被删除。`,
    confirmType: "danger",
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await apiRequest(`${apiBase}/albumgroups/${group.metadata.name}`, { method: "DELETE" });
        Toast.success("删除成功");
        await fetchGroups();
      } catch (e) {
        Toast.error(e instanceof ApiError ? e.message : "删除失败");
      }
    },
  });
}

onMounted(fetchGroups);
</script>
