package run.halo.gallery.reconciler;

import static run.halo.app.extension.ExtensionUtil.addFinalizers;
import static run.halo.app.extension.ExtensionUtil.isDeleted;
import static run.halo.app.extension.ExtensionUtil.removeFinalizers;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.album.Album;
import run.halo.gallery.photo.Photo;

/**
 * Reconciler for {@link Album}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Add finalizer on creation to protect against premature deletion.</li>
 *   <li>On deletion: cascade-delete all photos belonging to this album,
 *       then remove the finalizer so GC can reclaim the album.</li>
 *   <li>On update: recompute and persist {@code status.photoCount}.</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class AlbumReconciler implements Reconciler<Reconciler.Request> {

    static final String FINALIZER_NAME = "album-protection";

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Album.class, request.name()).ifPresent(album -> {
            if (isDeleted(album)) {
                // Cascade-delete all photos belonging to this album
                deletePhotosOfAlbum(album.getMetadata().getName());
                // Remove finalizer to allow GC
                if (removeFinalizers(album.getMetadata(), Set.of(FINALIZER_NAME))) {
                    client.update(album);
                    log.info("Album {} deleted with all photos cleaned up",
                        album.getMetadata().getName());
                }
                return;
            }
            // Ensure finalizer is present
            addFinalizers(album.getMetadata(), Set.of(FINALIZER_NAME));

            // Recompute and persist photoCount
            int count = countPhotos(album.getMetadata().getName());
            if (album.getStatus() == null) {
                album.setStatus(new Album.AlbumStatus());
            }
            album.getStatus().setPhotoCount(count);

            client.update(album);
        });
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Album())
            .workerCount(1)
            .build();
    }

    private void deletePhotosOfAlbum(String albumName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.albumName", albumName))
            .build();
        client.listAll(Photo.class, options, Sort.unsorted())
            .forEach(photo -> {
                try {
                    client.delete(photo);
                } catch (Exception e) {
                    log.warn("Failed to delete photo {} of album {}: {}",
                        photo.getMetadata().getName(), albumName, e.getMessage());
                }
            });
    }

    private int countPhotos(String albumName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.albumName", albumName))
            .build();
        return (int) client.listAll(Photo.class, options, Sort.unsorted()).stream()
            .filter(p -> Boolean.TRUE.equals(p.getSpec().getVisible()))
            .count();
    }
}
