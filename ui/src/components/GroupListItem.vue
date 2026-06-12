<template>
  <div>
    <div class="flex items-center justify-between border-b px-4 py-3 hover:bg-gray-50"
         :style="{ paddingLeft: (level * 32 + 16) + 'px' }">
      <div class="flex items-center gap-3 min-w-0">
        <div v-if="group.spec.cover" class="w-10 h-8 rounded bg-gray-100 overflow-hidden flex-shrink-0">
          <img :src="group.spec.cover" class="w-full h-full object-cover" />
        </div>
        <span class="font-medium truncate">{{ group.spec.displayName }}</span>
        <span v-if="group.spec.hideFromList" class="text-xs text-gray-400">(已隐藏)</span>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <span class="text-xs text-gray-400">{{ group.status?.albumCount ?? 0 }} 个相册</span>
        <VButton size="sm" type="secondary" @click="$emit('edit', group)">编辑</VButton>
        <VButton size="sm" type="secondary" @click="$emit('add-child', group)">添加子分组</VButton>
        <VButton size="sm" type="danger" @click="$emit('delete', group)">删除</VButton>
      </div>
    </div>
    <GroupListItem
      v-for="child in childGroups"
      :key="child.metadata.name"
      :group="child"
      :all-groups="allGroups"
      :level="level + 1"
      @edit="$emit('edit', $event)"
      @add-child="$emit('add-child', $event)"
      @delete="$emit('delete', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { VButton } from "@halo-dev/components";

interface AlbumGroup {
  metadata: { name: string; creationTimestamp?: string };
  spec: { displayName: string; slug: string; description?: string; cover?: string; priority?: number; children?: string[]; hideFromList?: boolean };
  status?: { albumCount?: number; permalink?: string };
}

const props = withDefaults(defineProps<{
  group: AlbumGroup;
  allGroups: AlbumGroup[];
  level?: number;
}>(), { level: 0 });

defineEmits<{
  (e: "edit", group: AlbumGroup): void;
  (e: "add-child", group: AlbumGroup): void;
  (e: "delete", group: AlbumGroup): void;
}>();

const childGroups = computed(() => {
  if (!props.group.spec.children?.length) return [];
  return props.group.spec.children
    .map(childName => props.allGroups.find(g => g.metadata.name === childName))
    .filter(Boolean) as AlbumGroup[];
});
</script>
