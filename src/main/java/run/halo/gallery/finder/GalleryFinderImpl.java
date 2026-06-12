package run.halo.gallery.finder;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.app.theme.finders.Finder;
import run.halo.gallery.album.Album;
import run.halo.gallery.group.AlbumGroup;
import run.halo.gallery.photo.Photo;
import run.halo.gallery.vo.AlbumGroupVo;
import run.halo.gallery.vo.AlbumVo;
import run.halo.gallery.vo.PhotoVo;

@Finder("photoGalleryFinder")
@RequiredArgsConstructor
public class GalleryFinderImpl implements GalleryFinder {

    private final ReactiveExtensionClient client;

    @Override
    public Flux<AlbumVo> listAlbums() {
        return client.listAll(Album.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.visible", "true"))
                    .build(),
                Sort.by(Sort.Order.asc("spec.priority"),
                    Sort.Order.desc("metadata.creationTimestamp"))
            )
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count))
                .defaultIfEmpty(AlbumVo.from(album, 0))
            );
    }

    @Override
    public Mono<AlbumVo> getAlbum(String slug) {
        return client.listAll(Album.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.slug", slug))
                    .build(),
                Sort.unsorted()
            )
            .next()
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count))
                .defaultIfEmpty(AlbumVo.from(album, 0))
            );
    }

    @Override
    public Mono<run.halo.app.extension.ListResult<PhotoVo>> listPhotos(String albumSlug,
        Integer page, Integer size) {
        return getAlbum(albumSlug)
            .flatMap(albumVo -> {
                var options = ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.albumName", albumVo.getMetadata().getName()))
                    .andQuery(QueryFactory.equal("spec.visible", "true"))
                    .build();
                var sort = Sort.by(Sort.Order.asc("spec.priority"),
                    Sort.Order.desc("metadata.creationTimestamp"));
                return client.listBy(Photo.class, options,
                    PageRequestImpl.of(page != null ? page : 1,
                        size != null ? size : 20, sort));
            })
            .map(listResult -> {
                List<PhotoVo> vos = new ArrayList<>();
                for (Photo photo : listResult.getItems()) {
                    vos.add(PhotoVo.from(photo));
                }
                return new run.halo.app.extension.ListResult<>(
                    listResult.getPage(), listResult.getSize(),
                    listResult.getTotal(), vos);
            });
    }

    @Override
    public Mono<run.halo.app.extension.ListResult<PhotoVo>> listAllPhotos(Integer page,
        Integer size) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        var sort = Sort.by(Sort.Order.asc("spec.priority"),
            Sort.Order.desc("metadata.creationTimestamp"));
        return client.listBy(Photo.class, options,
                PageRequestImpl.of(page != null ? page : 1,
                    size != null ? size : 20, sort))
            .map(listResult -> {
                List<PhotoVo> vos = new ArrayList<>();
                for (Photo photo : listResult.getItems()) {
                    vos.add(PhotoVo.from(photo));
                }
                return new run.halo.app.extension.ListResult<>(
                    listResult.getPage(), listResult.getSize(),
                    listResult.getTotal(), vos);
            });
    }

    @Override
    public Flux<AlbumGroupVo> listAlbumGroups() {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.notEqual("spec.hideFromList", "true"))
                    .build(),
                Sort.by(Sort.Order.asc("spec.priority"))
            )
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> AlbumGroupVo.from(group, count))
                .defaultIfEmpty(AlbumGroupVo.from(group, 0))
            );
    }

    @Override
    public Mono<AlbumGroupVo> getAlbumGroup(String slug) {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.slug", slug))
                    .build(),
                Sort.unsorted()
            )
            .next()
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> AlbumGroupVo.from(group, count))
                .defaultIfEmpty(AlbumGroupVo.from(group, 0))
            );
    }

    @Override
    public Flux<AlbumVo> listAlbumsByGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .andQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        return client.listAll(Album.class, options,
                Sort.by(Sort.Order.asc("spec.priority"),
                    Sort.Order.desc("metadata.creationTimestamp"))
            )
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count))
                .defaultIfEmpty(AlbumVo.from(album, 0))
            );
    }

    private Mono<Integer> countPhotos(String albumName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.albumName", albumName))
            .andQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        return client.countBy(Photo.class, options).map(Long::intValue);
    }

    private Mono<Integer> countAlbumsInGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .build();
        return client.countBy(Album.class, options).map(Long::intValue);
    }
}
