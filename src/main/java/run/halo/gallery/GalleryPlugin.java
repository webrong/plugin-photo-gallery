package run.halo.gallery;

import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.gallery.album.Album;
import run.halo.gallery.group.AlbumGroup;
import run.halo.gallery.photo.Photo;

@Slf4j
public class GalleryPlugin extends BasePlugin {

    private SchemeManager schemeManager;

    @Autowired
    public void setSchemeManager(SchemeManager schemeManager) {
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        log.info("Starting photo-gallery plugin, registering schemes...");
        schemeManager.register(Album.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Album, String>single("spec.slug", String.class)
                .indexFunc(a -> a.getSpec().getSlug())
                .unique(true)
                .build());
            indexSpecs.add(IndexSpecs.<Album, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(a -> a.getSpec().getVisible())
                .build());
            indexSpecs.add(IndexSpecs.<Album, Integer>single("spec.priority", Integer.class)
                .indexFunc(a -> a.getSpec().getPriority())
                .build());
            indexSpecs.add(IndexSpecs.<Album, String>single("spec.groupName", String.class)
                .indexFunc(a -> a.getSpec().getGroupName())
                .build());
        });

        schemeManager.register(Photo.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Photo, String>single("spec.albumName", String.class)
                .indexFunc(p -> p.getSpec().getAlbumName())
                .build());
            indexSpecs.add(IndexSpecs.<Photo, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(p -> p.getSpec().getVisible())
                .build());
            indexSpecs.add(IndexSpecs.<Photo, Integer>single("spec.priority", Integer.class)
                .indexFunc(p -> p.getSpec().getPriority())
                .build());
        });

        schemeManager.register(AlbumGroup.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<AlbumGroup, String>single("spec.slug", String.class)
                .indexFunc(g -> g.getSpec().getSlug())
                .unique(true)
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, Integer>single("spec.priority", Integer.class)
                .indexFunc(g -> g.getSpec().getPriority())
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, String>multi("spec.children", String.class)
                .indexFunc(g -> Optional.ofNullable(g.getSpec().getChildren())
                    .map(Set::copyOf).orElse(Set.of()))
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, Boolean>single("spec.hideFromList", Boolean.class)
                .indexFunc(g -> g.getSpec().isHideFromList())
                .build());
        });
        log.info("Photo-gallery plugin schemes registered successfully.");
    }

    @Override
    public void stop() {
        log.info("Stopping photo-gallery plugin, unregistering schemes...");
        schemeManager.schemes().stream()
            .filter(s -> s.type() == Album.class
                || s.type() == Photo.class
                || s.type() == AlbumGroup.class)
            .forEach(schemeManager::unregister);
        log.info("Photo-gallery plugin schemes unregistered.");
    }
}
