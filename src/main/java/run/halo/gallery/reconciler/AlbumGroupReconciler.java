package run.halo.gallery.reconciler;

import static run.halo.app.extension.ExtensionUtil.isDeleted;

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
import run.halo.gallery.group.AlbumGroup;

/**
 * Reconciler for {@link AlbumGroup}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>On update: recompute and persist {@code status.albumCount}.</li>
 *   <li>On deletion: no cascade needed (albums can exist without a group).</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class AlbumGroupReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(AlbumGroup.class, request.name()).ifPresent(group -> {
            if (isDeleted(group)) {
                // No cascade needed; albums can exist without a group
                return;
            }

            // Recompute and persist albumCount
            int count = countAlbumsInGroup(group.getMetadata().getName());
            if (group.getStatus() == null) {
                group.setStatus(new AlbumGroup.AlbumGroupStatus());
            }
            group.getStatus().setAlbumCount(count);

            client.update(group);
        });
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new AlbumGroup())
            .workerCount(1)
            .build();
    }

    private int countAlbumsInGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .andQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        return client.listAll(Album.class, options, Sort.unsorted()).size();
    }
}
