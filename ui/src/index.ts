import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw, h } from "vue";

const IconGallery = markRaw({
  name: "IconGallery",
  render() {
    return h("svg", {
      xmlns: "http://www.w3.org/2000/svg",
      viewBox: "0 0 24 24",
      width: "1.2em",
      height: "1.2em",
      innerHTML: '<path fill="currentColor" d="M20 13c0 1.1-.9 2-2 2h-2v2c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2V9c0-1.1.9-2 2-2h2V5c0-1.1.9-2 2-2h8c1.1 0 2 .9 2 2v6zm-4-2h2V5h-8v2h4c1.1 0 2 .9 2 2v2zm-2 2V9H6v8h8v-4zm-2 4H8v-1h2v1zm0-2H8v-1h2v1zm2 2h-1v-1h1v1zm0-2h-1v-1h1v1z"/>',
    });
  },
});

export default definePlugin({
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/photo-gallery",
        name: "PhotoGallery",
        component: () => import("./views/AlbumList.vue"),
        meta: {
          permissions: ["plugin:photo-gallery:view"],
          menu: {
            name: "相册",
            group: "content",
            icon: IconGallery,
          },
        },
      },
    },
    {
      parentName: "Root",
      route: {
        path: "/photo-gallery/albums/:name/photos",
        name: "PhotoList",
        component: () => import("./views/PhotoList.vue"),
        meta: {
          permissions: ["plugin:photo-gallery:view"],
        },
      },
    },
    {
      parentName: "Root",
      route: {
        path: "/photo-gallery/groups",
        name: "AlbumGroupList",
        component: () => import("./views/AlbumGroupList.vue"),
        meta: {
          permissions: ["plugin:photo-gallery:view"],
        },
      },
    },
  ],
});
