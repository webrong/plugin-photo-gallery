package run.halo.gallery.finder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.gallery.vo.AlbumGroupVo;
import run.halo.gallery.vo.AlbumVo;
import run.halo.gallery.vo.PhotoVo;

public interface GalleryFinder {

    Flux<AlbumVo> listAlbums();

    Mono<ListResult<AlbumVo>> listAlbumsPaged(Integer page, Integer size);

    Mono<AlbumVo> getAlbum(String slug);

    Mono<ListResult<PhotoVo>> listPhotos(String albumSlug, Integer page, Integer size);

    Mono<ListResult<PhotoVo>> listAllPhotos(Integer page, Integer size);

    Flux<AlbumGroupVo> listAlbumGroups();

    Mono<AlbumGroupVo> getAlbumGroup(String slug);

    Flux<AlbumVo> listAlbumsByGroup(String groupName);
}
