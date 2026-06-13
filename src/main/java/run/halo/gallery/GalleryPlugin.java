package run.halo.gallery;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
                .indexFunc(a -> albumSpec(a, Album.AlbumSpec::getSlug))
                .unique(true)
                .build());
            indexSpecs.add(IndexSpecs.<Album, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(a -> albumSpec(a, Album.AlbumSpec::getVisible))
                .build());
            indexSpecs.add(IndexSpecs.<Album, Integer>single("spec.priority", Integer.class)
                .indexFunc(a -> albumSpec(a, Album.AlbumSpec::getPriority))
                .build());
            indexSpecs.add(IndexSpecs.<Album, String>single("spec.groupName", String.class)
                .indexFunc(a -> albumSpec(a, Album.AlbumSpec::getGroupName))
                .build());
        });

        schemeManager.register(Photo.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Photo, String>single("spec.albumName", String.class)
                .indexFunc(p -> photoSpec(p, Photo.PhotoSpec::getAlbumName))
                .build());
            indexSpecs.add(IndexSpecs.<Photo, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(p -> photoSpec(p, Photo.PhotoSpec::getVisible))
                .build());
            indexSpecs.add(IndexSpecs.<Photo, Integer>single("spec.priority", Integer.class)
                .indexFunc(p -> photoSpec(p, Photo.PhotoSpec::getPriority))
                .build());
        });

        schemeManager.register(AlbumGroup.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<AlbumGroup, String>single("spec.slug", String.class)
                .indexFunc(g -> groupSpec(g, AlbumGroup.AlbumGroupSpec::getSlug))
                .unique(true)
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, Integer>single("spec.priority", Integer.class)
                .indexFunc(g -> groupSpec(g, AlbumGroup.AlbumGroupSpec::getPriority))
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, String>multi("spec.children", String.class)
                .indexFunc(g -> Optional.ofNullable(g.getSpec())
                    .map(AlbumGroup.AlbumGroupSpec::getChildren)
                    .map(list -> list.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                    .orElse(Set.of()))
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, Boolean>single("spec.hideFromList", Boolean.class)
                .indexFunc(g -> Optional.ofNullable(g.getSpec())
                    .map(AlbumGroup.AlbumGroupSpec::isHideFromList)
                    .orElse(false))
                .build());
            indexSpecs.add(IndexSpecs.<AlbumGroup, String>single("spec.parentName", String.class)
                .indexFunc(g -> groupSpec(g, AlbumGroup.AlbumGroupSpec::getParentName))
                .build());
        });
        log.info("Photo-gallery plugin schemes registered successfully.");
    }

    private static <R> R albumSpec(Album ext, java.util.function.Function<Album.AlbumSpec, R> getter) {
        return Optional.ofNullable(ext).map(Album::getSpec).map(getter).orElse(null);
    }

    private static <R> R photoSpec(Photo ext, java.util.function.Function<Photo.PhotoSpec, R> getter) {
        return Optional.ofNullable(ext).map(Photo::getSpec).map(getter).orElse(null);
    }

    private static <R> R groupSpec(AlbumGroup ext,
        java.util.function.Function<AlbumGroup.AlbumGroupSpec, R> getter) {
        return Optional.ofNullable(ext).map(AlbumGroup::getSpec).map(getter).orElse(null);
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
