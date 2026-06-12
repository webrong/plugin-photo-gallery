# plugin-photo-gallery

Halo 相册管理插件，支持创建相册、管理照片，前台展示相册列表和详情页。

## 功能

- 相册管理：创建、编辑、删除相册
- 照片管理：上传、排序、批量管理照片
- 相册分组：按分组组织相册
- 前台展示：相册列表页和详情页模板
- 公开 API：提供前台数据查询接口

## 要求

- Halo >= 2.20.0
- Java 21

## 安装

从 [Releases](https://github.com/webrong/plugin-photo-gallery/releases) 下载最新的 JAR 文件，在 Halo 后台插件管理中安装即可。

## 主题集成

插件提供以下模板页面，主题可通过路由 `/gallery` 和 `/gallery/{slug}` 访问：

- `gallery.html` — 相册列表页
- `gallery_detail.html` — 相册详情页

插件提供 `galleryFinder` 供主题模板调用，获取相册和照片数据。

## 开发

```bash
# 构建
./gradlew build

# 构建产物位于 build/libs/ 目录
```

## 许可证

[GPL-3.0](https://opensource.org/licenses/GPL-3.0)
