package run.halo.gallery.finder;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
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

    private static final Sort ALBUM_SORT = Sort.by(
        Sort.Order.asc("spec.priority"),
        Sort.Order.desc("metadata.creationTimestamp"));

    private final ReactiveExtensionClient client;

    @Override
    public Flux<AlbumVo> listAlbums() {
        return client.listAll(Album.class, visibleAlbumQuery(), ALBUM_SORT)
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count)));
    }

    @Override
    public Mono<ListResult<AlbumVo>> listAlbumsPaged(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 12 : size;
        return client.listBy(Album.class, visibleAlbumQuery(), PageRequestImpl.of(p, s, ALBUM_SORT))
            .flatMap(result -> enrichAlbumsWithPhotoCount(result.getItems())
                .collectList()
                .map(vos -> new ListResult<>(result.getPage(), result.getSize(),
                    result.getTotal(), vos)));
    }

    @Override
    public Mono<AlbumVo> getAlbum(String slug) {
        return client.listAll(Album.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.slug", slug))
                    .andQuery(QueryFactory.equal("spec.visible", "true"))
                    .build(),
                Sort.unsorted())
            .next()
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count)));
    }

    @Override
    public Mono<ListResult<PhotoVo>> listPhotos(String albumSlug, Integer page, Integer size) {
        return getAlbum(albumSlug)
            .flatMap(albumVo -> {
                var options = ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.albumName", albumVo.getMetadata().getName()))
                    .andQuery(QueryFactory.equal("spec.visible", "true"))
                    .build();
                int p = page == null || page < 1 ? 1 : page;
                int s = size == null || size < 1 ? 20 : size;
                return client.listBy(Photo.class, options, PageRequestImpl.of(p, s, ALBUM_SORT));
            })
            .map(this::toPhotoVoListResult);
    }

    @Override
    public Mono<ListResult<PhotoVo>> listAllPhotos(Integer page, Integer size) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : size;
        return client.listBy(Photo.class, options, PageRequestImpl.of(p, s, ALBUM_SORT))
            .map(this::toPhotoVoListResult);
    }

    @Override
    public Flux<AlbumGroupVo> listAlbumGroups() {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.notEqual("spec.hideFromList", "true"))
                    .build(),
                Sort.by(Sort.Order.asc("spec.priority"),
                    Sort.Order.desc("metadata.creationTimestamp")))
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> AlbumGroupVo.from(group, count)));
    }

    @Override
    public Mono<AlbumGroupVo> getAlbumGroup(String slug) {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.equal("spec.slug", slug))
                    .andQuery(QueryFactory.notEqual("spec.hideFromList", "true"))
                    .build(),
                Sort.unsorted())
            .next()
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> AlbumGroupVo.from(group, count)));
    }

    @Override
    public Flux<AlbumVo> listAlbumsByGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .andQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        return client.listAll(Album.class, options, ALBUM_SORT)
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count)));
    }

    private Flux<AlbumVo> enrichAlbumsWithPhotoCount(List<Album> albums) {
        return Flux.fromIterable(albums)
            .flatMap(album -> countPhotos(album.getMetadata().getName())
                .map(count -> AlbumVo.from(album, count)));
    }

    private ListResult<PhotoVo> toPhotoVoListResult(ListResult<Photo> result) {
        List<PhotoVo> vos = result.getItems().stream().map(PhotoVo::from).toList();
        return new ListResult<>(result.getPage(), result.getSize(), result.getTotal(), vos);
    }

    private static ListOptions visibleAlbumQuery() {
        return ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
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
