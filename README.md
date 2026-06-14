# plugin-photo-gallery

Halo 相册管理插件，支持创建相册、管理照片，前台展示相册列表和详情页。

## 功能

- 相册管理：创建、编辑、删除相册
- 照片管理：上传、排序、批量管理照片
- 相册分组：按分组组织相册（支持父子层级）
- 前台展示：相册列表页和详情页模板
- 公开 API：提供前台数据查询接口
- 安全防护：URL scheme 净化（防 XSS）、相册可见性校验（隐藏相册照片不可被公开访问）
- 可靠删除：finalizer 保护机制，删除相册时级联清理照片，保证最终一致

## 要求

- Halo >= 2.24.0
- Java 21

## 安装

从 [Releases](https://github.com/webrong/plugin-photo-gallery/releases) 下载最新的 JAR 文件，在 Halo 后台插件管理中安装即可。

## 主题集成

插件提供以下模板页面，主题可通过路由访问：

| 路由 | 模板 | 说明 |
| --- | --- | --- |
| `/gallery` | `gallery` | 相册列表页（支持分页 `/gallery/page/{page}`）|
| `/gallery/{slug}` | `gallery_detail` | 相册详情页（支持分页 `/gallery/{slug}/page/{page}`）|

模板中可用的变量：

- **列表页**：`${albums}`（`ListResult<AlbumVo>`，含分页信息和 `status.photoCount`）
- **详情页**：`${album}`（`AlbumVo`）、`${photos}`（`ListResult<PhotoVo>`，含分页信息）

插件提供 `photoGalleryFinder` 供主题模板调用，方法包括：

| 方法 | 返回类型 | 说明 |
| --- | --- | --- |
| `listAlbums()` | `List<AlbumVo>` | 全部可见相册（含照片数）|
| `listAlbumsPaged(page, size)` | `ListResult<AlbumVo>` | 分页查询相册 |
| `getAlbum(slug)` | `AlbumVo` | 根据 slug 获取相册详情 |
| `listPhotos(albumSlug, page, size)` | `ListResult<PhotoVo>` | 分页查询指定相册的照片 |
| `listAllPhotos(page, size)` | `ListResult<PhotoVo>` | 分页查询所有可见照片 |
| `listAlbumGroups()` | `List<AlbumGroupVo>` | 可见相册分组列表 |
| `getAlbumGroup(slug)` | `AlbumGroupVo` | 根据 slug 获取分组 |
| `listAlbumsByGroup(groupName)` | `List<AlbumVo>` | 查询分组下的相册 |

照片对象的 `spec.url` 是图片地址，`spec.thumbnail` 是缩略图地址（可选）。主题可用 `thumbnail.gen()` 生成缩略图。

## 开发

```bash
# 构建前端（ui 目录）
cd ui
pnpm install
pnpm build

# 构建插件（项目根目录）
./gradlew build

# 构建产物位于 build/libs/ 目录
```

## 架构

v1.1.0 起采用 Reconciler 架构：

- **AlbumReconciler**：监听相册变更，级联删除照片（finalizer 保护）、持久化照片数量
- **AlbumGroupReconciler**：监听分组变更，持久化相册数量
- **EndpointUtils**：公共工具类，统一错误处理和参数校验

## 许可证

[GPL-3.0](https://opensource.org/licenses/GPL-3.0)

## 更新日志

### v1.1.1

- hotfix：回退 v1.0.2 误改的 roleTemplate apiVersion（`rbac.authorization.halo.run/v1alpha1` → `v1alpha1`），修复插件停止时 SchemeNotFoundException
- Halo 核心 Role 的 GVK group 是空字符串，apiVersion 应为 `v1alpha1`（无 group 前缀），与核心 role-template-authenticated.yaml 等保持一致

### v1.1.0

- 架构重构：引入 Reconciler 机制（AlbumReconciler + AlbumGroupReconciler）
- 删除相册改为 finalizer 保护 + 异步级联清理照片，保证最终一致
- photoCount / albumCount 持久化到 status，列表查询从 O(N) 优化到 O(1)
- 新增 EndpointUtils 公共类，消除 endpoint 重复代码
- 删除 Album / Photo / AlbumGroup 中从未使用的 permalink 死字段

### v1.0.2

- 安全修复：公开照片端点补充相册可见性校验，防止隐藏相册的照片被未授权访问
- 安全修复：Album / AlbumGroup 的 cover 字段统一调用 UrlSanitizer 净化
- 安全修复：UrlSanitizer 禁止 data:image/svg（防存储型 XSS）
- plugin.yaml 补充 module 字段
- roleTemplate.yaml 保持 apiVersion: v1alpha1（与 Halo 核心 Role GVK group="" 一致）
- 性能优化：N+1 查询并发限制、getAlbum 改用 listBy
- 代码质量：删除 GalleryRouter 死代码

### v1.0.1

- GalleryPlugin 为所有索引函数增加 null 防护
- AlbumGroup 新增 spec.parentName 索引，支持父子层级
- 新增 UrlSanitizer 工具类（URL scheme 白名单净化）
- 新增前端 api.ts，统一封装 XSRF Token 与错误处理
- 相册编辑由独立页面改为弹窗
- build.gradle 提取 haloVersion 变量统一管理依赖版本

### v1.0.0

- 初始版本：相册管理、照片管理、相册分组、前台展示、公开 API
